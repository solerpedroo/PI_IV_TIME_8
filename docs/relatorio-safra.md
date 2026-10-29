<!-- Miguel — RA: 25003110 -->
# Relatório da safra — #172, etapa 2/4 da #28

Entrega restrita à [subissue #172](https://github.com/solerpedroo/PI_IV_TIME_8/issues/172), a partir de `dev` em `4f3daae`. Não implementa os relatórios da propriedade (#171), estoque (#173) ou atividades (#174), nem produtividade. A #171 ainda não oferece um contrato de relatório nesta base; esta frente usa os contratos operacionais existentes e documenta o seu contrato independente para permitir execução em paralelo.

## Uso e contrato

Na tela **Safras**, escolha **Relatório da safra** no ciclo desejado. O diálogo mostra propriedade, safra, cultura, etapa, período completo inclusivo, talhões, área atual, registros, contagens, total de custos e pendências. **Exportar CSV** baixa o mesmo resultado consultado, incluindo contexto e pendências. O parâmetro `?relatorio=<id>` reabre a consulta após reload.

`GET /api/relatorios/safras/{id}` exige Bearer. A propriedade vem exclusivamente da sessão, com verificação de titularidade; ocorrências respeitam também o usuário autenticado. A resposta não é armazenada em cache HTTP.

| Campo | Regra |
| --- | --- |
| `propriedade`, `safra` | Identificação pública, nome, cultura e etapa; sem dados de credenciais. |
| `periodo.inicio`, `periodo.fim` | Datas oficiais da safra, inclusivas. Não há filtro de subperíodo nesta entrega. |
| `areaHectares`, `origemArea`, `talhoes` | Soma reutilizada de `SafrasHandler.area`, com hectares **atuais** dos talhões associados. Não representa área histórica imutável. |
| `registros` | Registros operacionais reais, preservando IDs distintos mesmo com descrições iguais; data crescente e desempate por tipo/ID. |
| `contagens` | Quantidades de atividade, ocorrência e custo incluídas no período. |
| `totalCentavos` | Custos incluídos, somados em centavos; nenhum rateio. |
| `pendencias` | Registros explicitamente ligados ao ciclo com datas/vínculos inadequados, com motivo. Não entram nos totais. Talhão ausente também gera pendência. |
| `regraOcorrencias` | Associação por talhão e data, conforme contrato atual que não aceita `safraId` em ocorrências. |

Atividades e custos exigem `safraId` exato e data ISO real dentro do ciclo. Atividades com data sem ano não recebem ano presumido. O vínculo do talhão da atividade é conferido pelo nome, conforme o cadastro atual de atividades; nomes renomeados/incompatíveis viram pendência. Custos podem ser gerais da safra, sem talhão; custos sem safra ficam fora. Ocorrências excluídas, de outro usuário, sem talhão ou fora do período não entram.

Erros: 400 para ID inválido; 401 para sessão ausente/encerrada; 404 para safra inexistente/alheia; 409 para período cadastrado inválido ou troca de propriedade durante consulta; 500 para falha de fonte. Uma falha interrompe o relatório, sem apresentar zero fictício. O navegador bloqueia exportação durante carregamento/falha e invalida resultado após troca de contexto; respostas atrasadas após fechamento são descartadas.

O CSV contém BOM UTF-8, delimitador `;`, aspas duplicadas, valores monetários com duas casas e proteção contra fórmulas. Tela e CSV usam o mesmo objeto consolidado. As leituras entre repositórios **não são uma transação atômica**; alterações concorrentes podem exigir nova consulta.

## Evidência executada

Ambiente isolado local: Java Temurin 21, MongoDB 8.0.16 com replica set, Node 18 e Playwright 1.51.1/Chromium. Ferramentas, banco temporário, downloads e logs ficam fora do repositório. O teste cria um banco com nome aleatório, verifica que não existia e remove somente esse banco ao terminar.

| Camada/caso | Resultado observado |
| --- | --- |
| Regressões do servidor | `335/335 testes ok`. |
| Perfil e ocorrências | 49 e 61 verificações aprovadas. |
| Novo teste Java | 21 verificações aprovadas: limites inclusivos, dados repetidos por ID, conjunto vazio, datas impossíveis/sem ano/fora do ciclo, vínculos ausentes, centavos, autenticação, erro de fonte e mudança de contexto. |
| HTTP real com Mongo | Dois ciclos, dois usuários, ocorrência alheia na mesma propriedade excluída, safra alheia e IDs inválidos rejeitados. 2 atividades + 1 ocorrência + 2 custos, área 12,5 ha, total R$ 0,30 e 2 pendências no cenário conhecido. |
| Navegador → API → banco → tela | Total e pendências conferidos; conteúdo HTML tratado como texto; zero exceções JavaScript; largura de desktop e ausência de rolagem horizontal no celular conferidas. |
| Exportação | Todos os IDs da API/resultado presentes no CSV, contexto e pendências incluídos, total 0,30, acentos/aspas/quebra de linha preservados e fórmula neutralizada. |
| Falhas e concorrência na UI | HTTP 503 injetado bloqueia exportação; fechamento descarta resposta atrasada; mudança de propriedade invalida resultado antes da exportação. |
| Reload | Relatório reabre pelo parâmetro da URL com o mesmo total. |
| Reinício | Processo **Java** encerrado e iniciado novamente; novo login; resposta completa comparada campo a campo com a anterior, usando Mongo persistente. O processo Mongo não foi reiniciado. |

O fluxo real tem **20 verificações nomeadas**, além das asserções HTTP e comparação dos campos/IDs. A falha HTTP e a resposta atrasada são injetadas somente nos testes desses estados da interface; criação, consulta, CSV, reload e reinício usam o servidor e o Mongo reais.

Dois subagentes revisaram o plano e a implementação. O erro inicial da expressão de datas foi corrigido antes do primeiro commit e coberto pelo teste Java e pela API real. A revisão final não identificou defeito crítico/médio residual.

## Reprodução

Com Java 21 no PATH:

```sh
sh backend/servidor/testar.sh
```

Para o fluxo real, inicie um Mongo **local** com replica set. O teste exige porta 8080 livre, conforme a configuração atual do frontend. Dependências de teste podem ficar em diretório temporário:

```sh
npm install --prefix /tmp/agro-i172-deps playwright@1.51.1 mongodb@6 --no-audit --no-fund
/tmp/agro-i172-deps/node_modules/.bin/playwright install chromium
NODE_PATH=/tmp/agro-i172-deps/node_modules \
TEST_MONGO_URI='mongodb://127.0.0.1:27017/?replicaSet=rs0' \
node tests/relatorio-safra-real.cjs
```

`JAVA_BIN` permite indicar o executável Java; `BROWSER_PATH` permite navegador instalado; `TEST_ARTIFACT_DIR` opcional recebe captura de tela fora do repositório. Sem `TEST_MONGO_URI`, o teste usa `127.0.0.1:27172`/`rs172`, a instância isolada usada nesta validação.

## Matriz da frente

| Requisito | Evidência | Situação nesta branch |
| --- | --- | --- |
| Cultura, área, atividades, ocorrências e custos do ciclo | Serviço `RelatorioSafra`, teste Java e fluxo real | Implementado e validado |
| Safra/período explícitos | Contexto da API, diálogo e CSV | Implementado e validado |
| Exportação corresponde à consulta | CSV usa o mesmo objeto da tela, comparação por IDs e total | Implementado e validado |
| Casos inválidos e persistência | Testes de fonte/contexto/datas e reload/reinício Java | Validado |
| Integração à `dev` | Branch `feat/i172-relatorio-safra` | Pendente de revisão/merge pela equipe |
| Principal #28 | Esta entrega cobre apenas #172 | Continua parcial; não concluir #171/#173/#174 |
