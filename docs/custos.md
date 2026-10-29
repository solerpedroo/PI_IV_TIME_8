<!-- Autor: Tomas de Paula Michelon Toniato - RA: 25004211 -->
# Custos - I18/#22 e I19/#23 (#156, #157, #158, #159)

A tela de Custos usa somente a API Java autenticada (`/api/custos` e
`/api/custos/resumo`, contrato no
[README do servidor](../backend/servidor/README.md)). Não existem mais lançamentos de exemplo nem totais fixos:
`categoryBase`, a área 1284 e os valores de cultura foram removidos.

## Confirmação pela API (#157)

Criar, editar e excluir enviam a operação e só depois releem a lista. O toast de
sucesso aparece apenas quando o servidor confirma; um erro de validação volta no
campo correspondente (por exemplo, talhão que não pertence à safra escolhida) e
nada é gravado. Safra finalizada aparece desabilitada para lançamentos novos.

## Filtros, período e exportação (#158)

Categoria, busca, talhão (ou "somente propriedade"), safra (ou "sem safra") e
período viram parâmetros da mesma consulta (`category`, `q`, `talhaoId`,
`safraId`, `de`, `ate`). Lista, cards, gráfico, cultura, evolução e CSV usam o
resultado dessa consulta, então o arquivo exportado é exatamente o recorte
exibido. O período pode ser todo, mês atual, últimos 3 meses, ano atual ou um
intervalo personalizado; o intervalo invertido é recusado na tela e no servidor.
Os links `custos.html?talhao=<nome>` de Áreas de Cultivo e Safras continuam
abrindo a tela filtrada pelo talhão.

## Indicadores e área reais (#159)

- Total, quantidade e categorias vêm de `/api/custos/resumo`.
- Custo por hectare usa a área do recorte: talhões da safra filtrada, o talhão
  filtrado ou todos os talhões da propriedade. Sem área, a tela mostra "-".
- Custo por cultura usa a cultura da safra do lançamento ou, sem safra, a do
  talhão. Gastos sem nenhum dos dois ficam em "Sem vínculo com cultura".
- Evolução mensal mostra o total de cada mês do recorte.
- O Painel lê o mesmo total sem filtros em `/api/custos/resumo`.

## O que continua no navegador

O orçamento previsto e o período escolhido ficam no armazenamento local da
propriedade (`costs`), porque não há rota de orçamento nesta entrega. O campo
`entries` desse registro fica vazio e só existe para aceitar o cache anterior.

## Custo gerado pelo consumo de estoque (#156)

- Toda saída de insumo vinculada a uma atividade gera um lançamento de custo
  em Insumos: quantidade x custo médio do insumo no momento da baixa, com o
  talhão e a safra da atividade e a data da baixa. A resposta da saída traz
  `custoId` e `custoValor`, e a tela de Insumos avisa o valor lançado.
- O lançamento guarda `origem` (movimentação, insumo, atividade, quantidade,
  unidade e custo unitário). A movimentação é a chave: existe no máximo um custo
  por movimentação (índice único no MongoDB), então repetir o registro ou as
  consultas nunca duplica o valor.
- A baixa continua sendo do estoque. Se o custo não for gravado logo depois da
  baixa, a próxima consulta de custos da propriedade cria o que falta.
- Custo de consumo não é editado nem excluído em Custos (409): ele muda pela
  movimentação do insumo. Na lista aparece com a marca "Consumo" e um atalho
  para o produto no estoque; o CSV tem a coluna Origem.
- Atividade de safra finalizada: o custo fica só no talhão, sem a safra.
- Sem custo unitário no insumo, a saída não gera lançamento.
- No modo `--memoria`, o estoque passou a enxergar as atividades reais do
  servidor, como já acontece no MongoDB.

A conclusão da atividade com baixa automática de estoque continua na #24 (#160,
#161); quando ela registrar a saída, o custo sai por este mesmo caminho.

## Evidência

- `node tests/custos-real.cjs`: servidor Java em memória e Chromium. Cria pela
  tela, confere total 3.300,50, custo por hectare 36,67 (90 ha) e 20,00 com o
  talhão de 50 ha, cultura da safra e do talhão, evolução, filtros de talhão,
  safra e período, CSV do recorte, erro de vínculo no campo, edição, exclusão,
  recarga mantendo o período e o total no Painel.
- `backend/servidor/testar.sh`: 18 verificações novas em `testarCustosDeConsumo`
  (50 kg a R$ 2,50 = R$ 125,00 no talhão e na safra da atividade, origem,
  saldo, repetição sem duplicar, custo recuperado na consulta seguinte, saída
  sem atividade e entrada sem custo, safra finalizada, 409 em editar e excluir,
  resumo da safra e isolamento). Total: 353/353. `TesteMongo` confere o índice
  único com MongoDB real.
- Consumo pela tela de Insumos em `tests/custos-real.cjs` e validação pela API
  com MongoDB real, antes e depois de reiniciar o servidor.
- `node tests/ui-polish.cjs` e `node tests/local-screens.cjs`: layout nas sete
  larguras e comportamento sem API (nenhum lançamento inventado).
