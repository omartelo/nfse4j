# Contribuindo

Contribuições são bem-vindas! Algumas diretrizes:

## Desenvolvimento

- Java 21, UTF-8, 4 espaços de indentação. Pacote raiz `io.github.omartelo.nfse4j`.
- O build usa toolchain Java 21: se o seu JDK padrão for mais novo, o 21 precisa estar instalado (o Gradle o encontra sozinho). Assim os testes locais rodam no mesmo JDK do CI.
- Mantenha o `nfse4j-core` com **uma única dependência de runtime**, o Gson. Qualquer outra passa por discussão antes.
- Rode os testes antes de abrir PR: `./gradlew test`.
- Testes não devem exigir certificado real — gere material de teste com `TestPkcs12Factory`.

## Build e teste

```bash
./gradlew build                # build + testes de todos os modulos
./gradlew :nfse4j-core:test    # so o SDK
```

## Release

1. Atualize `version` no `gradle.properties` e mova as entradas de "Não lançado" do `CHANGELOG.md` para a nova versão.
2. Commite na `main` e envie a tag: `git tag -a vX.Y.Z -m vX.Y.Z && git push origin main vX.Y.Z`.

O workflow `Release` confere se a tag bate com o `gradle.properties`, roda os testes, publica o `nfse4j-core` e o `nfse4j-danfse` no Maven Central e cria a GitHub Release com os jars do CLI e do MCP. Uma versão publicada no Central não pode ser apagada nem sobrescrita: correção vira nova versão.

## Segurança — leia antes de commitar

- **Nunca** inclua certificados reais (`.pfx`/`.p12`), `.env` com senha, ou XMLs de notas emitidas (dados de contribuinte). O `.gitignore` bloqueia esses arquivos — confirme antes de cada commit.
- Use dados **fictícios** em exemplos e testes.
- Produção emite documento fiscal real: mudanças que afetem o fluxo de emissão devem preservar a trava de produção (confirmação explícita).

## Pull Requests

Inclua um resumo da mudança, os comandos de teste rodados e, para mudanças no fluxo de emissão, descreva como validou em homologação.
