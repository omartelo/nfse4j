package io.github.omartelo.nfse4j.core.xml.dps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DpsTest {

    @Test
    void tributacaoRecusaMaisDeUmGrupoDeTotalDeTributos() {
        var valor = new Dps.TotalTributos(new BigDecimal("33.13"), BigDecimal.ZERO, new BigDecimal("4.80"));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> new Dps.Tributacao(1, 1, 0, null, valor, null));

        assertEquals(
            "totTrib aceita um so grupo (choice do XSD da DPS); vieram juntos: "
                + "valorTotalTributos, indicadorTotalTributos",
            ex.getMessage());
    }
}
