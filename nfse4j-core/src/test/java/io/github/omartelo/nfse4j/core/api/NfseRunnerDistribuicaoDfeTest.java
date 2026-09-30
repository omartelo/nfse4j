package io.github.omartelo.nfse4j.core.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omartelo.nfse4j.core.Ambiente;
import io.github.omartelo.nfse4j.core.certificate.CertificadoA1;
import io.github.omartelo.nfse4j.core.certificate.TestPkcs12Factory;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NfseRunnerDistribuicaoDfeTest {

    private static final char[] SENHA = "test-pass".toCharArray();
    private static final String CNPJ_CERTIFICADO = "12345678000199";
    private static final String CNPJ_OUTRA_RAIZ = "87654321000199";

    private static CertificadoA1 certificado(Path dir) throws Exception {
        Path p = dir.resolve("cert.p12");
        TestPkcs12Factory.create(p, SENHA, "1", CNPJ_CERTIFICADO);
        return CertificadoA1.fromFile(p, SENHA);
    }

    @Test
    void distribuirComCnpjDeOutraRaizFalhaAntesDaRede(@TempDir Path dir) throws Exception {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> NfseRunner.distribuirDfe(0, CNPJ_OUTRA_RAIZ, Ambiente.HOMOLOGACAO, certificado(dir)));
        assertTrue(ex.getMessage().contains("raiz"));
    }

    @Test
    void consultarNsuComCnpjDeOutraRaizFalhaAntesDaRede(@TempDir Path dir) throws Exception {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> NfseRunner.consultarDfe(1, CNPJ_OUTRA_RAIZ, Ambiente.HOMOLOGACAO, certificado(dir)));
        assertTrue(ex.getMessage().contains("raiz"));
    }
}
