<!-- Autor Principal: Pedro Henrique Contardi Soler - 25005592 -->
# Estoque — implementação e validação

Entrega da branch `feat/estoque`, issues #18, #141, #142, #143, #19, #144, #145,
#146 e #147. Código Java 21 sem framework: HTTP manual existente, handlers,
interfaces de repositório, memória e driver oficial MongoDB.

## Regras e escopo

- #141/#144: catálogo da propriedade ativa com nome, categoria, unidade,
  mínimo, custo médio unitário e limiar crítico opcional. Cadastro e edição
  consultam a API após confirmação; consulta individual e exclusão também existem.
- #142/#145: entradas/saídas positivas, ajuste por saldo contado com motivo,
  inclusive zero; saldo insuficiente não grava histórico. Mongo usa transação
  com repetição automática de conflitos. Duplo submit é bloqueado no formulário.
- #143/#146: histórico imutável com data, unidade, fornecedor, custo, motivo,
  saldo anterior/posterior e referência opcional de consumo. O seletor lista
  somente atividades já persistidas do titular e propriedade; IDs inexistentes
  ou cruzados são rejeitados. ID e título ficam preservados depois do consumo.
  O estoque não cria atividades para simular essa dependência.
- #147: total de itens ativos, críticos pelo limiar cadastrado, valor pelo
  custo médio ponderado e quantidade de entradas no mês/ano de São Paulo.
  Exclusão lógica conserva o histórico e as entradas mensais anteriores.
- #18/#19: todos os vínculos são filtrados pela propriedade autenticada;
  tabela HTML inicia vazia, indicadores iniciam indisponíveis e falha da API
  mostra erro com opção de repetir a consulta, sem recuperar exemplos locais.

O limiar crítico é uma quantidade absoluta configurável por produto, menor ou
igual ao mínimo. Saldo até esse limiar é Crítico. Sem limiar, há somente Normal
ou Baixo (abaixo do mínimo). Nenhuma proporção 0,6 é presumida. Assim a equipe
pode registrar sua escolha sem transformar a antiga regra mock em requisito.

Uma entrada com preço calcula `(saldoAnterior × custoAnterior + quantidade ×
preçoDaEntrada) / saldoNovo`. Preço ausente conserva o custo anterior. Quantidades
e custos aceitam até seis casas decimais; totais monetários são arredondados
para duas. Ajuste é um saldo absoluto contado, não uma diferença assinada.

Alterar a unidade de um item com saldo/histórico é rejeitado para não transformar
kg antigos em litros. Para outra unidade, crie outro item. Excluir um item de
saldo zero arquiva-o; `/api/insumos/{id}/movimentacoes` continua consultável.
Propriedades com catálogo ou histórico de estoque ficam protegidas da exclusão.

## Reproduzir

Na raiz, JDK 21 instalado:

```bash
sh backend/servidor/testar.sh
```

Mongo local com transações (o Compose inicializa `rs0` e aguarda primário):

```bash
docker compose up -d --wait mongo
export MONGODB_URI='mongodb://127.0.0.1:27017/agro_estoque_teste?replicaSet=rs0&serverSelectionTimeoutMS=5000'
sh backend/servidor/testar-estoque-mongo.sh
node tests/estoque-real.cjs
```

Para o navegador, instale Playwright e seu Chromium no ambiente de testes.
`PLAYWRIGHT_MODULE` pode indicar uma instalação já existente e `BROWSER_PATH`
um Chromium existente. A porta 8080 precisa estar livre: o teste abre seu próprio
servidor e o encerra/reinicia. `MONGODB_URI` deve apontar para um banco dedicado
terminado em `_teste`, nunca para o banco de desenvolvimento. As suítes não usam
`drop`: removem somente contextos aleatórios ou contas fictícias do cenário.

`CenarioEstoqueTeste` cria duas contas fictícias e duas atividades persistidas
somente para o teste de navegador. Não adiciona rotas de negócio nem dados de
exemplo à aplicação. O teste remove esses registros ao terminar, inclusive
após erro, e encerra os processos Java que abriu. A imagem de evidência fica
em `/tmp/agro-estoque-verificado.png` (ou `STOCK_SCREENSHOT`).

## Evidência local — 24/10/2026

- `TesteServidor`: 199/199 verificações, incluindo validações de entrada,
  ajuste zero, concorrência, referência inexistente, titular diferente,
  exclusão com histórico e conjunto conhecido de R$60.
- `TesteEstoqueMongo`: dois clientes independentes disputam duas saídas de
  7 kg para saldo de 10 kg. Somente uma é aceita, saldo final 3 kg e nenhum
  evento extra. Testados custo médio, isolamento, referências BSON, reconexão
  e preservação de cinco movimentos após exclusão lógica.
- `tests/estoque-real.cjs`: formulário → HTTP Java → Mongo → interface;
  cadastro, edição/reload, entrada, saída insuficiente, atividade real,
  referência cruzada, falha de comunicação, ajuste para zero, exclusão e
  histórico. Encerra o servidor Java inteiro, abre outro processo e comprova
  saldo/histórico persistidos com nova sessão. API 500 deixa zero linhas e
  indicadores indisponíveis, sem dados fictícios. Sem sessão redireciona ao login.
- `TesteMongo`: regressão de cadastro/propriedades/transações aprovada no
  mesmo Mongo local isolado. Os testes de armazenamento local continuam cobrindo
  os módulos que usam AgroLocal; a cobertura de estoque foi migrada para o E2E real.

A execução local usou MongoDB 8.0.12 em replica set isolado. O Compose/CI mantém
a imagem já definida pelo projeto, 8.0.32. A automação da nova suíte está em
`.github/workflows/estoque-testes.yml`; a execução remota depende do próximo PR.

Esta evidência documenta o código local. Commit, revisão, merge e atualização
do Project devem apontar para a versão efetivamente revisada.
