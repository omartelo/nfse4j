package io.github.omartelo.nfse4j.core.xml.evento;

import java.util.Locale;
import java.util.Objects;

final class CamposPedidoEvento {

    private CamposPedidoEvento() {
    }

    static String normalizarAutor(String cpfCnpjAutor) {
        Objects.requireNonNull(cpfCnpjAutor, "cpfCnpjAutor is required");
        String documento = cpfCnpjAutor.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (documento.length() != 11 && documento.length() != 14) {
            throw new IllegalArgumentException("CPF/CNPJ do autor deve ter 11 ou 14 caracteres.");
        }
        return documento;
    }

    static void exigirPreenchido(String valor, String mensagem) {
        if (!preenchido(valor)) {
            throw new IllegalArgumentException(mensagem);
        }
    }

    static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
