package io.github.omartelo.nfse4j.core.xml.dps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
            Arguments.of("infoCompl", "</cServ>", "</cServ><infoCompl><xInfComp>Observacao</xInfComp></infoCompl>"),
            Arguments.of("vDedRed/documentos", "</vServPrest>",
                "</vServPrest><vDedRed><documentos><docDedRed><nDoc>1</nDoc><tpDedRed>1</tpDedRed>"
                    + "<dtEmiDoc>2026-01-10</dtEmiDoc><vDedutivelRedutivel>50.00</vDedutivelRedutivel>"
                    + "<vDeducaoReducao>50.00</vDeducaoReducao></docDedRed></documentos></vDedRed>")
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
}
