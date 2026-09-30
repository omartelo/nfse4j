package io.github.omartelo.nfse4j.core.xml.evento;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipoManifestacaoTest {

    @Test
    void shouldAcceptEnumNameAndKebabCase() {
        assertEquals(TipoManifestacao.CONFIRMACAO_TOMADOR, TipoManifestacao.porNome("CONFIRMACAO_TOMADOR"));
        assertEquals(TipoManifestacao.REJEICAO_INTERMEDIARIO, TipoManifestacao.porNome("rejeicao-intermediario"));
    }

    @Test
    void shouldListAcceptedValuesWhenInvalid() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> TipoManifestacao.porNome("xpto"));

        assertTrue(exception.getMessage().startsWith("Tipo de manifestacao invalido: xpto."));
        for (TipoManifestacao tipo : TipoManifestacao.values()) {
            assertTrue(exception.getMessage().contains(tipo.name()), exception.getMessage());
        }
    }
}
