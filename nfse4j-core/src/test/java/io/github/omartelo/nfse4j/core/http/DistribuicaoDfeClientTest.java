package io.github.omartelo.nfse4j.core.http;

import io.github.omartelo.nfse4j.core.Ambiente;
import io.github.omartelo.nfse4j.core.NfseContext;
import io.github.omartelo.nfse4j.core.http.LoteDistribuicaoDfe.Documento;
import io.github.omartelo.nfse4j.core.http.LoteDistribuicaoDfe.StatusProcessamento;
import io.github.omartelo.nfse4j.core.xml.XmlPayloadCodec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistribuicaoDfeClientTest {
    private static final String CHAVE = "26125051226063877000115250000000685425085159913279";
    private static final String XML_NFSE = "<NFSe><infNFSe><nNFSe>1</nNFSe></infNFSe></NFSe>";

    private HttpServer server;
    private final List<String> requisicoes = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReadBatchFromNsuWithDecompressedXml() throws Exception {
        startServer(Map.of("/contribuintes/DFe/0?lote=true", resposta(200, documentosLocalizados(
            documento(1, "NFSE", null), documento(7, "EVENTO", "101101")))));

        LoteDistribuicaoDfe lote = client().distribuir(0, null);

        assertEquals(StatusProcessamento.DOCUMENTOS_LOCALIZADOS, lote.statusProcessamento());
        assertEquals(2, lote.loteDfe().size());
        Documento nota = lote.loteDfe().get(0);
        assertEquals(1L, nota.nsu());
        assertEquals("NFSE", nota.tipoDocumento());
        assertEquals(CHAVE, nota.chaveAcesso());
        assertNull(nota.tipoEvento());
        assertEquals("2025-08-30T00:04:36.633", nota.dataHoraGeracao());
        assertEquals(XML_NFSE, nota.xml());
        assertEquals("101101", lote.loteDfe().get(1).tipoEvento());
    }

    @Test
    void shouldSendAlphanumericCnpjConsultaUppercased() throws Exception {
        startServer(Map.of("/contribuintes/DFe/5?lote=true&cnpjConsulta=12ABC34501DE35",
            resposta(200, documentosLocalizados(documento(6, "NFSE", null)))));

        LoteDistribuicaoDfe lote = client().distribuir(5, "12abc34501de35");

        assertEquals(1, lote.loteDfe().size());
    }

    @Test
    void shouldRejectMaskedOrMalformedCnpjConsultaBeforeTheRequest() throws Exception {
        startServer(Map.of());
        DistribuicaoDfeClient client = client();

        assertThrows(IllegalArgumentException.class, () -> client.distribuir(0, "12.ABC.345/01DE-35"));
        assertThrows(IllegalArgumentException.class, () -> client.distribuir(0, "1234567800019"));
        assertThrows(IllegalArgumentException.class, () -> client.distribuir(0, "12345678000AB9"));
        assertTrue(requisicoes.isEmpty());
    }

    @Test
    void shouldRejectNegativeNsu() throws Exception {
        startServer(Map.of());

        assertThrows(IllegalArgumentException.class, () -> client().distribuir(-1, null));
        assertThrows(IllegalArgumentException.class, () -> client().consultarNsu(-1, null));
    }

    @Test
    void shouldReadBusinessErrorsFrom400And404InsteadOfThrowing() throws Exception {
        startServer(Map.of(
            "/contribuintes/DFe/999?lote=true", resposta(400, nenhumDocumento("E2215")),
            "/contribuintes/DFe/42", resposta(404, nenhumDocumento("E2230"))));
        DistribuicaoDfeClient client = client();

        LoteDistribuicaoDfe semDocumentos = client.distribuir(999, null);
        LoteDistribuicaoDfe nsuInexistente = client.consultarNsu(42, null);

        assertEquals(StatusProcessamento.NENHUM_DOCUMENTO_LOCALIZADO, semDocumentos.statusProcessamento());
        assertTrue(semDocumentos.loteDfe().isEmpty());
        assertEquals("E2215", semDocumentos.erros().get(0).codigo());
        assertEquals("E2230", nsuInexistente.erros().get(0).codigo());
    }

    @Test
    void shouldThrowOnOtherStatusCodes() throws Exception {
        startServer(Map.of("/contribuintes/DFe/0?lote=true", resposta(500, "falha interna")));

        DistribuicaoDfeException exception = assertThrows(DistribuicaoDfeException.class,
            () -> client().distribuir(0, null));

        assertTrue(exception.getMessage().contains("500"));
        assertTrue(exception.getMessage().contains("falha interna"));
    }

    @Test
    void shouldThrowOnBusinessStatusWithBlankBody() throws Exception {
        startServer(Map.of("/contribuintes/DFe/0?lote=true", resposta(404, "")));

        assertThrows(DistribuicaoDfeException.class, () -> client().distribuir(0, null));
    }

    @Test
    void shouldConsultSingleNsuWithoutBatchParameter() throws Exception {
        startServer(Map.of("/contribuintes/DFe/7?cnpjConsulta=12345678000199",
            resposta(200, documentosLocalizados(documento(7, "NFSE", null)))));

        LoteDistribuicaoDfe lote = client().consultarNsu(7, "12345678000199");

        assertEquals(7L, lote.loteDfe().get(0).nsu());
    }

    @Test
    void shouldListEventsOfAccessKey() throws Exception {
        startServer(Map.of("/contribuintes/NFSe/" + CHAVE + "/Eventos",
            resposta(200, documentosLocalizados(documento(9, "EVENTO", "101101")))));

        LoteDistribuicaoDfe eventos = client().consultarEventos(CHAVE);

        assertEquals("EVENTO", eventos.loteDfe().get(0).tipoDocumento());
    }

    @Test
    void shouldRejectAccessKeyWithoutFiftyDigits() throws Exception {
        startServer(Map.of());

        assertThrows(IllegalArgumentException.class, () -> client().consultarEventos("123"));
        assertThrows(IllegalArgumentException.class, () -> client().consultarEventos(CHAVE.substring(1) + "A"));
    }

    @Test
    void shouldDrainUntilNoDocumentSkippingRepeatedStartingNsu() throws Exception {
        startServer(Map.of(
            "/contribuintes/DFe/0?lote=true", resposta(200, documentosLocalizados(
                documento(1, "NFSE", null), documento(2, "NFSE", null))),
            "/contribuintes/DFe/2?lote=true", resposta(200, documentosLocalizados(
                documento(2, "NFSE", null), documento(3, "EVENTO", "101101"))),
            "/contribuintes/DFe/3?lote=true", resposta(404, nenhumDocumento("E2215"))));

        DrenagemDfe drenagem = client().drenar(0, null, 10);

        assertTrue(drenagem.concluida());
        assertEquals(3L, drenagem.ultimoNsu());
        assertEquals(List.of(1L, 2L, 3L), drenagem.documentos().stream().map(Documento::nsu).toList());
        assertEquals(3, requisicoes.size());
    }

    @Test
    void shouldFinishDrainWhenBatchOnlyRepeatsStartingNsu() throws Exception {
        startServer(Map.of("/contribuintes/DFe/5?lote=true",
            resposta(200, documentosLocalizados(documento(5, "NFSE", null)))));

        DrenagemDfe drenagem = client().drenar(5, null, 10);

        assertTrue(drenagem.concluida());
        assertEquals(5L, drenagem.ultimoNsu());
        assertTrue(drenagem.documentos().isEmpty());
        assertEquals(1, requisicoes.size());
    }

    @Test
    void shouldStopDrainAtBatchLimitAndReportItIsNotFinished() throws Exception {
        startServer(Map.of(
            "/contribuintes/DFe/0?lote=true", resposta(200, documentosLocalizados(documento(1, "NFSE", null))),
            "/contribuintes/DFe/1?lote=true", resposta(200, documentosLocalizados(documento(2, "NFSE", null)))));

        DrenagemDfe drenagem = client().drenar(0, null, 2);

        assertFalse(drenagem.concluida());
        assertEquals(2L, drenagem.ultimoNsu());
        assertEquals(2, drenagem.documentos().size());
    }

    @Test
    void shouldThrowOnRejectionInsteadOfTreatingItAsEmpty() throws Exception {
        startServer(Map.of("/contribuintes/DFe/0?lote=true", resposta(400, """
            {"StatusProcessamento":"REJEICAO","LoteDFe":null,
             "Erros":[{"Codigo":"E2000","Descricao":"CNPJ de consulta nao autorizado"}]}""")));

        DistribuicaoDfeException exception = assertThrows(DistribuicaoDfeException.class,
            () -> client().drenar(0, null, 10));

        assertTrue(exception.getMessage().contains("E2000"));
    }

    @Test
    void shouldRejectDrainWithoutBatches() throws Exception {
        startServer(Map.of());

        assertThrows(IllegalArgumentException.class, () -> client().drenar(0, null, 0));
    }

    private DistribuicaoDfeClient client() {
        NfseContext context = NfseContext.builder().ambiente(Ambiente.HOMOLOGACAO).build();
        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/contribuintes");
        return new DistribuicaoDfeClient(NfseHttpClient.create(context), baseUri);
    }

    private void startServer(Map<String, Resposta> respostasPorUri) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/contribuintes/", exchange -> {
            try {
                String uri = exchange.getRequestURI().toString();
                requisicoes.add(uri);
                assertEquals("GET", exchange.getRequestMethod());
                Resposta resposta = respostasPorUri.get(uri);
                if (resposta == null) {
                    respond(exchange, 599, "requisicao inesperada: " + uri);
                    return;
                }
                respond(exchange, resposta.status(), resposta.corpo());
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static Resposta resposta(int status, String corpo) {
        return new Resposta(status, corpo);
    }

    private static String documentosLocalizados(String... documentos) {
        return """
            {"StatusProcessamento":"DOCUMENTOS_LOCALIZADOS","LoteDFe":[%s],"Alertas":[],"Erros":[],
             "TipoAmbiente":"HOMOLOGACAO","VersaoAplicativo":"1.0.0.0",
             "DataHoraProcessamento":"2026-08-13T20:37:43-03:00"}""".formatted(String.join(",", documentos));
    }

    private static String documento(long nsu, String tipo, String tipoEvento) {
        return """
            {"NSU":%d,"ChaveAcesso":"%s","TipoDocumento":"%s","TipoEvento":%s,"ArquivoXml":"%s",
             "DataHoraGeracao":"2025-08-30T00:04:36.633"}""".formatted(
            nsu, CHAVE, tipo, tipoEvento == null ? "null" : "\"" + tipoEvento + "\"",
            XmlPayloadCodec.gzipBase64(XML_NFSE));
    }

    private static String nenhumDocumento(String codigoErro) {
        return """
            {"StatusProcessamento":"NENHUM_DOCUMENTO_LOCALIZADO","LoteDFe":null,"Alertas":[],
             "Erros":[{"Codigo":"%s","Descricao":"Nenhum documento localizado","Complemento":null}]}"""
            .formatted(codigoErro);
    }

    private record Resposta(int status, String corpo) {
    }
}
