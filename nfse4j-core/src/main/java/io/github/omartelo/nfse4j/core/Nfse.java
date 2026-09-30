package io.github.omartelo.nfse4j.core;

import io.github.omartelo.nfse4j.core.service.ContribuinteService;

public final class Nfse {
    private final ContribuinteService contribuinteService;

    public Nfse(NfseContext context) {
        this.contribuinteService = new ContribuinteService(context);
    }

    public ContribuinteService contribuinte() {
        return contribuinteService;
    }
}
