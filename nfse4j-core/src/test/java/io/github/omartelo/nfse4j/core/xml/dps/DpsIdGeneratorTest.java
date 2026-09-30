package io.github.omartelo.nfse4j.core.xml.dps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DpsIdGeneratorTest {

    @Test
    void shouldGenerateDpsIdForCnpj() {
        String id = DpsIdGenerator.generate("19.441.457/0001-60", "3129806", "70000", 24);

        assertEquals("DPS312980621944145700016070000000000000000024", id);
        assertEquals(45, id.length());
    }

    @Test
    void shouldGenerateDpsIdForAlphanumericCnpj() {
        String id = DpsIdGenerator.generate("12ABC34501DE35", "3129806", "70000", 24);

        assertEquals("DPS3129806212ABC34501DE3570000000000000000024", id);
        assertEquals(45, id.length());
    }

    @Test
    void shouldAcceptMaskedAlphanumericCnpj() {
        assertEquals(
            DpsIdGenerator.generate("12ABC34501DE35", "3129806", "1", 1),
            DpsIdGenerator.generate("12.ABC.345/01DE-35", "3129806", "1", 1));
    }

    @Test
    void shouldUppercaseAlphanumericCnpj() {
        assertEquals(
            DpsIdGenerator.generate("12ABC34501DE35", "3129806", "1", 1),
            DpsIdGenerator.generate("12abc34501de35", "3129806", "1", 1));
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
