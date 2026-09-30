package io.github.omartelo.nfse4j.danfse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NfseXmlReaderTest {

    static String xmlExemplo() throws IOException {
        try (var in = NfseXmlReaderTest.class.getResourceAsStream("/nfse-exemplo-ficticio.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void extraiChaveDeAcessoRemovendoPrefixoNFS() throws Exception {
        Danfse d = NfseXmlReader.read(xmlExemplo());
        assertEquals("35503082212345678000199000000000001234500000000000001", d.chaveAcesso());
    }

    @Test
    void extraiIdentificacaoNfseEDps() throws Exception {
        Danfse.Identificacao id = NfseXmlReader.read(xmlExemplo()).identificacao();
        assertEquals("12345", id.numeroNfse());
        assertEquals("1234", id.numeroDps());
        assertEquals("00900", id.serieDps());
        assertEquals("Sao Paulo", id.municipioEmissao());
        assertEquals(java.time.LocalDate.parse("2026-06-23"), id.competencia());
    }

    @Test
    void extraiPrestadorComEnderecoERegimeSimples() throws Exception {
        Danfse.Pessoa p = NfseXmlReader.read(xmlExemplo()).prestador();
        assertEquals("12345678000199", p.cnpj());
        assertEquals("1234567890", p.inscricaoMunicipal());
        assertEquals("EMPRESA EXEMPLO LTDA", p.nome());
        assertEquals("Centro", p.endereco().bairro());
        assertEquals("SP", p.endereco().uf());
        assertTrue(p.regimeSimplesNacional().contains("ME/EPP"));
    }

    @Test
    void extraiTomadorPorCpfComEnderecoAninhado() throws Exception {
        Danfse.Pessoa t = NfseXmlReader.read(xmlExemplo()).tomador();
        assertEquals("11144477735", t.cpf());
        assertNull(t.cnpj());
        assertEquals("Fulano de Tal", t.nome());
        assertEquals("Avenida Central", t.endereco().logradouro());
        // municipio resolvido de codigo -> nome via pares do proprio XML (cLocPrestacao/xLocPrestacao=3550308/Sao Paulo)
        assertEquals("Sao Paulo", t.endereco().municipio());
    }

    @Test
    void extraiServicoEValores() throws Exception {
        Danfse d = NfseXmlReader.read(xmlExemplo());
        assertEquals("010101", d.servico().codigoTributacaoNacional());
        assertEquals("001", d.servico().codigoTributacaoMunicipal());
        assertEquals("Servico municipal ficticio.", d.servico().descricaoTributacaoMunicipal());
        assertEquals("Operação Tributável", d.tributacaoMunicipal().tipoTributacaoIssqn());
        assertEquals("Não Retido", d.tributacaoMunicipal().retencaoIssqn());
        assertEquals("Nenhum", d.tributacaoMunicipal().regimeEspecial());
        assertEquals(0, d.valores().valorServico().compareTo(new BigDecimal("250.00")));
        assertEquals(0, d.valores().valorLiquido().compareTo(new BigDecimal("250.00")));
    }

    @Test
    void extraiTotaisAproximadosDoSimplesNacional() throws Exception {
        Danfse.TotaisTributos t = NfseXmlReader.read(xmlExemplo()).totaisTributos();
        assertEquals(0, t.percentualSimplesNacional().compareTo(new BigDecimal("6.00")));
        assertFalse(t.naoInformado());
    }

    @Test
    void extraiCamposDoCabecalhoEIdentificacao() throws Exception {
        Danfse.Identificacao id = NfseXmlReader.read(xmlExemplo()).identificacao();
        assertEquals("Prestador", id.emitente());
        assertEquals("Ambiente Nacional", id.ambienteGerador());
        assertEquals("Produção Restrita", id.tipoAmbiente());
        assertEquals("NFS-e MEI", id.situacao(), "cStat por extenso (TStat do leiaute)");
    }

    @Test
    void semIbsCbsQuandoAusente() throws Exception {
        assertNull(NfseXmlReader.read(xmlExemplo()).ibsCbs());
    }

    @Test
    void detectaAmbientePeloTpAmbDaDpsNaoPeloAmbGer() throws Exception {
        // o XML ficticio tem tpAmb=2 (homologacao)
        assertTrue(NfseXmlReader.read(xmlExemplo()).homologacao());
        // tpAmb=1 => producao
        String prod = xmlExemplo().replace("<tpAmb>2</tpAmb>", "<tpAmb>1</tpAmb>");
        assertFalse(NfseXmlReader.read(prod).homologacao());
    }

    @Test
    void producaoComAmbGer2NaoEhMarcadaComoHomologacao() throws Exception {
        // Caso real: nota de PRODUCAO traz ambGer=2 mas tpAmb=1. Deve contar como producao.
        String prod = xmlExemplo()
            .replace("<tpAmb>2</tpAmb>", "<tpAmb>1</tpAmb>"); // ambGer permanece 2
        assertFalse(NfseXmlReader.read(prod).homologacao(),
            "producao (tpAmb=1) nao pode ser homologacao mesmo com ambGer=2");
    }

    @Test
    void leInscricaoImobiliariaDoBlocoImovel() throws Exception {
        // NT 008, item 2.4.5: o caminho oficial do campo e NFSe/infNFSe/DPS/infDPS/IBSCBS/imovel/,
        // irmao de serv. A Nota 8 exige o prefixo "Insc. Imob.: ".
        String xml = xmlExemplo().replace("</infDPS>",
            "<IBSCBS><imovel><inscImobFisc>987654321</inscImobFisc></imovel></IBSCBS></infDPS>");
        String info = NfseXmlReader.read(xml).informacoesComplementares();
        assertNotNull(info, "com imovel preenchido as informacoes complementares nao podem ser nulas");
        assertTrue(info.contains("Insc. Imob.: 987654321"),
            "esperado o prefixo da Nota 8 para o bloco imovel; veio: " + info);
    }

    @Test
    void mantemInscricaoImobiliariaDoBlocoObra() throws Exception {
        // O mesmo campo existe em serv/obra (TCInfoObra no leiaute); nao pode regredir.
        String xml = xmlExemplo().replace("</serv>",
            "<obra><inscImobFisc>111222333</inscImobFisc></obra></serv>");
        String info = NfseXmlReader.read(xml).informacoesComplementares();
        assertNotNull(info);
        assertTrue(info.contains("Insc. Imob.: 111222333"),
            "inscricao imobiliaria do bloco obra; veio: " + info);
    }

    @Test
    void leNumeroDoPedidoEItensDoPedido() throws Exception {
        // NT 008, item 2.4.5: a ordem obrigatoria inclui "Núm. Ped.:" (serv/infoCompl/xPed) e
        // "Item Ped.:" (serv/infoCompl/gItemPed/xItemPed, que o leiaute permite repetir ate 99x).
        String xml = xmlExemplo().replace("</serv>",
            "<infoCompl><xPed>PED-2026-001</xPed>"
            + "<gItemPed><xItemPed>10</xItemPed><xItemPed>20</xItemPed></gItemPed>"
            + "</infoCompl></serv>");
        String info = NfseXmlReader.read(xml).informacoesComplementares();
        assertNotNull(info, "informacoes complementares nao podem ser nulas com pedido preenchido");
        assertTrue(info.contains("Núm. Ped.: PED-2026-001"), "numero do pedido; veio: " + info);
        assertTrue(info.contains("Item Ped.: 10, 20"), "itens do pedido concatenados; veio: " + info);
    }

    @Test
    void ordemDasInformacoesComplementaresSegueONt008() throws Exception {
        // A NT fixa a ordem: Inf. Cont.; NFS-e Subst.; Doc. Ref.; Cod. Obra; Insc. Imob.;
        // Cod. Evt.; Doc. Tec.; Núm. Ped.; Item Ped.; Inf. A. T. Mun.
        String xml = xmlExemplo().replace("</serv>",
            "<infoCompl><idDocTec>DOC-1</idDocTec><xPed>PED-1</xPed>"
            + "<gItemPed><xItemPed>7</xItemPed></gItemPed></infoCompl></serv>");
        String info = NfseXmlReader.read(xml).informacoesComplementares();
        assertTrue(info.indexOf("Doc. Tec.:") < info.indexOf("Núm. Ped.:"), "veio: " + info);
        assertTrue(info.indexOf("Núm. Ped.:") < info.indexOf("Item Ped.:"), "veio: " + info);
    }

    @Test
    void xmlSemInfNFSeFalha() {
        assertThrows(DanfseException.class, () -> NfseXmlReader.read("<NFSe></NFSe>"));
    }
}
