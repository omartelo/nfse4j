package io.github.omartelo.nfse4j.danfse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Fidelidade ao modelo da NT 008/2026 v1.02 usando o exemplo completo (tributacao federal,
 * totais por esfera, intermediario, suspensao, beneficio municipal e valores apurados).
 */
class Nt008FidelidadeTest {

    private static String xml() throws Exception {
        try (var in = Nt008FidelidadeTest.class.getResourceAsStream("/nfse-exemplo-completo.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void leTributacaoFederalCompleta() throws Exception {
        Danfse.TributacaoFederal t = NfseXmlReader.read(xml()).tributacaoFederal();
        assertEquals(0, t.valorRetidoIrrf().compareTo(new BigDecimal("3.75")));
        assertEquals(0, t.valorRetidoContribuicaoPrevidenciaria().compareTo(new BigDecimal("2.75")));
        assertEquals(0, t.valorRetidoCsll().compareTo(new BigDecimal("2.50")));
        assertEquals(0, t.valorPis().compareTo(new BigDecimal("1.63")));
        assertEquals(0, t.valorCofins().compareTo(new BigDecimal("7.50")));
        assertEquals("PIS/COFINS Não Retidos", t.descricaoRetencaoPisCofins());
    }

    @Test
    void leTributacaoMunicipalCompleta() throws Exception {
        Danfse.TributacaoMunicipal t = NfseXmlReader.read(xml()).tributacaoMunicipal();
        assertEquals("Estimativa", t.regimeEspecial());
        assertEquals("Exigibilidade Suspensa por Decisão Judicial", t.suspensaoExigibilidade());
        assertEquals("0012345-67.2026.8.26.0100", t.numeroProcessoSuspensao());
        assertEquals("Retido pelo Tomador", t.retencaoIssqn());
        assertEquals(0, t.baseCalculo().compareTo(new BigDecimal("240.00")));
        assertEquals(0, t.aliquotaAplicada().compareTo(new BigDecimal("2.00")));
        assertEquals(0, t.issqnApurado().compareTo(new BigDecimal("4.80")));
        assertEquals(0, t.calculoBeneficioMunicipal().compareTo(new BigDecimal("5.00")));
        // vDR (10.00) sem vCalcReeRepRes -> soma = 10.00
        assertEquals(0, t.totalDeducoesReducoes().compareTo(new BigDecimal("10.00")));
        assertEquals(0, t.descontoIncondicionado().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void leTotaisAproximadosPorEsferaEIntermediario() throws Exception {
        Danfse d = NfseXmlReader.read(xml());
        assertEquals(0, d.totaisTributos().valorFederal().compareTo(new BigDecimal("33.13")));
        assertEquals(0, d.totaisTributos().valorMunicipal().compareTo(new BigDecimal("4.80")));
        assertNull(d.totaisTributos().percentualSimplesNacional());
        assertEquals("INTERMEDIARIA EXEMPLO LTDA", d.intermediario().nome());
        assertEquals("11222333000144", d.intermediario().cnpj());
        assertEquals(0, d.valores().totalRetencoes().compareTo(new BigDecimal("13.80")));
    }

    @Test
    void leInformacoesComplementaresComPipes() throws Exception {
        String info = NfseXmlReader.read(xml()).informacoesComplementares();
        assertTrue(info.contains("Pagamento via PIX"), "xInfComp presente");
        // Item 2.4.5: a ordem obrigatoria comeca por "Inf. Cont.:", que prefixa o xInfComp.
        assertTrue(info.startsWith("Inf. Cont.: "), "prefixo obrigatorio do xInfComp; veio: " + info);
        assertTrue(info.contains("Doc. Tec.: OS-2026-0042"), "idDocTec prefixado");
        assertTrue(info.contains("Inf. A. T. Mun.: Nota fiscal de teste"), "xOutInf prefixado");
        assertTrue(info.contains(" | "), "segmentos separados por pipes");
    }

    @Test
    void usaAsDescricoesDoLeiauteNasTabelasDeCodigo() throws Exception {
        // O item 2.4.5 manda "utilizar a descricao das opcoes previstas no leiaute" em cStat,
        // finNFSe e tpBM; tpRetPisCofins preve 10 opcoes (0 a 9) e regEspTrib inclui o 9.
        String base = xml();
        assertEquals("Isenção", NfseXmlReader.read(base).tributacaoMunicipal().beneficioMunicipal());
        assertEquals("NFS-e Gerada", NfseXmlReader.read(base).identificacao().situacao());
        assertEquals("NFS-e Avulsa", NfseXmlReader.read(
            base.replace("<cStat>100</cStat>", "<cStat>103</cStat>")).identificacao().situacao());
        assertEquals("NFS-e regular", NfseXmlReader.read(
            base.replace("</infDPS>", "<IBSCBS><finNFSe>0</finNFSe></IBSCBS></infDPS>"))
            .identificacao().finalidade());
        assertEquals("PIS/COFINS/CSLL Retidos", NfseXmlReader.read(
            base.replace("<tpRetPisCofins>2</tpRetPisCofins>", "<tpRetPisCofins>3</tpRetPisCofins>"))
            .tributacaoFederal().descricaoRetencaoPisCofins());
        assertEquals("Outros", NfseXmlReader.read(
            base.replace("<regEspTrib>2</regEspTrib>", "<regEspTrib>9</regEspTrib>"))
            .tributacaoMunicipal().regimeEspecial());
    }

    @Test
    void codigoForaDaTabelaCaiNoProprioCodigo() throws Exception {
        // Mesmo fallback dos demais mapeamentos do leitor.
        assertEquals("42", NfseXmlReader.read(
            xml().replace("<tpBM>1</tpBM>", "<tpBM>42</tpBM>"))
            .tributacaoMunicipal().beneficioMunicipal());
    }

    @Test
    void pdfTrazTributacaoFederalIntermediarioETotais() throws Exception {
        byte[] pdf = DanfseGenerator.gerarPdf(xml());
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            assertEquals(1, doc.getNumberOfPages(), "DANFSe deve ter pagina unica (NT 008, item 2.2)");
            String texto = new PDFTextStripper().getText(doc);
            assertTrue(texto.contains("R$ 3,75"), "IRRF no PDF");
            assertTrue(texto.contains("R$ 2,75"), "Contribuicao previdenciaria retida no PDF");
            assertTrue(texto.contains("INTERMEDIARIA EXEMPLO LTDA"), "intermediario identificado");
            assertTrue(texto.contains("Estimativa"), "regime especial de tributacao");
            assertTrue(texto.contains("Federais: R$ 33,13"), "totais aproximados por esfera");
            assertTrue(texto.contains("R$ 226,20"), "valor liquido");
        }
    }

    @Test
    void pdfDoExemploSimplesTrazLinhaDosTotaisDoSimplesNacional() throws Exception {
        byte[] pdf = DanfseGenerator.gerarPdf(NfseXmlReaderTest.xmlExemplo(), false);
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            String texto = new PDFTextStripper().getText(doc);
            assertTrue(texto.contains("Totais Aproximados dos Tributos"), "linha obrigatoria (Nota 10)");
            assertTrue(texto.contains("6,00%"), "percentual do Simples Nacional");
            assertTrue(texto.contains("NÃO IDENTIFICADO NA NFS-e")
                    || texto.contains("NAO IDENTIFICADO NA NFS-e"),
                "bandas de destinatario/intermediario ausentes");
        }
    }

    @Test
    void siglaUfAparecePorBlocoQuandoOXmlNaoTrazATagUf() throws Exception {
        // Itens 2.1.3 a 2.1.6: "Municipio / Sigla UF" e exigido em prestador, tomador,
        // destinatario e intermediario. No exemplo ficticio so o emitente tem <UF>; o tomador
        // traz apenas <cMun> em endNac, e a sigla pode ser derivada do codigo IBGE.
        String html = DanfseHtmlRenderer.render(
            NfseXmlReader.read(NfseXmlReaderTest.xmlExemplo()), null);
        // Verifica o bloco do tomador especificamente: contar ocorrencias no documento inteiro
        // deixaria o teste passar mesmo se so o tomador regredisse.
        int inicio = html.indexOf("TOMADOR / ADQUIRENTE");
        assertTrue(inicio > 0, "bloco do tomador nao encontrado");
        int fim = html.indexOf("DESTINATÁRIO", inicio);
        String blocoTomador = html.substring(inicio, fim > 0 ? fim : html.length());
        assertTrue(blocoTomador.contains("Sao Paulo / SP"),
            "o tomador tem apenas cMun no endNac; a sigla deve vir do codigo IBGE");
    }

    @Test
    void siglaUfNoLocalDaPrestacaoNoIssqnENoIbsCbs() throws Exception {
        // Itens 2.1.7, 2.1.8 e 2.1.10: os rotulos prometem "Sigla UF", mas o valor saia sem ela.
        Danfse d = NfseXmlReader.read(NfseXmlReaderTest.xmlExemplo());
        assertEquals("3550308", d.servico().codigoLocalPrestacao(), "codigo do local da prestacao");
        assertEquals("3550308", d.tributacaoMunicipal().codigoMunicipioIncidencia(),
            "codigo do municipio de incidencia do ISSQN");

        String html = DanfseHtmlRenderer.render(d, null);
        int ocorrencias = html.split("Sao Paulo / SP", -1).length - 1;
        assertTrue(ocorrencias >= 4,
            "prestador, tomador, local da prestacao e incidencia do ISSQN; veio " + ocorrencias);
    }

    @Test
    void siglaUfNoBlocoIbsCbs() throws Exception {
        String xml;
        try (var in = Nt008FidelidadeTest.class.getResourceAsStream("/nfse-exemplo-ibscbs.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String html = DanfseHtmlRenderer.render(NfseXmlReader.read(xml), null);
        assertTrue(html.contains("3550308 / Sao Paulo / SP"),
            "codigo IBGE, municipio e sigla no bloco IBS/CBS");
    }

    @Test
    void pdfEmbuteFonteLiberationSans() throws Exception {
        byte[] pdf = DanfseGenerator.gerarPdf(NfseXmlReaderTest.xmlExemplo(), false);
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            var resources = doc.getPage(0).getResources();
            boolean liberation = false;
            for (var name : resources.getFontNames()) {
                String fonte = resources.getFont(name).getName();
                if (fonte != null && fonte.contains("LiberationSans")) {
                    liberation = true;
                }
            }
            assertTrue(liberation, "PDF deve embutir a Liberation Sans (nao usar base-14 Helvetica)");
        }
    }
}
