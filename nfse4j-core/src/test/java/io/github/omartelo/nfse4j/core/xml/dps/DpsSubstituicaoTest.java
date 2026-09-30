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

class DpsSubstituicaoTest {
    private static final String CHAVE_SUBSTITUIDA = "31129806212345678000195000000000000026061234567890";
    private static final String MOTIVO = "Desenquadramento do Simples Nacional";

    @TempDir
    Path tempDir;

    @Test
    void geraSubstEntreCLocEmiEPrest() {
        String xml = new DpsXmlBuilder().build(dps(new Dps.Substituicao(CHAVE_SUBSTITUIDA, "01", MOTIVO)));

        assertTrue(xml.contains("<cLocEmi>3129806</cLocEmi><subst><chSubstda>" + CHAVE_SUBSTITUIDA
            + "</chSubstda><cMotivo>01</cMotivo><xMotivo>" + MOTIVO + "</xMotivo></subst><prest>"), xml);
    }

    @Test
    void semDescricaoDoMotivoOmiteXMotivo() {
        String xml = new DpsXmlBuilder().build(dps(new Dps.Substituicao(CHAVE_SUBSTITUIDA, "99", null)));

        assertTrue(xml.contains("<cMotivo>99</cMotivo></subst>"), xml);
    }

    @Test
    void semSubstituicaoNaoGeraSubst() {
        String xml = new DpsXmlBuilder().build(dps(null));

        assertFalse(xml.contains("<subst>"), xml);
    }

    @Test
    void dpsSubstitutaAssinadaPassaNoXsd() throws Exception {
        String xml = XmlSigner.signInfDps(
            new DpsXmlBuilder().build(dps(new Dps.Substituicao(CHAVE_SUBSTITUIDA, "01", MOTIVO))), certificado());

        assertEquals(List.of(), XmlSchemaValidator.validarDps(xml));
    }

    @Test
    void trocarAmbienteMantemSubstituicao() {
        var substituicao = new Dps.Substituicao(CHAVE_SUBSTITUIDA, "01", MOTIVO);

        assertEquals(substituicao, dps(substituicao).infDps().withTipoAmbiente(1).substituicao());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "3112980621234567800019500000000000002606123456789A"})
    void recusaChaveSubstituidaForaDoFormato(String chave) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Dps.Substituicao(chave, "01", MOTIVO));

        assertTrue(ex.getMessage().contains("50 digitos"), ex.getMessage());
    }

    @Test
    void recusaChaveSubstituidaNula() {
        assertThrows(IllegalArgumentException.class, () -> new Dps.Substituicao(null, "01", MOTIVO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "06", "98", "001"})
    void recusaCodigoDeMotivoForaDoDominio(String codigo) {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> new Dps.Substituicao(CHAVE_SUBSTITUIDA, codigo, MOTIVO));

        assertTrue(ex.getMessage().contains("01, 02, 03, 04, 05, 99"), ex.getMessage());
    }

    @Test
    void recusaCodigoDeMotivoNulo() {
        assertThrows(IllegalArgumentException.class, () -> new Dps.Substituicao(CHAVE_SUBSTITUIDA, null, MOTIVO));
    }

    @Test
    void recusaDescricaoDoMotivoComMenosDe15Caracteres() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> new Dps.Substituicao(CHAVE_SUBSTITUIDA, "99", "Curta demais"));

        assertTrue(ex.getMessage().contains("15 a 255"), ex.getMessage());
    }

    @Test
    void recusaDescricaoDoMotivoComMaisDe255Caracteres() {
        assertThrows(IllegalArgumentException.class,
            () -> new Dps.Substituicao(CHAVE_SUBSTITUIDA, "99", "x".repeat(256)));
    }

    @Test
    void recusaDescricaoDoMotivoEmBranco() {
        assertThrows(IllegalArgumentException.class,
            () -> new Dps.Substituicao(CHAVE_SUBSTITUIDA, "99", " ".repeat(20)));
    }

    @Test
    void aceitaDescricaoDoMotivoNosLimites() {
        new Dps.Substituicao(CHAVE_SUBSTITUIDA, "99", "x".repeat(15));
        new Dps.Substituicao(CHAVE_SUBSTITUIDA, "99", "x".repeat(255));
    }

    private CertificadoA1 certificado() throws Exception {
        Path certificatePath = tempDir.resolve("certificado-teste.p12");
        TestPkcs12Factory.create(certificatePath, TestPkcs12Factory.SENHA, "nfse-test", "12345678000195");
        return CertificadoA1.fromFile(certificatePath, TestPkcs12Factory.SENHA);
    }

    private static Dps dps(Dps.Substituicao substituicao) {
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
                new Dps.Servico("3129806", "141001", "Servico de lavanderia conforme pedido de teste"),
                new Dps.Valores(new BigDecimal("442.00"), new Dps.Tributacao(1, 1, 0)),
                substituicao
            )
        );
    }
}
