<!-- Autor: Tomas de Paula Michelon Toniato - RA: 25004211 -->
# Colheita e produtividade - I25/#222 (#223, #224, #225)

A safra passou a registrar o que foi colhido em cada talhão e a calcular a
produtividade por hectare com a área real, comparando os ciclos produtivos. É
controle gerencial: não há previsão de produtividade nem valor estimado. O
contrato da API está no [README do servidor](../backend/servidor/README.md).

## Persistir a colheita por safra e talhão (#223)

- `POST /api/colheitas` grava `safraId`, `talhaoId`, `quantidade`, `unidade`,
  `data` e `observacao` na propriedade ativa; `PUT` e `DELETE` alteram e excluem.
- Unidade explícita: `kg`, `t` (1.000 kg) ou `sc` (saca de 60 kg). A quantidade
  fica como foi informada e também convertida em `quantidadeKg`.
- Recusas (400, campo a campo): safra ou talhão de fora da propriedade, talhão
  que não é da safra, quantidade zero, negativa ou em texto, unidade fora da
  lista, data impossível, anterior ao início da safra ou no futuro, observação
  com mais de 200 caracteres. Nada é gravado nas recusas.
- Regra do ciclo: colheita só a partir do Plantio (Planejamento e Preparação
  devolvem 409 no campo `safraId`); safra finalizada fica só para consulta
  (`POST`, `PUT` e `DELETE` devolvem 409, como os custos dela).
- A edição de uma safra não tira dela um talhão que já tem colheita registrada.

## Produtividade por hectare (#224)

- `GET /api/colheitas/resumo` soma a produção do recorte (safra, talhão,
  cultura e período) em gramas e divide pela área real: a soma dos hectares dos
  talhões de cada safra, ou só o talhão filtrado. Um talhão colhido em dois
  ciclos conta a área uma vez por ciclo.
- Devolve `kg/ha` e `sc/ha`, e o detalhamento `porSafra` e `porTalhao`.
- Sem área (talhão com 0 ha) ou sem colheita, a produtividade fica `null`:
  a área não é inventada.

## Comparar ciclos e exibir produtividade (#225)

- `GET /api/colheitas/comparativo?cultura=` traz todas as safras em ordem de
  início, inclusive as que ainda não colheram (0 kg e produtividade `null`), com
  talhões colhidos, última colheita e a variação percentual frente ao ciclo
  anterior da mesma cultura.
- Na tela de Safras, a seção "Colheita e produtividade" mostra produção, área,
  sc/ha e kg/ha do recorte, o comparativo de ciclos (clicar num ciclo filtra o
  resto) e a lista de colheitas com edição e exclusão confirmadas pelo servidor.
- O formulário só oferece safras do Plantio em diante e só os talhões da safra
  escolhida. Erro do servidor volta no campo. Ao avançar uma safra na lista de
  ciclos, a seção relê as safras (a finalizada vira somente leitura).

## Evidência

- `backend/servidor/testar.sh`: `TesteColheitas` com 41 verificações em memória
  sobre um conjunto conhecido (Soja 24/25 com 150 t em 50 ha = 50 sc/ha e Soja
  25/26 com 186 t = 62 sc/ha, +24%): todas as recusas, etapa antes do plantio,
  conversão de unidades, resumo por safra, talhão, cultura e período, talhão
  sem área, comparativo com ciclo sem colheita, isolamento entre propriedades,
  troca de contexto, safra que não perde talhão colhido e safra finalizada.
- `java -cp 'bin:lib/*' TesteColheitas --mongo` com MongoDB real (replica set):
  43 verificações, incluindo a leitura por repositórios novos (reinício) e os
  índices.
- `node tests/colheita-real.cjs` (servidor Java em memória + Chromium): nada
  inventado sem colheita, validação local e do servidor, dois registros pela
  tela, recarga, produtividade e variação entre os dois ciclos, edição, exclusão
  com confirmação e safra finalizada pela própria tela ficando somente leitura.
  Com `UI_SCREENSHOTS=<pasta>` grava a seção.
- `relatorio-safra-real` (MongoDB real), `local-screens`, `local-persistence` e
  `storage-scope` continuam passando.
