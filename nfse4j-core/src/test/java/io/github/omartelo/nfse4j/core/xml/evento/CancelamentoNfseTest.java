package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancelamentoNfseTest {

    private static final String CHAVE = "31129806211222333000181000000000000001234567890123";

    private static CancelamentoNfse cancelamento(String cpfCnpjAutor) {
        return new CancelamentoNfse(
            CHAVE,
            cpfCnpjAutor,
            OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.ofHours(-3)),
            1,
            "1",
            "Cancelamento por erro na emissao",
            "1.0.0");
    }

    @Test
    void shouldAcceptAlphanumericCnpj() {
        CancelamentoNfse evento = cancelamento("12ABC34501DE35");

        assertEquals("12ABC34501DE35", evento.cpfCnpjAutor());
        assertTrue(evento.autorPessoaJuridica());
    }

    @Test
    void shouldNormalizeMaskAndCase() {
        assertEquals("12ABC34501DE35", cancelamento("12.abc.345/01de-35").cpfCnpjAutor());
    }

    @Test
    void shouldAcceptNumericCnpjAndCpf() {
        assertEquals("11222333000181", cancelamento("11.222.333/0001-81").cpfCnpjAutor());
        assertEquals("11144477735", cancelamento("111.444.777-35").cpfCnpjAutor());
    }

    @Test
    void shouldRejectDocumentWithWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> cancelamento("123"));
    }
}
