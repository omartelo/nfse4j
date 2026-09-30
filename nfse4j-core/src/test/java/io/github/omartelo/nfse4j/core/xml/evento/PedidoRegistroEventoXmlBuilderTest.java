package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PedidoRegistroEventoXmlBuilderTest {

    private static final String CHAVE = "31298062112223330001810000000000000012345678901234";
    private static final String CNPJ = "12345678000195";
    private static final OffsetDateTime DATA_HORA = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.ofHours(-3));

    private final PedidoRegistroEventoXmlBuilder builder = new PedidoRegistroEventoXmlBuilder();

    @Test
    void shouldBuildSolicitacaoAnaliseFiscalCancelamento() {
        String xml = builder.build(2, new SolicitacaoAnaliseFiscalCancelamentoNfse(
            CHAVE, CNPJ, DATA_HORA, "2", "Servico nao foi prestado ao tomador", "nfse4j"));

        assertTrue(xml.contains("<infPedReg Id=\"PRE" + CHAVE + "101103\">"));
        assertTrue(xml.contains("<tpAmb>2</tpAmb><verAplic>nfse4j</verAplic>"
            + "<dhEvento>2026-01-15T10:00:00-03:00</dhEvento><CNPJAutor>" + CNPJ + "</CNPJAutor>"
            + "<chNFSe>" + CHAVE + "</chNFSe>"));
        assertTrue(xml.contains("<e101103><xDesc>Solicitação de Análise Fiscal para Cancelamento de NFS-e</xDesc>"
            + "<cMotivo>2</cMotivo><xMotivo>Servico nao foi prestado ao tomador</xMotivo></e101103>"));
    }

    @Test
    void shouldBuildConfirmacaoWithDescriptionOnly() {
        String xml = builder.build(2, new ManifestacaoNfse(
            CHAVE, "11144477735", DATA_HORA, TipoManifestacao.CONFIRMACAO_TOMADOR, null, null, "nfse4j"));

        assertTrue(xml.contains("<infPedReg Id=\"PRE" + CHAVE + "203202\">"));
        assertTrue(xml.contains("<CPFAutor>11144477735</CPFAutor>"));
        assertTrue(xml.contains("<e203202><xDesc>Manifestação de NFS-e - Confirmação do Tomador</xDesc></e203202>"));
    }

    @Test
    void shouldBuildRejeicaoWithMotivo() {
        String xml = builder.build(1, new ManifestacaoNfse(
            CHAVE, CNPJ, DATA_HORA, TipoManifestacao.REJEICAO_INTERMEDIARIO, "3",
            "Fato gerador nao ocorreu nesta data", "nfse4j"));

        assertTrue(xml.contains("<infPedReg Id=\"PRE" + CHAVE + "204207\">"));
        assertTrue(xml.contains("<e204207><xDesc>Manifestação de NFS-e - Rejeição do Intermediário</xDesc>"
            + "<cMotivo>3</cMotivo><xMotivo>Fato gerador nao ocorreu nesta data</xMotivo></e204207>"));
    }

    @Test
    void shouldOmitOptionalDescricaoOfRejeicao() {
        String xml = builder.build(2, new ManifestacaoNfse(
            CHAVE, CNPJ, DATA_HORA, TipoManifestacao.REJEICAO_PRESTADOR, "1", null, "nfse4j"));

        assertTrue(xml.contains("<e202205><xDesc>Manifestação de NFS-e - Rejeição do Prestador</xDesc>"
            + "<cMotivo>1</cMotivo></e202205>"));
        assertFalse(xml.contains("<xMotivo>"));
    }
}
