package io.github.omartelo.nfse4j.core.xml.evento;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestacaoNfseTest {

    private static final String CHAVE = "31298062112223330001810000000000000012345678901234";

    private static ManifestacaoNfse manifestacao(TipoManifestacao tipo, String codigoMotivo, String descricaoMotivo) {
        return new ManifestacaoNfse(
            CHAVE, "12.345.678/0001-95", OffsetDateTime.now(), tipo, codigoMotivo, descricaoMotivo, "nfse4j");
    }

    @Test
    void shouldRequireMotivoOnRejeicao() {
        assertThrows(IllegalArgumentException.class,
            () -> manifestacao(TipoManifestacao.REJEICAO_TOMADOR, null, null));
    }

    @Test
    void shouldRefuseMotivoOnConfirmacao() {
        assertThrows(IllegalArgumentException.class,
            () -> manifestacao(TipoManifestacao.CONFIRMACAO_PRESTADOR, "1", null));
        assertThrows(IllegalArgumentException.class,
            () -> manifestacao(TipoManifestacao.CONFIRMACAO_PRESTADOR, null, "Descricao qualquer do motivo"));
    }

    @Test
    void shouldNormalizeAutorAndExposeTipoEvento() {
        ManifestacaoNfse manifestacao = manifestacao(TipoManifestacao.CONFIRMACAO_INTERMEDIARIO, null, null);

        assertEquals("12345678000195", manifestacao.cpfCnpjAutor());
        assertEquals("204203", manifestacao.tipoEvento());
        assertEquals("PRE" + CHAVE + "204203", manifestacao.idPedidoRegistroEvento());
    }
}
