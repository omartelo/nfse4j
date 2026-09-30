package io.github.omartelo.nfse4j.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omartelo.nfse4j.core.Ambiente;
import io.github.omartelo.nfse4j.core.api.EmitirNfseRequest.ServicoRequest;
import io.github.omartelo.nfse4j.core.api.EmitirNfseRequest.TomadorRequest;
import io.github.omartelo.nfse4j.core.api.EmitirNfseRequest.TributacaoRequest;
import io.github.omartelo.nfse4j.core.certificate.CertificadoA1;
import io.github.omartelo.nfse4j.core.certificate.TestPkcs12Factory;
import io.github.omartelo.nfse4j.core.xml.evento.TipoManifestacao;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NfseRunnerTest {

    private static final char[] SENHA = "test-pass".toCharArray();
    private static final String CNPJ = "12345678000199";
    private static final String CHAVE_ACESSO = "31298062112223330001810000000000000012345678901234";

    private static EmitirNfseRequest request() {
        return new EmitirNfseRequest(
            null, "3550308", "1", 1L, null, new BigDecimal("100.00"),
            null,
            new TomadorRequest(null, "11144477735", "Fulano", null, null, null, null, null, null, null, null),
            new ServicoRequest(null, "010101", null, "Servico de teste", null),
            new TributacaoRequest(1, 1, 0, null));
    }

    private static CertificadoA1 certValido(Path dir) throws Exception {
        Path p = dir.resolve("valido.p12");
        TestPkcs12Factory.create(p, SENHA, "1", CNPJ);
        return CertificadoA1.fromFile(p, SENHA);
    }

    @Test
    void ambienteResolveValores() {
        assertEquals(Ambiente.HOMOLOGACAO, NfseRunner.ambiente(null));
        assertEquals(Ambiente.HOMOLOGACAO, NfseRunner.ambiente("homologacao"));
        assertEquals(Ambiente.PRODUCAO, NfseRunner.ambiente("producao"));
        assertThrows(IllegalArgumentException.class, () -> NfseRunner.ambiente("xpto"));
    }

    @Test
    void certExtraiDadosDoCertificado(@TempDir Path dir) throws Exception {
        NfseRunner.CertInfo info = NfseRunner.cert(certValido(dir));
        assertEquals(CNPJ, info.cpfCnpj());
        assertFalse(info.expirado());
    }

    @Test
    void emitirEmProducaoSemConfirmacaoFalhaAntesDaRede(@TempDir Path dir) throws Exception {
        var ex = assertThrows(IllegalStateException.class,
            () -> NfseRunner.emitir(request(), Ambiente.PRODUCAO, certValido(dir), false));
        assertTrue(ex.getMessage().contains("PRODUCAO"));
    }

    @Test
    void cancelarEmProducaoSemConfirmacaoFalhaAntesDaRede(@TempDir Path dir) throws Exception {
        var ex = assertThrows(IllegalStateException.class,
            () -> NfseRunner.cancelar("CHAVE", null, 1, "1", "motivo",
                Ambiente.PRODUCAO, certValido(dir), false));
        assertTrue(ex.getMessage().contains("PRODUCAO"));
    }

    @Test
    void manifestarEmProducaoSemConfirmacaoFalhaAntesDaRede(@TempDir Path dir) throws Exception {
        var ex = assertThrows(IllegalStateException.class,
            () -> NfseRunner.manifestar(CHAVE_ACESSO, null, TipoManifestacao.CONFIRMACAO_TOMADOR, null, null,
                Ambiente.PRODUCAO, certValido(dir), false));
        assertTrue(ex.getMessage().contains("PRODUCAO"));
    }

    @Test
    void solicitarAnaliseFiscalEmProducaoSemConfirmacaoFalhaAntesDaRede(@TempDir Path dir) throws Exception {
        var ex = assertThrows(IllegalStateException.class,
            () -> NfseRunner.solicitarAnaliseFiscalCancelamento(CHAVE_ACESSO, null, "1",
                "Erro na emissao da nota fiscal", Ambiente.PRODUCAO, certValido(dir), false));
        assertTrue(ex.getMessage().contains("PRODUCAO"));
    }

    @Test
    void travaDeProducaoNomeiaAOperacao(@TempDir Path dir) throws Exception {
        CertificadoA1 cert = certValido(dir);

        assertTrue(assertThrows(IllegalStateException.class,
            () -> NfseRunner.emitir(request(), Ambiente.PRODUCAO, cert, false))
            .getMessage().startsWith("Emissao em PRODUCAO"));
        assertTrue(assertThrows(IllegalStateException.class,
            () -> NfseRunner.cancelar(CHAVE_ACESSO, null, 1, "1", "Erro na emissao da nota",
                Ambiente.PRODUCAO, cert, false))
            .getMessage().startsWith("Cancelamento em PRODUCAO"));
        assertTrue(assertThrows(IllegalStateException.class,
            () -> NfseRunner.solicitarAnaliseFiscalCancelamento(CHAVE_ACESSO, null, "1",
                "Erro na emissao da nota", Ambiente.PRODUCAO, cert, false))
            .getMessage().startsWith("Solicitacao de analise fiscal de cancelamento em PRODUCAO"));
        assertTrue(assertThrows(IllegalStateException.class,
            () -> NfseRunner.manifestar(CHAVE_ACESSO, null, TipoManifestacao.REJEICAO_TOMADOR, "1", null,
                Ambiente.PRODUCAO, cert, false))
            .getMessage().startsWith("Manifestacao em PRODUCAO"));
    }

    @Test
    void manifestarComCertificadoExpiradoFalha(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("expirado.p12");
        TestPkcs12Factory.createExpired(p, SENHA, "1", CNPJ);
        CertificadoA1 expirado = CertificadoA1.fromFile(p, SENHA);

        var ex = assertThrows(IllegalStateException.class,
            () -> NfseRunner.manifestar(CHAVE_ACESSO, null, TipoManifestacao.CONFIRMACAO_TOMADOR, null, null,
                Ambiente.HOMOLOGACAO, expirado, false));
        assertTrue(ex.getMessage().contains("validade"));
    }

    @Test
    void emitirComCertificadoExpiradoFalha(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("expirado.p12");
        TestPkcs12Factory.createExpired(p, SENHA, "1", CNPJ);
        CertificadoA1 expirado = CertificadoA1.fromFile(p, SENHA);

        var ex = assertThrows(IllegalStateException.class,
            () -> NfseRunner.emitir(request(), Ambiente.HOMOLOGACAO, expirado, false));
        assertTrue(ex.getMessage().contains("validade"));
    }

    @Test
    void consultarAliquotaComMunicipioInvalidoFalhaAntesDaRede(@TempDir Path dir) throws Exception {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> NfseRunner.consultarAliquota("123", "010101", null, Ambiente.HOMOLOGACAO, certValido(dir)));
        assertTrue(ex.getMessage().contains("municipio"));
    }
}
