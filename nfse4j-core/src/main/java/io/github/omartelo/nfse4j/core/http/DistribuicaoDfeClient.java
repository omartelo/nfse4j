package io.github.omartelo.nfse4j.core.http;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.omartelo.nfse4j.core.NfseContext;
import io.github.omartelo.nfse4j.core.http.LoteDistribuicaoDfe.Documento;
import io.github.omartelo.nfse4j.core.http.LoteDistribuicaoDfe.StatusProcessamento;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Distribuicao de DF-e do ADN: entrega os documentos em que o CNPJ do certificado e emitente, tomador
 * ou intermediario, em ordem de NSU. Autentica pelo proprio certificado (mTLS), sem token.
 *
 * <p>HTTP 400 e 404 sao respostas de negocio (ex.: E2215, nada a partir do NSU; E2230, NSU inexistente)
 * e voltam como {@link LoteDistribuicaoDfe}; os demais codigos lancam {@link DistribuicaoDfeException}.
 *
 * <p>{@code cnpjConsulta} e opcional ({@code null} consulta o CNPJ do certificado); quando informado,
 * precisa ter a mesma raiz do certificado, senao o ADN rejeita. Aceita CNPJ alfanumerico, sem mascara.
 */
public final class DistribuicaoDfeClient {
    private static final Map<String, String> JSON_HEADERS = Map.of("Accept", "application/json");
    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
        .create();
    // CNPJ alfanumerico (IN RFB 2.229/2024): 12 posicoes alfanumericas + 2 digitos verificadores.
    private static final Pattern CNPJ = Pattern.compile("[A-Z0-9]{12}[0-9]{2}");
    private static final Pattern CHAVE_ACESSO = Pattern.compile("[0-9]{50}");
    private static final int LIMITE_CORPO_NA_MENSAGEM = 300;

    private final NfseHttpClient httpClient;
    private final URI baseUri;

    public DistribuicaoDfeClient(NfseContext context) {
        this(
            NfseHttpClient.create(context),
            URI.create(context.endpointResolver().adn(context.ambiente()) + "/contribuintes")
        );
    }

    DistribuicaoDfeClient(NfseHttpClient httpClient, URI baseUri) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri is required");
    }

    /** Lote de documentos a partir do NSU informado; {@code 0} comeca do inicio. */
    public LoteDistribuicaoDfe distribuir(long nsu, String cnpjConsulta) {
        return consultar("/DFe/" + exigirNsu(nsu) + "?lote=true" + parametroCnpj("&", cnpjConsulta));
    }

    /** Apenas o documento do NSU informado, sem avancar em lote. */
    public LoteDistribuicaoDfe consultarNsu(long nsu, String cnpjConsulta) {
        return consultar("/DFe/" + exigirNsu(nsu) + parametroCnpj("?", cnpjConsulta));
    }

    /** Eventos vinculados a uma chave de acesso de 50 digitos. */
    public LoteDistribuicaoDfe consultarEventos(String chaveAcesso) {
        Objects.requireNonNull(chaveAcesso, "chaveAcesso is required");
        if (!CHAVE_ACESSO.matcher(chaveAcesso).matches()) {
            throw new IllegalArgumentException("Chave de acesso da NFS-e deve ter 50 digitos: " + chaveAcesso);
        }
        return consultar("/NFSe/" + chaveAcesso + "/Eventos");
    }

    /**
     * Drena a distribuicao a partir do ultimo NSU ja processado ({@code 0} comeca do inicio) ate o ADN
     * responder {@link StatusProcessamento#NENHUM_DOCUMENTO_LOCALIZADO} ou ate {@code maxLotes} lotes.
     * O ADN nao informa teto de NSU, entao a trava de lotes e o que impede laco infinito.
     *
     * <p>Documentos com NSU ate o ultimo processado sao descartados: o lote seguinte pode repetir o NSU
     * de partida.
     *
     * @throws DistribuicaoDfeException se o ADN rejeitar a consulta ou devolver status desconhecido
     */
    public DrenagemDfe drenar(long ultimoNsuProcessado, String cnpjConsulta, int maxLotes) {
        if (maxLotes < 1) {
            throw new IllegalArgumentException("maxLotes deve ser ao menos 1: " + maxLotes);
        }
        List<Documento> documentos = new ArrayList<>();
        long ultimoNsu = ultimoNsuProcessado;
        for (int lotes = 0; lotes < maxLotes; lotes++) {
            LoteDistribuicaoDfe lote = distribuir(ultimoNsu, cnpjConsulta);
            if (lote.statusProcessamento() == StatusProcessamento.NENHUM_DOCUMENTO_LOCALIZADO) {
                return new DrenagemDfe(documentos, ultimoNsu, true);
            }
            if (lote.statusProcessamento() != StatusProcessamento.DOCUMENTOS_LOCALIZADOS) {
                throw new DistribuicaoDfeException("Distribuicao de DF-e a partir do NSU " + ultimoNsu
                    + " retornou " + lote.statusProcessamento() + ": " + lote.descreverErros());
            }
            List<Documento> novos = documentosApos(lote, ultimoNsu);
            if (novos.isEmpty()) {
                return new DrenagemDfe(documentos, ultimoNsu, true);
            }
            documentos.addAll(novos);
            ultimoNsu = novos.stream().mapToLong(Documento::nsu).max().orElseThrow();
        }
        return new DrenagemDfe(documentos, ultimoNsu, false);
    }

    private static List<Documento> documentosApos(LoteDistribuicaoDfe lote, long nsu) {
        return lote.loteDfe().stream().filter(documento -> documento.nsu() > nsu).toList();
    }

    private LoteDistribuicaoDfe consultar(String caminho) {
        URI uri = URI.create(baseUri + caminho);
        NfseHttpResponse response = httpClient.get(uri, JSON_HEADERS);
        boolean respostaDeNegocio = response.statusCode() == 400 || response.statusCode() == 404;
        if (!response.isSuccessful() && !respostaDeNegocio) {
            throw new DistribuicaoDfeException("Distribuicao de DF-e em " + uri + " retornou HTTP "
                + response.statusCode() + ": " + abreviar(response.body()));
        }
        if (response.body() == null || response.body().isBlank()) {
            throw new DistribuicaoDfeException("Distribuicao de DF-e em " + uri + " retornou HTTP "
                + response.statusCode() + " com corpo vazio.");
        }
        return GSON.fromJson(response.body(), LoteDistribuicaoDfe.class);
    }

    private static long exigirNsu(long nsu) {
        if (nsu < 0) {
            throw new IllegalArgumentException("NSU nao pode ser negativo: " + nsu);
        }
        return nsu;
    }

    private static String parametroCnpj(String separador, String cnpjConsulta) {
        if (cnpjConsulta == null) {
            return "";
        }
        String cnpj = cnpjConsulta.toUpperCase(Locale.ROOT);
        if (!CNPJ.matcher(cnpj).matches()) {
            throw new IllegalArgumentException(
                "cnpjConsulta deve ter 14 caracteres sem mascara (12 alfanumericos + 2 digitos): " + cnpjConsulta);
        }
        return separador + "cnpjConsulta=" + cnpj;
    }

    private static String abreviar(String corpo) {
        if (corpo == null || corpo.length() <= LIMITE_CORPO_NA_MENSAGEM) {
            return corpo;
        }
        return corpo.substring(0, LIMITE_CORPO_NA_MENSAGEM) + "...";
    }
}
