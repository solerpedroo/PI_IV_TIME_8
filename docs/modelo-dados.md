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


## #102 — Atividades e histórico

`atividades`: `_id`, titularidade, `talhaoId`/`safraId` opcionais, título, tipo, responsável, data, horário, prioridade, `criadaEm` e `statusInicial=AGENDADA`. A safra exige um talhão que pertença a ela. A cultura é obtida pelo vínculo da safra, evitando duas referências divergentes.

`atividades_historico`: titularidade, `atividadeId`, `ordem`, `registradoEm`, `status`, `descricao`. Eventos ficam separados para o histórico crescer sem aumentar o documento principal. A criação é o evento 0, derivado do documento principal; os eventos posteriores são inseridos sem substituir os anteriores. O estado atual é o último evento, sem uma segunda cópia mutável de status que poderia ficar inconsistente.

Transições: AGENDADA → EM_ANDAMENTO → CONCLUIDA; cancelamento permitido antes da conclusão. A sequência esperada e o índice único por registro/ordem rejeitam gravações repetidas ou concorrentes; o chamador deve consultar novamente após conflito. Datas não podem regredir. Datas de eventos usam BSON Date com precisão de milissegundos. `buscar`, `listarHistorico`, `statusAtual` e `listarDoContexto` permitem consultar o ciclo concluído.

O vocabulário segue o documento do projeto §8.5; divergências gerais de terminologia da #5 continuam naquela frente. Execute também `br.com.agrogestao.core.domain.operacao.DadosAtividadeTest` pelo mesmo comando Maven dos testes locais.

### Evidência da #102 — 23/10/2026

`DadosAtividadeTest: OK` e `CicloDadosAtlasTest: OK (#101–#102)`. Confirmados no Atlas: conclusão com três eventos (incluindo criação), consulta pelo ciclo e histórico após reconexão, isolamento, rejeição de talhão fora da safra, datas regressivas, ordem repetida e reabertura de atividade concluída. Dados fictícios removidos.


## #103 — Insumos e movimentações

`insumos`: `_id`, titularidade, nome, categoria, unidade normalizada e `estoqueMinimo` não negativo (Decimal128). `movimentacoes_insumos`: `_id`, titularidade, `insumoId`, tipo, quantidade (Decimal128), unidade, `atividadeId` opcional, `ocorridaEm`, motivo.

Entradas e saídas possuem quantidade positiva. AJUSTE representa uma diferença assinada, não zero, com motivo. Uma saída com `atividadeId` é consumo daquela atividade; uma saída manual pode omitir esse vínculo. Entradas e ajustes não apontam para uma atividade consumidora. A unidade da movimentação deve corresponder ao catálogo; não há conversão implícita de unidades. O repositório confere insumo e atividade na mesma propriedade e oferece consultas por insumo e por atividade. Movimentações são documentos separados e não substituem o histórico anterior.

O exemplo oficial preserva entrada de 500 kg e consumo de 50 kg; a soma das variações é 450 kg. A modelagem da #103 não executa a conclusão com baixa transacional nem garante saldo sob concorrência: essas entregas estão delimitadas nas #142 e #160. Não há cópia mutável de saldo neste catálogo. Execute também `br.com.agrogestao.core.domain.operacao.DadosInsumoTest` para as validações locais.

### Evidência da #103 — 23/10/2026

`DadosInsumoTest: OK` e `CicloDadosAtlasTest: OK (#101–#103)`. No Atlas, a entrada de 500 kg e o consumo de 50 kg permanecem no histórico após reconexão; soma conhecida de 450 kg e mínimo do catálogo conferidos. Unidade divergente, referências inexistentes e insumo/atividade de outro produtor foram rejeitados; consultas cruzadas retornaram vazio. Dados fictícios removidos.
