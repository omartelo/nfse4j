package io.github.omartelo.nfse4j.core.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.omartelo.nfse4j.core.NfseContext;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.AliquotasResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.BeneficioResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.ConvenioResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.RegimesEspeciaisResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.RetencoesResposta;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cliente da API de Parametros Municipais do ADN ({@code <adn>/parametrizacao}), autenticado por mTLS com o A1.
 *
 * <p>Status 2xx e 404 sao respostas de negocio: o 404 volta como resposta com os dados nulos e a
 * {@code mensagem} do ADN (parametro nao encontrado). Qualquer outro status lanca
 * {@link ParametrosMunicipaisException}.
 */
public final class ParametrosMunicipaisClient {
    private static final Map<String, String> JSON_HEADERS = Map.of("Accept", "application/json");
    private static final int NAO_ENCONTRADO = 404;
    private static final Pattern CODIGO_MUNICIPIO = Pattern.compile("\\d{7}");
    // Codigo de tributacao nacional: 6 digitos (item + subitem + desdobro) ou 9 com o sufixo municipal.
    private static final Pattern CODIGO_SERVICO = Pattern.compile("\\d{6}|\\d{9}");
    // O ADN le a competencia da rota como MM-dd-yyyy (mesmo formato usado pelo t3wv/nfse).
    private static final DateTimeFormatter COMPETENCIA = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final Set<String> VERDADEIROS = Set.of("1", "true", "sim", "s");
    private static final Set<String> FALSOS = Set.of("0", "false", "nao", "não", "n");
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(BigDecimal.class, (JsonDeserializer<BigDecimal>) (json, type, context) -> decimal(json))
        .registerTypeAdapter(Boolean.class, (JsonDeserializer<Boolean>) (json, type, context) -> booleano(json))
        .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, type, context) -> dataHora(json))
        .create();

    private final NfseHttpClient httpClient;
    private final URI baseUri;

    public ParametrosMunicipaisClient(NfseContext context) {
        this(
            NfseHttpClient.create(context),
            URI.create(context.endpointResolver().adn(context.ambiente()) + "/parametrizacao")
        );
    }

    ParametrosMunicipaisClient(NfseHttpClient httpClient, URI baseUri) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri is required");
    }

    public ConvenioResposta consultarConvenio(String codigoMunicipio) {
        return get(municipio(codigoMunicipio) + "/convenio", ConvenioResposta.class);
    }

    /**
     * @param codigoServico codigo de tributacao nacional com 6 ou 9 digitos, com ou sem pontos (010101, 01.01.01.000)
     * @throws IllegalArgumentException se o municipio nao tiver 7 digitos ou o servico nao tiver 6 ou 9
     */
    public AliquotasResposta consultarAliquota(String codigoMunicipio, String codigoServico, LocalDate competencia) {
        return get(
            municipio(codigoMunicipio) + "/" + servico(codigoServico) + "/" + competencia(competencia) + "/aliquota",
            AliquotasResposta.class
        );
    }

    public AliquotasResposta consultarHistoricoAliquotas(String codigoMunicipio, String codigoServico) {
        return get(
            municipio(codigoMunicipio) + "/" + servico(codigoServico) + "/historicoaliquotas",
            AliquotasResposta.class
        );
    }

    public BeneficioResposta consultarBeneficio(String codigoMunicipio, String numeroBeneficio, LocalDate competencia) {
        Objects.requireNonNull(numeroBeneficio, "numeroBeneficio is required");
        String numero = URLEncoder.encode(numeroBeneficio, StandardCharsets.UTF_8).replace("+", "%20");
        return get(
            municipio(codigoMunicipio) + "/" + numero + "/" + competencia(competencia) + "/beneficio",
            BeneficioResposta.class
        );
    }

    public RegimesEspeciaisResposta consultarRegimesEspeciais(String codigoMunicipio, String codigoServico,
                                                              LocalDate competencia) {
        return get(
            municipio(codigoMunicipio) + "/" + servico(codigoServico) + "/" + competencia(competencia)
                + "/regimes_especiais",
            RegimesEspeciaisResposta.class
        );
    }

    public RetencoesResposta consultarRetencoes(String codigoMunicipio, LocalDate competencia) {
        return get(municipio(codigoMunicipio) + "/" + competencia(competencia) + "/retencoes", RetencoesResposta.class);
    }

    private <T> T get(String path, Class<T> tipo) {
        NfseHttpResponse response = httpClient.get(URI.create(baseUri + "/" + path), JSON_HEADERS);
        if (!response.isSuccessful() && response.statusCode() != NAO_ENCONTRADO) {
            throw new ParametrosMunicipaisException(response.statusCode(),
                "ADN retornou HTTP " + response.statusCode() + " em " + path + ": " + mensagemDeErro(response.body()));
        }
        try {
            T resposta = GSON.fromJson(response.body(), tipo);
            if (resposta == null) {
                throw new ParametrosMunicipaisException(response.statusCode(),
                    "ADN retornou HTTP " + response.statusCode() + " sem corpo em " + path + ".");
            }
            return resposta;
        } catch (JsonParseException exception) {
            throw new ParametrosMunicipaisException(response.statusCode(),
                "Resposta inesperada do ADN em " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static String mensagemDeErro(String body) {
        try {
            JsonElement json = JsonParser.parseString(body);
            if (json.isJsonObject() && json.getAsJsonObject().get("mensagem") instanceof JsonPrimitive mensagem) {
                return mensagem.getAsString();
            }
        } catch (JsonParseException naoJson) {
            // corpo nao-JSON (ex.: pagina do gateway) vai cru na mensagem
        }
        return body;
    }

    private static String municipio(String codigoMunicipio) {
        String digitos = Objects.requireNonNull(codigoMunicipio, "codigoMunicipio is required").replaceAll("\\D", "");
        if (!CODIGO_MUNICIPIO.matcher(digitos).matches()) {
            throw new IllegalArgumentException(
                "Codigo do municipio deve ter 7 digitos (IBGE); recebido: " + codigoMunicipio);
        }
        return digitos;
    }

    private static String servico(String codigoServico) {
        String digitos = Objects.requireNonNull(codigoServico, "codigoServico is required").replaceAll("\\D", "");
        if (!CODIGO_SERVICO.matcher(digitos).matches()) {
            throw new IllegalArgumentException(
                "Codigo do servico deve ter 6 ou 9 digitos (ex.: 010101 ou 01.01.01.000); recebido: " + codigoServico);
        }
        String completo = digitos.length() == 6 ? digitos + "000" : digitos;
        return completo.substring(0, 2) + "." + completo.substring(2, 4) + "."
            + completo.substring(4, 6) + "." + completo.substring(6);
    }

    private static String competencia(LocalDate competencia) {
        return Objects.requireNonNull(competencia, "competencia is required").format(COMPETENCIA);
    }

    // O ADN manda valores numericos como numero JSON ou como texto pt-BR ("2,50", "1.234,56").
    // Texto sem virgula e lido com ponto decimal.
    private static BigDecimal decimal(JsonElement json) {
        JsonPrimitive valor = json.getAsJsonPrimitive();
        if (valor.isNumber()) {
            return valor.getAsBigDecimal();
        }
        String texto = valor.getAsString().trim();
        String normalizado = texto.contains(",") ? texto.replace(".", "").replace(',', '.') : texto;
        try {
            return new BigDecimal(normalizado);
        } catch (NumberFormatException exception) {
            throw new JsonParseException("Valor decimal desconhecido: '" + texto + "'", exception);
        }
    }

    // O ADN manda flags como booleano JSON, 0/1 ou texto (Sim/Nao, S/N).
    private static Boolean booleano(JsonElement json) {
        JsonPrimitive valor = json.getAsJsonPrimitive();
        if (valor.isBoolean()) {
            return valor.getAsBoolean();
        }
        String texto = valor.getAsString().trim().toLowerCase(Locale.ROOT);
        if (VERDADEIROS.contains(texto)) {
            return true;
        }
        if (FALSOS.contains(texto)) {
            return false;
        }
        throw new JsonParseException("Valor booleano desconhecido: '" + valor.getAsString() + "'");
    }

    // Aceita data-hora com ou sem offset; o offset e descartado porque as vigencias sao datas locais.
    private static LocalDateTime dataHora(JsonElement json) {
        String texto = json.getAsString();
        try {
            return LocalDateTime.parse(texto, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new JsonParseException("Data-hora desconhecida: '" + texto + "'", exception);
        }
    }
}
