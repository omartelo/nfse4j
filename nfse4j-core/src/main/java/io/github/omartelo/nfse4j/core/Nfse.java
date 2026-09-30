package io.github.omartelo.nfse4j.core;

import io.github.omartelo.nfse4j.core.http.ParametrosMunicipaisClient;
import io.github.omartelo.nfse4j.core.service.ContribuinteService;

public final class Nfse {
    private final NfseContext context;
    private final ContribuinteService contribuinteService;

    public Nfse(NfseContext context) {
        this.context = context;
        this.contribuinteService = new ContribuinteService(context);
    }

    public ContribuinteService contribuinte() {
        return contribuinteService;
    }

    public ParametrosMunicipaisClient parametrosMunicipais() {
        return new ParametrosMunicipaisClient(context);
    }
}
