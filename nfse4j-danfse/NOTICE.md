# Avisos de terceiros — nfse4j-danfse

## Logo da NFS-e

O arquivo `src/main/resources/danfse/nfse-logo.png` é o logotipo oficial da **NFS-e**
(Nota Fiscal de Serviço eletrônica), obtido do portal nacional:
https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/logos-da-nfs-e

Licença: **Creative Commons Atribuição-SemDerivações 3.0 (CC BY-ND 3.0)**.
O arquivo é redistribuído **sem modificações**, para uso no cabeçalho do DANFSe conforme a
Nota Técnica SE/CGNFS-e nº 008/2026.

## Fontes Liberation Sans

Os arquivos `src/main/resources/danfse/fonts/LiberationSans-{Regular,Bold}.ttf` são da família
**Liberation Fonts** (https://github.com/liberationfonts/liberation-fonts), versão 2.1.5,
licenciados sob a **SIL Open Font License 1.1** (texto completo em
`src/main/resources/danfse/fonts/LICENSE-OFL.txt`). A Liberation Sans é metricamente compatível
com a Arial exigida pela NT 008/2026 e é embutida (subset) nos PDFs gerados, o que a OFL permite.

## Origem do código

O código deste módulo vem do [xml-danfse-br](https://github.com/rzmt/xml-danfse-br) 0.9.0
(MIT, © Rafael Matos) com as correções do PR #2 (rjunior-coder), trazido de volta para este
projeto, de onde o xml-danfse-br tinha sido extraído.

## API do IBGE

Para resolver o nome de municípios que não vêm nomeados no XML, este módulo consulta a API pública
de localidades do IBGE (`servicodados.ibge.gov.br`) em tempo de geração, com cache em memória e
fallback gracioso ao código. Desative com `-Dnfse4j.danfse.ibge=false` (geração 100% offline).

## Dependências de runtime

- OpenHTMLtoPDF — LGPL-2.1
- Apache PDFBox / FontBox / XmpBox — Apache-2.0
- ZXing (core) — Apache-2.0

O código deste módulo é licenciado sob MIT (ver `LICENSE` na raiz do projeto).
