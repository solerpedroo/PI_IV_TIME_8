# 2026-08-15 — Novas telas: Áreas de Cultivo, Atividades, Insumos, Ocorrências

[← índice de changelogs](README.md)

### Contexto / pedido original

Usuário pediu para implementar 4 telas do design system (`designSystemProjetoAgro`,
JSON do pen.dev na raiz do repo) que ainda não existiam no frontend: **Áreas de
Cultivo**, **Atividades**, **Insumos**, **Ocorrências**. Pedido explícito de:
analisar o design system a fundo (seções "Telas Navegáveis", "Fluxos
Complementares — Lacunas UX", "Fluxos de Interação") antes de codar, seguir o
padrão de qualidade do código já existente (dashboard.html/js/css), documentar
bem, e fechar os critérios de aceite de cada tela (toasts, ícones, validação,
estados vazios etc.).

Depois de uma primeira entrega, o usuário mandou 8 screenshots de referência
das telas "desejadas" (Painel, Áreas de Cultivo, Atividades, Insumos,
Ocorrências, Custos, Configurações — as duas últimas fora de escopo, só
contexto visual) e pediu ajustes. Em seguida pediu para abrir localmente e
**testar de verdade** as 4 telas — o que levou à instalação do Playwright
(rede estava disponível nessa sessão, apesar de não estar no início) e a uma
rodada de debugging que encontrou e corrigiu 7 bugs reais que as checagens
estáticas (HTML balanceado, `node --check`, assets 200) não pegavam.

### Arquivos novos

```
areas-cultivo.html / scripts/areas-cultivo.js / styles/areas-cultivo.css
atividades.html    / scripts/atividades.js    / styles/atividades.css
insumos.html        / scripts/insumos.js       / styles/insumos.css
ocorrencias.html    / scripts/ocorrencias.js   / styles/ocorrencias.css
styles/modules.css   — padrões compartilhados pelas 4 telas (ver CLAUDE.md)
CLAUDE.md            — handoff + arquitetura
changelogs/           — pasta de changelogs (este arquivo mora aqui)
```

### Arquivos existentes alterados

- **`dashboard.html`**: os 4 links da sidebar que apontavam para toast "em
  preparação" (Áreas de Cultivo, Atividades, Insumos, Ocorrências) agora são
  `<a href>` reais; o link "Ver todas" do card de atividades aponta para
  `atividades.html`.
- **`scripts/shell.js`**: mudanças estruturais, não só cosméticas —
  - `initPopoverTriggers` reescrito de `addEventListener` por elemento para
    **delegação no `document`**: triggers `[data-popover-trigger]` criados
    dinamicamente (nova linha de talhão, novo card de atividade, nova
    ocorrência) agora funcionam sem re-inicialização.
  - Popover que abre dentro de um ancestral com `overflow: auto/hidden/scroll`
    (qualquer tabela ou coluna kanban com scroll próprio) é promovido para
    `position: fixed` com coordenadas calculadas do trigger
    (`findScrollClipAncestor` / `positionPopoverFixed` / `resetPopoverPosition`),
    senão o popover renderiza cortado/invisível.
  - Novo listener: clicar em qualquer `[role="menuitem"]` fecha todos os
    popovers automaticamente — necessário porque nem todo item de menu abre
    um modal (ex.: "Excluir" bloqueado por regra em Insumos só mostra um
    toast), e sem isso o menu ficava aberto, flutuando em `position: fixed`
    por cima de outras linhas e interceptando cliques.
  - `window.AgroPopover = { closeAll: closeAllPopovers }` exposto para uso
    futuro por scripts de página.
- **`styles/base.css`**: adicionada regra global `[hidden] { display: none
  !important; }` (ver "Bugs encontrados" abaixo — corrige também um bug
  preexistente no `.notif-list` do Dashboard, não introduzido nesta sessão).
- **`README.md`**: reescrito para documentar as 4 telas novas, os fluxos, os
  padrões compartilhados e a decisão de escopo (fichas de detalhe são modais,
  não rotas separadas).

### Bugs reais encontrados testando com Playwright (todos corrigidos)

Fica registrado porque são bugs de **classe recorrente** — se a próxima
sessão adicionar uma 5ª tela de módulo, é fácil reintroduzir a mesma forma
de bug:

1. **`.empty-state` sempre visível.** `display: flex` na classe tinha a
   mesma especificidade do `[hidden]` da UA stylesheet; como o CSS do autor
   carrega depois, `hidden` perdia. Corrigido com a regra global em
   `base.css` (ver acima) em vez de overrides pontuais por componente.
2. **Menu "⋮" cortado/invisível** quando a linha/card estava perto do fim de
   um container com scroll próprio (`.table-wrap`, `.kanban-cards`).
   Corrigido em `shell.js` (reposicionamento `fixed`, ver acima).
3. **Menu "⋮" de elementos criados após o load não abria** — clique não
   registrava porque o listener só era ligado nos elementos existentes no
   boot. Corrigido com delegação de evento (ver acima).
4. **Select "Produto" do modal de Entrada (Insumos) nunca mostrava opções.**
   `insumos.js` populava `<option>`s via JS depois que `select.js` já tinha
   montado o dropdown customizado a partir do HTML original (quase vazio).
   Corrigido removendo `data-enhance-select` do HTML e chamando
   `AgroSelect.enhance()` manualmente em `insumos.js`, depois de popular as
   opções.
5. **🔴 Mais grave: grid de métricas colapsando o card da tabela a ~2px de
   altura.** `.metrics-row` (grid de 4 `.metric-card`) só tinha `display:
   grid` + `flex: 0 0 auto` definidos em `dashboard.css`, que
   `atividades.html`/`insumos.html`/`ocorrencias.html` não carregam. Sem
   `display: grid`, os cards empilhavam verticalmente como blocos comuns; sem
   `flex: 0 0 auto`, essa pilha (bem mais alta que o esperado) competia por
   espaço com o card da tabela abaixo dentro do `.app-main` (flex column,
   `overflow: hidden`) e o espremia a ~2px — a tabela ficava tecnicamente no
   DOM, com linhas "clicáveis" via seletor, mas fisicamente invisível/
   inacessível na tela. Corrigido movendo `.metrics-row` para
   `styles/modules.css` (compartilhado pelas 4 telas novas).
6. **Menu de ação deixado aberto após uma exclusão bloqueada** (ver item da
   lista de `shell.js` acima) — causava interceptação de clique em linhas
   subsequentes.
7. **Botão "Registrar Ocorrência" não fazia nada.** `ocorrencias.js` nunca
   ligou o clique do botão `[data-open-modal="occ-form"]` a `openModal()`.
   Corrigido.

Bug menor: plural errado ("1 talhõ encontrado" → "1 talhão encontrado") em
`areas-cultivo.js`.

### Ajustes de fidelidade visual (comparando com as screenshots de referência do usuário)

- Adicionado botão "Exportar" (outline, toast "em preparação") no cabeçalho
  de `atividades.html`, presente no design original e na referência mas
  ausente na primeira entrega.
- Chip "Talhão: —" que aparecia mesmo sem filtro ativo (mesma causa-raiz do
  bug 1) — resolvido pela regra global de `[hidden]`.

### Decisões de escopo tomadas (documentadas também no README/CLAUDE.md)

- **Fichas de detalhe são modais dentro da própria lista**, não páginas/rotas
  separadas (ex.: não existe `talhao.html?id=`) — mantém a entrega em HTML
  estático sem roteador client-side. As telas de detalhe completas descritas
  no pen.dev ("3 · Talhão e Safra: detalhes e ações", "4 · Atividade: detalhe
  + concluir + duplicar", "5 · Ocorrência, Insumo e Relatórios") foram usadas
  como referência de conteúdo/campos para os modais, não implementadas como
  telas cheias.
- **Custos e Configurações continuam fora de escopo** — sidebar linka para
  elas como "em preparação" (toast), igual ao padrão já existente no
  Dashboard antes desta sessão.
- **Regra de status de estoque em Insumos** (`insumos.js`, função
  `statusFor`): saldo/mínimo < 0.6 → Crítico, < 1.0 → Baixo, ≥ 1.0 → Normal.
  Os valores iniciais mockados do pen.dev não seguem exatamente essa fórmula
  (ex.: Inseticida Lambda tem saldo acima do mínimo mas status "Baixo" no
  mock original) — a fórmula só passa a valer para mutações feitas nesta
  tela (entrada/saída), não altera os badges iniciais copiados do design.
- **"Resolvidas (Mês)" em Ocorrências** reflete a contagem atual de linhas
  com status "Resolvida" na tabela, não um recorte histórico por mês real
  (não há dado de mês na camada mock).

### Estado atual / o que falta

- As 4 telas estão funcionalmente completas e testadas ponta a ponta
  (criar/editar/filtrar/concluir/duplicar/excluir/resolver, conforme o
  módulo) via Playwright, sem erros de console.
- **Ambiente de teste não é permanente**: Playwright foi instalado em
  `/tmp/.../scratchpad/pwtest` (fora do repo, em diretório temporário da
  sessão) — não está disponível por padrão em uma sessão nova. Se a próxima
  sessão precisar testar interativamente, repetir: `npm install playwright`
  num dir de scratch + `npx playwright install --with-deps chromium`
  (precisa de rede; nem toda sessão tem rede liberada — testar com `curl -sI
  https://registry.npmjs.org` antes de assumir que vai funcionar).
- Nenhum commit foi feito nesta sessão — todas as mudanças estão no working
  tree (`git status` mostra os arquivos novos como `??` e os modificados como
  `M`). Ainda na branch `main`.
- Não foi feita revisão de acessibilidade/mobile das 4 telas novas além do
  que já vem "de graça" por reaproveitar componentes existentes
  (`role="menuitem"`, `aria-expanded`, `aria-hidden` nos modais). Não há
  breakpoint mobile dedicado para kanban/timeline — só os breakpoints
  genéricos herdados de `shell.css`/`modules.css`.
- `designSystemProjetoAgro` aparece como modificado no `git status` (diff
  gigante, "todas as linhas trocadas") desde antes desta sessão — é
  provavelmente uma normalização de encoding/line-ending, não conteúdo
  reescrito; não investigado nesta sessão, não é uma mudança feita por ela.
