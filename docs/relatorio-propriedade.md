<!-- Miguel — RA: 25003110 -->
# Relatório da propriedade — #171

Na tela **Painel Geral**, abra **Relatório da propriedade**, informe as datas inicial e final e consulte. O resultado separa os indicadores atuais (quantidade de talhões, área atual dos talhões, quantidade de safras e de produtos ativos no estoque) dos registros de atividades, ocorrências e custos no período inclusivo. Custos gerais da propriedade são incluídos; não há soma de quantidades de estoque com unidades distintas, cálculo de produtividade nem reconstrução histórica de saldos.

O endpoint `GET /api/relatorios/propriedade?de=AAAA-MM-DD&ate=AAAA-MM-DD` exige autenticação e usa a propriedade da sessão. Ocorrências são restritas ao usuário e à propriedade, respeitando exclusão lógica. Registros com data ausente, inválida ou sem ano aparecem em pendências e não entram nos totais; datas completas fora do período ficam fora do resultado. Custos são somados em centavos. Os indicadores atuais não são filtrados pela data; um período sem registros pode conter indicadores atuais e pendências.

A exportação CSV contém o mesmo resultado exibido, com identificação da propriedade, datas, indicadores atuais, contagens, registros e pendências. A troca de sessão/propriedade invalida a exportação. O relatório falha quando uma fonte falha; não transforma erro em conjunto vazio. As leituras dos módulos não são uma transação global: alterações concorrentes podem exigir nova consulta.

## Evidência e reprodução

Com Java 21, execute `sh backend/servidor/testar.sh`. Para o fluxo real, siga as instruções em [relatorio-safra.md](relatorio-safra.md): o teste `tests/relatorio-safra-real.cjs` também cobre a propriedade com Java, MongoDB local isolado e Chromium. O teste cria um banco aleatório e remove apenas esse banco ao terminar.

Validação executada: regressões Java (335 testes do servidor, 49 de perfil, 61 de ocorrências e 22 do relatório de safra) e 49 verificações nomeadas no fluxo real. Para a propriedade, conferiu-se o conjunto conhecido de 2 talhões e 17,5 ha, 3 safras, 2 atividades, 1 ocorrência, 3 custos e total R$ 999,30; período vazio, pendência por data sem ano, datas inválidas, isolamento, CSV fiel, reload, troca de contexto e reinício do processo Java com Mongo persistente. O processo Mongo não foi reiniciado. O fluxo também verifica que o relatório de safra continua funcionando após compartilhar o utilitário de CSV.

## Matriz da frente

| Critério | Situação na branch |
| --- | --- |
| Resumo da propriedade sobre registros reais, período explícito e CSV correspondente | Implementado e validado |
| Casos inválidos, conjunto conhecido e vazio, reload e reinício Java | Validados conforme o teste reproduzível |
| Integração à `dev` | Pendente de revisão e merge do PR #220 |
| Relatórios detalhados de estoque (#173) e atividades (#174) | Implementados nesta branch; aguardam integração |
