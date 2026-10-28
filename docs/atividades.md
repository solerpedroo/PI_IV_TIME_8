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

Para preservar referências existentes, o servidor recusa renomear talhão com
atividades e recusa excluir safra vinculada ou alterar seu nome, cultura ou
remover talhões existentes. As validações compartilham sincronização dentro de
uma instância do servidor; concorrência entre várias instâncias ainda não tem
garantia transacional para esses vínculos.

## Limite deliberado

Concluir uma atividade ainda não gera consumo de estoque ou custo automaticamente:
esse ciclo transacional é da I20/#24. A tela não anuncia essa integração como se
ela existisse.

## Transições e decisão pendente

O servidor impede reabertura e qualquer alteração depois de conclusão ou
cancelamento. A definição formal do grafo entre estados não terminais permanece
em I01/#5; por isso esta entrega não promove uma sequência presumida a regra
aprovada. Menu, Lista, arraste e API passam pela mesma rota de status.

## Evidência

`backend/servidor/testar.bat` valida criação, edição, vínculo safra-talhão,
campos inválidos, três caminhos de status, cancelamento e histórico (319/319 na
execução de 28/10/2026). O repositório Mongo grava atividade e evento de
histórico na mesma transação; `TesteMongo` foi ampliado para reabrir a conexão e
conferir ambos quando houver um Mongo replica set disponível.
`tests/atividades-real.cjs` cobre navegador, filtros, falhas de API e reload;
requer Playwright disponível no ambiente de execução.

Para a #135, `node tests/atividades-reinicio.cjs` usa o Mongo local do Compose,
um banco isolado e reinicia o servidor Java. Em 28/10/2026 o teste confirmou
criação, edição, campos, vínculos, rejeições e consulta após reinício; o banco
temporário da execução foi removido ao final. A sincronização entre instâncias
distintas continua sendo um limite descrito acima.
