<!-- Autor: Tomas de Paula Michelon Toniato - RA: 25004211 -->
# Orçamento por safra - I26/#226 (#227, #228, #229)

O orçamento deixou de ser um número guardado só no navegador (o antigo
"R$ 520.000" do card de Custos). Agora cada safra pode ter um valor planejado
gravado no servidor, e o realizado vem dos custos efetivos lançados nela. O
contrato da API está no [README do servidor](../backend/servidor/README.md).

## Persistir o orçamento por safra (#227)

- `PUT /api/orcamentos/{safraId}` define (201) ou altera (200) o planejado
  da safra da propriedade ativa; `DELETE` remove (204).
- Um orçamento por safra: índice único `(propriedadeId, safraId)` no MongoDB.
  Repetir a gravação altera o mesmo registro e mantém o `criadoEm` original.
- Recusas: valor ausente, texto, menor que R$ 0,01, acima de 1 bilhão e alerta
  fora de 50% a 100% (400, campo a campo); safra inexistente ou de outra
  propriedade (404); propriedade trocada no meio da escrita (409). Nada é gravado
  nas recusas e não existe orçamento padrão.
- Safra finalizada mantém o planejado para consulta, mas `PUT` e `DELETE`
  devolvem 409, como os custos dela.
- Excluir a safra (só em Planejamento) remove o orçamento junto — sem documento
  órfão em `orcamentos`.

## Desvios e alerta de limite (#228)

- Realizado = soma, em centavos, de todos os custos com aquela `safraId`
  (manuais e de consumo de estoque). É o mesmo total de
  `GET /api/custos/resumo?safraId=`, sem recorte de período.
- `saldo` = planejado - realizado; `desvio` = realizado - planejado;
  `desvioPercentual` e `percentualUsado` com uma casa decimal.
- `situacao`: `sem-orcamento`, `dentro`, `proximo` (usou o percentual de
  `alerta` ou mais, padrão 80%; inclui 100% exato) ou `acima` (realizado
  **maior** que o planejado). No limite (100%) ainda é `proximo`, não `acima`.
- Safra sem orçamento não ganha meta nem alerta: os campos ficam `null` e a
  situação é `sem-orcamento`.
- Com área conhecida, também vêm `planejadoPorHectare` e `realizadoPorHectare`.
- `GET /api/orcamentos/{safraId}` soma só os custos daquela safra
  (`listarPorSafra` + índice `(propriedadeId, safraId)`), sem varrer a
  propriedade inteira.

## Planejado versus realizado na tela (#229)

- O card "Orçamento da Safra" em Custos escolhe a safra (segue o filtro de safra
  da página; sem filtro, a primeira em andamento) e mostra planejado, realizado,
  desvio, barra de uso e a situação com o texto do alerta.
- Abaixo, "Planejado x realizado por safra" lista todas as safras com a
  situação de cada uma; clicar numa delas troca o card.
- Definir, editar (valor ≥ R$ 0,01 e percentual de aviso) e remover (com modal
  de confirmação) só valem depois da confirmação do servidor; erro de
  validação volta no campo.
- Lançar, editar ou excluir um custo recalcula o card.
- Safra finalizada fica somente leitura. Sem safra cadastrada, o card avisa e
  não abre a edição.
- O valor antigo do `localStorage` não é mais usado (o campo continua no cache
  só para não invalidar o que já estava gravado).

## Evidência

- `backend/servidor/testar.sh`: `TesteOrcamentos` com 41 verificações em memória
  (situação nos limites, todas as recusas, criação 201 e alteração 200,
  realizado igual ao resumo de custos, saldo, desvio e percentuais, proximidade
  e ultrapassagem, custo excluído saindo do realizado, isolamento entre
  propriedades, troca de contexto, remoção, exclusão de safra sem órfão e
  safra finalizada).
- `java -cp 'bin:lib/*' TesteOrcamentos --mongo` com MongoDB real (replica set):
  45 verificações, incluindo leitura por um repositório novo (reinício), segunda
  gravação sem duplicar, `criadoEm` preservado e o índice único recusando um
  segundo orçamento da mesma safra.
- `node tests/orcamento-real.cjs` (servidor Java em memória + Chromium): safra
  sem orçamento, validação local (≥ R$ 0,01), erro do servidor no campo,
  definição, custo lançado pela tela passando do aviso, planejado menor que o
  realizado, troca pelo filtro, recarga, remoção com confirmação e safra
  finalizada somente leitura. Com `UI_SCREENSHOTS=<pasta>` grava as telas.
- `ui-polish`, `local-screens`, `local-persistence`, `storage-scope` e
  `custos-real` continuam passando.
