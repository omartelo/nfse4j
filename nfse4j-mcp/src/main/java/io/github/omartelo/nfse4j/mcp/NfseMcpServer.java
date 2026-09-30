package io.github.omartelo.nfse4j.mcp;

import io.github.omartelo.nfse4j.core.Ambiente;
import io.github.omartelo.nfse4j.core.api.EmitirNfseRequest;
import io.github.omartelo.nfse4j.core.api.NfseRunner;
import io.github.omartelo.nfse4j.core.certificate.CertificadoA1;
import io.github.omartelo.nfse4j.core.xml.dps.Dps;
import io.github.omartelo.nfse4j.core.xml.dps.DpsReemissao;
import io.github.omartelo.nfse4j.core.xml.evento.TipoManifestacao;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servidor MCP (stdio) que expoe a emissao de NFS-e Nacional para agentes de IA.
 *
 * <p>Padrao homologacao; producao exige {@code confirmarProducao=true} (documento fiscal REAL).
 * O certificado A1 vem das envs NFSE_CERT_PATH / NFSE_CERT_PASSWORD ou dos parametros da ferramenta.
 *
 * <p>IMPORTANTE: stdout e o canal do protocolo JSON-RPC; nada deve ser escrito em System.out aqui.
 */
public final class NfseMcpServer {

    // Mapper JSON do proprio SDK (Jackson 3). Usado tambem para converter/serializar nossos dados,
    // evitando trazer Jackson 2 para este modulo (que conflitaria com o Jackson 3 do MCP).
    private static McpJsonMapper MAPPER;

    public static void main(String[] args) throws Exception {
        MAPPER = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        StdioServerTransportProvider transport = new StdioServerTransportProvider(MAPPER);

        McpServer.sync(transport)
            .serverInfo("nfse4j", "0.1.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tools(ferramentas(MAPPER))
            .build();

        new CountDownLatch(1).await(); // mantem o processo vivo enquanto o transporte stdio roda
    }

    private static SyncToolSpecification[] ferramentas(McpJsonMapper jsonMapper) {
        List<SyncToolSpecification> tools = new ArrayList<>();
        tools.add(tool(jsonMapper, "validar_certificado",
            "Valida o certificado A1 e retorna CNPJ/CPF, emissor e validade.",
            """
            {"type":"object","properties":{
              "caminhoCertificado":{"type":"string","description":"Caminho do .pfx/.p12. Se omitido, usa NFSE_CERT_PATH."},
              "senhaCertificado":{"type":"string","description":"Senha do certificado. Se omitida, usa NFSE_CERT_PASSWORD."}
            }}""",
            (ex, req) -> ok(NfseRunner.cert(certificado(req.arguments())))));

        tools.add(tool(jsonMapper, "emitir_nfse",
            "Emite uma NFS-e a partir dos dados informados. Padrao homologacao; producao exige confirmarProducao=true (documento fiscal REAL).",
            """
            {"type":"object","required":["dados"],"properties":{
              "dados":{"type":"object","description":"Payload da nota: codigoMunicipio, numero, valorServico, prestador{cnpj/cpf,opcaoSimplesNacional,regimeApuracaoSimplesNacional,regimeEspecialTributacao,inscricaoMunicipal}, tomador{cnpj/cpf,nome, e opcionalmente codigoMunicipio,cep,logradouro,numero,bairro}, servico{codigoTributacaoNacional,descricao,codigoLocalPrestacao,codigoTributacaoMunicipal,codigoNbs}, tributacao{tributacaoIssqn,tipoRetencaoIssqn, e indicadorTotalTributos OU percentualTotalTributosSimplesNacional}."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "confirmarProducao":{"type":"boolean","default":false,"description":"Obrigatorio true para emitir em producao."},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                EmitirNfseRequest dados = MAPPER.convertValue(exigir(a, "dados"), EmitirNfseRequest.class);
                return ok(NfseRunner.emitir(dados, ambiente(a), certificado(a), confirmar(a)));
            }));

        tools.add(tool(jsonMapper, "emitir_de_exemplo",
            "Reaproveita uma nota existente (XML de DPS ou NFSe) trocando so o que mudar (tomador, descricao, valor, numero). Ideal quando a pessoa aponta uma nota antiga como modelo.",
            """
            {"type":"object","required":["caminhoXmlExemplo"],"properties":{
              "caminhoXmlExemplo":{"type":"string","description":"Caminho de um XML de nota anterior usado como modelo."},
              "numero":{"type":"integer","description":"Novo numero da DPS (recomendado para evitar duplicidade)."},
              "serie":{"type":"string"},
              "descricao":{"type":"string","description":"Nova descricao do servico."},
              "valor":{"type":"number","description":"Novo valor do servico."},
              "competencia":{"type":"string","description":"Data de competencia (YYYY-MM-DD)."},
              "tomador":{"type":"object","description":"Novo tomador: {cpf|cnpj, nome, e opcionalmente codigoMunicipio,cep,logradouro,numero,bairro,complemento}."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "confirmarProducao":{"type":"boolean","default":false},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                String xml = lerArquivo(exigirTexto(a, "caminhoXmlExemplo"));
                DpsReemissao.Overrides overrides = new DpsReemissao.Overrides(
                    longOrNull(a.get("numero"), "numero"),
                    texto(a, "serie"),
                    tomadorOverride(a.get("tomador")),
                    texto(a, "descricao"),
                    decimalOrNull(a.get("valor"), "valor"),
                    dateOrNull(a.get("competencia"), "competencia"),
                    texto(a, "versao"));
                return ok(NfseRunner.emitirDeExemplo(xml, overrides, ambiente(a), certificado(a), confirmar(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_nfse",
            "Consulta uma NFS-e pela chave de acesso.",
            """
            {"type":"object","required":["chaveAcesso"],"properties":{
              "chaveAcesso":{"type":"string"},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultar(exigirTexto(a, "chaveAcesso"), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "distribuir_dfe",
            "Lista as NFS-e e eventos em que o CNPJ do certificado e emitente, tomador ou intermediario (distribuicao de DF-e do ADN), a partir do ultimo NSU ja processado. Devolve so o resumo (nsu, tipo, chave); o XML de um documento sai por consultar_dfe. Se concluida=false, chame de novo com nsu=ultimoNsu.",
            """
            {"type":"object","properties":{
              "nsu":{"type":"integer","default":0,"description":"Ultimo NSU ja processado; 0 comeca do inicio."},
              "cnpjConsulta":{"type":"string","description":"CNPJ (14 caracteres, sem mascara, aceita alfanumerico) de mesma raiz do certificado. Se omitido, usa o do certificado."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                Long nsu = longOrNull(a.get("nsu"), "nsu");
                return ok(NfseRunner.distribuirDfe(nsu == null ? 0 : nsu, textoOu(a, "cnpjConsulta", null), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_dfe",
            "Devolve o XML de um documento da distribuicao de DF-e do ADN pelo NSU (obtido em distribuir_dfe).",
            """
            {"type":"object","required":["nsu"],"properties":{
              "nsu":{"type":"integer","description":"NSU do documento."},
              "cnpjConsulta":{"type":"string","description":"O mesmo cnpjConsulta usado em distribuir_dfe, se houver."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                long nsu = longOrNull(exigir(a, "nsu"), "nsu");
                return ok(NfseRunner.consultarDfe(nsu, textoOu(a, "cnpjConsulta", null), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_eventos_dfe",
            "Lista os eventos (cancelamento, manifestacao...) de uma NFS-e pela chave de acesso, via distribuicao de DF-e do ADN. Devolve so o resumo (nsu, tipo, tipoEvento); o XML de um evento sai por consultar_dfe com o nsu.",
            """
            {"type":"object","required":["chaveAcesso"],"properties":{
              "chaveAcesso":{"type":"string","description":"Chave de acesso da NFS-e (50 digitos)."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultarEventosDfe(exigirTexto(a, "chaveAcesso"), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "cancelar_nfse",
            "Cancela uma NFS-e (evento 101101). Producao exige confirmarProducao=true.",
            """
            {"type":"object","required":["chaveAcesso","codigoMotivo","descricaoMotivo"],"properties":{
              "chaveAcesso":{"type":"string"},
              "codigoMotivo":{"type":"string"},
              "descricaoMotivo":{"type":"string"},
              "numeroPedido":{"type":"integer","default":1},
              "autorCpfCnpj":{"type":"string","description":"Se omitido, usa o CNPJ/CPF do certificado."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "confirmarProducao":{"type":"boolean","default":false},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.cancelar(
                    exigirTexto(a, "chaveAcesso"), texto(a, "autorCpfCnpj"), intOr(a.get("numeroPedido"), 1, "numeroPedido"),
                    exigirTexto(a, "codigoMotivo"), exigirTexto(a, "descricaoMotivo"),
                    ambiente(a), certificado(a), confirmar(a)));
            }));

        tools.add(tool(jsonMapper, "solicitar_analise_fiscal_cancelamento",
            "Pede ao municipio a analise fiscal para cancelar uma NFS-e (evento 101103), para quando o cancelamento direto nao e mais aceito. Producao exige confirmarProducao=true.",
            """
            {"type":"object","required":["chaveAcesso","codigoMotivo","descricaoMotivo"],"properties":{
              "chaveAcesso":{"type":"string"},
              "codigoMotivo":{"type":"string","enum":["1","2","9"],"description":"1 erro na emissao, 2 servico nao prestado, 9 outros."},
              "descricaoMotivo":{"type":"string","description":"15 a 255 caracteres."},
              "autorCpfCnpj":{"type":"string","description":"Se omitido, usa o CNPJ/CPF do certificado."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "confirmarProducao":{"type":"boolean","default":false},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.solicitarAnaliseFiscalCancelamento(
                    exigirTexto(a, "chaveAcesso"), texto(a, "autorCpfCnpj"),
                    exigirTexto(a, "codigoMotivo"), exigirTexto(a, "descricaoMotivo"),
                    ambiente(a), certificado(a), confirmar(a)));
            }));

        tools.add(tool(jsonMapper, "manifestar_nfse",
            "Registra manifestacao sobre uma NFS-e: confirmacao ou rejeicao pelo prestador, tomador ou intermediario (eventos 202201, 203202, 204203, 202205, 203206, 204207). Producao exige confirmarProducao=true.",
            """
            {"type":"object","required":["chaveAcesso","tipo"],"properties":{
              "chaveAcesso":{"type":"string"},
              "tipo":{"type":"string","enum":["CONFIRMACAO_PRESTADOR","CONFIRMACAO_TOMADOR","CONFIRMACAO_INTERMEDIARIO","REJEICAO_PRESTADOR","REJEICAO_TOMADOR","REJEICAO_INTERMEDIARIO"]},
              "codigoMotivo":{"type":"string","enum":["1","2","3","4","5","9"],"description":"Obrigatorio na rejeicao, proibido na confirmacao. 1 duplicidade, 2 ja emitida pelo tomador, 3 fato gerador nao ocorreu, 4 erro de responsabilidade tributaria, 5 erro de valor/servico/data, 9 outros."},
              "descricaoMotivo":{"type":"string","description":"Opcional na rejeicao (15 a 255 caracteres)."},
              "autorCpfCnpj":{"type":"string","description":"Se omitido, usa o CNPJ/CPF do certificado."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "confirmarProducao":{"type":"boolean","default":false},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.manifestar(
                    exigirTexto(a, "chaveAcesso"), texto(a, "autorCpfCnpj"),
                    TipoManifestacao.porNome(exigirTexto(a, "tipo")),
                    texto(a, "codigoMotivo"), texto(a, "descricaoMotivo"),
                    ambiente(a), certificado(a), confirmar(a)));
            }));

        tools.add(tool(jsonMapper, "gerar_danfse",
            "Gera o PDF do DANFSe LOCALMENTE a partir do XML autorizado da NFS-e (campo nfseXmlGZipB64 da emissao, ou o XML <NFSe>).",
            """
            {"type":"object","properties":{
              "xmlNfse":{"type":"string","description":"XML autorizado da NFS-e (<NFSe>...). Use este OU caminhoXmlNfse OU nfseXmlGZipB64."},
              "caminhoXmlNfse":{"type":"string","description":"Caminho de um arquivo com o XML da NFS-e."},
              "nfseXmlGZipB64":{"type":"string","description":"Conteudo do campo nfseXmlGZipB64 retornado na emissao (gzip+base64)."},
              "caminhoSaida":{"type":"string","description":"Arquivo PDF de saida. Padrao: danfse-<chave>.pdf."},
              "logoEmitenteArquivo":{"type":"string","description":"Caminho de uma imagem (PNG/JPG) com o logo do prestador, exibido no cabecalho. Tamanho sugerido ~300x120 px."},
              "producao":{"type":"boolean","default":false,"description":"Define a URL do QR (consulta publica). Nao emite nada."}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                String xml = xmlNfse(a);
                boolean producao = Boolean.TRUE.equals(a.get("producao"))
                    || "true".equalsIgnoreCase(String.valueOf(a.get("producao")));
                Path saida = Path.of(textoOu(a, "caminhoSaida", "danfse.pdf"));
                var config = io.github.omartelo.nfse4j.danfse.DanfseConfig.vazio();
                String logo = texto(a, "logoEmitenteArquivo");
                if (logo != null && !logo.isBlank()) {
                    config = io.github.omartelo.nfse4j.danfse.DanfseConfig.comLogoEmitente(
                        io.github.omartelo.nfse4j.danfse.DanfseGenerator.dataUriImagem(Path.of(logo)));
                }
                byte[] pdf = io.github.omartelo.nfse4j.danfse.DanfseGenerator.gerarPdf(xml, producao, config, saida);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("sucesso", true);
                r.put("caminho", saida.toAbsolutePath().normalize().toString());
                r.put("bytes", pdf.length);
                return ok(r);
            }));

        tools.add(tool(jsonMapper, "consultar_aliquota",
            "Consulta no ADN a aliquota de ISS de um servico em um municipio. Use antes de emitir para conferir se ha incidencia e qual aliquota aplicar. Somente leitura.",
            """
            {"type":"object","required":["codigoMunicipio","codigoServico"],"properties":{
              "codigoMunicipio":{"type":"string","description":"Codigo IBGE do municipio (7 digitos)."},
              "codigoServico":{"type":"string","description":"Codigo de tributacao nacional: 6 ou 9 digitos, com ou sem pontos (010101 ou 01.01.01.000)."},
              "competencia":{"type":"string","description":"Data de competencia (YYYY-MM-DD). Padrao: hoje."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultarAliquota(
                    exigirTexto(a, "codigoMunicipio"), exigirTexto(a, "codigoServico"),
                    dateOrNull(a.get("competencia"), "competencia"), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_historico_aliquotas",
            "Lista no ADN todas as aliquotas de ISS que um servico ja teve em um municipio, com as vigencias. Somente leitura.",
            """
            {"type":"object","required":["codigoMunicipio","codigoServico"],"properties":{
              "codigoMunicipio":{"type":"string","description":"Codigo IBGE do municipio (7 digitos)."},
              "codigoServico":{"type":"string","description":"Codigo de tributacao nacional: 6 ou 9 digitos, com ou sem pontos."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultarHistoricoAliquotas(
                    exigirTexto(a, "codigoMunicipio"), exigirTexto(a, "codigoServico"), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_convenio_municipio",
            "Consulta no ADN se o municipio aderiu ao ambiente nacional e ao emissor nacional da NFS-e. Somente leitura.",
            """
            {"type":"object","required":["codigoMunicipio"],"properties":{
              "codigoMunicipio":{"type":"string","description":"Codigo IBGE do municipio (7 digitos)."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultarConvenio(exigirTexto(a, "codigoMunicipio"), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_beneficio_municipal",
            "Consulta no ADN um beneficio fiscal municipal (isencao, reducao de base, aliquota diferenciada) pelo numero. Somente leitura.",
            """
            {"type":"object","required":["codigoMunicipio","numeroBeneficio"],"properties":{
              "codigoMunicipio":{"type":"string","description":"Codigo IBGE do municipio (7 digitos)."},
              "numeroBeneficio":{"type":"string","description":"Numero do beneficio fiscal cadastrado pelo municipio."},
              "competencia":{"type":"string","description":"Data de competencia (YYYY-MM-DD). Padrao: hoje."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultarBeneficio(
                    exigirTexto(a, "codigoMunicipio"), exigirTexto(a, "numeroBeneficio"),
                    dateOrNull(a.get("competencia"), "competencia"), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_regimes_especiais",
            "Consulta no ADN os regimes especiais de tributacao aceitos pelo municipio para um servico. Somente leitura.",
            """
            {"type":"object","required":["codigoMunicipio","codigoServico"],"properties":{
              "codigoMunicipio":{"type":"string","description":"Codigo IBGE do municipio (7 digitos)."},
              "codigoServico":{"type":"string","description":"Codigo de tributacao nacional: 6 ou 9 digitos, com ou sem pontos."},
              "competencia":{"type":"string","description":"Data de competencia (YYYY-MM-DD). Padrao: hoje."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultarRegimesEspeciais(
                    exigirTexto(a, "codigoMunicipio"), exigirTexto(a, "codigoServico"),
                    dateOrNull(a.get("competencia"), "competencia"), ambiente(a), certificado(a)));
            }));

        tools.add(tool(jsonMapper, "consultar_retencoes_municipio",
            "Consulta no ADN as regras de retencao de ISS do municipio (art. 6o da LC 116 e retencoes municipais por servico e responsavel). Somente leitura.",
            """
            {"type":"object","required":["codigoMunicipio"],"properties":{
              "codigoMunicipio":{"type":"string","description":"Codigo IBGE do municipio (7 digitos)."},
              "competencia":{"type":"string","description":"Data de competencia (YYYY-MM-DD). Padrao: hoje."},
              "ambiente":{"type":"string","enum":["homologacao","producao"],"default":"homologacao"},
              "caminhoCertificado":{"type":"string"},"senhaCertificado":{"type":"string"}
            }}""",
            (ex, req) -> {
                Map<String, Object> a = req.arguments();
                return ok(NfseRunner.consultarRetencoes(
                    exigirTexto(a, "codigoMunicipio"), dateOrNull(a.get("competencia"), "competencia"),
                    ambiente(a), certificado(a)));
            }));

        return tools.toArray(new SyncToolSpecification[0]);
    }

    // ---- montagem de ferramenta ----

    private static SyncToolSpecification tool(McpJsonMapper jsonMapper, String nome, String descricao,
            String schema, BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange,
                McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name(nome)
            .description(descricao)
            .inputSchema(jsonMapper, schema)
            .build();
        return new SyncToolSpecification(tool, (exchange, request) -> {
            try {
                return handler.apply(exchange, request);
            } catch (Exception exception) {
                return erro(exception);
            }
        });
    }

    private static McpSchema.CallToolResult ok(Object resultado) {
        return McpSchema.CallToolResult.builder()
            .addTextContent(toJson(resultado))
            .isError(false)
            .build();
    }

    private static McpSchema.CallToolResult erro(Exception exception) {
        String mensagem = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("sucesso", false);
        corpo.put("erro", mensagem);
        return McpSchema.CallToolResult.builder()
            .addTextContent(toJson(corpo))
            .isError(true)
            .build();
    }

    // ---- certificado / ambiente / args ----

    private static CertificadoA1 certificado(Map<String, Object> args) {
        String caminho = textoOu(args, "caminhoCertificado", System.getenv("NFSE_CERT_PATH"));
        String senha = textoOu(args, "senhaCertificado", System.getenv("NFSE_CERT_PASSWORD"));
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalStateException("Informe caminhoCertificado ou configure NFSE_CERT_PATH.");
        }
        if (senha == null) {
            throw new IllegalStateException("Informe senhaCertificado ou configure NFSE_CERT_PASSWORD.");
        }
        return NfseRunner.carregarCertificado(caminho, senha);
    }

    private static Ambiente ambiente(Map<String, Object> args) {
        return NfseRunner.ambiente(texto(args, "ambiente"));
    }

    private static boolean confirmar(Map<String, Object> args) {
        Object value = args.get("confirmarProducao");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Dps.Tomador tomadorOverride(Object tomador) {
        if (!(tomador instanceof Map<?, ?> map)) {
            return null;
        }
        Dps.Endereco endereco = null;
        if (map.get("codigoMunicipio") != null || map.get("cep") != null || map.get("logradouro") != null) {
            endereco = new Dps.Endereco(
                str(map.get("codigoMunicipio")), str(map.get("cep")), str(map.get("logradouro")),
                str(map.get("numero")), str(map.get("complemento")), str(map.get("bairro")));
        }
        return new Dps.Tomador(
            str(map.get("cnpj")), str(map.get("cpf")), str(map.get("nome")),
            endereco, str(map.get("telefone")), str(map.get("email")));
    }

    // Resolve o XML da NFS-e a partir de: xmlNfse (direto), caminhoXmlNfse (arquivo) ou
    // nfseXmlGZipB64 (campo retornado na emissao). Exatamente uma fonte deve ser informada.
    private static String xmlNfse(Map<String, Object> args) {
        String direto = texto(args, "xmlNfse");
        if (direto != null && !direto.isBlank()) {
            return direto;
        }
        String caminho = texto(args, "caminhoXmlNfse");
        if (caminho != null && !caminho.isBlank()) {
            return lerArquivo(caminho);
        }
        String gzip = texto(args, "nfseXmlGZipB64");
        if (gzip != null && !gzip.isBlank()) {
            return io.github.omartelo.nfse4j.core.xml.XmlPayloadCodec.ungzipBase64(gzip);
        }
        throw new IllegalArgumentException("Informe xmlNfse, caminhoXmlNfse ou nfseXmlGZipB64.");
    }

    private static String lerArquivo(String caminho) {
        try {
            return Files.readString(Path.of(caminho));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Nao foi possivel ler o XML de exemplo: " + caminho, exception);
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"erro\":\"falha ao serializar resultado\"}";
        }
    }

    private static Object exigir(Map<String, Object> args, String chave) {
        Object value = args.get(chave);
        if (value == null) {
            throw new IllegalArgumentException("Parametro obrigatorio ausente: " + chave);
        }
        return value;
    }

    private static String exigirTexto(Map<String, Object> args, String chave) {
        String value = texto(args, chave);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Parametro obrigatorio ausente: " + chave);
        }
        return value;
    }

    private static String texto(Map<String, Object> args, String chave) {
        return str(args.get(chave));
    }

    private static String textoOu(Map<String, Object> args, String chave, String fallback) {
        String value = texto(args, chave);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long longOrNull(Object value, String campo) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(campo + " deve ser um inteiro (recebido: " + value + ").");
        }
    }

    private static int intOr(Object value, int fallback, String campo) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(campo + " deve ser um inteiro (recebido: " + value + ").");
        }
    }

    private static BigDecimal decimalOrNull(Object value, String campo) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(campo + " deve ser um numero (recebido: " + value + ").");
        }
    }

    private static LocalDate dateOrNull(Object value, String campo) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString().trim());
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(campo + " deve ser uma data YYYY-MM-DD (recebido: " + value + ").");
        }
    }

    private NfseMcpServer() {
    }
}
