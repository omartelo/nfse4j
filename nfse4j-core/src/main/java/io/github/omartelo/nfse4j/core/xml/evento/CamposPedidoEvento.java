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
        recusarCnpjAlfanumerico(documento);
        return documento;
    }

    // O leiaute v1.01 (CNPJAutor do tipo TSCNPJ [0-9]{14}) ainda nao aceita o CNPJ alfanumerico da
    // IN RFB 2.229/2024. A normalizacao ja preserva as letras: quando o XSD mudar, basta remover esta checagem.
    private static void recusarCnpjAlfanumerico(String documento) {
        if (documento.length() == 14 && !documento.matches("[0-9]{14}")) {
            throw new IllegalArgumentException(
                "CNPJ alfanumérico ainda não é aceito pelo leiaute da NFS-e Nacional (TSCNPJ [0-9]{14}, XSD v1.01): "
                    + documento);
        }
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
