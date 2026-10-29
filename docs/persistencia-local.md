# Persistência local dos dados operacionais

Miguel — RA: 25003110
Autor secundário: Pedro Henrique Contardi Soler - 25005592

Atualização de atividades em 28/10/2026: `atividades.html` passou a consultar a
API Java para criação, edição, status, cancelamento e histórico. O cache
`activities` é apenas uma cópia de leitura por usuário/propriedade e não confirma
nenhuma mutação quando há sessão autenticada.

Atualização de estoque em 24/10/2026: `insumos.html` passou a consultar a API
Java e o MongoDB, conforme [estoque.md](estoque.md). Esta documentação de
armazenamento local descreve os demais módulos. A chave `stock` legada fica
intacta, mas não alimenta o catálogo persistido e não é importada automaticamente.

Atualização da branch `feat/perfil`: dados pessoais, preferências da conta e
propriedade padrão usam a API Java, conforme [perfil.md](perfil.md). O antigo
`settings` local é preservado como legado, mas a tela não o usa para recuperar
perfil nem simular gravação. A documentação abaixo descreve o mecanismo local
dos módulos que ainda o utilizam.

Autor Secundário: Pedro Henrique Contardi Soler - 25005592

Atualização da branch de ocorrências (24/10/2026): o módulo e os alertas de
ocorrências do Dashboard agora usam a API Java/MongoDB. Seu cache local legado é
ignorado e preservado. Consulte [ocorrencias.md](ocorrencias.md) para contrato,
histórico, indicador mensal e validação real; as regras locais abaixo se aplicam
aos módulos que continuam usando `AgroLocal`.

O `localStorage` pertence ao navegador. Por isso, o acesso foi implementado em JavaScript nos scripts estáticos existentes:

- `frontend/scripts/data/operational-defaults.js` — dados de demonstração (carregados só nas telas operacionais).
- `frontend/scripts/agro-local.js` — API `AgroLocal` (leitura, gravação, validação, `connect`).
- `frontend/scripts/shell.js` — chrome do app (notificações, popovers, logout).

Nos módulos que ainda usam `AgroLocal`, a recuperação é feita no navegador.
Propriedades, talhões e ocorrências também possuem APIs, com regras próprias
documentadas no servidor; o armazenamento local não comprova sua persistência.

## O que é salvo

Cada chave começa com `agro.operational.v1.`. O conteúdo tem o formato `{ "version": 1, "data": ... }`.

| Chave | Dados |
| --- | --- |
| `plots` | Identificador, nome, cultura, hectares e status dos talhões. |
| `activities` | Cache de leitura da API, separado por usuário/propriedade. Inclui planejamento, vínculos e status, mas não confirma gravações autenticadas. |
| `stock` | Legado preservado; não é mais lido nem escrito pela tela de insumos. |
| `occurrences` | Legado preservado; não é mais lido nem escrito pela tela de ocorrências. |
| `costs` | Lançamentos, orçamento e período selecionado. |
| `settings` | Perfil, propriedade, safra e preferências de notificação. |
| `notifications` | IDs estáveis dos alertas já lidos (`stock:…`, `atraso:…`, `hoje:…`, `ocorrencia:…`). Ver [notificacoes.md](notificacoes.md). |

Senhas, tokens, formulários de autenticação, HTML de telas e filtros temporários não fazem parte desses dados. A troca de senha continua dependendo de integração com o servidor; a interface informa que nenhuma senha foi alterada, em vez de simular sucesso.

## Salvamento e recuperação

As telas reaproveitam suas mutações e construtores atuais. Os registros são serializados em JSON; ao abrir a página, os dados são validados e os elementos da interface são recriados. As métricas são calculadas a partir dos registros, sem persistir contadores separados. Um identificador estável mantém o vínculo dos históricos e dos modais mesmo quando uma falha exige reconstruir a interface.

Sem uma chave salva, são usados os dados de demonstração que já existiam no projeto. Isso é a inicialização da tela, não uma recuperação confirmada. Uma coleção salva como `[]` permanece vazia, inclusive após excluir todos os registros: os exemplos não reaparecem.

Uma gravação somente é confirmada depois de `localStorage.setItem` concluir. Se houver falta de espaço, bloqueio de acesso, dados inválidos ou conflito com outra aba, a alteração é desfeita e aparece um aviso visível. A tela não anuncia sucesso. Em caso de JSON ou esquema inválido na leitura, os registros afetados não são apresentados como recuperados e o conteúdo original não é sobrescrito. Para orçamento e configurações, os valores iniciais servem apenas para manter a interface utilizável, acompanhados do aviso de falha.

O controle entre abas compara o conteúdo lido com o conteúdo existente antes de salvar. Uma aba desatualizada deve ser recarregada antes de tentar novamente; não há mesclagem automática. O painel também atualiza sua leitura quando recebe alterações de outra aba.

## Métricas e limites do escopo

- Áreas ativas contam talhões com status `Ativo`; hectares somam os registros existentes.
- Atividades pendentes incluem `Pendente` e `Agendada`; concluídas e em andamento têm contagens próprias.
- Estoque usa indicadores e regras do servidor documentados em `estoque.md`, sem a regra mock 0,6.
- Ocorrências usam o resumo da API, com mês/ano da resolução e contexto da consulta, conforme `ocorrencias.md`.
- Custos e gráficos somam os lançamentos, sem totais históricos fictícios ocultos. O custo por hectare usa a área da propriedade nas configurações. O período selecionado é preservado; não foi criado um novo mecanismo de fechamento ou filtro contábil por safra.
- Concluir atividades não cria automaticamente consumo de estoque ou custo: essa integração pertence à I20/#24 e não é simulada pela interface.

Os dados dos módulos locais são limitados a este navegador e à mesma origem
(protocolo, domínio e porta). Estas limitações não descrevem o estoque atual,
que exige API e guarda catálogo/histórico no MongoDB. Esta task não instala
service worker nem promete abrir um site ainda não carregado sem conexão.

## Validação reproduzível

Os testes abrem o Edge real com Playwright e interceptam a entrega dos arquivos estáticos diretamente do repositório. Não iniciam backend Java, gateway ou API. As dependências de teste podem ficar fora do repositório:

```bash
npm install --no-save playwright
npx playwright install chromium
node tests/local-persistence.cjs
node tests/local-screens.cjs
```

No Windows, para usar o Edge em vez do Chromium empacotado:

```powershell
$env:BROWSER_PATH = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
node tests/local-persistence.cjs
```

Execute na raiz do projeto. O CI (workflow `local-persistence.yml`) roda os mesmos testes com Chromium no Ubuntu. Os testes verificam fluxos operacionais, reload, recuperação sem backend, coleções vazias, JSON/esquemas inválidos e falhas de armazenamento. Os arquivos de código alterados trazem a identificação de autoria no início e comentários em português.
