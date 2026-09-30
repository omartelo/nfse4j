package io.github.omartelo.nfse4j.core.http;

import io.github.omartelo.nfse4j.core.Ambiente;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class EndpointResolver {
    private static final URI SEFIN_HOMOLOGACAO = URI.create(
        "https://sefin.producaorestrita.nfse.gov.br/SefinNacional"
    );
    private static final URI SEFIN_PRODUCAO = URI.create("https://sefin.nfse.gov.br/SefinNacional");

    private final Map<Ambiente, URI> sefinEndpoints;

    private EndpointResolver(Map<Ambiente, URI> sefinEndpoints) {
        this.sefinEndpoints = new EnumMap<>(sefinEndpoints);
    }

    public static EndpointResolver defaultResolver() {
        return withSefinEndpoints(SEFIN_HOMOLOGACAO, SEFIN_PRODUCAO);
    }

    public static EndpointResolver withSefinEndpoints(URI homologacao, URI producao) {
        Objects.requireNonNull(homologacao, "homologacao is required");
        Objects.requireNonNull(producao, "producao is required");

        EnumMap<Ambiente, URI> endpoints = new EnumMap<>(Ambiente.class);
        endpoints.put(Ambiente.HOMOLOGACAO, homologacao);
        endpoints.put(Ambiente.PRODUCAO, producao);
        return new EndpointResolver(endpoints);
    }

    public URI sefin(Ambiente ambiente) {
        URI endpoint = sefinEndpoints.get(Objects.requireNonNull(ambiente, "ambiente is required"));
        if (endpoint == null) {
            throw new IllegalArgumentException("Ambiente sem endpoint SEFIN configurado: " + ambiente);
        }
        return endpoint;
    }
}
