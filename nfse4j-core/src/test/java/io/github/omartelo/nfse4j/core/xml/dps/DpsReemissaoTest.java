package io.github.omartelo.nfse4j.core.xml.dps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omartelo.nfse4j.core.xml.XmlSchemaValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DpsReemissaoTest {

    private static Dps exemplo() {
        Dps.InfDps inf = new Dps.InfDps(
            DpsIdGenerator.generate("12345678000199", "3550308", "00900", 10),
            1,
            OffsetDateTime.parse("2026-01-15T10:00:00-03:00"),
            "EmissorWeb",
            "00900",
            10,
            LocalDate.parse("2026-01-15"),
            1,
            "3550308",
            new Dps.Prestador("12345678000199", null, "IM123", null, null,
                new Dps.RegimeTributario(2, null, 0)),
            new Dps.Tomador(null, "11122233344", "Tomador Antigo", null, null, null),
            new Dps.Servico("3550308", "010101", "001", "Servico antigo", "123"),
            new Dps.Valores(new BigDecimal("100.00"), new Dps.Tributacao(1, 1, 0, null)));
        return new Dps("1.00", inf);
    }

    @Test
    void apenasNumeroPreservaRestoERegeneraId() {
        Dps re = DpsReemissao.reemitir(exemplo(), new DpsReemissao.Overrides(
            42L, null, null, null, null, null, null));

        assertEquals(42, re.infDps().numero());
        assertEquals(DpsIdGenerator.generate("12345678000199", "3550308", "00900", 42), re.infDps().id());
        // preserva o que nao foi sobrescrito
        assertEquals("Tomador Antigo", re.infDps().tomador().nome());
        assertEquals("Servico antigo", re.infDps().servico().descricao());
        assertEquals(0, re.infDps().valores().valorServico().compareTo(new BigDecimal("100.00")));
        assertEquals("12345678000199", re.infDps().prestador().cnpj());
        // verAplic e marcado como este kit
        assertEquals("nfse4j", re.infDps().versaoAplicativo());
    }

    @Test
    void sobrescreveTomadorDescricaoValor() {
        Dps re = DpsReemissao.reemitir(exemplo(), new DpsReemissao.Overrides(
            11L, null,
            new Dps.Tomador(null, "55566677788", "Novo Tomador", null, null, null),
            "Novo servico", new BigDecimal("250.00"), null, null));

        assertEquals("55566677788", re.infDps().tomador().cpf());
        assertEquals("Novo Tomador", re.infDps().tomador().nome());
        assertNull(re.infDps().tomador().endereco());
        assertEquals("Novo servico", re.infDps().servico().descricao());
        assertEquals(0, re.infDps().valores().valorServico().compareTo(new BigDecimal("250.00")));
        // tributacao do exemplo preservada
        assertEquals(0, re.infDps().valores().tributacao().indicadorTotalTributos());
    }

    @Test
    void overrideDeSerieEntraNoId() {
        Dps re = DpsReemissao.reemitir(exemplo(), new DpsReemissao.Overrides(
            7L, "00955", null, null, null, null, null));

        assertEquals("00955", re.infDps().serie());
        assertEquals(DpsIdGenerator.generate("12345678000199", "3550308", "00955", 7), re.infDps().id());
        assertNotEquals(exemplo().infDps().id(), re.infDps().id());
    }

    @Test
    void exemploSemPrestadorFalha() {
        Dps.InfDps inf = new Dps.InfDps(
            "DPS1", 1, OffsetDateTime.now(), "x", "1", 1, LocalDate.now(), 1, "3550308",
            null, // prestador ausente
            new Dps.Tomador(null, "1", "x", null, null, null),
            new Dps.Servico("3550308", "010101", "x"),
            new Dps.Valores(new BigDecimal("1.00"), new Dps.Tributacao(1, 1, 0)));
        Dps semPrestador = new Dps("1.00", inf);

        assertThrows(DpsXmlException.class, () -> DpsReemissao.reemitir(
            semPrestador, new DpsReemissao.Overrides(1L, null, null, null, null, null, null)));
    }

    private static String exemploComTotTrib(String conteudoTotTrib) {
        return """
            <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.01"><infNFSe Id="NFS1">
              <DPS versao="1.01"><infDPS Id="DPS1">
                <tpAmb>2</tpAmb><dhEmi>2026-01-15T10:00:00-03:00</dhEmi><verAplic>EmissorWeb</verAplic>
                <serie>00900</serie><nDPS>10</nDPS><dCompet>2026-01-15</dCompet><tpEmit>1</tpEmit>
                <cLocEmi>3550308</cLocEmi>
                <prest><CNPJ>12345678000199</CNPJ><regTrib><opSimpNac>1</opSimpNac><regEspTrib>0</regEspTrib></regTrib></prest>
                <toma><CPF>11122233344</CPF><xNome>Tomador</xNome></toma>
                <serv><locPrest><cLocPrestacao>3550308</cLocPrestacao></locPrest>
                  <cServ><cTribNac>010101</cTribNac><xDescServ>Servico</xDescServ></cServ></serv>
                <valores><vServPrest><vServ>250.00</vServ></vServPrest>
                  <trib><tribMun><tribISSQN>1</tribISSQN><tpRetISSQN>1</tpRetISSQN></tribMun>
                    <totTrib>%s</totTrib></trib></valores>
              </infDPS></DPS>
            </infNFSe></NFSe>
            """.formatted(conteudoTotTrib);
    }

    private static String reemitirXml(String exemploXml) {
        Dps re = DpsReemissao.reemitir(DpsXmlReader.read(exemploXml), new DpsReemissao.Overrides(
            11L, null, null, null, null, null, null));
        return new DpsXmlBuilder().build(re);
    }

    @Test
    void reemissaoPreservaValorTotalDosTributos() {
        String xml = reemitirXml(exemploComTotTrib(
            "<vTotTrib><vTotTribFed>33.13</vTotTribFed><vTotTribEst>0.00</vTotTribEst>"
                + "<vTotTribMun>4.80</vTotTribMun></vTotTrib>"));

        assertTrue(xml.contains("<totTrib><vTotTrib><vTotTribFed>33.13</vTotTribFed>"
            + "<vTotTribEst>0.00</vTotTribEst><vTotTribMun>4.80</vTotTribMun></vTotTrib></totTrib>"), xml);
    }

    @Test
    void reemissaoPreservaPercentualTotalDosTributos() {
        String xml = reemitirXml(exemploComTotTrib(
            "<pTotTrib><pTotTribFed>13.25</pTotTribFed><pTotTribEst>0.00</pTotTribEst>"
                + "<pTotTribMun>1.92</pTotTribMun></pTotTrib>"));

        assertTrue(xml.contains("<totTrib><pTotTrib><pTotTribFed>13.25</pTotTribFed>"
            + "<pTotTribEst>0.00</pTotTribEst><pTotTribMun>1.92</pTotTribMun></pTotTrib></totTrib>"), xml);
    }

    private static final String TRIBUTACAO_MINIMA = "<trib><tribMun><tribISSQN>1</tribISSQN><tpRetISSQN>1</tpRetISSQN></tribMun>"
        + "<totTrib><indTotTrib>0</indTotTrib></totTrib></trib>";

    private static String exemploComValores(String conteudoValores) {
        return """
            <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse" versao="1.01"><infNFSe Id="NFS1">
              <DPS versao="1.01"><infDPS Id="DPS1">
                <tpAmb>2</tpAmb><dhEmi>2026-01-15T10:00:00-03:00</dhEmi><verAplic>EmissorWeb</verAplic>
                <serie>00900</serie><nDPS>10</nDPS><dCompet>2026-01-15</dCompet><tpEmit>1</tpEmit>
                <cLocEmi>3550308</cLocEmi>
                <prest><CNPJ>12345678000199</CNPJ><regTrib><opSimpNac>1</opSimpNac><regEspTrib>0</regEspTrib></regTrib></prest>
                <toma><CPF>11122233344</CPF><xNome>Tomador</xNome></toma>
                <serv><locPrest><cLocPrestacao>3550308</cLocPrestacao></locPrest>
                  <cServ><cTribNac>010101</cTribNac><xDescServ>Servico</xDescServ></cServ></serv>
                <valores>%s</valores>
              </infDPS></DPS>
            </infNFSe></NFSe>
            """.formatted(conteudoValores);
    }

    private static String exemploMinimo() {
        return exemploComValores("<vServPrest><vServ>250.00</vServ></vServPrest>" + TRIBUTACAO_MINIMA);
    }

    static Stream<Arguments> gruposNaoSuportados() {
        return Stream.of(
            Arguments.of("cMotivoEmisTI", "<cLocEmi>", "<cMotivoEmisTI>4</cMotivoEmisTI><cLocEmi>"),
            Arguments.of("chNFSeRej", "<cLocEmi>", "<chNFSeRej>" + "1".repeat(50) + "</chNFSeRej><cLocEmi>"),
            Arguments.of("subst", "<prest>",
                "<subst><chSubstda>" + "1".repeat(50) + "</chSubstda><cMotivo>01</cMotivo></subst><prest>"),
            Arguments.of("interm", "<serv>", "<interm><CPF>99988877766</CPF><xNome>Intermediario</xNome></interm><serv>"),
            Arguments.of("IBSCBS", "</infDPS>", "<IBSCBS><finNFSe>0</finNFSe><indFinal>0</indFinal></IBSCBS></infDPS>"),
            Arguments.of("cPaisPrestacao", "<cLocPrestacao>3550308</cLocPrestacao>", "<cPaisPrestacao>US</cPaisPrestacao>"),
            Arguments.of("cIntContrib", "</xDescServ>", "</xDescServ><cIntContrib>SERV-01</cIntContrib>"),
            Arguments.of("comExt", "</cServ>", "</cServ><comExt><mdPrestacao>1</mdPrestacao><tpMoeda>220</tpMoeda></comExt>"),
            Arguments.of("obra", "</cServ>", "</cServ><obra><cObra>123456</cObra></obra>"),
            Arguments.of("atvEvento", "</cServ>",
                "</cServ><atvEvento><xNome>Evento</xNome><dtIni>2026-01-10</dtIni><dtFim>2026-01-11</dtFim></atvEvento>"),
            Arguments.of("infoCompl/idDocTec", "</cServ>", "</cServ><infoCompl><idDocTec>ART-1</idDocTec></infoCompl>"),
            Arguments.of("infoCompl/docRef", "</cServ>", "</cServ><infoCompl><docRef>CONTRATO-1</docRef></infoCompl>"),
            Arguments.of("infoCompl/xPed", "</cServ>",
                "</cServ><infoCompl><xPed>PED-1</xPed><xInfComp>Observacao</xInfComp></infoCompl>"),
            Arguments.of("infoCompl/gItemPed", "</cServ>",
                "</cServ><infoCompl><gItemPed><xItemPed>7</xItemPed></gItemPed></infoCompl>")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gruposNaoSuportados")
    void exemploComGrupoNaoSuportadoFalhaNaLeituraNomeandoOGrupo(String grupo, String ancora, String substituto) {
        String xml = exemploMinimo().replace(ancora, substituto);

        DpsXmlException erro = assertThrows(DpsXmlException.class, () -> DpsXmlReader.read(xml));

        assertTrue(erro.getMessage().contains(grupo), erro.getMessage());
    }

    @Test
    void exemploSemGrupoNaoSuportadoContinuaSendoLido() {
        assertEquals("Servico", DpsXmlReader.read(exemploMinimo()).infDps().servico().descricao());
    }

    private static final String VALOR_SERVICO = "<vServPrest><vServ>250.00</vServ></vServPrest>";
    private static final String ISSQN = "<tribISSQN>1</tribISSQN>";
    private static final String RETENCAO_ISSQN = "<tpRetISSQN>1</tpRetISSQN>";
    private static final String TOTAL_TRIBUTOS = "<totTrib><indTotTrib>0</indTotTrib></totTrib>";

    private static String trib(String conteudoTribMun, String tribFed) {
        return "<trib><tribMun>" + conteudoTribMun + "</tribMun>" + tribFed + TOTAL_TRIBUTOS + "</trib>";
    }

    private static String tribFed(String conteudo) {
        return "<tribFed>" + conteudo + "</tribFed>";
    }

    private static String piscofins(String conteudo) {
        return tribFed("<piscofins><CST>01</CST>" + conteudo + "</piscofins>");
    }

    static Stream<Arguments> camposDeValores() {
        String tribPadrao = trib(ISSQN + RETENCAO_ISSQN, "");
        String exigSusp = "<exigSusp><tpSusp>1</tpSusp><nProcesso>" + "1".repeat(30) + "</nProcesso></exigSusp>";
        String bmValor = "<BM><nBM>" + "2".repeat(14) + "</nBM><vRedBCBM>30.00</vRedBCBM></BM>";
        String bmPercentual = "<BM><nBM>" + "2".repeat(14) + "</nBM><pRedBCBM>12.50</pRedBCBM></BM>";
        return Stream.of(
            campoComTribPadrao("vReceb",
                "<vServPrest><vReceb>240.00</vReceb><vServ>250.00</vServ></vServPrest>", tribPadrao),
            campoComTribPadrao("vDescIncond",
                VALOR_SERVICO + "<vDescCondIncond><vDescIncond>10.00</vDescIncond></vDescCondIncond>", tribPadrao),
            campoComTribPadrao("vDescCond",
                VALOR_SERVICO + "<vDescCondIncond><vDescCond>5.00</vDescCond></vDescCondIncond>", tribPadrao),
            campoComTribPadrao("pDR", VALOR_SERVICO + "<vDedRed><pDR>20.00</pDR></vDedRed>", tribPadrao),
            campoComTribPadrao("vDR", VALOR_SERVICO + "<vDedRed><vDR>50.00</vDR></vDedRed>", tribPadrao),
            campoDeTrib("cPaisResult",
                trib("<tribISSQN>3</tribISSQN><cPaisResult>US</cPaisResult>" + RETENCAO_ISSQN, "")),
            campoDeTrib("tpImunidade",
                trib("<tribISSQN>2</tribISSQN><tpImunidade>3</tpImunidade>" + RETENCAO_ISSQN, "")),
            campoDeTrib("exigSusp", trib(ISSQN + exigSusp + RETENCAO_ISSQN, "")),
            campoDeTrib("BM/vRedBCBM", trib(ISSQN + bmValor + RETENCAO_ISSQN, "")),
            campoDeTrib("BM/pRedBCBM", trib(ISSQN + bmPercentual + RETENCAO_ISSQN, "")),
            campoDeTrib("pAliq", trib(ISSQN + RETENCAO_ISSQN + "<pAliq>2.00</pAliq>", "")),
            campoDeTrib("tribMun completo", trib("<tribISSQN>1</tribISSQN><cPaisResult>BR</cPaisResult>"
                + "<tpImunidade>0</tpImunidade>" + exigSusp + bmValor + RETENCAO_ISSQN + "<pAliq>5.00</pAliq>", "")),
            campoDeTrib("CST", trib(ISSQN + RETENCAO_ISSQN, piscofins(""))),
            campoDeTrib("vBCPisCofins", trib(ISSQN + RETENCAO_ISSQN, piscofins("<vBCPisCofins>250.00</vBCPisCofins>"))),
            campoDeTrib("pAliqPis", trib(ISSQN + RETENCAO_ISSQN, piscofins("<pAliqPis>0.65</pAliqPis>"))),
            campoDeTrib("pAliqCofins", trib(ISSQN + RETENCAO_ISSQN, piscofins("<pAliqCofins>3.00</pAliqCofins>"))),
            campoDeTrib("vPis", trib(ISSQN + RETENCAO_ISSQN, piscofins("<vPis>1.63</vPis>"))),
            campoDeTrib("vCofins", trib(ISSQN + RETENCAO_ISSQN, piscofins("<vCofins>7.50</vCofins>"))),
            campoDeTrib("tpRetPisCofins", trib(ISSQN + RETENCAO_ISSQN, piscofins("<tpRetPisCofins>2</tpRetPisCofins>"))),
            campoDeTrib("vRetCP", trib(ISSQN + RETENCAO_ISSQN, tribFed("<vRetCP>11.00</vRetCP>"))),
            campoDeTrib("vRetIRRF", trib(ISSQN + RETENCAO_ISSQN, tribFed("<vRetIRRF>3.75</vRetIRRF>"))),
            campoDeTrib("vRetCSLL", trib(ISSQN + RETENCAO_ISSQN, tribFed("<vRetCSLL>2.50</vRetCSLL>"))),
            campoDeTrib("tribFed completo", trib(ISSQN + RETENCAO_ISSQN, tribFed("<piscofins><CST>01</CST>"
                + "<vBCPisCofins>250.00</vBCPisCofins><pAliqPis>0.65</pAliqPis><pAliqCofins>3.00</pAliqCofins>"
                + "<vPis>1.63</vPis><vCofins>7.50</vCofins><tpRetPisCofins>2</tpRetPisCofins></piscofins>"
                + "<vRetCP>11.00</vRetCP><vRetIRRF>3.75</vRetIRRF><vRetCSLL>2.50</vRetCSLL>")))
        );
    }

    /** O trecho esperado e todo o conteudo de valores: o campo, com os vizinhos na ordem do XSD. */
    private static Arguments campoComTribPadrao(String campo, String antesDoTrib, String trib) {
        String valores = antesDoTrib + trib;
        return Arguments.of(campo, valores, valores);
    }

    private static Arguments campoDeTrib(String campo, String trib) {
        return Arguments.of(campo, VALOR_SERVICO + trib, trib);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("camposDeValores")
    void reemissaoPreservaCampoDeValores(String campo, String conteudoValores, String trechoEsperado) {
        String xml = reemitirXml(exemploComValores(conteudoValores));

        assertTrue(xml.contains(trechoEsperado), xml);
    }

    private static Dps reemitirComNovoValor(String exemploXml) {
        return DpsReemissao.reemitir(DpsXmlReader.read(exemploXml), new DpsReemissao.Overrides(
            11L, null, null, null, new BigDecimal("400.00"), null, null));
    }

    static Stream<Arguments> valoresAbsolutosDerivadosDoServico() {
        String tribPadrao = trib(ISSQN + RETENCAO_ISSQN, "");
        return Stream.of(
            Arguments.of("vReceb", "<vServPrest><vReceb>240.00</vReceb><vServ>250.00</vServ></vServPrest>" + tribPadrao),
            Arguments.of("vDescIncond",
                VALOR_SERVICO + "<vDescCondIncond><vDescIncond>10.00</vDescIncond></vDescCondIncond>" + tribPadrao),
            Arguments.of("vDescCond",
                VALOR_SERVICO + "<vDescCondIncond><vDescCond>5.00</vDescCond></vDescCondIncond>" + tribPadrao),
            Arguments.of("vDR", VALOR_SERVICO + "<vDedRed><vDR>50.00</vDR></vDedRed>" + tribPadrao),
            Arguments.of("vRedBCBM", VALOR_SERVICO + trib(ISSQN + "<BM><nBM>" + "2".repeat(14) + "</nBM>"
                + "<vRedBCBM>30.00</vRedBCBM></BM>" + RETENCAO_ISSQN, "")),
            Arguments.of("vBCPisCofins", VALOR_SERVICO
                + trib(ISSQN + RETENCAO_ISSQN, piscofins("<vBCPisCofins>250.00</vBCPisCofins>"))),
            Arguments.of("vPis", VALOR_SERVICO + trib(ISSQN + RETENCAO_ISSQN, piscofins("<vPis>1.63</vPis>"))),
            Arguments.of("vCofins", VALOR_SERVICO + trib(ISSQN + RETENCAO_ISSQN, piscofins("<vCofins>7.50</vCofins>"))),
            Arguments.of("vRetCP", VALOR_SERVICO + trib(ISSQN + RETENCAO_ISSQN, tribFed("<vRetCP>11.00</vRetCP>"))),
            Arguments.of("vRetIRRF", VALOR_SERVICO + trib(ISSQN + RETENCAO_ISSQN, tribFed("<vRetIRRF>3.75</vRetIRRF>"))),
            Arguments.of("vRetCSLL", VALOR_SERVICO + trib(ISSQN + RETENCAO_ISSQN, tribFed("<vRetCSLL>2.50</vRetCSLL>"))),
            Arguments.of("vTotTrib", VALOR_SERVICO + "<trib><tribMun>" + ISSQN + RETENCAO_ISSQN + "</tribMun><totTrib>"
                + "<vTotTrib><vTotTribFed>33.13</vTotTribFed><vTotTribEst>0.00</vTotTribEst>"
                + "<vTotTribMun>4.80</vTotTribMun></vTotTrib></totTrib></trib>")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valoresAbsolutosDerivadosDoServico")
    void trocaDeValorRecusaExemploComValorAbsolutoDerivadoDoServico(String campo, String conteudoValores) {
        String exemplo = exemploComValores(conteudoValores);

        DpsXmlException erro = assertThrows(DpsXmlException.class, () -> reemitirComNovoValor(exemplo));

        assertTrue(erro.getMessage().contains(campo), erro.getMessage());
    }

    @Test
    void trocaDeValorListaTodosOsValoresAbsolutosDoExemplo() {
        String exemplo = exemploComValores(VALOR_SERVICO + "<vDedRed><vDR>50.00</vDR></vDedRed>"
            + trib(ISSQN + RETENCAO_ISSQN, tribFed("<vRetIRRF>3.75</vRetIRRF>")));

        DpsXmlException erro = assertThrows(DpsXmlException.class, () -> reemitirComNovoValor(exemplo));

        assertTrue(erro.getMessage().contains("vDR, vRetIRRF"), erro.getMessage());
    }

    @Test
    void trocaDeValorPreservaPercentuaisDoExemplo() {
        String percentuais = "<vDedRed><pDR>20.00</pDR></vDedRed>"
            + "<trib><tribMun>" + ISSQN + "<BM><nBM>" + "2".repeat(14) + "</nBM><pRedBCBM>12.50</pRedBCBM></BM>"
            + RETENCAO_ISSQN + "<pAliq>2.00</pAliq></tribMun>"
            + "<tribFed><piscofins><CST>01</CST><pAliqPis>0.65</pAliqPis><pAliqCofins>3.00</pAliqCofins></piscofins></tribFed>"
            + "<totTrib><pTotTrib><pTotTribFed>13.25</pTotTribFed><pTotTribEst>0.00</pTotTribEst>"
            + "<pTotTribMun>1.92</pTotTribMun></pTotTrib></totTrib></trib>";

        String xml = new DpsXmlBuilder().build(reemitirComNovoValor(exemploComValores(VALOR_SERVICO + percentuais)));

        assertTrue(xml.contains("<vServPrest><vServ>400.00</vServ></vServPrest>" + percentuais), xml);
    }

    private static final String REGIME_TRIBUTARIO = "<regTrib><opSimpNac>1</opSimpNac><regEspTrib>0</regEspTrib></regTrib>";
    private static final String END_NACIONAL = "<end><endNac><cMun>3550308</cMun><CEP>01000000</CEP></endNac>"
        + "<xLgr>Rua A</xLgr><nro>100</nro><xCpl>Sala 1</xCpl><xBairro>Centro</xBairro></end>";
    private static final String END_EXTERIOR = "<end><endExt><cPais>US</cPais><cEndPost>10001</cEndPost>"
        + "<xCidade>New York</xCidade><xEstProvReg>NY</xEstProvReg></endExt>"
        + "<xLgr>5th Avenue</xLgr><nro>1</nro><xBairro>Manhattan</xBairro></end>";

    private static String exemploComPessoas(String prest, String toma) {
        return exemploMinimo()
            .replace("<prest><CNPJ>12345678000199</CNPJ>" + REGIME_TRIBUTARIO + "</prest>", prest)
            .replace("<toma><CPF>11122233344</CPF><xNome>Tomador</xNome></toma>", toma);
    }

    private static String reemitirXmlValidandoXsd(Dps exemplo, DpsReemissao.Overrides overrides) {
        String xml = new DpsXmlBuilder().build(DpsReemissao.reemitir(exemplo, overrides));
        assertEquals(List.of(), XmlSchemaValidator.validarDps(xml), xml);
        return xml;
    }

    private static DpsReemissao.Overrides soNumero() {
        return new DpsReemissao.Overrides(11L, null, null, null, null, null, null);
    }

    static Stream<Arguments> prestadores() {
        return Stream.of(
            Arguments.of("CAEPF", "<prest><CPF>11122233344</CPF><CAEPF>12345678901234</CAEPF>" + REGIME_TRIBUTARIO + "</prest>"),
            Arguments.of("xNome", "<prest><CNPJ>12345678000199</CNPJ><xNome>Prestador Ltda</xNome>" + REGIME_TRIBUTARIO + "</prest>"),
            Arguments.of("end nacional", "<prest><CNPJ>12345678000199</CNPJ>" + END_NACIONAL + REGIME_TRIBUTARIO + "</prest>"),
            Arguments.of("end exterior", "<prest><CNPJ>12345678000199</CNPJ>" + END_EXTERIOR + REGIME_TRIBUTARIO + "</prest>"),
            Arguments.of("completo", "<prest><CNPJ>12345678000199</CNPJ><CAEPF>12345678901234</CAEPF><IM>IM123</IM>"
                + "<xNome>Prestador Ltda</xNome>" + END_NACIONAL + "<fone>1133334444</fone><email>p@exemplo.com</email>"
                + "<regTrib><opSimpNac>3</opSimpNac><regApTribSN>1</regApTribSN><regEspTrib>0</regEspTrib></regTrib></prest>")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("prestadores")
    void reemissaoPreservaPrestadorDoExemplo(String caso, String prest) {
        Dps exemplo = DpsXmlReader.read(exemploComPessoas(prest, "<toma><CPF>11122233344</CPF><xNome>Tomador</xNome></toma>"));

        String xml = reemitirXmlValidandoXsd(exemplo, soNumero());

        assertTrue(xml.contains(prest), xml);
    }

    @Test
    void leituraPreservaIdentificacaoEstrangeiraDoPrestador() {
        Dps comNif = DpsXmlReader.read(exemploComPessoas(
            "<prest><NIF>US-123456</NIF>" + REGIME_TRIBUTARIO + "</prest>", "<toma><CPF>11122233344</CPF><xNome>Tomador</xNome></toma>"));
        Dps semNif = DpsXmlReader.read(exemploComPessoas(
            "<prest><cNaoNIF>2</cNaoNIF>" + REGIME_TRIBUTARIO + "</prest>", "<toma><CPF>11122233344</CPF><xNome>Tomador</xNome></toma>"));

        assertEquals("US-123456", comNif.infDps().prestador().nif());
        assertEquals(2, semNif.infDps().prestador().codigoNaoNif());
    }

    static Stream<Arguments> tomadores() {
        return Stream.of(
            Arguments.of("NIF", "<toma><NIF>US-123456</NIF><xNome>Foreign Inc</xNome></toma>"),
            Arguments.of("cNaoNIF", "<toma><cNaoNIF>1</cNaoNIF><xNome>Foreign Inc</xNome></toma>"),
            Arguments.of("CAEPF", "<toma><CPF>11122233344</CPF><CAEPF>12345678901234</CAEPF><xNome>Tomador</xNome></toma>"),
            Arguments.of("IM", "<toma><CNPJ>98765432000198</CNPJ><IM>IM999</IM><xNome>Tomador</xNome></toma>"),
            Arguments.of("end exterior", "<toma><NIF>US-123456</NIF><xNome>Foreign Inc</xNome>" + END_EXTERIOR + "</toma>"),
            Arguments.of("completo", "<toma><CNPJ>98765432000198</CNPJ><CAEPF>12345678901234</CAEPF><IM>IM999</IM>"
                + "<xNome>Tomador Ltda</xNome>" + END_NACIONAL + "<fone>1144445555</fone><email>t@exemplo.com</email></toma>")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tomadores")
    void reemissaoSemTrocaDeTomadorPreservaTomadorDoExemplo(String caso, String toma) {
        Dps exemplo = DpsXmlReader.read(exemploComPessoas(
            "<prest><CNPJ>12345678000199</CNPJ>" + REGIME_TRIBUTARIO + "</prest>", toma));

        String xml = reemitirXmlValidandoXsd(exemplo, soNumero());

        assertTrue(xml.contains(toma), xml);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tomadores")
    void trocaDeTomadorNaoHerdaDadosDoTomadorDoExemplo(String caso, String toma) {
        Dps exemplo = DpsXmlReader.read(exemploComPessoas(
            "<prest><CNPJ>12345678000199</CNPJ>" + REGIME_TRIBUTARIO + "</prest>", toma));
        DpsReemissao.Overrides novoTomador = new DpsReemissao.Overrides(
            11L, null, new Dps.Tomador(null, "55566677788", "Novo Tomador", null, null, null), null, null, null, null);

        String xml = reemitirXmlValidandoXsd(exemplo, novoTomador);

        assertTrue(xml.contains("<toma><CPF>55566677788</CPF><xNome>Novo Tomador</xNome></toma>"), xml);
    }

    private static String docDedRed(String identificacao, String depoisDaIdentificacao) {
        return "<docDedRed>" + identificacao + depoisDaIdentificacao + "</docDedRed>";
    }

    private static String docDedRed(String identificacao) {
        return docDedRed(identificacao, "<tpDedRed>2</tpDedRed><dtEmiDoc>2026-01-10</dtEmiDoc>"
            + "<vDedutivelRedutivel>80.00</vDedutivelRedutivel><vDeducaoReducao>50.00</vDeducaoReducao>");
    }

    private static String documentosDedRed(String... docs) {
        return "<vDedRed><documentos>" + String.join("", docs) + "</documentos></vDedRed>";
    }

    static Stream<Arguments> documentosDeDeducao() {
        return Stream.of(
            Arguments.of("chNFSe", documentosDedRed(docDedRed("<chNFSe>" + "3".repeat(50) + "</chNFSe>"))),
            Arguments.of("chNFe", documentosDedRed(docDedRed("<chNFe>" + "4".repeat(44) + "</chNFe>"))),
            Arguments.of("NFSeMun", documentosDedRed(docDedRed("<NFSeMun><cMunNFSeMun>3550308</cMunNFSeMun>"
                + "<nNFSeMun>" + "5".repeat(15) + "</nNFSeMun><cVerifNFSeMun>AB12CD</cVerifNFSeMun></NFSeMun>"))),
            Arguments.of("NFNFS", documentosDedRed(docDedRed("<NFNFS><nNFS>0001234</nNFS>"
                + "<modNFS>" + "0".repeat(14) + "1</modNFS><serieNFS>A1</serieNFS></NFNFS>"))),
            Arguments.of("nDocFisc", documentosDedRed(docDedRed("<nDocFisc>DF-77</nDocFisc>"))),
            Arguments.of("nDoc", documentosDedRed(docDedRed("<nDoc>RECIBO-1</nDoc>"))),
            Arguments.of("xDescOutDed e fornec", documentosDedRed(docDedRed("<nDoc>RECIBO-2</nDoc>",
                "<tpDedRed>99</tpDedRed><xDescOutDed>Outra deducao</xDescOutDed><dtEmiDoc>2026-01-10</dtEmiDoc>"
                    + "<vDedutivelRedutivel>80.00</vDedutivelRedutivel><vDeducaoReducao>50.00</vDeducaoReducao>"
                    + "<fornec><CNPJ>98765432000198</CNPJ><IM>IM555</IM><xNome>Fornecedor Ltda</xNome>"
                    + END_NACIONAL + "<fone>1155556666</fone><email>f@exemplo.com</email></fornec>"))),
            Arguments.of("varios documentos", documentosDedRed(
                docDedRed("<nDoc>RECIBO-1</nDoc>"),
                docDedRed("<chNFe>" + "4".repeat(44) + "</chNFe>"),
                docDedRed("<nDocFisc>DF-77</nDocFisc>")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentosDeDeducao")
    void reemissaoPreservaDocumentosDeDeducao(String caso, String vDedRed) {
        String valores = VALOR_SERVICO + vDedRed + trib(ISSQN + RETENCAO_ISSQN, "");
        Dps exemplo = DpsXmlReader.read(exemploComValores(valores));

        String xml = reemitirXmlValidandoXsd(exemplo, soNumero());

        assertTrue(xml.contains("<valores>" + valores + "</valores>"), xml);
    }

    @Test
    void trocaDeValorRecusaExemploComDocumentosDeDeducao() {
        String exemplo = exemploComValores(VALOR_SERVICO + documentosDedRed(docDedRed("<nDoc>RECIBO-1</nDoc>"))
            + trib(ISSQN + RETENCAO_ISSQN, ""));

        DpsXmlException erro = assertThrows(DpsXmlException.class, () -> reemitirComNovoValor(exemplo));

        assertTrue(erro.getMessage().contains("vDedutivelRedutivel, vDeducaoReducao"), erro.getMessage());
    }
}
