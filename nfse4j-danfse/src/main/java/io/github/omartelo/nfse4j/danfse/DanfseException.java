package io.github.omartelo.nfse4j.danfse;

public class DanfseException extends RuntimeException {

    public DanfseException(String message) {
        super(message);
    }

    public DanfseException(String message, Throwable cause) {
        super(message, cause);
    }
}
