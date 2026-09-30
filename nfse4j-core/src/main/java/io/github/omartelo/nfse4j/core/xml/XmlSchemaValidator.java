package io.github.omartelo.nfse4j.core.xml;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Valida DPS e pedido de registro de evento contra os XSDs oficiais da NFS-e Nacional (leiaute v1.01),
 * empacotados em {@code resources/.../xml/xsd}. Valida tanto o XML assinado quanto o nao assinado:
 * no XSD a {@code ds:Signature} e opcional e, quando presente, e validada contra o xmldsig-core-schema.
 */
public final class XmlSchemaValidator {
    private static final Schema DPS = compilar("DPS_v1.01.xsd");
    private static final Schema PEDIDO_REGISTRO_EVENTO = compilar("pedRegEvento_v1.01.xsd");

    private XmlSchemaValidator() {
    }

    /** @return violacoes encontradas, vazia se o XML e valido. XML malformado tambem vira violacao. */
    public static List<XmlSchemaViolation> validarDps(String xml) {
        return validar(DPS, xml);
    }

    /** @return violacoes encontradas, vazia se o XML e valido. XML malformado tambem vira violacao. */
    public static List<XmlSchemaViolation> validarPedidoRegistroEvento(String xml) {
        return validar(PEDIDO_REGISTRO_EVENTO, xml);
    }

    private static List<XmlSchemaViolation> validar(Schema schema, String xml) {
        Objects.requireNonNull(xml, "xml is required");
        ElementoCorrente elementoCorrente = new ElementoCorrente();
        ColetorDeViolacoes coletor = new ColetorDeViolacoes(elementoCorrente);
        ValidatorHandler validatorHandler = schema.newValidatorHandler();
        validatorHandler.setErrorHandler(coletor);
        elementoCorrente.setContentHandler(validatorHandler);
        try {
            XMLReader reader = novoParser().newSAXParser().getXMLReader();
            elementoCorrente.setParent(reader);
            elementoCorrente.setErrorHandler(coletor);
            elementoCorrente.parse(new InputSource(new StringReader(xml)));
        } catch (SAXParseException malformado) {
            // XML malformado: o coletor ja registrou o erro fatal antes do parser abortar.
        } catch (SAXException | IOException | ParserConfigurationException exception) {
            throw new IllegalStateException("Falha inesperada ao validar XML contra o XSD.", exception);
        }
        return List.copyOf(coletor.violacoes);
    }

    private static SAXParserFactory novoParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory;
    }

    private static Schema compilar(String arquivo) {
        URL xsd = XmlSchemaValidator.class.getResource("xsd/" + arquivo);
        if (xsd == null) {
            throw new IllegalStateException("XSD nao encontrado no classpath: xsd/" + arquivo);
        }
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // O xmldsig-core-schema.xsd oficial declara DOCTYPE com DTD em http://www.w3.org/.
            // Bloquear DTD externo evita acesso a rede; o DTD interno do arquivo continua valendo.
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            return factory.newSchema(xsd);
        } catch (SAXException exception) {
            throw new IllegalStateException("Nao foi possivel carregar o XSD " + arquivo + ".", exception);
        }
    }

    /** Repassa os eventos SAX ao validador guardando o elemento aberto, que o SAXParseException nao informa. */
    private static final class ElementoCorrente extends XMLFilterImpl {
        private final Deque<String> abertos = new ArrayDeque<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            abertos.push(localName);
            super.startElement(uri, localName, qName, atts);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            super.endElement(uri, localName, qName);
            abertos.pop();
        }

        String nome() {
            return abertos.isEmpty() ? "" : abertos.peek();
        }
    }

    private static final class ColetorDeViolacoes implements ErrorHandler {
        private final ElementoCorrente elementoCorrente;
        private final List<XmlSchemaViolation> violacoes = new ArrayList<>();

        ColetorDeViolacoes(ElementoCorrente elementoCorrente) {
            this.elementoCorrente = elementoCorrente;
        }

        @Override
        public void warning(SAXParseException exception) {
        }

        @Override
        public void error(SAXParseException exception) {
            registrar(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXParseException {
            registrar(exception);
            throw exception;
        }

        private void registrar(SAXParseException exception) {
            violacoes.add(new XmlSchemaViolation(
                exception.getLineNumber(),
                exception.getColumnNumber(),
                elementoCorrente.nome(),
                exception.getMessage()
            ));
        }
    }
}
