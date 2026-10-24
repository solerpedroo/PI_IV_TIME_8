# Persistência local dos dados operacionais

Miguel — RA: 25003110
Autor secundário: Pedro Henrique Contardi Soler - 25005592

Atualização da branch `feat/perfil`: dados pessoais, preferências da conta e
propriedade padrão usam a API Java, conforme [perfil.md](perfil.md). O antigo
`settings` local é preservado como legado, mas a tela não o usa para recuperar
perfil nem simular gravação. A documentação abaixo descreve o mecanismo local
dos módulos que ainda o utilizam.

O `localStorage` pertence ao navegador. Por isso, o acesso foi implementado em JavaScript nos scripts estáticos existentes:

- `scripts/data/operational-defaults.js` — dados de demonstração (carregados só nas telas operacionais).
- `scripts/agro-local.js` — API `AgroLocal` (leitura, gravação, validação, `connect`).
- `scripts/shell.js` — chrome do app (notificações, popovers, logout).

O servidor Java continua responsável pela camada de servidor e não participa da recuperação local. Não foram criados novos módulos funcionais ou endpoints.

## O que é salvo

Cada chave começa com `agro.operational.v1.`. O conteúdo tem o formato `{ "version": 1, "data": ... }`.

| Chave | Dados |
| --- | --- |
| `plots` | Identificador, nome, cultura, hectares e status dos talhões. |
| `activities` | Identificador, nome, tipo, talhão, data e status das atividades. Painel, Kanban e Lista compartilham os mesmos registros. |
| `stock` | Produtos, categorias, saldos, unidades, mínimos e movimentações com quantidade, data e fornecedor/destino. |
| `occurrences` | Ocorrências, severidade, status e histórico de atualizações e resolução. |
| `costs` | Lançamentos, orçamento e período selecionado. |
| `settings` | Perfil, propriedade, safra e preferências de notificação. |
| `notifications` | Identificação das notificações estáticas já lidas. |

Senhas, tokens, formulários de autenticação, HTML de telas e filtros temporários não fazem parte desses dados. A troca de senha continua dependendo de integração com o servidor; a interface informa que nenhuma senha foi alterada, em vez de simular sucesso.

## Salvamento e recuperação

As telas reaproveitam suas mutações e construtores atuais. Os registros são serializados em JSON; ao abrir a página, os dados são validados e os elementos da interface são recriados. As métricas são calculadas a partir dos registros, sem persistir contadores separados. Um identificador estável mantém o vínculo dos históricos e dos modais mesmo quando uma falha exige reconstruir a interface.

Sem uma chave salva, são usados os dados de demonstração que já existiam no projeto. Isso é a inicialização da tela, não uma recuperação confirmada. Uma coleção salva como `[]` permanece vazia, inclusive após excluir todos os registros: os exemplos não reaparecem.

Uma gravação somente é confirmada depois de `localStorage.setItem` concluir. Se houver falta de espaço, bloqueio de acesso, dados inválidos ou conflito com outra aba, a alteração é desfeita e aparece um aviso visível. A tela não anuncia sucesso. Em caso de JSON ou esquema inválido na leitura, os registros afetados não são apresentados como recuperados e o conteúdo original não é sobrescrito. Para orçamento e configurações, os valores iniciais servem apenas para manter a interface utilizável, acompanhados do aviso de falha.

O controle entre abas compara o conteúdo lido com o conteúdo existente antes de salvar. Uma aba desatualizada deve ser recarregada antes de tentar novamente; não há mesclagem automática. O painel também atualiza sua leitura quando recebe alterações de outra aba.

## Métricas e limites do escopo

- Áreas ativas contam talhões com status `Ativo`; hectares somam os registros existentes.
- Atividades pendentes incluem `Pendente` e `Agendada`; concluídas e em andamento têm contagens próprias.
- Estoque aplica a regra existente: saldo/mínimo abaixo de 0,6 é crítico; abaixo de 1 é baixo; a partir de 1 é normal. Entradas do mês são calculadas pelo histórico local dos produtos existentes.
- Ocorrências mantêm a regra atual: resolvidas representam a contagem dos registros resolvidos, sem implementar um novo relatório mensal.
- Custos e gráficos somam os lançamentos, sem totais históricos fictícios ocultos. O custo por hectare usa a área da propriedade nas configurações. O período selecionado é preservado; não foi criado um novo mecanismo de fechamento ou filtro contábil por safra.
- Concluir atividades ou movimentar estoque não cria automaticamente registros em outros módulos: as mensagens que afirmavam integrações simuladas foram corrigidas. Isso exigiria regras e vínculos adicionais fora desta task.

Os dados são locais a este navegador e à mesma origem do site (protocolo, domínio e porta). Limpar os dados do site os remove. Não existe sincronização com servidor ou entre dispositivos. A recuperação não precisa do backend nem de rede, desde que os arquivos estáticos da aplicação estejam disponíveis no navegador ou sejam servidos localmente. Esta task não instala um service worker nem promete abrir um site ainda não carregado sem conexão.

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
