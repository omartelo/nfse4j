package io.github.omartelo.nfse4j.danfse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve o nome ("Cidade - UF") de um código IBGE de município consultando a API pública do IBGE,
 * para os municípios que o XML da NFS-e não traz por nome (ex.: tomador em outra cidade).
 *
 * <p>Robusto por design: resultado em cache em memória, timeout curto e <b>fallback gracioso</b> —
 * se o IBGE estiver indisponível/offline, retorna vazio e o DANFSe mostra o próprio código. Nunca
 * lança exceção que quebre a geração do PDF.
 */
public final class MunicipioResolver {

    private static final String API = "https://servicodados.ibge.gov.br/api/v1/localidades/municipios/";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Pattern NOME = Pattern.compile("\"nome\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGLA = Pattern.compile("\"sigla\"\\s*:\\s*\"([^\"]+)\"");

    // Cache compartilhado entre gerações. "" marca código consultado sem sucesso (evita repetir).
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    // Os dois primeiros digitos do codigo IBGE do municipio identificam a UF (tabela fixa do IBGE).
    private static final Map<String, String> UF_POR_PREFIXO = Map.ofEntries(
        Map.entry("11", "RO"), Map.entry("12", "AC"), Map.entry("13", "AM"), Map.entry("14", "RR"),
        Map.entry("15", "PA"), Map.entry("16", "AP"), Map.entry("17", "TO"), Map.entry("21", "MA"),
        Map.entry("22", "PI"), Map.entry("23", "CE"), Map.entry("24", "RN"), Map.entry("25", "PB"),
        Map.entry("26", "PE"), Map.entry("27", "AL"), Map.entry("28", "SE"), Map.entry("29", "BA"),
        Map.entry("31", "MG"), Map.entry("32", "ES"), Map.entry("33", "RJ"), Map.entry("35", "SP"),
        Map.entry("41", "PR"), Map.entry("42", "SC"), Map.entry("43", "RS"), Map.entry("50", "MS"),
        Map.entry("51", "MT"), Map.entry("52", "GO"), Map.entry("53", "DF"));

    private MunicipioResolver() {
    }

    /**
     * Sigla da UF a partir do codigo IBGE do municipio, sem consultar a rede. A NT 008 exige a
     * sigla em varios blocos (itens 2.1.3 a 2.1.8 e 2.1.10) e o XML nem sempre traz a tag UF,
     * mas o codigo do municipio esta sempre presente nesses pontos.
     *
     * @return a sigla, ou {@code null} se o codigo nao for um codigo IBGE de municipio valido
     */
    public static String uf(String codigoMunicipio) {
        if (codigoMunicipio == null || !codigoMunicipio.matches("\\d{7}")) {
            return null;
        }
        return UF_POR_PREFIXO.get(codigoMunicipio.substring(0, 2));
    }

    /**
     * Retorna "Cidade - UF" para o código IBGE, ou vazio se não resolver (offline/desconhecido).
     * Desative a consulta ao IBGE com {@code -Dnfse4j.danfse.ibge=false} (modo offline).
     */
    public static Optional<String> resolver(String codigoMunicipio) {
        if (codigoMunicipio == null || !codigoMunicipio.matches("\\d{7}")) {
            return Optional.empty();
        }
        if ("false".equalsIgnoreCase(System.getProperty("nfse4j.danfse.ibge"))) {
            return Optional.empty();
        }
        String cached = CACHE.get(codigoMunicipio);
        if (cached != null) {
            return cached.isEmpty() ? Optional.empty() : Optional.of(cached);
        }
        String resolvido = consultarIbge(codigoMunicipio);
        CACHE.put(codigoMunicipio, resolvido == null ? "" : resolvido);
        return Optional.ofNullable(resolvido);
    }

    private static String consultarIbge(String codigo) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(API + codigo))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return null;
            }
            // 1o "nome" = municipio; 1o "sigla" = UF (vem antes da sigla da regiao).
            Matcher mNome = NOME.matcher(resp.body());
            Matcher mSigla = SIGLA.matcher(resp.body());
            if (!mNome.find()) {
                return null;
            }
            String nome = mNome.group(1);
            return mSigla.find() ? nome + " - " + mSigla.group(1) : nome;
        } catch (Exception exception) {
            return null; // offline / timeout / erro: fallback gracioso
        }
    }
}
