package io.github.omartelo.nfse4j.core.xml;

public record XmlSchemaViolation(int linha, int coluna, String elemento, String mensagem) {

    @Override
    public String toString() {
        return "linha " + linha + ", coluna " + coluna + ", elemento <" + elemento + ">: " + mensagem;
    }
}
