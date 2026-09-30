package io.github.omartelo.nfse4j.cli;

import io.github.omartelo.nfse4j.core.Ambiente;
import io.github.omartelo.nfse4j.core.api.EmitirNfseRequest;
import io.github.omartelo.nfse4j.core.api.NfseRunner;
import io.github.omartelo.nfse4j.core.certificate.CertificadoA1;
import io.github.omartelo.nfse4j.core.xml.dps.Dps;
import io.github.omartelo.nfse4j.core.xml.dps.DpsReemissao;
import io.github.omartelo.nfse4j.core.xml.evento.TipoManifestacao;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * CLI de NFS-e Nacional. Subcomandos: cert, emitir, emitir-de-exemplo, consultar, cancelar,
 * solicitar-analise-cancelamento, manifestar, danfse, distribuir-dfe, consultar-dfe e as consultas de
 * parametros municipais do ADN (aliquota, historico-aliquotas, convenio, beneficio, regimes-especiais,
 * retencoes).
 * Padrao homologacao; producao exige --confirmar-producao. Use --json para saida estavel.
 */
public final class Cli {

    private static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final ObjectMapper OUT = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }
        String command = args[0];
        Args a = Args.parse(args);
        try {
            switch (command) {
                case "cert" -> cert(a);
                case "emitir" -> emitir(a);
                case "emitir-de-exemplo" -> emitirDeExemplo(a);
                case "consultar" -> consultar(a);
                case "cancelar" -> cancelar(a);
                case "solicitar-analise-cancelamento" -> solicitarAnaliseCancelamento(a);
                case "manifestar" -> manifestar(a);
                case "danfse" -> danfse(a);
                case "distribuir-dfe" -> distribuirDfe(a);
                case "consultar-dfe" -> consultarDfe(a);
                case "aliquota" -> emit(a, NfseRunner.consultarAliquota(
                    a.required("municipio"), a.required("servico"), a.getDate("competencia"), a.ambiente(), certificado(a)));
                case "historico-aliquotas" -> emit(a, NfseRunner.consultarHistoricoAliquotas(
                    a.required("municipio"), a.required("servico"), a.ambiente(), certificado(a)));
                case "convenio" -> emit(a, NfseRunner.consultarConvenio(a.required("municipio"), a.ambiente(), certificado(a)));
                case "beneficio" -> emit(a, NfseRunner.consultarBeneficio(
                    a.required("municipio"), a.required("beneficio"), a.getDate("competencia"), a.ambiente(), certificado(a)));
                case "regimes-especiais" -> emit(a, NfseRunner.consultarRegimesEspeciais(
                    a.required("municipio"), a.required("servico"), a.getDate("competencia"), a.ambiente(), certificado(a)));
                case "retencoes" -> emit(a, NfseRunner.consultarRetencoes(
                    a.required("municipio"), a.getDate("competencia"), a.ambiente(), certificado(a)));
                case "help", "-h", "--help" -> usage();
                default -> {
                    System.err.println("Comando desconhecido: " + command);
                    usage();
                    System.exit(1);
                }
            }
        } catch (Exception exception) {
            erro(a, exception);
            System.exit(1);
        }
    }

    private static void cert(Args a) {
        emit(a, NfseRunner.cert(certificado(a)));
    }

    private static void emitir(Args a) throws Exception {
        EmitirNfseRequest request = JSON.readValue(
            Files.readString(Path.of(a.required("arquivo"))), EmitirNfseRequest.class);
        var result = NfseRunner.emitir(request, a.ambiente(), certificado(a), a.flag("confirmar-producao"));
        emit(a, result);
    }

    private static void emitirDeExemplo(Args a) throws Exception {
        String xml = Files.readString(Path.of(a.required("exemplo")));
        DpsReemissao.Overrides overrides = new DpsReemissao.Overrides(
            a.getLong("numero"),
            a.get("serie"),
            tomadorOverride(a),
            a.get("descricao"),
            a.getDecimal("valor"),
            a.getDate("competencia"),
            a.get("versao")
        );
        var result = NfseRunner.emitirDeExemplo(xml, overrides, a.ambiente(), certificado(a), a.flag("confirmar-producao"));
        emit(a, result);
    }

    private static void consultar(Args a) {
        emit(a, NfseRunner.consultar(a.required("chave"), a.ambiente(), certificado(a)));
    }

    private static void cancelar(Args a) {
        var result = NfseRunner.cancelar(
            a.required("chave"),
            a.get("autor"),
            a.getInt("numero-pedido", 1),
            a.required("motivo-codigo"),
            a.required("motivo-descricao"),
            a.ambiente(),
            certificado(a),
            a.flag("confirmar-producao"));
        emit(a, result);
    }

    private static void distribuirDfe(Args a) {
        Long nsu = a.getLong("nsu");
        emit(a, NfseRunner.distribuirDfe(nsu == null ? 0 : nsu, a.get("cnpj-consulta"), a.ambiente(), certificado(a)));
    }

    private static void consultarDfe(Args a) {
        emit(a, NfseRunner.consultarDfe(Long.parseLong(a.required("nsu")), a.get("cnpj-consulta"), a.ambiente(), certificado(a)));
    }

    private static void solicitarAnaliseCancelamento(Args a) {
        var result = NfseRunner.solicitarAnaliseFiscalCancelamento(
            a.required("chave"),
            a.get("autor"),
            a.required("motivo-codigo"),
            a.required("motivo-descricao"),
            a.ambiente(),
            certificado(a),
            a.flag("confirmar-producao"));
        emit(a, result);
    }

    private static void manifestar(Args a) {
        var result = NfseRunner.manifestar(
            a.required("chave"),
            a.get("autor"),
            TipoManifestacao.porNome(a.required("tipo")),
            a.get("motivo-codigo"),
            a.get("motivo-descricao"),
            a.ambiente(),
            certificado(a),
            a.flag("confirmar-producao"));
        emit(a, result);
    }

    private static void danfse(Args a) throws Exception {
        String xml = Files.readString(Path.of(a.required("xml")));
        Path saida = Path.of(a.getOr("saida", "danfse.pdf"));
        boolean producao = a.ambiente() == Ambiente.PRODUCAO;
        var config = io.github.omartelo.nfse4j.danfse.DanfseConfig.vazio();
        String logo = a.get("logo-emitente");
        if (logo != null) {
            config = io.github.omartelo.nfse4j.danfse.DanfseConfig.comLogoEmitente(
                io.github.omartelo.nfse4j.danfse.DanfseGenerator.dataUriImagem(Path.of(logo)));
        }
        byte[] pdf = io.github.omartelo.nfse4j.danfse.DanfseGenerator.gerarPdf(xml, producao, config, saida);
        emit(a, java.util.Map.of(
            "sucesso", true,
            "caminho", saida.toAbsolutePath().normalize().toString(),
            "bytes", pdf.length));
    }

    private static Dps.Tomador tomadorOverride(Args a) {
        boolean temTomador = a.get("tomador-cpf") != null || a.get("tomador-cnpj") != null;
        if (!temTomador) {
            return null;
        }
        Dps.Endereco endereco = null;
        if (a.get("tomador-municipio") != null || a.get("tomador-cep") != null) {
            endereco = new Dps.Endereco(
                a.get("tomador-municipio"), a.get("tomador-cep"), a.get("tomador-logradouro"),
                a.get("tomador-numero"), a.get("tomador-complemento"), a.get("tomador-bairro"));
        }
        return new Dps.Tomador(
            a.get("tomador-cnpj"), a.get("tomador-cpf"), a.required("tomador-nome"),
            endereco, a.get("tomador-fone"), a.get("tomador-email"));
    }

    private static CertificadoA1 certificado(Args a) {
        String caminho = a.getOr("cert", System.getenv("NFSE_CERT_PATH"));
        String senha = a.getOr("senha", System.getenv("NFSE_CERT_PASSWORD"));
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalStateException("Informe o certificado via --cert ou NFSE_CERT_PATH.");
        }
        if (senha == null) {
            throw new IllegalStateException("Informe a senha via --senha ou NFSE_CERT_PASSWORD.");
        }
        return NfseRunner.carregarCertificado(caminho, senha);
    }

    private static void emit(Args a, Object result) {
        try {
            System.out.println(OUT.writeValueAsString(result));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void erro(Args a, Exception exception) {
        String mensagem = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        if (a.flag("json")) {
            System.out.println("{\"success\":false,\"erro\":" + quote(mensagem) + "}");
        } else {
            System.err.println("Erro: " + mensagem);
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void usage() {
        System.out.println("""
            nfse4j-cli <comando> [opcoes]

            Comandos:
              cert                          Valida o certificado A1 e mostra dados (CNPJ/CPF, validade).
              emitir --arquivo nota.json    Emite uma DPS a partir de um payload JSON.
              emitir-de-exemplo --exemplo nota.xml [--numero N] [--descricao D] [--valor V]
                                            [--tomador-cpf X --tomador-nome Y] [--tomador-cnpj X]
                                            Reaproveita uma nota existente trocando so o que mudar.
              consultar --chave CHAVE       Consulta uma NFS-e pela chave de acesso.
              cancelar --chave CHAVE --motivo-codigo C --motivo-descricao D
                                            Cancela uma NFS-e (evento 101101).
              solicitar-analise-cancelamento --chave CHAVE --motivo-codigo 1|2|9 --motivo-descricao D
                                            Pede analise fiscal para cancelar (evento 101103).
              manifestar --chave CHAVE --tipo TIPO [--motivo-codigo C] [--motivo-descricao D]
                                            Confirma ou rejeita uma NFS-e. TIPO: confirmacao-prestador,
                                            confirmacao-tomador, confirmacao-intermediario,
                                            rejeicao-prestador, rejeicao-tomador, rejeicao-intermediario.
                                            Rejeicao exige --motivo-codigo (1,2,3,4,5,9).
              danfse --xml nota.xml [--saida arq.pdf] [--logo-emitente logo.png]
                                            Gera o DANFSe/PDF localmente a partir do XML da NFS-e.
                                            --logo-emitente: logo do prestador no cabecalho (~300x120 px).
              distribuir-dfe [--nsu N] [--cnpj-consulta CNPJ]
                                            Lista (resumo) as NFS-e e eventos em que o CNPJ e emitente,
                                            tomador ou intermediario, a partir do ultimo NSU processado.
                                            Se "concluida" vier false, rode de novo com --nsu ultimoNsu.
              consultar-dfe --nsu N [--cnpj-consulta CNPJ]
                                            Mostra o XML de um documento da distribuicao pelo NSU.

            Parametros municipais do ADN (somente leitura; --competencia YYYY-MM-DD, padrao hoje):
              aliquota --municipio IBGE --servico 01.01.01.000 [--competencia D]
                                            Aliquota de ISS do servico no municipio (confira antes de emitir).
              historico-aliquotas --municipio IBGE --servico COD
                                            Todas as aliquotas que o servico ja teve, com vigencias.
              convenio --municipio IBGE     Adesao do municipio ao ambiente/emissor nacional.
              beneficio --municipio IBGE --beneficio NUM [--competencia D]
                                            Beneficio fiscal municipal pelo numero.
              regimes-especiais --municipio IBGE --servico COD [--competencia D]
                                            Regimes especiais de tributacao aceitos para o servico.
              retencoes --municipio IBGE [--competencia D]
                                            Regras de retencao de ISS do municipio.

            Opcoes comuns:
              --ambiente homologacao|producao   Padrao: homologacao.
              --confirmar-producao              Obrigatorio para emitir/registrar evento em PRODUCAO (doc fiscal REAL).
              --cert CAMINHO --senha SENHA      Ou via env NFSE_CERT_PATH / NFSE_CERT_PASSWORD.
              --json                            Saida JSON estavel (para agentes).
            """);
    }

    /** Parser simples de --chave valor e flags booleanas (--json, --confirmar-producao). */
    private record Args(Map<String, String> values, java.util.Set<String> flags) {
        private static final java.util.Set<String> BOOLEANS = java.util.Set.of("json", "confirmar-producao");

        static Args parse(String[] argv) {
            Map<String, String> values = new HashMap<>();
            java.util.Set<String> flags = new java.util.HashSet<>();
            for (int i = 1; i < argv.length; i++) {
                String token = argv[i];
                if (!token.startsWith("--")) {
                    continue;
                }
                String key = token.substring(2);
                if (key.contains("=")) {
                    int eq = key.indexOf('=');
                    values.put(key.substring(0, eq), key.substring(eq + 1));
                } else if (BOOLEANS.contains(key)) {
                    flags.add(key);
                } else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                    values.put(key, argv[++i]);
                } else {
                    flags.add(key);
                }
            }
            return new Args(values, flags);
        }

        String get(String key) {
            return values.get(key);
        }

        String getOr(String key, String fallback) {
            String value = values.get(key);
            return value == null || value.isBlank() ? fallback : value;
        }

        String required(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Opcao obrigatoria ausente: --" + key);
            }
            return value;
        }

        boolean flag(String key) {
            return flags.contains(key);
        }

        Long getLong(String key) {
            String value = values.get(key);
            return value == null ? null : Long.valueOf(value);
        }

        int getInt(String key, int fallback) {
            String value = values.get(key);
            return value == null ? fallback : Integer.parseInt(value);
        }

        BigDecimal getDecimal(String key) {
            String value = values.get(key);
            return value == null ? null : new BigDecimal(value);
        }

        LocalDate getDate(String key) {
            String value = values.get(key);
            return value == null ? null : LocalDate.parse(value);
        }

        Ambiente ambiente() {
            return NfseRunner.ambiente(values.get("ambiente"));
        }
    }
}
