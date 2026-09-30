package io.github.omartelo.nfse4j.core.api;

import io.github.omartelo.nfse4j.core.Ambiente;
import io.github.omartelo.nfse4j.core.Nfse;
import io.github.omartelo.nfse4j.core.NfseContext;
import io.github.omartelo.nfse4j.core.certificate.CertificadoA1;
import io.github.omartelo.nfse4j.core.http.DistribuicaoDfeClient;
import io.github.omartelo.nfse4j.core.http.DistribuicaoDfeException;
import io.github.omartelo.nfse4j.core.http.DrenagemDfe;
import io.github.omartelo.nfse4j.core.http.LoteDistribuicaoDfe;
import io.github.omartelo.nfse4j.core.http.NfseHttpResponse;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais;
import io.github.omartelo.nfse4j.core.service.EmissaoNfseResult;
import io.github.omartelo.nfse4j.core.service.SuccessfulXmlLogger;
import io.github.omartelo.nfse4j.core.xml.dps.Dps;
import io.github.omartelo.nfse4j.core.xml.dps.DpsReemissao;
import io.github.omartelo.nfse4j.core.xml.dps.DpsXmlReader;
import io.github.omartelo.nfse4j.core.xml.evento.CancelamentoNfse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Facade de alto nivel da SDK: uma chamada por operacao de NFS-e, compartilhada por CLI e MCP.
 * Centraliza a trava de producao: operacoes de escrita em PRODUCAO exigem confirmacao explicita.
 */
public final class NfseRunner {
    // Trava de iteracoes da drenagem: o ADN nao informa teto de NSU. Quem precisa de mais chama de novo
    // a partir do ultimoNsu devolvido; o limite tambem segura o tamanho da resposta para o agente.
    private static final int MAX_LOTES_DFE = 10;
    private static final int TAMANHO_RAIZ_CNPJ = 8;

    private NfseRunner() {
    }

    public record EmissaoResult(int statusHttp, boolean sucesso, String ambiente,
                                String chaveAcesso, String corpo, String xmlLogPath) {
    }

    public record RespostaSimples(int statusHttp, boolean sucesso, String ambiente, String corpo) {
    }

    public record DfeResumo(long nsu, String tipoDocumento, String chaveAcesso, String tipoEvento,
                            String dataHoraGeracao) {
    }

    public record DistribuicaoDfeResult(String ambiente, boolean concluida, long ultimoNsu,
                                        List<DfeResumo> documentos) {
    }

    public record DfeDocumento(long nsu, String tipoDocumento, String chaveAcesso, String tipoEvento,
                               String dataHoraGeracao, String xml) {
    }

    public record CertInfo(String alias, String cpfCnpj, String subject, String issuer,
                           String validoDe, String validoAte, boolean expirado) {
    }

    public static CertInfo cert(CertificadoA1 cert) {
        return new CertInfo(
            cert.alias(),
            cert.cpfCnpj().orElse(null),
            cert.subject(),
            cert.issuer(),
            cert.validFrom().toString(),
            cert.validUntil().toString(),
            cert.isExpired(Clock.systemUTC())
        );
    }

    public static EmissaoResult emitir(EmitirNfseRequest request, Ambiente ambiente,
                                       CertificadoA1 cert, boolean confirmarProducao) {
        exigirConfirmacaoProducao(ambiente, confirmarProducao);
        validarCertificado(cert);
        // Quando o prestador nao informa documento, herdamos o do certificado — que precisa existir.
        String cpfCnpjPrestador = cert.cpfCnpj().orElseThrow(() -> new IllegalStateException(
            "Nao foi possivel extrair CPF/CNPJ do certificado; informe o documento do prestador no payload."));
        Dps dps = request.toDps(cpfCnpjPrestador);
        return emitirDps(dps, ambiente, cert, "emitir");
    }

    public static EmissaoResult emitirDeExemplo(String xmlExemplo, DpsReemissao.Overrides overrides,
                                                Ambiente ambiente, CertificadoA1 cert, boolean confirmarProducao) {
        exigirConfirmacaoProducao(ambiente, confirmarProducao);
        validarCertificado(cert);
        Dps exemplo = DpsXmlReader.read(xmlExemplo);
        Dps dps = DpsReemissao.reemitir(exemplo, overrides);
        return emitirDps(dps, ambiente, cert, "emitir-de-exemplo");
    }

    private static EmissaoResult emitirDps(Dps dps, Ambiente ambiente, CertificadoA1 cert, String operacao) {
        Nfse nfse = nfse(ambiente, cert);
        EmissaoNfseResult result = nfse.contribuinte().emitirDetalhado(dps);
        Optional<Path> xmlLog = SuccessfulXmlLogger.saveIfSuccessful(
            ambienteLabel(ambiente) + "-" + operacao, result.response(), result.signedXml());
        NfseHttpResponse response = result.response();
        return new EmissaoResult(
            response.statusCode(),
            response.isSuccessful(),
            ambienteLabel(ambiente),
            campo(response.body(), "chaveAcesso"),
            response.body(),
            xmlLog.map(p -> p.toAbsolutePath().normalize().toString()).orElse(null)
        );
    }

    public static RespostaSimples consultar(String chaveAcesso, Ambiente ambiente, CertificadoA1 cert) {
        NfseHttpResponse response = nfse(ambiente, cert).contribuinte().consultarNfse(chaveAcesso);
        return new RespostaSimples(response.statusCode(), response.isSuccessful(), ambienteLabel(ambiente), response.body());
    }

    public static RespostaSimples cancelar(String chaveAcesso, String cpfCnpjAutor, int numeroPedido,
                                           String codigoMotivo, String descricaoMotivo,
                                           Ambiente ambiente, CertificadoA1 cert, boolean confirmarProducao) {
        exigirConfirmacaoProducao(ambiente, confirmarProducao);
        validarCertificado(cert);
        String autor = (cpfCnpjAutor == null || cpfCnpjAutor.isBlank()) ? cert.cpfCnpj().orElse(null) : cpfCnpjAutor;
        CancelamentoNfse cancelamento = new CancelamentoNfse(
            chaveAcesso, autor, OffsetDateTime.now(), numeroPedido, codigoMotivo, descricaoMotivo, "nfse4j");
        NfseHttpResponse response = nfse(ambiente, cert).contribuinte().cancelar(cancelamento);
        return new RespostaSimples(response.statusCode(), response.isSuccessful(), ambienteLabel(ambiente), response.body());
    }

    /**
     * Drena a distribuicao de DF-e do ADN (notas em que o CNPJ e emitente, tomador ou intermediario) a
     * partir do ultimo NSU ja processado, ate {@value #MAX_LOTES_DFE} lotes. Devolve so o resumo de cada
     * documento; o XML sai por {@link #consultarDfe}. {@code concluida=false}: chame de novo a partir de
     * {@code ultimoNsu}.
     */
    public static DistribuicaoDfeResult distribuirDfe(long ultimoNsuProcessado, String cnpjConsulta,
                                                      Ambiente ambiente, CertificadoA1 cert) {
        exigirMesmaRaizDoCertificado(cnpjConsulta, cert);
        DrenagemDfe drenagem = distribuicaoDfe(ambiente, cert)
            .drenar(ultimoNsuProcessado, cnpjConsulta, MAX_LOTES_DFE);
        List<DfeResumo> documentos = drenagem.documentos().stream()
            .map(d -> new DfeResumo(d.nsu(), d.tipoDocumento(), d.chaveAcesso(), d.tipoEvento(), d.dataHoraGeracao()))
            .toList();
        return new DistribuicaoDfeResult(ambienteLabel(ambiente), drenagem.concluida(), drenagem.ultimoNsu(), documentos);
    }

    /** Documento de um NSU com o XML ja desempacotado. */
    public static DfeDocumento consultarDfe(long nsu, String cnpjConsulta, Ambiente ambiente, CertificadoA1 cert) {
        exigirMesmaRaizDoCertificado(cnpjConsulta, cert);
        LoteDistribuicaoDfe lote = distribuicaoDfe(ambiente, cert).consultarNsu(nsu, cnpjConsulta);
        LoteDistribuicaoDfe.Documento documento = lote.loteDfe().stream()
            .filter(d -> d.nsu() == nsu)
            .findFirst()
            .orElseThrow(() -> new DistribuicaoDfeException("Nenhum DF-e no NSU " + nsu + " ("
                + lote.statusProcessamento() + "): " + lote.descreverErros()));
        return new DfeDocumento(documento.nsu(), documento.tipoDocumento(), documento.chaveAcesso(),
            documento.tipoEvento(), documento.dataHoraGeracao(), documento.xml());
    }

    public static ParametrosMunicipais.ConvenioResposta consultarConvenio(String codigoMunicipio,
                                                                        Ambiente ambiente, CertificadoA1 cert) {
        return nfse(ambiente, cert).parametrosMunicipais().consultarConvenio(codigoMunicipio);
    }

    /** Sem competencia, consulta a aliquota vigente hoje. */
    public static ParametrosMunicipais.AliquotasResposta consultarAliquota(String codigoMunicipio, String codigoServico,
                                                                         LocalDate competencia,
                                                                         Ambiente ambiente, CertificadoA1 cert) {
        return nfse(ambiente, cert).parametrosMunicipais()
            .consultarAliquota(codigoMunicipio, codigoServico, competenciaOuHoje(competencia));
    }

    public static ParametrosMunicipais.AliquotasResposta consultarHistoricoAliquotas(String codigoMunicipio,
                                                                                    String codigoServico,
                                                                                    Ambiente ambiente,
                                                                                    CertificadoA1 cert) {
        return nfse(ambiente, cert).parametrosMunicipais().consultarHistoricoAliquotas(codigoMunicipio, codigoServico);
    }

    public static ParametrosMunicipais.BeneficioResposta consultarBeneficio(String codigoMunicipio,
                                                                          String numeroBeneficio,
                                                                          LocalDate competencia,
                                                                          Ambiente ambiente, CertificadoA1 cert) {
        return nfse(ambiente, cert).parametrosMunicipais()
            .consultarBeneficio(codigoMunicipio, numeroBeneficio, competenciaOuHoje(competencia));
    }

    public static ParametrosMunicipais.RegimesEspeciaisResposta consultarRegimesEspeciais(String codigoMunicipio,
                                                                                        String codigoServico,
                                                                                        LocalDate competencia,
                                                                                        Ambiente ambiente,
                                                                                        CertificadoA1 cert) {
        return nfse(ambiente, cert).parametrosMunicipais()
            .consultarRegimesEspeciais(codigoMunicipio, codigoServico, competenciaOuHoje(competencia));
    }

    public static ParametrosMunicipais.RetencoesResposta consultarRetencoes(String codigoMunicipio,
                                                                          LocalDate competencia,
                                                                          Ambiente ambiente, CertificadoA1 cert) {
        return nfse(ambiente, cert).parametrosMunicipais()
            .consultarRetencoes(codigoMunicipio, competenciaOuHoje(competencia));
    }

    public static CertificadoA1 carregarCertificado(String caminho, String senha) {
        return CertificadoA1.fromFile(Path.of(caminho), senha.toCharArray());
    }

    public static Ambiente ambiente(String valor) {
        if (valor == null || valor.isBlank() || valor.equalsIgnoreCase("homologacao")) {
            return Ambiente.HOMOLOGACAO;
        }
        if (valor.equalsIgnoreCase("producao")) {
            return Ambiente.PRODUCAO;
        }
        throw new IllegalArgumentException("Ambiente invalido: " + valor + " (use homologacao ou producao).");
    }

    // Assinar com certificado fora da validade gera documento invalido — falha cedo, antes da rede.
    private static void validarCertificado(CertificadoA1 cert) {
        if (cert.isExpired(Clock.systemUTC())) {
            throw new IllegalStateException(
                "Certificado A1 fora da validade (valido de " + cert.validFrom() + " a " + cert.validUntil()
                    + "); nao e possivel emitir/cancelar.");
        }
    }

    private static void exigirConfirmacaoProducao(Ambiente ambiente, boolean confirmarProducao) {
        if (ambiente == Ambiente.PRODUCAO && !confirmarProducao) {
            throw new IllegalStateException(
                "Emissao em PRODUCAO cria documento fiscal REAL. Confirme explicitamente "
                    + "(CLI: --confirmar-producao; MCP: confirmarProducao=true).");
        }
    }

    // O ADN so aceita cnpjConsulta com a mesma raiz do CNPJ do certificado; falha antes da rede.
    private static void exigirMesmaRaizDoCertificado(String cnpjConsulta, CertificadoA1 cert) {
        if (cnpjConsulta == null) {
            return;
        }
        String cnpjCertificado = cert.cpfCnpj()
            .filter(documento -> documento.length() == 14)
            .orElseThrow(() -> new IllegalArgumentException("cnpjConsulta exige certificado de CNPJ."));
        String raiz = cnpjCertificado.substring(0, TAMANHO_RAIZ_CNPJ);
        if (!cnpjConsulta.toUpperCase(Locale.ROOT).startsWith(raiz)) {
            throw new IllegalArgumentException("cnpjConsulta " + cnpjConsulta
                + " nao tem a mesma raiz (" + raiz + ") do CNPJ do certificado.");
        }
    }

    private static DistribuicaoDfeClient distribuicaoDfe(Ambiente ambiente, CertificadoA1 cert) {
        return new DistribuicaoDfeClient(NfseContext.builder().ambiente(ambiente).certificado(cert).build());
    }

    private static LocalDate competenciaOuHoje(LocalDate competencia) {
        return competencia == null ? LocalDate.now() : competencia;
    }

    private static Nfse nfse(Ambiente ambiente, CertificadoA1 cert) {
        return new Nfse(NfseContext.builder().ambiente(ambiente).certificado(cert).build());
    }

    private static String ambienteLabel(Ambiente ambiente) {
        return ambiente.name().toLowerCase();
    }

    /** Extrai um campo string simples de um corpo JSON (ex.: chaveAcesso), sem dependencia de parser. */
    private static String campo(String json, String nome) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String marca = "\"" + nome + "\"";
        int i = json.indexOf(marca);
        if (i < 0) {
            return null;
        }
        i = json.indexOf(':', i + marca.length());
        if (i < 0) {
            return null;
        }
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        int start = i + 1;
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }
}
