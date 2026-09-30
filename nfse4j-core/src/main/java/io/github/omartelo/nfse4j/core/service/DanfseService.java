package io.github.omartelo.nfse4j.core.service;

import io.github.omartelo.nfse4j.core.NfseContext;
import io.github.omartelo.nfse4j.core.http.DanfseClient;
import io.github.omartelo.nfse4j.core.http.NfseBinaryResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class DanfseService {
    private final DanfseClient danfseClient;

    public DanfseService(NfseContext context) {
        this.danfseClient = new DanfseClient(context);
    }

    public NfseBinaryResponse baixarPdf(String chaveAcesso) {
        return danfseClient.baixarPdf(chaveAcesso);
    }

    public NfseBinaryResponse baixarPdf(String chaveAcesso, Path outputPath) {
        Objects.requireNonNull(outputPath, "outputPath is required");
        NfseBinaryResponse response = baixarPdf(chaveAcesso);
        if (response.isSuccessful()) {
            try {
                Path parent = outputPath.toAbsolutePath().normalize().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(outputPath, response.body());
            } catch (IOException exception) {
                throw new DanfseServiceException("Nao foi possivel gravar o PDF do DANFSe.", exception);
            }
        }
        return response;
    }
}
