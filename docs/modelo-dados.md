# Modelo de dados da operação

Complementa a base de usuários e propriedades da #100. Implementação Java 21 com o driver MongoDB oficial, sem framework.

## Titularidade e referências

Todo documento operacional carrega `proprietarioId` e `propriedadeId`. As consultas exigem ambos; cada gravação verifica a propriedade do titular e as referências dentro dessa mesma propriedade. Um ID de outro produtor ou de outra propriedade do mesmo produtor é inválido. Essa verificação no repositório prepara o contrato para a autenticação HTTP da #8/#112; as rotas de domínio permanecem na frente correspondente.

MongoDB não oferece chave estrangeira: as escritas da aplicação devem passar pelos repositórios. O modelo desta frente não oferece exclusão de registros referenciados; uma política futura de exclusão precisará preservar os históricos.

## #101 — Talhões, culturas e safras

- `talhoes`: `_id`, titularidade, `nome`, `areaHectares` (Decimal128 positivo), `status` (ATIVO/INATIVO).
- `culturas`: `_id`, titularidade, `nome`. O catálogo é da propriedade.
- `safras`: `_id`, titularidade, `nome`, `culturaId`, `talhaoIds`, `inicio`, `fim`, `areaHectares`, `status`.

São coleções separadas: a safra referencia uma cultura e um ou mais talhões, permitindo várias safras ao longo do tempo sem substituir a cultura histórica do talhão. Datas de calendário usam ISO `YYYY-MM-DD`, sem conversão de fuso. O fim não pode anteceder o início; a lista de talhões deve ser não vazia, sem duplicatas. Status da safra: PLANEJAMENTO, PREPARACAO, PLANTIO, MANEJO, COLHEITA, FINALIZADA. Índices começam pelo titular e propriedade; há índice para consultar safras por talhão e início.

## Validação reproduzível

Em `backend/core`, compile com `mvn test-compile` e execute:

```bash
mvn -q exec:java -Dexec.mainClass=br.com.agrogestao.core.domain.operacao.DadosCultivoTest -Dexec.classpathScope=test
```

Após carregar o `.env` conforme [mongodb-atlas.md](mongodb-atlas.md), execute:

```bash
mvn -q exec:java -Dexec.mainClass=br.com.agrogestao.core.infra.mongo.CicloDadosAtlasTest -Dexec.classpathScope=test
```

O teste Atlas usa dois produtores fictícios e uma terceira propriedade do mesmo titular. Confere referências inexistentes e cruzadas, reabre a conexão para conferir persistência e remove exclusivamente os dados fictícios dos escopos gerados para essa execução. Não usa `drop` nem apaga coleções.

### Evidência da #101 — 23/10/2026

`DadosCultivoTest: OK` e `CicloDadosAtlasTest: OK (#101)` com Java 21 (compilação direta por `javac --release 21` e execução por `java`) e o driver 5.8.1 da base. Atlas `agrogestao_dev`: dados consultados após reconectar, referências inexistentes/cruzadas rejeitadas e registros fictícios removidos ao final.
