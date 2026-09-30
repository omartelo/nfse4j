package io.github.omartelo.nfse4j.core.http;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Respostas da API de Parametros Municipais do ADN. Os campos espelham o JSON do ADN com nomes
 * legiveis; qualquer campo pode vir nulo quando o municipio nao parametrizou aquele dado.
 * {@code mensagem} vem preenchida quando o ADN nao encontra o parametro consultado.
 */
public final class ParametrosMunicipais {

    private ParametrosMunicipais() {
    }

    public record ConvenioResposta(Convenio parametrosConvenio, String mensagem) {
    }

    public record Convenio(
        Boolean aderenteAmbienteNacional,
        Boolean aderenteEmissorNacional,
        @SerializedName("situacaoEmissaoPadraoContribuintesRFB") Integer situacaoEmissaoPadraoContribuintesRfb,
        @SerializedName("aderenteMAN") Boolean aderenteMan,
        // "Aproveitameto" (sem n) e a grafia do ADN.
        @SerializedName("permiteAproveitametoDeCreditos") Boolean permiteAproveitamentoDeCreditos
    ) {
    }

    /** Aliquotas por codigo de servico no formato 00.00.00.000. */
    public record AliquotasResposta(Map<String, List<Aliquota>> aliquotas, String mensagem) {
    }

    public record Aliquota(
        @SerializedName("Incidencia") Boolean incidencia,
        @SerializedName("Aliq") BigDecimal aliquota,
        @SerializedName("DtIni") LocalDateTime inicioVigencia,
        @SerializedName("DtFim") LocalDateTime fimVigencia
    ) {
    }

    public record BeneficioResposta(
        LocalDateTime dataHoraProcessamento,
        Integer tipoAmbiente,
        Beneficio beneficio,
        String mensagem
    ) {
    }

    public record Beneficio(
        @SerializedName("numBenef") String numero,
        @SerializedName("desc") String descricao,
        @SerializedName("dtIni") LocalDateTime inicioVigencia,
        @SerializedName("dtFim") LocalDateTime fimVigencia,
        @SerializedName("tpoBenef") Integer tipo,
        @SerializedName("tpoRedBC") Integer tipoReducaoBaseCalculo,
        @SerializedName("redPerclBC") BigDecimal percentualReducaoBaseCalculo,
        @SerializedName("aliqDiferenc") BigDecimal aliquotaDiferenciada,
        @SerializedName("restAoMun") Boolean restritoAoMunicipio,
        @SerializedName("serv") List<ServicoVigente> servicos,
        @SerializedName("contrib") List<ContribuinteBeneficiado> contribuintes
    ) {
    }

    public record ServicoVigente(
        String codigo,
        @SerializedName("dtIni") LocalDateTime inicioVigencia,
        @SerializedName("dtFim") LocalDateTime fimVigencia
    ) {
    }

    public record ContribuinteBeneficiado(
        @SerializedName("tpoInsc") Integer tipoInscricao,
        @SerializedName("insc") String inscricao,
        @SerializedName("dtIni") LocalDateTime inicioVigencia,
        @SerializedName("dtFim") LocalDateTime fimVigencia
    ) {
    }

    /** Regimes especiais por codigo de servico e, dentro dele, por codigo do regime. */
    public record RegimesEspeciaisResposta(
        LocalDateTime dataHoraProcessamento,
        Integer tipoAmbiente,
        Map<String, Map<String, List<RegimeEspecial>>> regimesEspeciais,
        String mensagem
    ) {
    }

    public record RegimeEspecial(
        @SerializedName("sit") Integer situacao,
        @SerializedName("dtIni") LocalDateTime inicioVigencia,
        @SerializedName("dtFim") LocalDateTime fimVigencia,
        String motivo
    ) {
    }

    public record RetencoesResposta(
        LocalDateTime dataHoraProcessamento,
        Integer tipoAmbiente,
        Retencoes retencoes,
        String mensagem
    ) {
    }

    public record Retencoes(
        @SerializedName("art6") RetencaoArtigoSexto artigoSexto,
        @SerializedName("retMun") List<RetencaoMunicipal> retencoesMunicipais
    ) {
    }

    /** Retencao do art. 6o da LC 116/2003. */
    public record RetencaoArtigoSexto(Boolean habilitado, @SerializedName("hist") List<Vigencia> historico) {
    }

    public record RetencaoMunicipal(
        @SerializedName("desc") String descricao,
        @SerializedName("dtIni") LocalDateTime inicioVigencia,
        @SerializedName("dtFim") LocalDateTime fimVigencia,
        @SerializedName("tpRet") List<Integer> tiposRetencao,
        @SerializedName("serv") List<ServicoRetido> servicos,
        @SerializedName("respTrib") List<ResponsavelTributario> responsaveisTributarios
    ) {
    }

    public record ServicoRetido(String codigo, @SerializedName("hist") List<Vigencia> historico) {
    }

    public record ResponsavelTributario(
        @SerializedName("tpInsc") Integer tipoInscricao,
        @SerializedName("insc") String inscricao,
        @SerializedName("hist") List<Vigencia> historico
    ) {
    }

    public record Vigencia(
        @SerializedName("dtIni") LocalDateTime inicio,
        @SerializedName("dtFim") LocalDateTime fim
    ) {
    }
}
