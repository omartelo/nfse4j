package io.github.omartelo.nfse4j.core.xml;

import java.util.List;
import java.util.stream.Collectors;

public class XmlSchemaValidationException extends RuntimeException {
    private final List<XmlSchemaViolation> violacoes;

    public XmlSchemaValidationException(String documento, List<XmlSchemaViolation> violacoes) {
        super(documento + " nao passou na validacao do XSD oficial: " + violacoes.stream()
            .map(XmlSchemaViolation::toString)
            .collect(Collectors.joining(" | ")));
        this.violacoes = List.copyOf(violacoes);
    }

    public List<XmlSchemaViolation> violacoes() {
        return violacoes;
    }
}
