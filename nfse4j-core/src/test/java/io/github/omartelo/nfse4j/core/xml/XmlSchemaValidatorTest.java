package io.github.omartelo.nfse4j.core.xml;

import io.github.omartelo.nfse4j.core.certificate.CertificadoA1;
import io.github.omartelo.nfse4j.core.certificate.TestPkcs12Factory;
import io.github.omartelo.nfse4j.core.xml.dps.Dps;
import io.github.omartelo.nfse4j.core.xml.dps.DpsIdGenerator;
import io.github.omartelo.nfse4j.core.xml.dps.DpsXmlBuilder;
import io.github.omartelo.nfse4j.core.xml.evento.CancelamentoNfse;
import io.github.omartelo.nfse4j.core.xml.evento.PedidoRegistroEventoXmlBuilder;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlSchemaValidatorTest {
    private static final String CHAVE = "31129806211222333000181000000000000001234567890123";

    @TempDir
    Path tempDir;

    @Test
    void dpsGeradaPeloBuilderEAssinadaPassaNoXsd() throws Exception {
        String xml = XmlSigner.signInfDps(new DpsXmlBuilder().build(dps()), certificado());

        assertEquals(List.of(), XmlSchemaValidator.validarDps(xml));
    }

    @Test
    void dpsInvalidaInformaLinhaElementoEMensagemDoSchema() {
        String xml = new DpsXmlBuilder().build(dps())
            .replace("<cTribNac>141001</cTribNac>", "<cTribNac>14</cTribNac>");

        List<XmlSchemaViolation> violacoes = XmlSchemaValidator.validarDps(xml);

        assertFalse(violacoes.isEmpty());
        XmlSchemaViolation violacao = violacoes.get(0);
        assertEquals(1, violacao.linha());
        assertEquals("cTribNac", violacao.elemento());
        assertTrue(violacao.mensagem().contains("'14'"), violacao.mensagem());
        assertTrue(violacao.toString().contains("cTribNac"), violacao.toString());
    }

    @Test
    void pedidoDeCancelamentoAssinadoPassaNoXsd() throws Exception {
        String xml = XmlSigner.signElement(
            new PedidoRegistroEventoXmlBuilder().buildCancelamento(2, cancelamento()),
            "infPedReg",
            certificado()
        );

        assertEquals(List.of(), XmlSchemaValidator.validarPedidoRegistroEvento(xml));
    }

    @Test
    void validacaoDaDpsRecusaOutroDocumento() {
        String evento = new PedidoRegistroEventoXmlBuilder().buildCancelamento(2, cancelamento());

        List<XmlSchemaViolation> violacoes = XmlSchemaValidator.validarDps(evento);

        assertEquals(1, violacoes.size());
        assertEquals("pedRegEvento", violacoes.get(0).elemento());
    }

    @Test
    void xmlMalformadoViraViolacao() {
        List<XmlSchemaViolation> violacoes = XmlSchemaValidator.validarDps("nao e xml");

        assertEquals(1, violacoes.size());
        assertEquals(1, violacoes.get(0).linha());
    }

    private CertificadoA1 certificado() throws Exception {
        char[] password = "senha-teste".toCharArray();
        Path certificatePath = tempDir.resolve("certificado-teste.p12");
        TestPkcs12Factory.create(certificatePath, password, "nfse-test", "12345678000195");
        return CertificadoA1.fromFile(certificatePath, password);
    }

    private static CancelamentoNfse cancelamento() {
        return new CancelamentoNfse(
            CHAVE,
            "12345678000195",
            OffsetDateTime.parse("2026-06-09T13:10:00-03:00"),
            1,
            "2",
            "Servico nao prestado",
            "nfse-nacional-kit"
        );
    }

    private static Dps dps() {
        return new Dps(
            "1.01",
            new Dps.InfDps(
                DpsIdGenerator.generate("12345678000195", "3129806", "70000", 24),
                2,
                OffsetDateTime.parse("2026-06-09T12:46:36-03:00"),
                "nfse-nacional-kit",
                "70000",
                24,
                LocalDate.parse("2026-05-22"),
                1,
                "3129806",
                new Dps.Prestador(
                    "12345678000195",
                    null,
                    "31999999999",
                    "prestador@example.test",
                    new Dps.RegimeTributario(2, 0)
                ),
                new Dps.Tomador(
                    "98765432000198",
                    null,
                    "TOMADOR EXEMPLO LTDA",
                    new Dps.Endereco("3129806", "32432025", "RUA EXEMPLO", "01", null, "CENTRO"),
                    null,
                    null
                ),
                new Dps.Servico("3129806", "141001", "Servico de lavanderia conforme pedido de teste"),
                new Dps.Valores(new BigDecimal("442.00"), new Dps.Tributacao(1, 1, 0))
            )
        );
    }
}
