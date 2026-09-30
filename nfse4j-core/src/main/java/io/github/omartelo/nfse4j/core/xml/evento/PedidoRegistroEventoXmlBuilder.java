package io.github.omartelo.nfse4j.core.xml.evento;

import io.github.omartelo.nfse4j.core.xml.dps.DpsXmlBuilder;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class PedidoRegistroEventoXmlBuilder {
    private static final String NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";
    private static final String VERSAO_EVENTO = "1.00";

    public String buildCancelamento(int tipoAmbiente, CancelamentoNfse cancelamento) {
        return build(tipoAmbiente, cancelamento);
    }

    public String build(int tipoAmbiente, PedidoRegistroEvento evento) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();

            Element root = document.createElementNS(NAMESPACE, "pedRegEvento");
            root.setAttribute("versao", VERSAO_EVENTO);
            document.appendChild(root);

            Element infPedReg = element(root, "infPedReg");
            infPedReg.setAttribute("Id", evento.idPedidoRegistroEvento());
            append(infPedReg, "tpAmb", tipoAmbiente);
            append(infPedReg, "verAplic", evento.versaoAplicativo());
            append(
                infPedReg,
                "dhEvento",
                DpsXmlBuilder.formatarDataHora(evento.dataHoraEvento())
            );
            append(infPedReg, evento.autorPessoaJuridica() ? "CNPJAutor" : "CPFAutor", evento.cpfCnpjAutor());
            append(infPedReg, "chNFSe", evento.chaveAcesso());

            Element grupo = element(infPedReg, "e" + evento.tipoEvento());
            switch (evento) {
                case CancelamentoNfse cancelamento -> {
                    append(grupo, "xDesc", CancelamentoNfse.DESCRICAO_EVENTO);
                    append(grupo, "cMotivo", cancelamento.codigoMotivo());
                    append(grupo, "xMotivo", cancelamento.descricaoMotivo());
                }
                case SolicitacaoAnaliseFiscalCancelamentoNfse solicitacao -> {
                    append(grupo, "xDesc", SolicitacaoAnaliseFiscalCancelamentoNfse.DESCRICAO_EVENTO);
                    append(grupo, "cMotivo", solicitacao.codigoMotivo());
                    append(grupo, "xMotivo", solicitacao.descricaoMotivo());
                }
                case ManifestacaoNfse manifestacao -> {
                    append(grupo, "xDesc", manifestacao.tipo().descricao());
                    append(grupo, "cMotivo", manifestacao.codigoMotivo());
                    append(grupo, "xMotivo", manifestacao.descricaoMotivo());
                }
            }

            return toXml(document);
        } catch (Exception exception) {
            throw new PedidoRegistroEventoXmlException("Nao foi possivel gerar XML de evento.", exception);
        }
    }

    private static Element element(Element parent, String name) {
        Document document = parent.getOwnerDocument();
        Element element = document.createElement(name);
        parent.appendChild(element);
        return element;
    }

    private static void append(Element parent, String name, Object value) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return;
        }

        Element element = element(parent, name);
        element.appendChild(parent.getOwnerDocument().createTextNode(value.toString()));
    }

    private static String toXml(Document document) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString().replace("\n", "").replace("\r", "");
    }
}
