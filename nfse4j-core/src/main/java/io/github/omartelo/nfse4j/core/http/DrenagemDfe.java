package io.github.omartelo.nfse4j.core.http;

import io.github.omartelo.nfse4j.core.http.LoteDistribuicaoDfe.Documento;
import java.util.List;

/**
 * Resultado de {@link DistribuicaoDfeClient#drenar}. {@code ultimoNsu} e o ponto de partida da proxima
 * chamada; {@code concluida=false} significa que a trava de lotes parou a drenagem antes do fim.
 */
public record DrenagemDfe(List<Documento> documentos, long ultimoNsu, boolean concluida) {

    public DrenagemDfe {
        documentos = List.copyOf(documentos);
    }
}
