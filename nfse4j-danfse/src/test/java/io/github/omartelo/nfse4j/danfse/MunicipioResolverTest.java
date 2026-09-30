package io.github.omartelo.nfse4j.danfse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MunicipioResolverTest {

    @Test
    void derivaSiglaDaUfDosDoisPrimeirosDigitosDoCodigoIbge() {
        assertEquals("SP", MunicipioResolver.uf("3550308"), "Sao Paulo");
        assertEquals("MT", MunicipioResolver.uf("5105101"), "codigo do MT");
        assertEquals("DF", MunicipioResolver.uf("5300108"), "Brasilia");
        assertEquals("RS", MunicipioResolver.uf("4314902"), "Porto Alegre");
    }

    @Test
    void ufNulaQuandoCodigoInvalidoOuDesconhecido() {
        assertNull(MunicipioResolver.uf(null));
        assertNull(MunicipioResolver.uf(""));
        assertNull(MunicipioResolver.uf("123"), "codigo curto demais");
        assertNull(MunicipioResolver.uf("9999999"), "prefixo 99 nao e UF");
        assertNull(MunicipioResolver.uf("abcdefg"));
    }
}
