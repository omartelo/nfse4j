# Componentes de terceiros nos jars das releases

Os fat jars `nfse4j-cli-<versao>.jar` e `nfse4j-mcp-<versao>.jar` publicados nas GitHub Releases
agregam, além do código MIT do nfse4j, os componentes abaixo. Os módulos `nfse4j-core` e
`nfse4j-danfse` publicados no Maven Central não agregam nada: declaram as bibliotecas como
dependências.

## nfse4j-cli e nfse4j-mcp

| Componente | Licença | Fonte |
|---|---|---|
| OpenHTMLtoPDF (openhtmltopdf-core / openhtmltopdf-pdfbox) 1.0.10 | LGPL-2.1 | https://github.com/danfickle/openhtmltopdf |
| Apache PDFBox / FontBox / XmpBox | Apache-2.0 | https://pdfbox.apache.org |
| de.rototor.pdfbox:graphics2d | Apache-2.0 | https://github.com/rototor/pdfbox-graphics2d |
| Apache Commons Logging | Apache-2.0 | https://commons.apache.org/logging |
| ZXing core | Apache-2.0 | https://github.com/zxing/zxing |
| Liberation Sans (Regular/Bold) | SIL OFL 1.1 | https://github.com/liberationfonts/liberation-fonts |
| Logomarca oficial da NFS-e | CC BY-ND 3.0 (redistribuída sem modificações) | https://www.gov.br/nfse |

## Só no nfse4j-cli

| Componente | Licença | Fonte |
|---|---|---|
| Jackson 2 (core, databind, annotations, datatype-jsr310) | Apache-2.0 | https://github.com/FasterXML/jackson |

## Só no nfse4j-mcp

| Componente | Licença | Fonte |
|---|---|---|
| MCP Java SDK (mcp, mcp-core, mcp-json-jackson3) | MIT | https://github.com/modelcontextprotocol/java-sdk |
| Jackson 3 (core, databind, dataformat-yaml, annotations) | Apache-2.0 | https://github.com/FasterXML/jackson |
| Project Reactor (reactor-core) | Apache-2.0 | https://github.com/reactor/reactor-core |
| Reactive Streams | MIT-0 | https://github.com/reactive-streams/reactive-streams-jvm |
| SLF4J API | MIT | https://www.slf4j.org |
| networknt json-schema-validator | Apache-2.0 | https://github.com/networknt/json-schema-validator |
| ethlo ITU | Apache-2.0 | https://github.com/ethlo/itu |
| SnakeYAML Engine | Apache-2.0 | https://github.com/snakeyaml/snakeyaml-engine |

## Nota sobre a LGPL-2.1 (OpenHTMLtoPDF)

O OpenHTMLtoPDF é agregado nos fat jars **sem modificações**, só por conveniência de distribuição.
Os jars originais estão no Maven Central (`com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10`), e
qualquer pessoa pode reconstruir os fat jars com outra versão da biblioteca a partir deste
repositório (`./gradlew build`), o que atende ao requisito de substituição da LGPL.

A licença MIT do nfse4j e este arquivo ficam em `META-INF/nfse4j/` dentro dos jars; o texto da
licença das fontes, em `danfse/fonts/LICENSE-OFL.txt`. Os avisos do logo da NFS-e e das fontes estão
em `nfse4j-danfse/NOTICE.md` no repositório.
