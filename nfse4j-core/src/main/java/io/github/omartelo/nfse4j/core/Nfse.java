package io.github.omartelo.nfse4j.core;

import io.github.omartelo.nfse4j.core.service.ContribuinteService;
import io.github.omartelo.nfse4j.core.service.DanfseService;

public final class Nfse {
    private final ContribuinteService contribuinteService;
    private final DanfseService danfseService;

    public Nfse(NfseContext context) {
        this.contribuinteService = new ContribuinteService(context);
        this.danfseService = new DanfseService(context);
    }

    public ContribuinteService contribuinte() {
        return contribuinteService;
    }

    public DanfseService danfse() {
        return danfseService;
    }
}
