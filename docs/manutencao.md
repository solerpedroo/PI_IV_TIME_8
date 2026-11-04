<!-- Autor: Pedro André do Carmo Chavier - RA: 25018639 -->
# Manutenção de equipamentos - #234 (#235, #236, #237)

A propriedade passa a cadastrar os equipamentos agrícolas, programar as manutenções
preventivas e registrar as intervenções (o que foi feito, quando, por quem e quanto custou).
A intervenção com valor vira um custo em Custos, na categoria Maquinário, ligado ao talhão e
à safra quando informados. O contrato da API está no
[README do servidor](../backend/api/README.md#manutenção).

## Cadastrar equipamentos e programar preventivas (#235)

- `POST /api/equipamentos` cadastra; `PUT` altera. Nome único por propriedade sem diferenciar
  maiúsculas (`nomeNormalizado` + índice único `(propriedadeId, nomeNormalizado)` no MongoDB):
  repetir responde 409 no campo `nome`.
- Regras de campo (no serversocket, `ValidadorDeManutencao.validarEquipamento`): nome até 80,
  tipo da lista (Trator, Colheitadeira, Pulverizador, Plantadeira, Implemento, Veículo, Outro),
  marca, modelo e identificação opcionais até 60, ano de 1950 até o ano atual + 1 (a API manda o
  ano atual: o socket não lê relógio), horímetro ≥ 0 e status Ativo (padrão), Em manutenção ou Inativo.
  Tipo errado no corpo (ano em texto, por exemplo) volta 400 sem chegar ao socket.
- Sem exclusão nesta entrega: o equipamento sai de uso com status Inativo e o histórico fica.
- `POST /api/preventivas` programa: descrição até 120, data prevista válida (data impossível como
  2026-02-30 é recusada) e intervalo opcional de 1 a 3650 dias. `PUT` altera; cancelar é `PUT` com
  `status: cancelada`; `DELETE` exclui. Preventiva já realizada não muda (409): ela faz parte do histórico.
- A agenda (`GET /api/preventivas`) traz, para cada preventiva programada, a `situacao` e os `diasAte`
  calculados no serversocket (`AgendaDeManutencao`): `atrasada` (data antes de hoje, dias negativos),
  `proxima` (de hoje até 7 dias, inclusive) ou `futura`. Realizadas e canceladas mantêm o próprio status.

## Registrar intervenções e vincular custos (#236)

- `POST /api/intervencoes`: tipo `preventiva` ou `corretiva`, data que não pode estar no futuro (fuso
  America/Sao_Paulo), descrição até 240, responsável e horímetro opcionais, valor opcional de R$ 0,01 até
  R$ 1 bilhão (no protocolo em `BigDecimal`, arredondado a 2 casas) e talhão/safra com as mesmas regras do
  custo (o talhão precisa ser da safra; safra finalizada responde 409 e nada é gravado).
- **Sem valor, não há custo**: a issue proíbe despesa zerada.
- Com valor, `CustosDeManutencao` grava um custo em **Maquinário** com a data, o talhão e a safra da
  intervenção, a descrição "Manutenção <tipo>: <equipamento> (<descrição>)" e
  `origem: {tipo: "manutencao", intervencaoId, equipamentoId}`. Existe no máximo um custo por intervenção:
  `criarSeAusente` procura por `origem.intervencaoId` (memória) e o MongoDB tem um índice único parcial em
  `(propriedadeId, origem.intervencaoId)`, ao lado do que já existia para `origem.movimentacaoId`.
- O custo só muda pela intervenção: editar o valor atualiza o mesmo lançamento, tirar o valor exclui o
  custo, pôr valor onde não havia cria, e excluir a intervenção exclui o custo junto.
  `PUT`/`DELETE /api/custos/{id}` desse custo respondem 409 "Custo gerado pela manutenção do equipamento.
  Corrija pela intervenção." (o custo de consumo continua com a mensagem do estoque).
- Intervenção com `preventivaId` (programada e do mesmo equipamento, e do tipo preventiva): a preventiva vira
  `realizada` e, se tem intervalo, nasce a próxima `programada` com data = data realizada + intervalo
  (`PedidoDeProximaPreventiva`, que trata virada de ano e 29/02). Editar a data da intervenção move a próxima
  enquanto ela estiver programada. Excluir a intervenção devolve a preventiva para `programada` e apaga a
  próxima gerada; se a próxima já foi realizada, responde 409.
- Horímetro maior que o do equipamento atualiza o equipamento.
- Ordem das gravações: tudo é validado antes (socket, equipamento, preventiva, talhão, safra e a próxima data)
  e só então gravado. A preventiva passa a `realizada` com update condicional (`status = programada`); se outra
  requisição já a realizou, a intervenção recém-criada é desfeita e a API responde 409. Falha ao gerar a próxima
  preventiva desfaz a intervenção e reverte a preventiva. Se o custo falhar depois da intervenção gravada, a resposta
  é 201 (ou 200) com `custoPendente: true`; editar a intervenção de novo recria o custo que faltou.
- O equipamento e a preventiva de uma intervenção não são trocados por `PUT` (400): exclua e registre de novo.
- Registro de outra propriedade responde 404 em todas as rotas, nunca 403.

## Exibir agenda e histórico de equipamentos (#237)

- Página nova `manutencao.html` (link "Manutenção" logo depois de Insumos no menu lateral de todas as telas).
- Métricas do `GET /api/manutencao/resumo`: equipamentos ativos, preventivas atrasadas, próximas 7 dias e gasto
  com manutenção no ano (soma em centavos dos custos com `origem.tipo = manutencao` do ano atual), com link para
  Custos. Mostram "-" até a API responder; erro mostra a mensagem e "Tentar novamente".
- Agenda de preventivas com filtro (todas, atrasadas, próximas, futuras) e as ações "Registrar como feita" (abre o
  formulário de intervenção já preenchido com tipo preventiva e a preventiva), "Editar" e "Cancelar" (com confirmação).
- Tabela de equipamentos (nome, tipo, marca/modelo, horímetro, status, próxima preventiva e última intervenção) com
  "Histórico" e "Editar".
- Histórico do equipamento com as intervenções, o valor do custo (link para Custos) e as ações "Editar" e "Excluir"; a
  exclusão confirma antes: "O custo de R$ X lançado em Custos também será excluído.".
- `manutencao.html?equipamentoId=` abre o histórico daquele equipamento: é o atalho que vem de Custos.
- Em Custos, o lançamento de manutenção tem o selo "Manutenção", não tem menu de editar e o atalho vai para
  `manutencao.html?equipamentoId=...`; no CSV a origem é "Manutenção de equipamento".
- Nada fica no `AgroLocal`: equipamentos e manutenção vêm sempre da API; o toast de sucesso só aparece depois da
  confirmação do servidor e uma resposta antiga nunca substitui a mais nova.

## Evidência

- `sh backend/serversocket/testar.sh`: `TesteAgendaDeManutencao` com 43 verificações (cada recusa de cada validador,
  ano limite, horímetro negativo e NaN, valor com 3 casas arredondado, valor 0,004, data futura com `hoje` fixo, data
  impossível, agenda com atrasada, próxima, futura, realizada e cancelada ignoradas, `diasDeAviso` 2 e 30, próxima
  preventiva com e sem intervalo, virada de ano e 29/02). `TesteAlgoritmos` 73 e `TesteProtocolo` 29 continuam.
- `sh backend/api/testar.sh`: `TesteManutencao` com 61 verificações em memória (401, recusas de equipamento, preventiva
  e intervenção, nome repetido com maiúsculas, isolamento 404 em todas as rotas, contexto trocado, agenda relativa a hoje,
  intervenção sem valor sem custo, `marcarRealizadaSeProgramada` atômico, `custoPendente` e recuperação no `PUT`, custo
  de 350,50 em Maquinário com talhão e safra, sem duplicar, 409 no `PUT`/`DELETE` do custo, editar/remover/recriar o valor,
  safra finalizada, próxima preventiva gerada e desfeita, 409 quando a próxima já
  foi realizada, horímetro e resumo). Com `--mongo`, também o índice único de nome, o índice parcial de
  `origem.intervencaoId` e a leitura por repositórios novos (reinício).
- `node tests/manutencao-real.cjs` (com o serversocket na porta 3000): métricas "-" até a API, estados vazios, cadastro e
  nome repetido, duas preventivas, registrar como feita com valor e talhão, custo em Custos com selo e atalho de volta ao
  histórico, corretiva sem custo, edição e exclusão do custo com confirmação e recarga. `custos-real`, `orcamento-real`,
  `ui-polish`, `local-screens` e `local-persistence` continuam passando.
