# nfse4j

[![Maven Central](https://img.shields.io/maven-central/v/io.github.omartelo/nfse4j-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.omartelo/nfse4j-core)
[![CI](https://github.com/omartelo/nfse4j/actions/workflows/ci.yml/badge.svg)](https://github.com/omartelo/nfse4j/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Emita NFS-e Nacional pelo seu agente de IA — ou direto do seu código Java.**

No coração, `nfse4j` é um **SDK Java** (uma única dependência de runtime, o Gson) para a **NFS-e Nacional** (padrão nacional brasileiro da Nota Fiscal de Serviço eletrônica). Sobre esse motor vêm duas portas de entrada: um **servidor MCP**, para um agente de IA emitir notas conversando (a pessoa só aponta o certificado, dá os dados do tomador — ou uma nota de exemplo — a descrição e o valor), e uma **CLI**, para o terminal. As três camadas usam o mesmo núcleo.

> ⚠️ **Documento fiscal real.** O padrão é **homologação** (produção restrita, ambiente de teste). Emitir em **produção** cria um documento fiscal com efeito tributário real e exige confirmação explícita (`confirmarProducao=true` / `--confirmar-producao`).

## Por que existe

As bibliotecas Java de NFS-e existentes atendem padrões municipais antigos ou são provas de conceito. Faltava um **motor Java limpo e embarcável** para o padrão **nacional** — com uma única dependência de runtime — e, por cima dele, um **servidor MCP** (1 runtime só) que deixa um agente de IA emitir notas conversando. É isso que este projeto entrega.

## Arquitetura: um motor, três portas

O `nfse4j-core` é o motor. O `nfse4j-mcp` e o `nfse4j-cli` são camadas finas por cima dele — não reimplementam nada, só expõem o SDK para públicos diferentes.

```
   agente de IA              terminal              seu código Java
        │                       │                         │
  ┌─────▼──────┐          ┌─────▼──────┐                  │
  │ nfse4j-mcp │          │ nfse4j-cli │                  │
  │ (serv MCP) │          │   (CLI)    │                  │
  └─────┬──────┘          └─────┬──────┘                  │
        └───────────┬───────────┴─────────────────────────┘
                    │
        ┌───────────┴────────────┐
        │                        │
┌───────▼───────┐       ┌────────▼────────┐
│  nfse4j-core  │       │  nfse4j-danfse  │  ← gera o PDF do DANFSe local
│  (só Gson)    │       │   (HTML→PDF)    │
└───────────────┘       └─────────────────┘
   ↑ Maven Central
```

| Módulo | O que é | Para quem | Distribuição |
|--------|---------|-----------|--------------|
| **`nfse4j-core`** | a biblioteca/motor (emissão, consulta, cancelamento) | devs que integram em Java | Maven Central |
| **`nfse4j-danfse`** | gera o PDF do DANFSe localmente a partir do XML | devs / MCP / CLI | Maven Central |
| **`nfse4j-mcp`** | servidor MCP sobre o SDK | agentes de IA | jar no GitHub Releases |
| **`nfse4j-cli`** | CLI sobre o SDK | humanos e scripts | jar no GitHub Releases |

## Requisitos

- Java 21+
- Certificado digital **A1** (`.pfx`/`.p12`) do prestador

## Instalação

A forma mais rápida: baixe os jars prontos da [última release](https://github.com/omartelo/nfse4j/releases/latest) — `nfse4j-mcp-<versao>.jar` (servidor MCP) e `nfse4j-cli-<versao>.jar` (CLI). Não precisa compilar.

Ou compile do código:

```bash
./gradlew build
```

Gera os jars executáveis:
- `nfse4j-mcp/build/libs/nfse4j-mcp.jar` — servidor MCP
- `nfse4j-cli/build/libs/nfse4j-cli.jar` — CLI

## Uso via MCP (destaque)

Registre o servidor no seu cliente MCP. Exemplo de `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "nfse": {
      "command": "java",
      "args": ["-jar", "/caminho/para/nfse4j-mcp.jar"],
      "env": {
        "NFSE_CERT_PATH": "/caminho/para/seu-certificado.pfx",
        "NFSE_CERT_PASSWORD": "sua-senha"
      }
    }
  }
}
```

Depois, é só conversar com o agente: _"Emita uma NFS-e de R$ 100 para o CPF 111.444.777-35, descrição 'consultoria', em homologação."_ — o agente chama a ferramenta `emitir_nfse`.

### Ferramentas expostas

| Ferramenta | O que faz |
|-----------|-----------|
| `validar_certificado` | Mostra CNPJ/CPF, emissor e validade do certificado A1. |
| `emitir_nfse` | Emite uma NFS-e a partir dos dados informados. |
| `emitir_de_exemplo` | Reaproveita uma nota anterior (XML), trocando só tomador/descrição/valor. |
| `consultar_nfse` | Consulta uma NFS-e pela chave de acesso. |
| `cancelar_nfse` | Cancela uma NFS-e (evento 101101). |
| `solicitar_analise_fiscal_cancelamento` | Pede ao município a análise fiscal para cancelar uma NFS-e (evento 101103). |
| `manifestar_nfse` | Confirma ou rejeita uma NFS-e como prestador, tomador ou intermediário (eventos 202201, 203202, 204203, 202205, 203206, 204207). |
| `distribuir_dfe` | Lista as NFS-e e eventos em que o CNPJ do certificado é emitente, tomador ou intermediário (distribuição de DF-e do ADN), a partir de um NSU. Devolve só o resumo. |
| `consultar_dfe` | Traz o XML de um documento da distribuição pelo NSU. |
| `consultar_eventos_dfe` | Lista os eventos de uma NFS-e pela chave de acesso (distribuição de DF-e do ADN). Devolve só o resumo; o XML sai por `consultar_dfe`. |
| `gerar_danfse` | **Gera o PDF do DANFSe localmente** a partir do XML da NFS-e. |
| `consultar_aliquota` | Alíquota de ISS de um serviço no município (ADN). Use antes de emitir. |
| `consultar_historico_aliquotas` | Todas as alíquotas que o serviço já teve no município, com vigências. |
| `consultar_convenio_municipio` | Adesão do município ao ambiente e ao emissor nacional. |
| `consultar_beneficio_municipal` | Benefício fiscal municipal pelo número. |
| `consultar_regimes_especiais` | Regimes especiais de tributação aceitos para o serviço. |
| `consultar_retencoes_municipio` | Regras de retenção de ISS do município. |

As consultas de parâmetros municipais recebem o código IBGE do município (7 dígitos), o código de tributação nacional com 6 ou 9 dígitos (`010101` ou `01.01.01.000`) e `competencia` opcional (`YYYY-MM-DD`, padrão hoje). Quando o município não parametrizou o dado, a resposta volta com os campos nulos e a `mensagem` do ADN.

Todas aceitam `ambiente` (`homologacao` por padrão) e, nas operações de escrita, `confirmarProducao` (obrigatório `true` para produção). O certificado vem das envs `NFSE_CERT_PATH`/`NFSE_CERT_PASSWORD` ou dos parâmetros da ferramenta.

## Uso via CLI

```bash
JAR=nfse4j-cli/build/libs/nfse4j-cli.jar
export NFSE_CERT_PATH=/caminho/cert.pfx NFSE_CERT_PASSWORD=senha

java -jar $JAR cert --json
java -jar $JAR emitir --arquivo docs/exemplos/emitir.request.json --json
java -jar $JAR emitir-de-exemplo --exemplo nota-antiga.xml --numero 123 \
  --tomador-cpf 11144477735 --tomador-nome "Fulano" --descricao "Consultoria" --valor 250.00
java -jar $JAR consultar --chave CHAVE_DA_NFSE --json
java -jar $JAR solicitar-analise-cancelamento --chave CHAVE_DA_NFSE --motivo-codigo 2 \
  --motivo-descricao "Servico nao foi prestado"
java -jar $JAR manifestar --chave CHAVE_DA_NFSE --tipo confirmacao-tomador
java -jar $JAR manifestar --chave CHAVE_DA_NFSE --tipo rejeicao-tomador --motivo-codigo 1
java -jar $JAR danfse --xml nota.xml --saida danfse.pdf
java -jar $JAR distribuir-dfe --nsu 0 --json      # notas emitidas contra o CNPJ (resumo)
java -jar $JAR consultar-dfe --nsu 42 --json      # XML de um documento da distribuição
java -jar $JAR consultar-eventos-dfe --chave CHAVE_DA_NFSE --json  # eventos da nota (resumo)
java -jar $JAR aliquota --municipio 3550308 --servico 01.01.01.000 --json
# Producao (documento fiscal REAL) exige a flag:
java -jar $JAR emitir --arquivo nota.json --ambiente producao --confirmar-producao
```

## Uso como SDK

Disponível no Maven Central:

```xml
<dependency>
  <groupId>io.github.omartelo</groupId>
  <artifactId>nfse4j-core</artifactId>
  <version>0.4.5</version>
</dependency>
```

```java
var cert = NfseRunner.carregarCertificado("/caminho/cert.pfx", "senha");
var resultado = NfseRunner.emitir(
    request,                 // EmitirNfseRequest
    Ambiente.HOMOLOGACAO,
    cert,
    false);                  // confirmarProducao
System.out.println(resultado.chaveAcesso());

// Alíquota de ISS vigente hoje (competência null) para o serviço 01.01.01.000 em São Paulo
var aliquotas = NfseRunner.consultarAliquota("3550308", "010101", null, Ambiente.HOMOLOGACAO, cert);
aliquotas.aliquotas().get("01.01.01.000").forEach(a -> System.out.println(a.aliquota()));
```

Os parâmetros municipais também estão em `new Nfse(context).parametrosMunicipais()` (convênio, alíquota, histórico, benefício, regimes especiais e retenções), com respostas tipadas em `ParametrosMunicipais`. Status 404 do ADN volta como resposta com a `mensagem`; outros erros lançam `ParametrosMunicipaisException` com o status HTTP.

O `nfse4j-core` tem uma única dependência de runtime, o **Gson** (JSON das APIs do ADN). O resto vem do JDK (HTTP via `java.net.http`, assinatura via `javax.xml.crypto.dsig`, mTLS via `SSLContext` do A1).

## Notas emitidas contra o CNPJ (distribuição de DF-e do ADN)

A distribuição do ADN entrega, em ordem de NSU, os documentos em que o CNPJ do certificado é
**emitente, tomador ou intermediário**: é por ela que se descobrem as NFS-e emitidas *contra* o CNPJ.
A autenticação é o próprio certificado (mTLS), sem token.

```java
var context = NfseContext.builder().ambiente(Ambiente.PRODUCAO).certificado(cert).build();
var distribuicao = new DistribuicaoDfeClient(context);

long ultimoNsu = 0;                // recupere do seu armazenamento; 0 começa do início
DrenagemDfe drenagem = distribuicao.drenar(ultimoNsu, null, 50); // cnpjConsulta, trava de lotes
for (var documento : drenagem.documentos()) {
    documento.tipoDocumento();     // NFSE, EVENTO, DPS, CNC...
    documento.chaveAcesso();       // 50 dígitos
    documento.xml();               // XML já desempacotado (o ADN entrega em gzip+base64)
}
// guarde drenagem.ultimoNsu(); concluida() == false quer dizer que a trava parou antes do fim
```

O `drenar` cuida das armadilhas do serviço: o ADN não informa teto de NSU (a drenagem termina em
`NENHUM_DOCUMENTO_LOCALIZADO`), o lote seguinte pode repetir o NSU de partida, e `REJEICAO` lança
`DistribuicaoDfeException` em vez de parecer lote vazio. Para controlar lote a lote, use
`distribuir(nsu, cnpjConsulta)`, `consultarNsu(nsu, cnpjConsulta)` e `consultarEventos(chave)`, que
devolvem o `LoteDistribuicaoDfe` tipado; HTTP 400 e 404 são respostas de negócio (ex.: `E2215`,
`E2230`) e chegam em `erros()`, não como exceção.

`cnpjConsulta` é opcional (`null` usa o CNPJ do certificado); quando informado, precisa ter a mesma
raiz do certificado e ir sem máscara (aceita CNPJ alfanumérico).

O `NfseRunner.distribuirDfe` / `consultarDfe` / `consultarEventosDfe` são a versão usada pela CLI e
pelo MCP: resumo sem XML, até 10 lotes por chamada, eventos de uma chave, e o XML de um NSU sob demanda.

## Emitir a partir de uma nota de exemplo

O fluxo mais simples para quem já emite: aponte uma nota anterior (XML de DPS ou NFS-e) e troque só o que muda. O `DpsXmlReader` lê o exemplo, o `DpsReemissao` aplica os overrides (novo número, tomador, descrição, valor) e regenera o `Id` da DPS.

Para não emitir uma nota diferente da original sem aviso, a reemissão recusa:
- exemplo com grupo da DPS que o modelo não cobre (`subst`, `interm`, `IBSCBS`, `comExt`, `obra`, `atvEvento`, `infoCompl`, entre outros);
- troca de valor quando o exemplo tem valores em R$ calculados sobre o valor original (`vDR`, valores dos documentos de dedução, descontos, PIS/COFINS, retenções federais, `vTotTrib` etc.). Percentuais são copiados.

Trocar o tomador substitui o grupo `toma` inteiro: nada do tomador do exemplo (IM, endereço, NIF etc.) passa para o novo.

## DANFSe (PDF) — geração local

O módulo `nfse4j-danfse` gera o PDF do DANFSe **localmente** a partir do XML da NFS-e (o `<NFSe>` que a SEFIN devolve na emissão, no campo `nfseXmlGZipB64`).

```java
// XML autorizado da NFS-e (string)
byte[] pdf = DanfseGenerator.gerarPdf(nfseXml, /* producao */ false, Path.of("danfse.pdf"));
```

CLI: `java -jar nfse4j-cli.jar danfse --xml nota.xml --saida danfse.pdf`
MCP: ferramenta `gerar_danfse` (aceita o XML, um arquivo, ou o `nfseXmlGZipB64`).

O layout segue o modelo do **Anexo I da NT 008/2026 v1.02** ("DANFSe v2.0"); o estado item a item está em [`docs/nt008-checklist.md`](docs/nt008-checklist.md). Inclui o logo oficial da NFS-e, o aviso **"NFS-e SEM VALIDADE JURÍDICA"** em homologação, a seção **IBS/CBS** (NT 009) quando presente no XML e a fonte Liberation Sans (métrica da Arial exigida pela NT) embutida no PDF. Render via HTML/CSS → PDF (OpenHTMLtoPDF) + QR Code (ZXing).

`DanfseGenerator.gerarPdf(nfseXml)` infere o ambiente pelo `tpAmb` do próprio XML.

O nome do município dos endereços é resolvido a partir do próprio XML quando possível; para municípios de fora (ex.: tomador em outra cidade), consulta a **API do IBGE** (com cache e *fallback* gracioso ao código). Para gerar 100% offline, use `-Dnfse4j.danfse.ibge=false`.

### Logo do emitente (prestador)

Para incluir o **logo da empresa** no cabeçalho, ao lado do logo oficial da NFS-e:

```java
var cfg = DanfseConfig.comLogoEmitente(DanfseGenerator.dataUriImagem(Path.of("logo.png")));
byte[] pdf = DanfseGenerator.gerarPdf(nfseXml, false, cfg, Path.of("danfse.pdf"));
```

CLI: `danfse --xml nota.xml --logo-emitente logo.png` · MCP: argumento `logoEmitenteArquivo`.

Aceita PNG, JPG, GIF e SVG. **Não precisa redimensionar antes**: imagens grandes são reduzidas automaticamente (máx. 600 px no maior lado, proporção preservada) para não inflar o PDF; o CSS ainda limita a altura no cabeçalho. Para melhor resultado, use PNG com fundo transparente.

### Brasão e contato da prefeitura

Não vêm no XML (e não há API pública que os forneça); são opcionais via `DanfseConfig` (todos os campos são opcionais — o último é o logo do emitente):

```java
var cfg = new DanfseConfig(
    "Belo Horizonte",        // nome do município (sobrepõe o do XML)
    brasaoDataUri,           // brasão (data URI) — use DanfseGenerator.dataUriImagem(...)
    "Secretaria de Fazenda", // departamento
    "(31) 3000-0000",        // telefone
    "nfse@pbh.gov.br",       // e-mail
    logoEmitenteDataUri);    // logo do emitente (ou null)
byte[] pdf = DanfseGenerator.gerarPdf(nfseXml, false, cfg, Path.of("danfse.pdf"));
```

## Ambientes e segurança

- **Homologação é o padrão.** Use para testar à vontade.
- **Produção cria documento fiscal real** e exige confirmação explícita.
- **Nunca** comite certificados (`.pfx`/`.p12`), `.env` com senha, nem os XMLs de notas emitidas (contêm dados reais de tomador/prestador). O `.gitignore` já bloqueia esses arquivos.

## Limitações

- DANFSe: o brasão e o contato da prefeitura dependem de `DanfseConfig` (não há API pública para obtê-los); a seção IBS/CBS é "best-effort" até haver nota real com reforma para validar.
- A DPS e os pedidos de registro de evento (cancelamento, análise fiscal e manifestação) são validados contra o XSD oficial v1.01 antes do envio. O envio genérico de evento (`registrarEventoXml`) ainda não é validado.
- Geração de classes JAXB a partir dos XSDs oficiais: próximo passo.

## Licença

[MIT](LICENSE) © Rafael Matos (projeto original, `nfse-java-mcp`), omartelo

O `nfse4j-core` depende só do **Gson** (Apache-2.0). O `nfse4j-danfse` depende de **OpenHTMLtoPDF** (LGPL-2.1), **Apache PDFBox** (Apache-2.0) e **ZXing** (Apache-2.0) — todas dependências de runtime, sem afetar a licença MIT deste código. Os fat jars do CLI e do MCP agregam essas e outras bibliotecas; a lista e as licenças estão em [`THIRD-PARTY.md`](THIRD-PARTY.md).
