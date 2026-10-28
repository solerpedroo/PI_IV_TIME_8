<!-- Miguel — RA: 25003110 -->
# Atividades — I13/#17

O planejamento e a execução de atividades usam a API Java autenticada. O cartão
da interface só muda depois da confirmação do servidor; isso vale igualmente
para menu, Lista, arraste, conclusão e cancelamento.

## Campos e vínculos

Uma atividade armazena nome, tipo, talhão, safra opcional, cultura derivada da
safra, data, horário, responsável, prioridade e status. A API aceita uma safra
somente se ela pertence à propriedade ativa e contém o talhão informado. O
histórico mantém o evento inicial e as mudanças de status.

## Limite deliberado

Concluir uma atividade ainda não gera consumo de estoque ou custo automaticamente:
esse ciclo transacional é da I20/#24. A tela não anuncia essa integração como se
ela existisse.

## Evidência

`backend/servidor/testar.bat` valida criação, edição, vínculo safra-talhão,
campos inválidos, três caminhos de status, cancelamento e histórico (319/319 na
execução de 28/10/2026). `tests/atividades-real.cjs` cobre navegador, filtros,
falhas de API e reload; requer Playwright disponível no ambiente de execução.
