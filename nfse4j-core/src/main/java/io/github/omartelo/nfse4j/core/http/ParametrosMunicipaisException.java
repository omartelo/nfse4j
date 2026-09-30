package io.github.omartelo.nfse4j.core.http;

/** Falha na consulta de parametros municipais: status HTTP de erro do ADN ou resposta ilegivel. */
public class ParametrosMunicipaisException extends RuntimeException {
    private final int statusHttp;

    public ParametrosMunicipaisException(int statusHttp, String message) {
        super(message);
        this.statusHttp = statusHttp;
    }

    public ParametrosMunicipaisException(int statusHttp, String message, Throwable cause) {
        super(message, cause);
        this.statusHttp = statusHttp;
    }

    public int statusHttp() {
        return statusHttp;
    }
}
