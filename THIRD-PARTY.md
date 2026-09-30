# Componentes de terceiros nos jars das releases

Os fat jars `nfse4j-cli-<versao>.jar` e `nfse4j-mcp-<versao>.jar` publicados nas GitHub Releases
agregam, além do código MIT do nfse4j, os componentes abaixo. Os módulos `nfse4j-core` e
`nfse4j-danfse` publicados no Maven Central não agregam bibliotecas: declaram-nas como
dependências. O `nfse4j-core` agrega apenas os XSDs oficiais descritos na seção seguinte.

## nfse4j-core (e, por consequência, os fat jars)

| Componente | Licença | Fonte |
|---|---|---|
| XSDs da NFS-e Nacional v1.01 (2026-02-09): `DPS`, `pedRegEvento`, `tiposComplexos`, `tiposEventos`, `tiposSimples` | Documento oficial do governo federal, redistribuído | https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual (pacote `nfse-esquemas_xsd-v1-01-20260209.zip`, sha256 `e7935cbd9470527c6cc32984c1b2263e614183bf0139ce2733eaaed2de9a8072`) |
| `xmldsig-core-schema.xsd` (XML Signature, incluso no mesmo pacote) | W3C Software and Document License | https://www.w3.org/TR/xmldsig-core/ |

Os arquivos ficam em `nfse4j-core/src/main/resources/io/github/omartelo/nfse4j/core/xml/xsd/`, copiados
byte a byte, com uma única alteração local: o pattern de `TSSerieDPS` em `tiposSimples_v1.01.xsd`
perdeu as âncoras `^`/`$`, que em regex de XML Schema são literais e faziam toda série ser recusada
(ver comentário no arquivo).

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
