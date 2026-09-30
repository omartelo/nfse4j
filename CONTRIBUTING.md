# Contribuindo

Contribuições são bem-vindas! Algumas diretrizes:

## Desenvolvimento

- Java 21, UTF-8, 4 espaços de indentação. Pacote raiz `io.github.omartelo.nfse4j`.
- Mantenha o `nfse4j-core` **sem dependências de runtime** (só JDK).
- Rode os testes antes de abrir PR: `./gradlew test`.
- Testes não devem exigir certificado real — gere material de teste com `TestPkcs12Factory`.

## Build e teste

```bash
./gradlew build                # build + testes de todos os modulos
./gradlew :nfse4j-core:test    # so o SDK
```

## Segurança — leia antes de commitar

- **Nunca** inclua certificados reais (`.pfx`/`.p12`), `.env` com senha, ou XMLs de notas emitidas (dados de contribuinte). O `.gitignore` bloqueia esses arquivos — confirme antes de cada commit.
- Use dados **fictícios** em exemplos e testes.
- Produção emite documento fiscal real: mudanças que afetem o fluxo de emissão devem preservar a trava de produção (confirmação explícita).

## Pull Requests

Inclua um resumo da mudança, os comandos de teste rodados e, para mudanças no fluxo de emissão, descreva como validou em homologação.
