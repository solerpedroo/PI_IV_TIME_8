<!-- Miguel — RA: 25003110 -->
# Relatório de atividades — #174

Em **Atividades → Relatório de atividades**, selecione o período inclusivo das datas planejadas. O relatório consulta somente a propriedade autenticada e apresenta o status atual: concluídas, pendentes (`Pendente` ou `Agendada`) e atrasadas. Atrasada é qualquer atividade não concluída nem cancelada cuja data planejada seja anterior ao dia da consulta em `America/Sao_Paulo`; pode coincidir com pendente ou estar em andamento. Atividades em andamento que ainda não estão atrasadas ficam fora desse recorte. Não se presume status histórico ou data de conclusão. Datas ausentes, inválidas ou sem ano ficam como pendências.

O CSV contém o mesmo conjunto da tela, com identificação da propriedade, período, dia da consulta, estados e indicadores. Falhas de fonte, mudança de período/contexto e respostas atrasadas impedem a exportação de resultado desatualizado.

Validação reproduzível: `sh backend/servidor/testar.sh` com Java 21; para o teste real com Mongo e navegador, siga [relatorio-safra.md](relatorio-safra.md). Foram executadas 49 verificações nomeadas no fluxo integrado: atividades em todos os estados, data de hoje, atraso, período vazio, datas inválidas e sem ano, isolamento, CSV, reload e reinício do processo Java usando Mongo persistente; além das regressões dos demais relatórios. O processo Mongo não foi reiniciado. Revisão independente Sol 6.1 medium não identificou defeito crítico ou alto.
