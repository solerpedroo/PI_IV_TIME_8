# Revisão de dados reais e interface

Base: `origin/dev` em `48e6c3f`, atualizada em 04/11/2026.

- Coleções iniciais e tabelas operacionais vazias; exemplos permanecem somente nas fixtures dos testes.
- Painel consulta atividades, talhões e estoque na API; criação e conclusão aguardam confirmação remota.
- Falhas de leitura não restauram registros antigos de demonstração. Falha de cache não desfaz uma gravação já confirmada no servidor.
- Requisições usam a origem da página, permitindo HTTPS e portas alternativas. Erros temporários na verificação de sessão preservam o token; 401/403 redirecionam para login.
- Geist variável local (com licença OFL) no aplicativo e nos slides, inclusive valores numéricos e elementos de código.
- Exclusão de propriedades bloqueada quando há safras ou equipamentos; criação concorrente de sessões respeita o limite de cinco.
- Inicialização do servidor de algoritmos corrigida quando os scripts são chamados da raiz.

## Validação

Executados com JDK 21 e Chromium/Playwright:

- Suítes Java da API, ServerSocket e gateway.
- Sete verificadores do core compilados diretamente com `javac`, usando os drivers Mongo já incluídos na API.
- E2E reais em memória: login (porta 18080, incluindo atividade pelo painel), talhões, atividades, calendário, custos, colheita, georreferenciamento, manutenção, notificações, ocorrências, orçamento e perfil.
- Interface em sete dimensões desktop, formulários e navegação; persistência local e isolamento por conta/propriedade.
- `tests/empty-state-origin.cjs`: HTTPS em porta alternativa, fonte local carregada, coleções vazias, cache antigo ignorado, falha de sessão 503 e expiração 401.
- Sintaxe de todos os scripts do frontend e `git diff --check`.

Os testes usaram servidores isolados em memória. As suítes específicas de MongoDB/Atlas, reinício com persistência em disco e envio SMTP real não foram executadas nesta revisão.
