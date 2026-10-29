<!-- Miguel — RA: 25003110 -->
# Relatório de estoque — #173

Em **Insumos → Relatório de estoque**, selecione o período inclusivo. O relatório usa a propriedade da sessão e apresenta separadamente os saldos atuais por produto e as movimentações no período, em fuso `America/Sao_Paulo`. Entradas e saídas preservam quantidade e unidade; ajustes, incluindo saldo inicial, representam saldo contado e não são classificados como entrada ou saída. Produtos arquivados mantêm seu histórico. Dados inválidos e vínculos indisponíveis são pendências. O CSV contém o mesmo conjunto da tela.

A consulta coordena a leitura com as escritas de estoque da mesma instância Java. Em múltiplas instâncias não há snapshot transacional; uma alteração simultânea pode exigir nova consulta. Os saldos são atuais, não históricos, e unidades distintas não são somadas.

Validação reproduzível: compile e execute `sh backend/servidor/testar.sh`; com Java 21, MongoDB local em replica set e Playwright, siga [relatorio-safra.md](relatorio-safra.md) para `tests/relatorio-safra-real.cjs`. Os casos incluem saldo conhecido, entrada, saída, ajustes, produto arquivado, período vazio, virada de data UTC/São Paulo, vínculo órfão, dados inválidos, isolamento, CSV, reload e reinício do servidor Java com Mongo persistente. O processo Mongo não foi reiniciado. A revisão independente Sol 6.1 medium encontrou concorrência na mesma instância e tratamento de dados inválidos, corrigidos nesta entrega.
