# Agro — frontend estático

Implementação das telas de **autenticação**, do **Painel Geral (Dashboard)** e dos módulos operacionais **Áreas de Cultivo**, **Atividades**, **Insumos** e **Ocorrências**, alinhada ao design system **Forest Sage** do pen.dev (`designSystemProjetoAgro`).

## Estrutura

```text
frontend_pi_iv/
├── login.html
├── criar-conta.html
├── recuperar-senha.html
├── redefinir-senha.html
├── confirmar-email.html
├── dashboard.html          # Painel Geral (frame ajDH7)
├── areas-cultivo.html      # Áreas de Cultivo — talhões (frame TaqqK)
├── atividades.html         # Atividades — Kanban + Lista (frame U6jZ05)
├── insumos.html            # Insumos — controle de estoque (frame QpBVa)
├── ocorrencias.html        # Ocorrências — registro + timeline (frame UX9SC)
├── styles/
│   ├── base.css            # tokens + componentes compartilhados
│   ├── auth.css            # layout das telas de autenticação
│   ├── shell.css           # sidebar, header, popovers, chrome
│   ├── dashboard.css       # métricas, tabela, alertas, culturas (Painel)
│   ├── modules.css         # padrões compartilhados dos 4 módulos novos:
│   │                       # filtros, menu de ações "⋮", meta-grid, timeline,
│   │                       # kanban, segmented control, textarea group
│   ├── areas-cultivo.css   # mapa esquemático + tabela de talhões
│   ├── atividades.css      # calendário da semana + toggle kanban/lista
│   ├── insumos.css         # tabela de estoque + rastreio operacional
│   ├── ocorrencias.css     # ficha de detalhe + timeline
│   ├── feedback.css        # toasts, empty, skeleton, modal
│   └── motion.css          # animações / prefers-reduced-motion
└── scripts/
    ├── auth.js             # validação e fluxos de auth
    ├── toast.js            # API AgroToast (4 variantes do pen)
    ├── select.js           # Dropdown Forest Sage (substitui select nativo)
    ├── shell.js            # notificações, perfil, logout, popovers genéricos
    ├── dashboard.js        # mock G4, busca, nova atividade (Painel)
    ├── areas-cultivo.js    # CRUD mock de talhão, filtro por cultura
    ├── atividades.js       # Kanban/Lista, concluir/duplicar/excluir
    ├── insumos.js          # entrada/saída, status automático, exclusão condicional
    └── ocorrencias.js      # registrar, timeline, resolver/excluir
```

## Como executar

```bash
python -m http.server 5500
```

Acesse `http://localhost:5500/login.html` (login redireciona ao painel) ou diretamente qualquer tela, ex.: `http://localhost:5500/dashboard.html`.

## Fluxos

```text
login.html ──sucesso──► dashboard.html
dashboard.html ──Sair──► login.html (+ toast “Você saiu com segurança.”)

Sidebar (Painel · Áreas de Cultivo · Atividades · Insumos · Ocorrências):
 todas navegáveis de verdade entre si. Custos e Configurações seguem
 como "em preparação" (fora desta entrega).

Áreas de Cultivo:
 · Novo Talhão / Editar → modal validado → tabela + métricas atualizadas
 · Menu "⋮" por linha → Ver atividades / Ver ocorrências (navega já
   filtrado por ?talhao=) / Ver custos (toast — módulo futuro)

Atividades:
 · Kanban (Pendente/Agendada/Em andamento/Concluída) com toggle para Lista
 · Nova Atividade → card em "Pendente" + toast
 · Menu "⋮" do card → Ver detalhes / Editar / Concluir (move de coluna +
   baixa simulada de insumo) / Duplicar (form pré-preenchido) / Excluir
 · Chip "Talhão: X" quando aberto via ?talhao= (vindo de Áreas de Cultivo)

Insumos:
 · Alerta de estoque crítico no topo (recalculado dinamicamente)
 · Registrar Entrada (cabeçalho, produto livre) / linha (produto fixo)
 · Registrar Saída valida saldo disponível e recalcula status
   (Normal/Baixo/Crítico)
 · Excluir só é permitido com saldo = 0

Ocorrências:
 · Registrar Ocorrência (tipo, talhão, prioridade em segmented control)
 · Ficha de detalhe com linha do tempo + "Publicar atualização"
 · Resolver (confirmação) e Excluir (confirmação destrutiva)
 · Chip "Talhão: X" quando aberto via ?talhao=
```

## Toasts (Flow E)

| Tipo | Uso |
|------|-----|
| success | Registro salvo, atividade concluída, ocorrência resolvida, login |
| error | Validação de formulário (G3), falha de regra (ex.: excluir com saldo > 0) |
| warning | Estoque baixo/crítico após uma saída |
| info | Módulo em preparação, exclusões (reversível apenas na sessão), notificação aberta |

API: `AgroToast.show({ type, title, description, duration })`.

## Padrões reaproveitados entre os módulos (`styles/modules.css`)

- **Barra de filtros**: chips (`.filter-chip`) para categoria/cultura e
  selects Forest Sage para status/tipo.
- **Menu de ações "⋮"**: usa a mesma infraestrutura de popover do
  `shell.js` (`data-popover-trigger` / `data-popover`) — nenhum JS extra
  é necessário para abrir/fechar/clicar fora/Escape.
- **Meta-grid, Textarea Group, Segmented control, Timeline, Kanban**:
  componentes descritos nas seções G7/G8/G9 e nos boards "Fluxos de
  Interação" do pen.dev, implementados uma vez e reaproveitados.

## Fora desta entrega

Custos, Relatórios, Safras, Meu Perfil, Configurações e mobile — os
links do shell mostram toast "em preparação". Páginas de detalhe em
tela cheia (ex.: `/talhao/:id`, `/atividade/:id`) foram implementadas
como **modais** dentro da própria lista, não como rotas separadas —
decisão de escopo para manter a entrega em HTML/CSS/JS estático sem
roteador client-side.

## Dados

Mocks locais híbridos em cada `*.js` / HTML. Toda mutação (criar,
editar, concluir, resolver, excluir, entrada/saída de estoque)
acontece em memória/DOM — nada é persistido entre reloads. Estrutura
pronta para trocar por `fetch` quando a API existir; a anatomia de
métricas, toasts e menus de ação já está desacoplada dos dados.
