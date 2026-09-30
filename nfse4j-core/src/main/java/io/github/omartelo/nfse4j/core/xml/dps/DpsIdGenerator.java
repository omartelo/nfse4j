package io.github.omartelo.nfse4j.core.xml.dps;

import java.util.Locale;
import java.util.Objects;

public final class DpsIdGenerator {

    private DpsIdGenerator() {
    }

    public static String generate(String cpfCnpj, String codigoMunicipio, String serie, long numeroDps) {
        String documento = normalizeDocumento(cpfCnpj);
        String municipio = onlyDigits(codigoMunicipio);
        String normalizedSerie = Objects.requireNonNull(serie, "serie is required");

        if (documento.length() != 11 && documento.length() != 14) {
            throw new IllegalArgumentException("CPF/CNPJ do emitente deve ter 11 ou 14 caracteres.");
        }
        recusarCnpjAlfanumerico(documento);
        if (municipio.length() != 7) {
            throw new IllegalArgumentException("Codigo do municipio deve ter 7 digitos.");
        }
        if (normalizedSerie.length() > 5) {
            throw new IllegalArgumentException("Serie da DPS deve ter no maximo 5 caracteres.");
        }
        if (numeroDps < 0) {
            throw new IllegalArgumentException("Numero da DPS nao pode ser negativo.");
        }

        String tipoInscricao = documento.length() == 14 ? "2" : "1";
        return "DPS"
            + municipio
            + tipoInscricao
            + leftPad(documento, 14)
            + leftPad(normalizedSerie, 5)
            + leftPad(Long.toString(numeroDps), 15);
    }

    private static String onlyDigits(String value) {
        Objects.requireNonNull(value, "value is required");
        return value.replaceAll("\\D", "");
    }

    private static String normalizeDocumento(String value) {
        Objects.requireNonNull(value, "value is required");
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    // O leiaute v1.01 (TSCNPJ [0-9]{14}, TSIdDPS DPS[0-9]{42}) ainda nao aceita o CNPJ alfanumerico da
    // IN RFB 2.229/2024. A normalizacao ja preserva as letras: quando o XSD mudar, basta remover esta checagem.
    private static void recusarCnpjAlfanumerico(String documento) {
        if (documento.length() == 14 && !documento.matches("[0-9]{14}")) {
            throw new IllegalArgumentException(
                "CNPJ alfanumérico ainda não é aceito pelo leiaute da NFS-e Nacional (TSCNPJ [0-9]{14}, XSD v1.01): "
                    + documento);
        }
    }

    private static String leftPad(String value, int size) {
        if (value.length() > size) {
            throw new IllegalArgumentException("Valor excede tamanho maximo de " + size + " caracteres.");
        }
        return "0".repeat(size - value.length()) + value;
    }
}
