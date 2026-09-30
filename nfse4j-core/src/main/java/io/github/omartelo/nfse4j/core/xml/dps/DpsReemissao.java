package io.github.omartelo.nfse4j.core.xml.dps;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reaproveita uma DPS lida de um exemplo ({@link DpsXmlReader}) para uma NOVA emissao, aplicando
 * apenas os campos que mudam (tomador, descricao, valor, numero/serie, competencia). Regenera o Id
 * da DPS e usa data/hora de emissao atual. O tpAmb e ajustado depois pela SDK conforme o ambiente.
 */
public final class DpsReemissao {

    private static final String VER_APLIC = "nfse4j";

    private DpsReemissao() {
    }

    public record Overrides(
        Long numero,
        String serie,
        Dps.Tomador tomador,
        String descricaoServico,
        BigDecimal valorServico,
        LocalDate dataCompetencia,
        String versao
    ) {
    }

    public static Dps reemitir(Dps exemplo, Overrides overrides) {
        Objects.requireNonNull(exemplo, "exemplo is required");
        Objects.requireNonNull(overrides, "overrides is required");

        Dps.InfDps base = exemplo.infDps();
        if (base.prestador() == null) {
            throw new DpsXmlException("Exemplo nao contem prestador; nao da para reemitir.");
        }
        String documento = valueOr(base.prestador().cnpj(), base.prestador().cpf());
        if (documento == null || documento.isBlank()) {
            throw new DpsXmlException("Exemplo nao contem CNPJ/CPF do prestador.");
        }
        if (base.codigoLocalEmissao() == null) {
            throw new DpsXmlException("Exemplo nao contem cLocEmi (municipio de emissao).");
        }

        String serie = valueOr(overrides.serie(), base.serie());
        long numero = overrides.numero() != null ? overrides.numero() : base.numero();
        String id = DpsIdGenerator.generate(documento, base.codigoLocalEmissao(), serie, numero);

        Dps.Tomador tomador = overrides.tomador() != null ? overrides.tomador() : base.tomador();
        Dps.Servico servico = comDescricao(base.servico(), overrides.descricaoServico());
        Dps.Valores valores = comValor(base.valores(), overrides.valorServico());
        LocalDate competencia = overrides.dataCompetencia() != null
            ? overrides.dataCompetencia()
            : valueOr(base.dataCompetencia(), LocalDate.now());
        String versao = valueOr(overrides.versao(), exemplo.versao());

        Dps.InfDps inf = new Dps.InfDps(
            id,
            base.tipoAmbiente(),
            OffsetDateTime.now(),
            VER_APLIC,
            serie,
            numero,
            competencia,
            base.tipoEmitente(),
            base.codigoLocalEmissao(),
            base.prestador(),
            tomador,
            servico,
            valores
        );
        return new Dps(versao, inf);
    }

    private static Dps.Servico comDescricao(Dps.Servico servico, String descricao) {
        if (servico == null || descricao == null || descricao.isBlank()) {
            return servico;
        }
        return new Dps.Servico(
            servico.codigoLocalPrestacao(),
            servico.codigoTributacaoNacional(),
            servico.codigoTributacaoMunicipal(),
            descricao,
            servico.codigoNbs()
        );
    }

    /**
     * Troca o vServ mantendo o resto do exemplo. Recusa a troca quando o exemplo tem valores em R$
     * calculados sobre o vServ original: copiados, ficariam incoerentes com o novo valor. Percentuais
     * continuam validos e sao copiados.
     */
    private static Dps.Valores comValor(Dps.Valores valores, BigDecimal valorServico) {
        if (valores == null || valorServico == null) {
            return valores;
        }
        List<String> absolutos = valoresAbsolutosDerivadosDoServico(valores);
        if (!absolutos.isEmpty()) {
            throw new DpsXmlException("Exemplo tem valores em R$ calculados sobre o valor do servico original ("
                + String.join(", ", absolutos) + "); trocar o valor do servico os deixaria incoerentes.");
        }
        return new Dps.Valores(
            valorServico,
            valores.tributacao(),
            valores.valorRecebido(),
            valores.descontos(),
            valores.deducaoReducao()
        );
    }

    private static List<String> valoresAbsolutosDerivadosDoServico(Dps.Valores valores) {
        List<String> campos = new ArrayList<>();
        adicionarSePresente(campos, "vReceb", valores.valorRecebido());
        if (valores.descontos() != null) {
            adicionarSePresente(campos, "vDescIncond", valores.descontos().incondicionado());
            adicionarSePresente(campos, "vDescCond", valores.descontos().condicionado());
        }
        if (valores.deducaoReducao() != null) {
            adicionarSePresente(campos, "vDR", valores.deducaoReducao().valor());
        }
        Dps.Tributacao tributacao = valores.tributacao();
        if (tributacao.beneficioMunicipal() != null) {
            adicionarSePresente(campos, "vRedBCBM", tributacao.beneficioMunicipal().valorReducaoBaseCalculo());
        }
        campos.addAll(valoresAbsolutosFederais(tributacao.tributacaoFederal()));
        adicionarSePresente(campos, "vTotTrib", tributacao.valorTotalTributos());
        return campos;
    }

    private static List<String> valoresAbsolutosFederais(Dps.TributacaoFederal federal) {
        List<String> campos = new ArrayList<>();
        if (federal == null) {
            return campos;
        }
        if (federal.pisCofins() != null) {
            adicionarSePresente(campos, "vBCPisCofins", federal.pisCofins().baseCalculo());
            adicionarSePresente(campos, "vPis", federal.pisCofins().valorPis());
            adicionarSePresente(campos, "vCofins", federal.pisCofins().valorCofins());
        }
        adicionarSePresente(campos, "vRetCP", federal.valorRetidoCp());
        adicionarSePresente(campos, "vRetIRRF", federal.valorRetidoIrrf());
        adicionarSePresente(campos, "vRetCSLL", federal.valorRetidoCsll());
        return campos;
    }

    private static void adicionarSePresente(List<String> campos, String nome, Object valor) {
        if (valor != null) {
            campos.add(nome);
        }
    }

    private static <T> T valueOr(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static String valueOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
