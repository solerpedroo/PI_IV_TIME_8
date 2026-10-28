<!-- Autor: Tomas de Paula Michelon Toniato - RA: 25004211 -->
# Calendário - I23/#27 (#169, #170)

O calendário da tela de Atividades deixou de ser fixo (Semana 32, contagens e
resumo escritos no HTML). Ele consulta `GET /api/calendario`, documentada no
[README do servidor](../backend/servidor/README.md), e mostra somente as
atividades reais da propriedade ativa.

## Semana e mês (#169)

- **Semana**: segunda a domingo, com o número ISO da semana no título.
- **Mês**: grade do primeiro ao último dia, com a contagem embaixo de cada dia.
- Setas trocam de período; "Voltar para hoje" volta ao período atual.
- O servidor devolve só as atividades com data dentro do período, a contagem de
  todos os dias (inclusive os zerados) e os totais por status e por tipo.
- O formato do protótipo (`12 ago`) não tem ano. Vale a ocorrência mais próxima
  da criação da atividade, então uma atividade de `10 jan` cadastrada em
  dezembro aparece no janeiro seguinte, não no anterior.

## Contagens e seleção (#170)

- O card "Total no período" e o "Resumo da semana/mês" usam a resposta da API.
- Os filtros de status, tipo, talhão e busca do quadro também vão para a
  consulta, então as contagens batem com o que está filtrado.
- Clicar num dia mostra no quadro e na lista só as atividades dele; clicar de
  novo volta. "Mostrar no quadro só este período" esconde o que está fora da
  semana ou do mês.
- Criar, editar, mudar status ou filtrar refaz a consulta. Uma resposta antiga
  nunca substitui uma mais nova.
- Visão, período, dia e a opção de período ficam na URL (`visao`, `data`,
  `dia`, `periodo`), então recarregar a página mantém o contexto.
- Sem resposta do servidor, a tela avisa, oferece "Tentar novamente" e não
  esconde cards que não conseguiu conferir.

## Evidência

- `backend/servidor/testar.sh`: 32 verificações novas em `testarCalendario`
  (período obrigatório, data impossível, período invertido e maior que 62 dias,
  semana e mês com dias zerados, contagem por dia, status e tipo, ordem por data
  e horário, filtros, mudança de status, isolamento entre usuários e a regra do
  ano para `12 ago`, inclusive 29 fev e 31 fev). Total: 367/367.
- `node tests/calendario-real.cjs`: servidor Java em memória e Chromium. Semana
  atual com contagens reais e data sem ano, seleção de dia, "só este período",
  troca de semana, visão mensal, filtro de tipo, atividade nova pela tela e
  recarga mantendo visão, período e dia. Com `UI_SCREENSHOTS=<pasta>` grava as
  imagens da semana e do mês.
- Com MongoDB real (replica set): duas atividades, uma em ISO e outra em
  `29 out`, aparecem no mesmo período antes e depois de reiniciar o servidor,
  com a data sem ano resolvida igual nas duas consultas.
- `node tests/ui-polish.cjs`, `local-screens.cjs` e `local-persistence.cjs`
  continuam passando com a API simulada.
