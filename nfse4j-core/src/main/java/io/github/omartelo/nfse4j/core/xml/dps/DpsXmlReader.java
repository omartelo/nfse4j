package io.github.omartelo.nfse4j.core.xml.dps;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Le um XML existente de DPS (ou de NFSe que embrulha uma DPS, como o DANFSe/retorno da SEFIN)
 * e reconstroi o modelo {@link Dps}. Usado para o fluxo "emitir a partir de uma nota de exemplo":
 * a pessoa aponta uma nota antiga e so troca tomador/descricao/valor (ver {@link DpsReemissao}).
 *
 * <p>Implementacao com DOM do JDK, sem biblioteca de XML.
 */
public final class DpsXmlReader {

    /**
     * Grupos do infDPS (XSD DPS v1.01) que o modelo {@link Dps} nao representa. Ler um exemplo com eles
     * descartaria os dados em silencio e a nota reemitida sairia diferente da original.
     */
    private static final List<String> GRUPOS_NAO_SUPORTADOS = List.of(
        "cMotivoEmisTI", "chNFSeRej", "subst", "interm", "IBSCBS",
        "cPaisPrestacao", "cIntContrib", "comExt", "obra", "atvEvento", "infoCompl",
        "vDedRed/documentos"
    );

    private DpsXmlReader() {
    }

    public static Dps read(String xml) {
        Objects.requireNonNull(xml, "xml is required");
        try {
            Document document = parse(xml);
            Element infDps = firstByLocalName(document.getDocumentElement(), "infDPS");
            if (infDps == null) {
                throw new DpsXmlException("XML nao contem elemento infDPS.");
            }
            recusarGruposNaoSuportados(infDps);
            String versao = versaoDaDps(infDps);

            Dps.InfDps inf = new Dps.InfDps(
                attr(infDps, "Id"),
                intOrDefault(text(infDps, "tpAmb"), 2),
                offsetOrNull(text(infDps, "dhEmi")),
                text(infDps, "verAplic"),
                text(infDps, "serie"),
                longOrZero(text(infDps, "nDPS")),
                dateOrNull(text(infDps, "dCompet")),
                intOrDefault(text(infDps, "tpEmit"), 1),
                text(infDps, "cLocEmi"),
                prestador(firstByLocalName(infDps, "prest")),
                tomador(firstByLocalName(infDps, "toma")),
                servico(firstByLocalName(infDps, "serv")),
                valores(firstByLocalName(infDps, "valores"))
            );
            return new Dps(versao, inf);
        } catch (DpsXmlException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DpsXmlException("Nao foi possivel ler o XML de exemplo da DPS.", exception);
        }
    }

    private static void recusarGruposNaoSuportados(Element infDps) {
        List<String> presentes = GRUPOS_NAO_SUPORTADOS.stream()
            .filter(caminho -> byPath(infDps, caminho) != null)
            .toList();
        if (!presentes.isEmpty()) {
            throw new DpsXmlException("Exemplo contem grupos que a reemissao nao suporta e seriam perdidos: "
                + String.join(", ", presentes) + ".");
        }
    }

    private static Dps.Prestador prestador(Element prest) {
        if (prest == null) {
            return null;
        }
        Element regTrib = firstByLocalName(prest, "regTrib");
        Dps.RegimeTributario regime = regTrib == null ? null : new Dps.RegimeTributario(
            integerOrNull(text(regTrib, "opSimpNac")),
            integerOrNull(text(regTrib, "regApTribSN")),
            integerOrNull(text(regTrib, "regEspTrib"))
        );
        return new Dps.Prestador(
            text(prest, "CNPJ"),
            text(prest, "CPF"),
            text(prest, "IM"),
            text(prest, "fone"),
            text(prest, "email"),
            regime
        );
    }

    private static Dps.Tomador tomador(Element toma) {
        if (toma == null) {
            return null;
        }
        Element end = firstByLocalName(toma, "end");
        Dps.Endereco endereco = null;
        if (end != null) {
            endereco = new Dps.Endereco(
                text(end, "cMun"),
                text(end, "CEP"),
                text(end, "xLgr"),
                text(end, "nro"),
                text(end, "xCpl"),
                text(end, "xBairro")
            );
        }
        return new Dps.Tomador(
            text(toma, "CNPJ"),
            text(toma, "CPF"),
            text(toma, "xNome"),
            endereco,
            text(toma, "fone"),
            text(toma, "email")
        );
    }

    private static Dps.Servico servico(Element serv) {
        if (serv == null) {
            return null;
        }
        return new Dps.Servico(
            text(serv, "cLocPrestacao"),
            text(serv, "cTribNac"),
            text(serv, "cTribMun"),
            text(serv, "xDescServ"),
            text(serv, "cNBS")
        );
    }

    private static Dps.Valores valores(Element valores) {
        if (valores == null) {
            return null;
        }
        Dps.Tributacao tributacao = new Dps.Tributacao(
            integerOrNull(text(valores, "tribISSQN")),
            integerOrNull(text(valores, "tpRetISSQN")),
            integerOrNull(text(valores, "indTotTrib")),
            decimalOrNull(text(valores, "pTotTribSN")),
            totalTributos(firstByLocalName(valores, "vTotTrib"), "vTotTribFed", "vTotTribEst", "vTotTribMun"),
            totalTributos(firstByLocalName(valores, "pTotTrib"), "pTotTribFed", "pTotTribEst", "pTotTribMun"),
            text(valores, "cPaisResult"),
            integerOrNull(text(valores, "tpImunidade")),
            exigibilidadeSuspensa(firstByLocalName(valores, "exigSusp")),
            beneficioMunicipal(firstByLocalName(valores, "BM")),
            decimalOrNull(text(valores, "pAliq")),
            tributacaoFederal(firstByLocalName(valores, "tribFed"))
        );
        return new Dps.Valores(
            decimalOrNull(text(valores, "vServ")),
            tributacao,
            decimalOrNull(text(valores, "vReceb")),
            descontos(firstByLocalName(valores, "vDescCondIncond")),
            deducaoReducao(firstByLocalName(valores, "vDedRed"))
        );
    }

    private static Dps.Descontos descontos(Element descontos) {
        if (descontos == null) {
            return null;
        }
        return new Dps.Descontos(decimalOrNull(text(descontos, "vDescIncond")), decimalOrNull(text(descontos, "vDescCond")));
    }

    private static Dps.DeducaoReducao deducaoReducao(Element deducaoReducao) {
        if (deducaoReducao == null) {
            return null;
        }
        return new Dps.DeducaoReducao(decimalOrNull(text(deducaoReducao, "pDR")), decimalOrNull(text(deducaoReducao, "vDR")));
    }

    private static Dps.ExigibilidadeSuspensa exigibilidadeSuspensa(Element exigSusp) {
        if (exigSusp == null) {
            return null;
        }
        return new Dps.ExigibilidadeSuspensa(integerOrNull(text(exigSusp, "tpSusp")), text(exigSusp, "nProcesso"));
    }

    private static Dps.BeneficioMunicipal beneficioMunicipal(Element bm) {
        if (bm == null) {
            return null;
        }
        return new Dps.BeneficioMunicipal(
            text(bm, "nBM"),
            decimalOrNull(text(bm, "vRedBCBM")),
            decimalOrNull(text(bm, "pRedBCBM"))
        );
    }

    private static Dps.TributacaoFederal tributacaoFederal(Element tribFed) {
        if (tribFed == null) {
            return null;
        }
        return new Dps.TributacaoFederal(
            pisCofins(firstByLocalName(tribFed, "piscofins")),
            decimalOrNull(text(tribFed, "vRetCP")),
            decimalOrNull(text(tribFed, "vRetIRRF")),
            decimalOrNull(text(tribFed, "vRetCSLL"))
        );
    }

    private static Dps.PisCofins pisCofins(Element piscofins) {
        if (piscofins == null) {
            return null;
        }
        return new Dps.PisCofins(
            text(piscofins, "CST"),
            decimalOrNull(text(piscofins, "vBCPisCofins")),
            decimalOrNull(text(piscofins, "pAliqPis")),
            decimalOrNull(text(piscofins, "pAliqCofins")),
            decimalOrNull(text(piscofins, "vPis")),
            decimalOrNull(text(piscofins, "vCofins")),
            integerOrNull(text(piscofins, "tpRetPisCofins"))
        );
    }

    private static Dps.TotalTributos totalTributos(Element total, String federal, String estadual, String municipal) {
        if (total == null) {
            return null;
        }
        return new Dps.TotalTributos(
            decimalOrNull(text(total, federal)),
            decimalOrNull(text(total, estadual)),
            decimalOrNull(text(total, municipal))
        );
    }

    private static String versaoDaDps(Element infDps) {
        Node parent = infDps.getParentNode();
        if (parent instanceof Element dps && "DPS".equals(localName(dps))) {
            String versao = dps.getAttribute("versao");
            if (versao != null && !versao.isBlank()) {
                return versao;
            }
        }
        return "1.00";
    }

    // ---- helpers DOM ----

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** Primeiro descendente (busca em profundidade) com o local name informado, ou null. */
    private static Element firstByLocalName(Element scope, String localName) {
        if (scope == null) {
            return null;
        }
        NodeList children = scope.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element) {
                if (localName.equals(localName(element))) {
                    return element;
                }
                Element nested = firstByLocalName(element, localName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /** Resolve um caminho de local names separados por '/', cada passo com {@link #firstByLocalName}. */
    private static Element byPath(Element scope, String path) {
        Element current = scope;
        for (String localName : path.split("/")) {
            current = firstByLocalName(current, localName);
        }
        return current;
    }

    /** Texto do primeiro descendente com o local name informado, ou null. */
    private static String text(Element scope, String localName) {
        Element element = firstByLocalName(scope, localName);
        if (element == null) {
            return null;
        }
        String content = element.getTextContent();
        return content == null || content.isBlank() ? null : content.trim();
    }

    private static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    private static Integer integerOrNull(String value) {
        return value == null ? null : Integer.valueOf(value.trim());
    }

    private static int intOrDefault(String value, int fallback) {
        return value == null ? fallback : Integer.parseInt(value.trim());
    }

    private static long longOrZero(String value) {
        return value == null ? 0L : Long.parseLong(value.trim());
    }

    private static BigDecimal decimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value.trim());
    }

    private static LocalDate dateOrNull(String value) {
        return value == null ? null : LocalDate.parse(value.trim());
    }

    private static OffsetDateTime offsetOrNull(String value) {
        return value == null ? null : OffsetDateTime.parse(value.trim());
    }
}
