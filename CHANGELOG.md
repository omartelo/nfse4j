# Changelog

Todos os releases relevantes deste projeto. O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e o versionamento segue [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

## [0.6.1] - 2026-09-30

### Corrigido
- **DPS e pedido de evento com CNPJ alfanumérico agora falham na montagem, com mensagem clara.** O
  leiaute da NFS-e Nacional ainda exige CNPJ numérico: `TSCNPJ` é `[0-9]{14}` e `TSIdDPS` é
  `DPS[0-9]{42}` no XSD v1.01 versionado (`tiposSimples_v1.01.xsd`) e nos pacotes oficiais de
  gov.br/nfse conferidos em 30/09/2026 (produção: `nfse-esquemas_xsd-v1-01-20260209.zip`; produção
  restrita da reforma: `nfse-esquemas_xsd-rtc-v1-00-20251210.zip`). Desde a 0.5.0 o SDK preservava as
  letras no Id da DPS e no autor do evento, e a validação por XSD da 0.6.0 recusava o próprio documento
  gerado com mensagem genérica de schema (`... is not facet-valid with respect to pattern
  'DPS[0-9]{42}'`). Agora o `DpsIdGenerator` (emissão e reemissão) e os pedidos de cancelamento,
  análise fiscal e manifestação lançam `IllegalArgumentException`: "CNPJ alfanumérico ainda não é
  aceito pelo leiaute da NFS-e Nacional (TSCNPJ [0-9]{14}, XSD v1.01)". A normalização que preserva as
  letras continua lá: quando o leiaute mudar, basta trocar o XSD e remover essa checagem. O
  `CertificadoA1` segue reconhecendo CNPJ alfanumérico no subject.

## [0.6.0] - 2026-09-30

### Adicionado
- **Eventos de uma NFS-e pela chave de acesso** (distribuição de DF-e do ADN) na CLI e no MCP:
  `NfseRunner.consultarEventosDfe`, ferramenta `consultar_eventos_dfe` e comando
  `consultar-eventos-dfe`. Devolvem só o resumo; o XML de cada evento sai por `consultar_dfe` pelo NSU.
- **Emissão de NFS-e substituta.** A DPS ganha o grupo `subst` (`Dps.Substituicao`: chave da nota
  substituída, código e descrição do motivo), gerado na posição do XSD v1.01 e com os domínios
  validados na construção. A SEFIN cancela a nota original (evento 105102) ao autorizar a substituta.
  SDK: `EmitirNfseRequest.substituicao` / `withSubstituicao`. MCP: `dados.substituicao` em
  `emitir_nfse`. CLI: `emitir --substituir-chave --substituicao-motivo-codigo
  --substituicao-motivo-descricao`. Em produção vale a mesma confirmação explícita da emissão.
- **Informações complementares da DPS (`serv/infoCompl/xInfComp`).** `Dps.Servico` ganha
  `informacoesComplementares` (`Dps.InformacoesComplementares`), gerado como último grupo de `serv`, na
  ordem do XSD v1.01, e exibido pelo DANFSe em "Informações complementares". O construtor valida o
  domínio do `TSDescInfCompl` (1 a 2000 caracteres de U+0020 a U+00FF, sem quebra de linha nem espaço
  nas pontas). Os construtores anteriores de `Dps.Servico` continuam valendo. SDK:
  `EmitirNfseRequest.ServicoRequest.informacoesComplementares`; no JSON da CLI e no `dados` do MCP,
  `servico.informacoesComplementares`. Na reemissão a partir de
  nota de exemplo, o `xInfComp` passa a ser lido e mantido; `idDocTec`, `docRef`, `xPed` e `gItemPed`
  seguem sem modelo e continuam recusados.
- **Validação contra o XSD oficial antes do envio.** A DPS (em `emitir`, `emitirDetalhado` e
  `emitirXml`) e os pedidos de registro de evento (cancelamento, análise fiscal e manifestação) são
  validados contra os XSDs da NFS-e Nacional v1.01, já assinados, antes de ir para a SEFIN. Violação
  lança `XmlSchemaValidationException`, com linha, coluna, elemento e mensagem do schema; nada é
  enviado. `XmlSchemaValidator` é público para validar DPS e pedido de registro de evento avulsos.
- **Distribuição de DF-e do ADN**: descobre as NFS-e em que o CNPJ do certificado é emitente, tomador
  ou intermediário (as notas emitidas contra o CNPJ). SDK: `DistribuicaoDfeClient` (lote a partir do
  NSU, NSU único, eventos por chave e `drenar` com trava de lotes). MCP: `distribuir_dfe` (resumo) e
  `consultar_dfe` (XML por NSU). CLI: `distribuir-dfe` e `consultar-dfe`.
- **Consulta de parâmetros municipais do ADN**: convênio, alíquota, histórico de alíquotas,
  benefício, regimes especiais e retenções. No SDK via `Nfse.parametrosMunicipais()` e `NfseRunner`
  (respostas tipadas, alíquotas em `BigDecimal`); no MCP as ferramentas `consultar_aliquota`,
  `consultar_historico_aliquotas`, `consultar_convenio_municipio`, `consultar_beneficio_municipal`,
  `consultar_regimes_especiais` e `consultar_retencoes_municipio`; na CLI os comandos `aliquota`,
  `historico-aliquotas`, `convenio`, `beneficio`, `regimes-especiais` e `retencoes`.
- **Eventos de NFS-e do contribuinte além do cancelamento.** Solicitação de análise fiscal para
  cancelamento (101103) e manifestação de NFS-e: confirmação e rejeição por prestador, tomador e
  intermediário (202201, 203202, 204203, 202205, 203206, 204207). No SDK:
  `NfseRunner.solicitarAnaliseFiscalCancelamento`, `NfseRunner.manifestar` e
  `ContribuinteService.registrarEvento(PedidoRegistroEvento)`; no MCP: `solicitar_analise_fiscal_cancelamento`
  e `manifestar_nfse`; no CLI: `solicitar-analise-cancelamento` e `manifestar`. Em produção, exigem a
  mesma confirmação explícita das outras operações de escrita.

### Corrigido
- **Reemissão a partir de nota de exemplo gerava DPS sem `totTrib`.** O leitor descartava
  `vTotTrib` e `pTotTrib` do XML de exemplo, e o `<trib>` saía sem `totTrib`, obrigatório no XSD da
  DPS v1.01, então a SEFIN recusaria a nota. O `Dps.Tributacao` passa a modelar os dois grupos
  (federal, estadual e municipal) e eles são lidos e gerados na ordem do XSD.
- **`Dps.Tributacao` aceitava mais de um grupo em `totTrib`.** O XSD da DPS permite um só
  (`vTotTrib`, `pTotTrib`, `indTotTrib` ou `pTotTribSN`). Agora o construtor lança
  `IllegalArgumentException` dizendo quais campos vieram juntos. Na emissão, informar
  `indicadorTotalTributos` junto com `percentualTotalTributosSimplesNacional` passa a falhar assim, em
  vez de gerar um XML que a SEFIN recusaria.
- **Reemissão a partir de nota de exemplo perdia grupos da DPS em silêncio.** O leitor ignorava
  `cMotivoEmisTI`, `chNFSeRej`, `subst`, `interm`, `IBSCBS`, `cPaisPrestacao`, `cIntContrib`, `comExt`,
  `obra`, `atvEvento` e `infoCompl`, e a nota saía sem eles. Agora a leitura falha com
  `DpsXmlException` nomeando os grupos presentes.
- **Reemissão a partir de nota de exemplo saía com tributação diferente da original.** O leitor
  descartava `vReceb`, `vDescCondIncond`, `vDedRed` (`pDR`/`vDR`), `cPaisResult`, `tpImunidade`,
  `exigSusp`, `BM`, `pAliq` e o grupo `tribFed` inteiro (PIS/COFINS e retenções de CP, IRRF e CSLL).
  O XSD aceitava a nota sem eles, então a SEFIN podia autorizá-la com a tributação errada. O
  `Dps.Valores` e o `Dps.Tributacao` passam a modelar esses campos, que são lidos e gerados na ordem
  do XSD.
- **Troca de valor na reemissão copiava valores em R$ calculados sobre o valor antigo.** Com
  `valorServico` sobrescrito, `vDR`, descontos, `vRedBCBM`, PIS/COFINS, retenções federais e `vTotTrib`
  do exemplo ficariam incoerentes com o novo valor. Agora o `DpsReemissao` recusa a troca listando os
  campos. Percentuais seguem copiados, inclusive o `pDR`, que antes se perdia na troca.
- **Reemissão a partir de nota de exemplo perdia dados do prestador e do tomador.** O leitor
  descartava `NIF`, `cNaoNIF`, `CAEPF`, o nome e o endereço do prestador, a `IM` do tomador e o
  endereço no exterior (`endExt`). O `Dps.Prestador`, o `Dps.Tomador` e o `Dps.Endereco` passam a
  modelar esses campos, lidos e gerados na ordem do XSD. Com tomador sobrescrito, nada do tomador do
  exemplo passa para o novo.
- **Reemissão a partir de nota de exemplo com documentos de dedução (`vDedRed/documentos`).** O
  `Dps.DeducaoReducao` passa a modelar a lista de `docDedRed` (identificação do documento, tipo,
  descrição, data, valores e fornecedor), lida e gerada na ordem do XSD, e o exemplo deixa de ser
  recusado. Com `valorServico` sobrescrito, a reemissão recusa a troca listando `vDedutivelRedutivel` e
  `vDeducaoReducao`.

### Alterado
- **O `nfse4j-core` passa a depender do Gson** (Apache-2.0), para ler o JSON das APIs do ADN. Deixa
  de ser zero-dependências: quem consome o core recebe o Gson por transitividade.
- `EndpointResolver.adn(ambiente)` resolve a base do ADN (`adn.nfse.gov.br` e produção restrita).
- O workflow de release passa a publicar o `nfse4j-core` e o `nfse4j-danfse` no Maven Central ao
  receber uma tag `v*`, depois de conferir que a tag bate com a versão do `gradle.properties`.

## [0.5.0] - 2026-09-30

### Corrigido
- **CNPJ alfanumérico (IN RFB 2.229/2024) perdia as letras.** A normalização removia as letras antes
  da checagem de tamanho, e o `CertificadoA1` não os reconhecia no subject. Agora só a máscara é
  removida. CPF e CNPJ numéricos seguem como antes. Correção posterior: isso **não** tornou possível
  emitir nem cancelar nota para esses CNPJs, porque o leiaute da NFS-e Nacional (XSD v1.01) ainda
  exige CNPJ numérico; ver [Não lançado].

### Alterado
- **Projeto renomeado para `nfse4j`** (antes `nfse-java-mcp`), mantido a partir deste fork. Mudanças
  incompatíveis para quem consome:
  - groupId `io.github.rafael-matos-dev` → `io.github.omartelo`.
  - Módulos: `nfse-sdk` → `nfse4j-core`, `nfse-danfse` → `nfse4j-danfse`, `nfse-cli` → `nfse4j-cli`,
    `nfse-mcp` → `nfse4j-mcp` (jars das releases acompanham).
  - Pacotes: `br.com.nfse.sdk` → `io.github.omartelo.nfse4j.core`; `br.com.nfse.{danfse,cli,mcp}` →
    `io.github.omartelo.nfse4j.{danfse,cli,mcp}`.
  - `verAplic` enviado na DPS/eventos e `serverInfo` do MCP passam a ser `nfse4j`.
- **Build migrado de Maven para Gradle** (Kotlin DSL, wrapper 9.7.1, version catalog em
  `gradle/libs.versions.toml`). Fat jars em `nfse4j-{cli,mcp}/build/libs/`; publicação no Central via
  `./gradlew publishToMavenCentral`.

- **DANFSe reescrito no modelo do Anexo I da NT 008/2026 v1.02** ("DANFSe v2.0"). O `nfse4j-danfse`
  passa a ser o código do [xml-danfse-br](https://github.com/rzmt/xml-danfse-br) 0.9.0 (que tinha
  sido extraído deste módulo na 0.4.5) com as correções de conformidade do PR #2 do xml-danfse-br.
  Conformidade item a item em `docs/nt008-checklist.md`. Principais ganhos:
  - Campos antes fixos passam a ser lidos do XML: intermediário, regime especial, tributação federal
    (IRRF, contribuição previdenciária, CSLL, PIS/COFINS) e totais da Lei 12.741/2012.
  - Novos blocos e campos: destinatário (NT 009), NIF e endereço no exterior, imunidade, suspensão,
    benefício municipal, deduções, BC/alíquota/ISSQN apurado, informações complementares unificadas.
  - Leitor do XML navega por filho direto, sem confundir `valores`/`IBSCBS` do `infNFSe` e do `infDPS`.
  - Fonte Liberation Sans embutida, QR Code de 1,52 cm e margens conforme a NT.
  - `DanfseGenerator.gerarPdf(xml)` infere o ambiente pelo `tpAmb`.
  - Testes golden (comparação visual do PDF) e de geometria; `-Dgolden.update=true` regenera.

  Mudanças incompatíveis no `nfse4j-danfse`: os records de `Danfse` foram reestruturados nos blocos da
  NT (`DanfseGenerator` e `DanfseConfig` mantêm as assinaturas); descrições de código passam a ser as
  do leiaute, acentuadas; a propriedade `nfse.danfse.ibge` vira `nfse4j.danfse.ibge`; o módulo deixa de
  depender do `nfse4j-core`, que não usava.

### Removido
- **Download do DANFSe pela API do ADN**, que foi desligada: `DanfseService`,
  `DanfseClient`, `Nfse.danfse()`, `NfseRunner.baixarPdf`/`PdfResult`, `NfseBinaryResponse`,
  `NfseHttpClient.getBytes`, `EndpointResolver.danfse()`/`withEndpoints(...)`, o comando `pdf` do
  CLI e a ferramenta `baixar_danfse` do MCP. Use a geração local (`nfse4j-danfse`, `danfse --xml`
  no CLI, `gerar_danfse` no MCP).

## [0.4.5] - 2026-06-25

### Adicionado
- DANFSe: o logo do emitente (e o brasão) agora é **redimensionado automaticamente** (máx. 600 px no
  maior lado, proporção preservada) ao ser embutido — não é mais preciso redimensionar a imagem antes;
  um logo grande não infla o PDF. Aceita PNG/JPG/GIF/SVG (SVG mantém o vetor).

## [0.4.4] - 2026-06-25

### Corrigido
- **DANFSe estampava "NFS-e SEM VALIDADE JURÍDICA" em notas de PRODUÇÃO.** O indicador de ambiente
  passou a ser o `tpAmb` da DPS (1=produção, 2=homologação, conforme o XSD), e não o `ambGer`
  ("ambiente gerador") — uma nota de produção pode ter `ambGer=2` e `tpAmb=1`, e a leitura anterior
  a marcava como homologação.
- **Fuso horário**: `dhEmi` (DPS) e `dhEvento` (cancelamento) agora são sempre formatados em
  `America/Sao_Paulo`, independentemente do fuso do servidor (evita offset errado, ex.: `Z`/UTC).

## [0.4.3] - 2026-06-24

### Adicionado
- DANFSe: opção de incluir o **logo do emitente (prestador)** no cabeçalho, ao lado do logo da NFS-e.
  `DanfseConfig.comLogoEmitente(...)` / `DanfseGenerator.dataUriImagem(arquivo)`; CLI `danfse --logo-emitente`;
  MCP `gerar_danfse` argumento `logoEmitenteArquivo`. Limitado por CSS para não quebrar o layout
  (tamanho sugerido ~300×120 px).

## [0.4.2] - 2026-06-24

### Alterado
- Layout do DANFSe mais próximo do oficial: fundo branco, **sem linhas verticais** entre colunas,
  apenas linhas horizontais finas entre os itens e **faixas de seção em cinza** marcando claramente
  cada parte. Descrições de tributação (nacional/municipal) truncadas em ~2 linhas com reticências.

## [0.4.1] - 2026-06-24

### Adicionado
- DANFSe: descrição do **Código de Tributação Municipal** (`xTribMun`) e o **NBS** (`cNBS`/`xNBS`),
  que vêm no XML mas não eram exibidos; rótulos do documento com **acentuação** correta.
- **Resolução do nome do município** dos endereços: usa os pares código→nome do próprio XML
  (prestador/emissão) e, para municípios de fora (ex.: tomador em outra cidade), consulta a **API do
  IBGE** (cache em memória, timeout curto, *fallback* gracioso ao código; desative com
  `-Dnfse.danfse.ibge=false`).

## [0.4.0] - 2026-06-23

### Adicionado
- DANFSe muito mais próximo do oficial: **logo oficial da NFS-e** no cabeçalho (CC BY-ND, ver
  `nfse-danfse/NOTICE.md`), aviso **"NFS-e SEM VALIDADE JURÍDICA"** em vermelho quando a nota é de
  homologação (lê `ambGer`/`tpAmb` do XML), cabeçalho em 3 colunas e layout mais compacto.
- `DanfseConfig` — identificação opcional do município (brasão + contato da prefeitura), já que esses
  dados não vêm no XML nem há API pública que os forneça. `DanfseGenerator.gerarPdf(xml, producao, config[, saida])`.

### Corrigido
- Cancelamento (evento 101101): o `nPedRegEvento` foi **removido** do Id do pedido de evento conforme
  o padrão `TSIdPedRegEvt` (`PRE[0-9]{56}` = `PRE` + chave de 50 + tipo de evento 6). Anexar o número
  do pedido gerava rejeição no esquema XML da SEFIN. O `numeroPedido` segue no modelo, mas não compõe o Id.

## [0.3.0] - 2026-06-23

### Adicionado
- **Módulo `nfse-danfse`**: geração **local** do PDF do DANFSe a partir do XML autorizado da NFS-e,
  sem depender da API oficial (desligada em 1º/07/2026 — NT SE/CGNFS-e nº 008/2026). Render HTML/CSS
  → PDF (OpenHTMLtoPDF) + QR Code (ZXing); layout NT 008 e seção **IBS/CBS** (NT 009) condicional.
  Publicado no Maven Central como `io.github.rafael-matos-dev:nfse-danfse`.
- MCP: ferramenta **`gerar_danfse`** (aceita o XML, um arquivo ou o `nfseXmlGZipB64`).
- CLI: subcomando **`danfse --xml nota.xml --saida danfse.pdf`**.

### Alterado
- `baixar_danfse` (MCP) marcada como **legado** (válida até 2026-07-01); prefira `gerar_danfse`.

## [0.2.0] - 2026-06-23

### Adicionado
- Workflow de release (GitHub Actions): ao publicar uma tag `v*`, anexa `nfse-mcp.jar` e
  `nfse-cli.jar` a um GitHub Release.
- `CHANGELOG.md` e badges (Maven Central, CI, licença) no README.
- Testes para `EmitirNfseRequest`, `DpsReemissao` e `NfseRunner`.

### Alterado
- Bloqueia emissão/cancelamento com **certificado A1 expirado** (falha cedo, com mensagem clara).
- `NfseRunner.emitir` exige CPF/CNPJ extraível do certificado quando o prestador não informa um.
- `EmitirNfseRequest` valida `numero > 0`.
- Servidor MCP: mensagens de erro por campo ao converter argumentos numéricos/data.

## [0.1.0] - 2026-06-23

### Adicionado
- `nfse-sdk` — motor Java sem dependências de runtime: emissão de DPS v1.01 (assinatura
  enveloped RSA-SHA256), consulta, cancelamento (evento 101101) e download de DANFSe; mTLS via
  certificado A1. Publicado no Maven Central como `io.github.rafael-matos-dev:nfse-sdk`.
- `DpsXmlReader` + `DpsReemissao` — emitir a partir de uma nota de exemplo, trocando só o que muda.
- `nfse-mcp` — servidor MCP (stdio) com as ferramentas `validar_certificado`, `emitir_nfse`,
  `emitir_de_exemplo`, `consultar_nfse`, `cancelar_nfse`, `baixar_danfse`.
- `nfse-cli` — CLI para humanos e agentes que rodam shell.
- Homologação por padrão; produção exige confirmação explícita.

[Não lançado]: https://github.com/omartelo/nfse4j/compare/v0.6.1...HEAD
[0.6.1]: https://github.com/omartelo/nfse4j/releases/tag/v0.6.1
[0.6.0]: https://github.com/omartelo/nfse4j/releases/tag/v0.6.0
[0.5.0]: https://github.com/omartelo/nfse4j/releases/tag/v0.5.0
[0.4.5]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.4.5
[0.4.4]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.4.4
[0.4.3]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.4.3
[0.4.2]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.4.2
[0.4.1]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.4.1
[0.4.0]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.4.0
[0.3.0]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.3.0
[0.2.0]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.2.0
[0.1.0]: https://github.com/rafael-matos-dev/nfse-java-mcp/releases/tag/v0.1.0
