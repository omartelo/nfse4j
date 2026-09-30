package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @ParameterizedTest
    @ValueSource(strings = {"12ABC34501DE35", "12.abc.345/01de-35"})
    void shouldRejectAlphanumericCnpjUntilLayoutAcceptsIt(String cnpj) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> cancelamento(cnpj));

        assertEquals("CNPJ alfanumérico ainda não é aceito pelo leiaute da NFS-e Nacional (TSCNPJ [0-9]{14}, XSD v1.01): 12ABC34501DE35", erro.getMessage());
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
