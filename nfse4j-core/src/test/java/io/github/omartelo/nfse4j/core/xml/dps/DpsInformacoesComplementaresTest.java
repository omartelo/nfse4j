package io.github.omartelo.nfse4j.core.xml.dps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omartelo.nfse4j.core.certificate.CertificadoA1;
import io.github.omartelo.nfse4j.core.certificate.TestPkcs12Factory;
import io.github.omartelo.nfse4j.core.xml.XmlSchemaValidator;
import io.github.omartelo.nfse4j.core.xml.XmlSigner;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DpsInformacoesComplementaresTest {
    private static final String TEXTO_MEI =
        "O MEI não está sujeito à retenção de ISS (Resolução CGSN 140/2018, art. 103, IV)";

    @TempDir
    Path tempDir;

    @Test
    void geraInfoComplDepoisDeCServ() {
        String xml = new DpsXmlBuilder().build(dps(new Dps.InformacoesComplementares(TEXTO_MEI)));

        assertTrue(xml.contains("</cServ><infoCompl><xInfComp>" + TEXTO_MEI + "</xInfComp></infoCompl></serv>"), xml);
    }

    @Test
    void semInformacoesComplementaresNaoGeraInfoCompl() {
        String xml = new DpsXmlBuilder().build(dps(null));

        assertFalse(xml.contains("<infoCompl>"), xml);
    }

    @Test
    void textoNoLimiteComAcentosAssinadoPassaNoXsd() throws Exception {
        String textoNoLimite = TEXTO_MEI + " " + "ç".repeat(2000 - TEXTO_MEI.length() - 1);
        String xml = XmlSigner.signInfDps(
            new DpsXmlBuilder().build(dps(new Dps.InformacoesComplementares(textoNoLimite))), certificado());

        assertEquals(List.of(), XmlSchemaValidator.validarDps(xml));
    }

    @Test
    void leDeVoltaAsInformacoesComplementares() {
        var informacoes = new Dps.InformacoesComplementares(TEXTO_MEI);

        Dps lido = DpsXmlReader.read(new DpsXmlBuilder().build(dps(informacoes)));

        assertEquals(informacoes, lido.infDps().servico().informacoesComplementares());
    }

    @Test
    void reemissaoComNovaDescricaoMantemInformacoesComplementares() {
        var informacoes = new Dps.InformacoesComplementares(TEXTO_MEI);
        Dps lido = DpsXmlReader.read(new DpsXmlBuilder().build(dps(informacoes)));

        Dps reemitida = DpsReemissao.reemitir(lido,
            new DpsReemissao.Overrides(26L, null, null, "Nova descricao do servico", null, null, null));

        assertEquals("Nova descricao do servico", reemitida.infDps().servico().descricao());
        assertEquals(informacoes, reemitida.infDps().servico().informacoesComplementares());
    }

    @Test
    void recusaTextoNulo() {
        assertThrows(IllegalArgumentException.class, () -> new Dps.InformacoesComplementares(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void recusaTextoEmBranco(String texto) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Dps.InformacoesComplementares(texto));

        assertTrue(ex.getMessage().contains("em branco"), ex.getMessage());
    }

    @Test
    void recusaTextoComMaisDe2000Caracteres() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> new Dps.InformacoesComplementares("x".repeat(2001)));

        assertTrue(ex.getMessage().contains("ate 2000 caracteres (recebido: 2001)"), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {" comeca com espaco", "termina com espaco ", "quebra\nde linha", "tab\tno meio",
        "travessao — fora do Latin-1"})
    void recusaTextoForaDoPadraoDoXsd(String texto) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Dps.InformacoesComplementares(texto));

        assertTrue(ex.getMessage().contains("U+0020 a U+00FF"), ex.getMessage());
    }

    @Test
    void aceitaTextoNosLimites() {
        new Dps.InformacoesComplementares("x");
        new Dps.InformacoesComplementares("x".repeat(2000));
    }

    private CertificadoA1 certificado() throws Exception {
        Path certificatePath = tempDir.resolve("certificado-teste.p12");
        TestPkcs12Factory.create(certificatePath, TestPkcs12Factory.SENHA, "nfse-test", "12345678000195");
        return CertificadoA1.fromFile(certificatePath, TestPkcs12Factory.SENHA);
    }

    private static Dps dps(Dps.InformacoesComplementares informacoesComplementares) {
        return new Dps(
            "1.01",
            new Dps.InfDps(
                DpsIdGenerator.generate("12345678000195", "3129806", "70000", 25),
                2,
                OffsetDateTime.parse("2026-06-09T12:46:36-03:00"),
                "nfse-nacional-kit",
                "70000",
                25,
                LocalDate.parse("2026-05-22"),
                1,
                "3129806",
                new Dps.Prestador("12345678000195", null, "31999999999", "prestador@example.test",
                    new Dps.RegimeTributario(2, 0)),
                new Dps.Tomador("98765432000198", null, "TOMADOR EXEMPLO LTDA",
                    new Dps.Endereco("3129806", "32432025", "RUA EXEMPLO", "01", null, "CENTRO"), null, null),
                new Dps.Servico("3129806", "141001", null, "Servico de lavanderia conforme pedido de teste", null,
                    informacoesComplementares),
                new Dps.Valores(new BigDecimal("442.00"), new Dps.Tributacao(1, 1, 0))
            )
        );
    }
}
