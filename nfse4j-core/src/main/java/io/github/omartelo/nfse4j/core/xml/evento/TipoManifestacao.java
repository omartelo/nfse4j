package io.github.omartelo.nfse4j.core.xml.evento;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Manifestacoes que prestador, tomador e intermediario podem registrar (tiposEventos v1.01).
 * Confirmacao tacita (205204) e anulacao da rejeicao (205208) ficam de fora: sao do sistema/fisco.
 */
public enum TipoManifestacao {
    CONFIRMACAO_PRESTADOR("202201", "Manifestação de NFS-e - Confirmação do Prestador", false),
    CONFIRMACAO_TOMADOR("203202", "Manifestação de NFS-e - Confirmação do Tomador", false),
    CONFIRMACAO_INTERMEDIARIO("204203", "Manifestação de NFS-e - Confirmação do Intermediário", false),
    REJEICAO_PRESTADOR("202205", "Manifestação de NFS-e - Rejeição do Prestador", true),
    REJEICAO_TOMADOR("203206", "Manifestação de NFS-e - Rejeição do Tomador", true),
    REJEICAO_INTERMEDIARIO("204207", "Manifestação de NFS-e - Rejeição do Intermediário", true);

    private final String tipoEvento;
    private final String descricao;
    private final boolean rejeicao;

    TipoManifestacao(String tipoEvento, String descricao, boolean rejeicao) {
        this.tipoEvento = tipoEvento;
        this.descricao = descricao;
        this.rejeicao = rejeicao;
    }

    /** Aceita o nome do enum em maiusculas ou minusculas, com '_' ou '-' (ex.: rejeicao-tomador). */
    public static TipoManifestacao porNome(String nome) {
        String normalizado = nome.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
            .filter(tipo -> tipo.name().equals(normalizado))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tipo de manifestacao invalido: " + nome
                + ". Aceitos: " + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) + "."));
    }

    public String tipoEvento() {
        return tipoEvento;
    }

    public String descricao() {
        return descricao;
    }

    public boolean rejeicao() {
        return rejeicao;
    }
}
