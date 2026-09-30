package io.github.omartelo.nfse4j.core.xml.dps;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DpsIdGeneratorTest {

    @Test
    void shouldGenerateDpsIdForCnpj() {
        String id = DpsIdGenerator.generate("19.441.457/0001-60", "3129806", "70000", 24);

        assertEquals("DPS312980621944145700016070000000000000000024", id);
        assertEquals(45, id.length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12ABC34501DE35", "12.ABC.345/01DE-35", "12abc34501de35"})
    void shouldRejectAlphanumericCnpjUntilLayoutAcceptsIt(String cnpj) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
            () -> DpsIdGenerator.generate(cnpj, "3129806", "70000", 24));

        assertEquals("CNPJ alfanumérico ainda não é aceito pelo leiaute da NFS-e Nacional (TSCNPJ [0-9]{14}, XSD v1.01): 12ABC34501DE35", erro.getMessage());
    }

    @Test
    void shouldGenerateDpsIdForCpf() {
        String id = DpsIdGenerator.generate("111.444.777-35", "3129806", "1", 1);

        assertEquals("DPS312980610001114447773500001000000000000001", id);
    }

    @Test
    void shouldRejectInvalidDocument() {
        assertThrows(IllegalArgumentException.class, () -> DpsIdGenerator.generate("123", "3129806", "1", 1));
    }
}
