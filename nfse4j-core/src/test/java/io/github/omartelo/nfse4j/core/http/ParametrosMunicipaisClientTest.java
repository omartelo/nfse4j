package io.github.omartelo.nfse4j.core.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.Aliquota;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.AliquotasResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.BeneficioResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.ConvenioResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.RegimesEspeciaisResposta;
import io.github.omartelo.nfse4j.core.http.ParametrosMunicipais.RetencoesResposta;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParametrosMunicipaisClientTest {
    private static final LocalDate COMPETENCIA = LocalDate.of(2026, 1, 5);

    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldConsultAliquotaWithFormattedServiceCodeAndCompetencia() throws Exception {
        startServer(200, """
            {"aliquotas":{"01.01.01.000":[
              {"Incidencia":"Sim","Aliq":"2,50","DtIni":"2025-01-01T00:00:00","DtFim":null}
            ]},"mensagem":null}""");

        AliquotasResposta resposta = client().consultarAliquota("3550308", "010101", COMPETENCIA);

        assertEquals("/parametrizacao/3550308/01.01.01.000/01-05-2026/aliquota", requestedPaths.get(0));
        Aliquota aliquota = resposta.aliquotas().get("01.01.01.000").get(0);
        assertEquals(Boolean.TRUE, aliquota.incidencia());
        assertEquals(new BigDecimal("2.50"), aliquota.aliquota());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), aliquota.inicioVigencia());
        assertNull(aliquota.fimVigencia());
    }

    @Test
    void shouldAcceptServiceCodeAlreadyFormattedOrWithNineDigits() throws Exception {
        startServer(200, "{\"aliquotas\":{}}");

        client().consultarAliquota("3550308", "01.01.01.000", COMPETENCIA);
        client().consultarAliquota("3550308", "010101001", COMPETENCIA);

        assertEquals("/parametrizacao/3550308/01.01.01.000/01-05-2026/aliquota", requestedPaths.get(0));
        assertEquals("/parametrizacao/3550308/01.01.01.001/01-05-2026/aliquota", requestedPaths.get(1));
    }

    @Test
    void shouldReadNumericAliquotaAndOffsetDates() throws Exception {
        startServer(200, """
            {"aliquotas":{"01.01.01.000":[
              {"Incidencia":1,"Aliq":5,"DtIni":"2025-01-01T00:00:00-03:00"}
            ]}}""");

        Aliquota aliquota = client().consultarAliquota("3550308", "010101", COMPETENCIA)
            .aliquotas().get("01.01.01.000").get(0);

        assertEquals(new BigDecimal("5"), aliquota.aliquota());
        assertEquals(Boolean.TRUE, aliquota.incidencia());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), aliquota.inicioVigencia());
    }

    @Test
    void shouldRejectInvalidCodesBeforeCallingAdn() throws Exception {
        startServer(200, "{}");
        ParametrosMunicipaisClient client = client();

        assertThrows(IllegalArgumentException.class, () -> client.consultarAliquota("355030", "010101", COMPETENCIA));
        assertThrows(IllegalArgumentException.class, () -> client.consultarAliquota("3550308", "0101", COMPETENCIA));
        assertTrue(requestedPaths.isEmpty());
    }

    @Test
    void shouldReturnAdnMessageWhenParameterIsNotFound() throws Exception {
        startServer(404, "{\"mensagem\":\"Aliquota nao parametrizada para o servico.\"}");

        AliquotasResposta resposta = client().consultarAliquota("3550308", "010101", COMPETENCIA);

        assertNull(resposta.aliquotas());
        assertEquals("Aliquota nao parametrizada para o servico.", resposta.mensagem());
    }

    @Test
    void shouldFailWithStatusAndAdnMessageOnOtherErrors() throws Exception {
        startServer(400, "{\"mensagem\":\"Competencia invalida.\"}");

        ParametrosMunicipaisException exception = assertThrows(ParametrosMunicipaisException.class,
            () -> client().consultarConvenio("3550308"));

        assertEquals(400, exception.statusHttp());
        assertTrue(exception.getMessage().contains("Competencia invalida."), exception.getMessage());
    }

    @Test
    void shouldFailWithRawBodyWhenErrorIsNotJson() throws Exception {
        startServer(502, "<html>Bad Gateway</html>");

        ParametrosMunicipaisException exception = assertThrows(ParametrosMunicipaisException.class,
            () -> client().consultarConvenio("3550308"));

        assertEquals(502, exception.statusHttp());
        assertTrue(exception.getMessage().contains("Bad Gateway"), exception.getMessage());
    }

    @Test
    void shouldFailOnUnexpectedBooleanValue() throws Exception {
        startServer(200, "{\"parametrosConvenio\":{\"aderenteAmbienteNacional\":\"talvez\"}}");

        assertThrows(ParametrosMunicipaisException.class, () -> client().consultarConvenio("3550308"));
    }

    @Test
    void shouldConsultConvenio() throws Exception {
        startServer(200, """
            {"parametrosConvenio":{"aderenteAmbienteNacional":"1","aderenteEmissorNacional":"Nao",
              "situacaoEmissaoPadraoContribuintesRFB":2,"aderenteMAN":true,
              "permiteAproveitametoDeCreditos":"s"}}""");

        ConvenioResposta resposta = client().consultarConvenio("3550308");

        assertEquals("/parametrizacao/3550308/convenio", requestedPaths.get(0));
        assertEquals(Boolean.TRUE, resposta.parametrosConvenio().aderenteAmbienteNacional());
        assertEquals(Boolean.FALSE, resposta.parametrosConvenio().aderenteEmissorNacional());
        assertEquals(2, resposta.parametrosConvenio().situacaoEmissaoPadraoContribuintesRfb());
        assertEquals(Boolean.TRUE, resposta.parametrosConvenio().permiteAproveitamentoDeCreditos());
    }

    @Test
    void shouldConsultHistoricoAliquotas() throws Exception {
        startServer(200, """
            {"aliquotas":{"01.01.01.000":[
              {"Incidencia":"Sim","Aliq":"2,00","DtIni":"2024-01-01T00:00:00","DtFim":"2024-12-31T00:00:00"},
              {"Incidencia":"Sim","Aliq":"3,00","DtIni":"2025-01-01T00:00:00"}
            ]}}""");

        AliquotasResposta resposta = client().consultarHistoricoAliquotas("3550308", "010101");

        assertEquals("/parametrizacao/3550308/01.01.01.000/historicoaliquotas", requestedPaths.get(0));
        assertEquals(2, resposta.aliquotas().get("01.01.01.000").size());
        assertEquals(new BigDecimal("3.00"), resposta.aliquotas().get("01.01.01.000").get(1).aliquota());
    }

    @Test
    void shouldConsultBeneficioWithEncodedNumber() throws Exception {
        startServer(200, """
            {"dataHoraProcessamento":"2026-01-05T10:00:00","tipoAmbiente":2,
             "beneficio":{"numBenef":"B 1","desc":"Isencao","tpoBenef":1,"redPerclBC":"10,5",
               "aliqDiferenc":2.5,"restAoMun":"n",
               "serv":[{"codigo":"01.01.01.000","dtIni":"2025-01-01T00:00:00"}],
               "contrib":[{"tpoInsc":1,"insc":"12345678000199"}]}}""");

        BeneficioResposta resposta = client().consultarBeneficio("3550308", "B 1", COMPETENCIA);

        assertEquals("/parametrizacao/3550308/B%201/01-05-2026/beneficio", requestedPaths.get(0));
        assertEquals("Isencao", resposta.beneficio().descricao());
        assertEquals(new BigDecimal("10.5"), resposta.beneficio().percentualReducaoBaseCalculo());
        assertEquals(new BigDecimal("2.5"), resposta.beneficio().aliquotaDiferenciada());
        assertEquals(Boolean.FALSE, resposta.beneficio().restritoAoMunicipio());
        assertEquals("01.01.01.000", resposta.beneficio().servicos().get(0).codigo());
        assertEquals("12345678000199", resposta.beneficio().contribuintes().get(0).inscricao());
    }

    @Test
    void shouldConsultRegimesEspeciais() throws Exception {
        startServer(200, """
            {"regimesEspeciais":{"01.01.01.000":{"1":[
              {"sit":1,"dtIni":"2025-01-01T00:00:00","motivo":"Sociedade de profissionais"}
            ]}}}""");

        RegimesEspeciaisResposta resposta = client().consultarRegimesEspeciais("3550308", "010101", COMPETENCIA);

        assertEquals("/parametrizacao/3550308/01.01.01.000/01-05-2026/regimes_especiais", requestedPaths.get(0));
        assertEquals("Sociedade de profissionais",
            resposta.regimesEspeciais().get("01.01.01.000").get("1").get(0).motivo());
    }

    @Test
    void shouldConsultRetencoes() throws Exception {
        startServer(200, """
            {"retencoes":{
              "art6":{"habilitado":true,"hist":[{"dtIni":"2025-01-01T00:00:00"}]},
              "retMun":[{"desc":"Construcao civil","tpRet":[1,2],
                "serv":[{"codigo":"07.02.01.000","hist":[{"dtIni":"2025-01-01T00:00:00"}]}],
                "respTrib":[{"tpInsc":1,"insc":"12345678000199","hist":[]}]}]}}""");

        RetencoesResposta resposta = client().consultarRetencoes("3550308", COMPETENCIA);

        assertEquals("/parametrizacao/3550308/01-05-2026/retencoes", requestedPaths.get(0));
        assertEquals(Boolean.TRUE, resposta.retencoes().artigoSexto().habilitado());
        assertEquals(List.of(1, 2), resposta.retencoes().retencoesMunicipais().get(0).tiposRetencao());
        assertEquals("07.02.01.000", resposta.retencoes().retencoesMunicipais().get(0).servicos().get(0).codigo());
    }

    private ParametrosMunicipaisClient client() {
        NfseHttpClient httpClient = NfseHttpClient.withHttpClient(HttpClient.newHttpClient(), Duration.ofSeconds(5));
        return new ParametrosMunicipaisClient(
            httpClient,
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/parametrizacao"));
    }

    private void startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/parametrizacao/", exchange -> {
            try {
                assertEquals("GET", exchange.getRequestMethod());
                requestedPaths.add(exchange.getRequestURI().getRawPath());
                respond(exchange, statusCode, body);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
