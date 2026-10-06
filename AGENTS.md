# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this is

Static frontend (no build step, no framework, no package manager) for **AgroGestão**, an agricultural management platform. See `AgroGestao_Projeto.md` for the full product spec and `README.md` for the implemented-screen inventory and flows. This repo only implements a slice of that spec: auth screens, Dashboard, and four operational modules (Áreas de Cultivo, Atividades, Insumos, Ocorrências). Custos, Relatórios, Safras, Meu Perfil, Configurações and mobile are out of scope — their nav links show an "em preparação" toast instead of navigating.

The design system ("Forest Sage") is authored in pen.dev and exported as `designSystemProjetoAgro`, a large JSON file at the repo root (~5MB, one JSON object per screen/frame). It is the source of truth for tokens, copy, icons, and interaction flows — **read it before implementing or changing a screen**, don't guess at pen.dev content. It's too large to `cat`; extract it with a small Python script (walk `children`, print `type`/`name`/`content`/`icon`, resolve `type: "ref"` nodes via their `ref` id + `descendants` overrides) rather than reading it raw.

## Commands

There is no build/lint/test tooling — plain HTML/CSS/JS served statically.

```bash
python -m http.server 5500        # serve the site
# open http://localhost:5500/login.html (redirects to dashboard.html on submit)
# or open any screen directly, e.g. http://localhost:5500/dashboard.html

node --check scripts/whatever.js  # syntax-check a script (no bundler/transpiler exists)
```

### Backend foundation (`backend/`)

The static frontend still uses local mocks and does **not** call the API yet. A separate backend stack lives under `backend/` — see **`backend/README.md`** for prerequisites, env vars (`.env.example` at repo root), Docker Compose MongoDB, and health checks.

```text
frontend (HTML/CSS/JS mocks)
    ↓ future integration
backend/gateway/   Node.js + TypeScript — GET /health, GET /ready (probes Java)
    ↓
backend/core/      Java 21 + Spring Boot — Actuator + MongoDB connection config
    ↓
compose.yaml       MongoDB 8 (local dev)
```

Gateway tests: `cd backend/gateway && npm ci && npm test`. Java tests: `cd backend/core && mvn test` (context test excludes Mongo auto-config; runtime still needs Mongo for a healthy core).

There's no headless browser tool installed by default (no Playwright/Puppeteer/chromium-cli). To actually click through a screen: `npm install playwright` in a scratch dir, then `npx playwright install --with-deps chromium` (needs network access) before scripting interactions. See "Verifying a screen" below for why this matters — static checks (HTML tag balance, `node --check`, asset 200s) do **not** catch most real bugs here.

## Architecture

**Every HTML page is self-contained and duplicates its own shell markup** (sidebar nav + header + logout modal). There is no templating, no includes, no SSR. When adding a new screen, copy the sidebar/header block from an existing screen (e.g. `atividades.html`) rather than inventing new markup, and update the `is-active` link + page title.

### Script loading order matters, and it's always this order:
```html
<script src="scripts/toast.js" defer></script>
<script src="scripts/select.js" defer></script>
<script src="scripts/shell.js" defer></script>
<script src="scripts/<page>.js" defer></script>
```
All four are deferred, so they run in this order on `DOMContentLoaded`, before the page paints. `select.js`'s `AgroSelect.init()` enhances every `select[data-enhance-select]` **present in the DOM at that moment** — if a page-specific script populates a select's `<option>`s dynamically (see `insumos.js`'s product picker), do it *before* calling `AgroSelect.enhance()` manually and **omit** `data-enhance-select` from that select's markup, or the custom dropdown will be built from whatever options existed at load time and silently ignore ones added later.

### Shared infrastructure lives in `scripts/shell.js` and `styles/modules.css` — reuse it, don't reinvent

- **Popovers/dropdowns** (profile menu, notifications, every row/card "⋮" action menu across the 4 modules): mark the trigger `data-popover-trigger aria-controls="<id>"` and the panel `data-popover id="<id>"`. `shell.js` handles open/close, click-outside, Escape, arrow-key navigation between `[role="menuitem"]` children, and — critically — **clipping avoidance**: if the trigger sits inside a `overflow: auto/hidden/scroll` ancestor (any scrollable table or kanban column), the popover is promoted to `position: fixed` with coordinates computed from the trigger's rect, otherwise it would render invisible/clipped. All of this is delegated on `document`, so triggers created dynamically after page load work with zero extra wiring. `window.AgroPopover.closeAll()` is exposed for cases where a menu-item click resolves to a toast instead of a modal (the menu must still close, or it lingers as a fixed-position overlay swallowing clicks on rows beneath it — see changelogs/2026-08-15-modulos-operacionais.md for how this bit us).
- **Toasts**: `window.AgroToast.show({ type: 'success'|'error'|'warning'|'info', title, description, duration })`, defined in `toast.js`.
- **Modals**: no shared open/close helper — each page script defines its own local `openModal`/`closeModal` (add/remove `.is-open` + `aria-hidden`) and wires `[data-close-modal]` / backdrop-click. This is intentionally duplicated per page (small, and each page's modal set differs).
- **Custom select**: `<select data-enhance-select data-placeholder="…">` gets replaced visually by `select.js` with a Forest-Sage-styled dropdown; the native `<select>` stays in the DOM (hidden) as the source of truth — read/write `.value` on it normally, dispatch a `change` event after setting it programmatically to resync the visual trigger.
- **`.metrics-row`, `.data-table`, filter chips (`.filter-chip`), the row/card action-menu markup, `.meta-grid`, `.timeline`, `.kanban-*`, `.segmented`, `.textarea-group`** all live in `styles/modules.css`, shared by the four module screens. `dashboard.css` keeps its own copies of `.metrics-row`/`.data-table` because `dashboard.html` doesn't load `modules.css`. **If you add a 5th module screen, load `modules.css` and reuse these — don't redefine them in a per-page stylesheet.**
- **`[hidden]` is guaranteed to hide**: `base.css` has a global `[hidden] { display: none !important; }`. This exists because several components set `display: flex/grid` unconditionally, and the UA-stylesheet `[hidden]` rule loses to same-specificity author rules that load later — don't remove that global rule, and don't fight it with per-component `[hidden]` overrides (redundant).
- **Never give an ancestor of a `position: fixed` popover a `transform`-based `animation`/`transition` with `fill-mode: forwards`/`both`.** A held `transform` (even a no-op one like the settled `translateY(0)` of an entrance animation) makes that ancestor the containing block for `position: fixed` descendants, so the popover's fixed coordinates — computed against the viewport in `shell.js` — get resolved against the ancestor's box instead, rendering the menu offset and cut off. This bit `.app-main`'s page-load entrance animation (fixed with `fill-mode: backwards`) and then bit again, worse, when a per-row fade-in animation was added to `.data-table tbody tr`/`.kanban-card` with `both` (fixed with `fill-mode: none` — see `changelogs/2026-08-17-drag-drop-e-fix-containing-block.md`). If you add motion to any container that can hold a "⋮" action menu, a table row, or a kanban card, keep `fill-mode` to `none`/`backwards` only, never `forwards`/`both`.

### Per-module JS pattern (`areas-cultivo.js`, `atividades.js`, `insumos.js`, `ocorrencias.js`)

Data is mock, held in the DOM itself (dataset attributes on `<tr>`/`.kanban-card`) — there is no separate JS state object mirroring it, mutations happen directly on the elements and metrics are **recomputed from the DOM** after every mutation (`recomputeMetrics()`/`recomputeCriticalAlert()`-style functions), not incrementally tracked. Follow this pattern for new mutations: mutate the element's `dataset`, update its visible cell/badge, then call the page's recompute function — don't hand-roll a parallel counter.

Cross-module navigation passes context via `?talhao=` query param (e.g. a talhão's "Ver atividades" row-menu link goes to `atividades.html?talhao=T-01%20Norte`); the receiving page reads it with `URLSearchParams`, filters its list, and shows a removable filter chip. `atividades.js` and `ocorrencias.js` both implement this identically (`initTalhaoFilterChip`) — copy that pattern for any new cross-module link rather than inventing a new query param.

Each module's action menu ("⋮" per row/card) is rebuilt via a JS template function (`actionMenuMarkup()` in each file) rather than hand-written once per row in HTML, so the item set can change based on state (e.g. a completed activity's menu omits "Editar"/"Concluir"; a resolved ocorrência's menu omits "Marcar resolvida"). When adding a new action, add it to that template function, not to static HTML.

## Verifying a screen after changes

Static checks catch syntax errors but **not** layout/interaction bugs — a prior pass on this codebase shipped four screens that passed HTML-balance + `node --check` + "all assets 200" checks while having a broken metrics grid (cards stacked instead of gridded, squeezing a data table to 2px tall), popovers that opened invisible, and a button wired to nothing. Don't trust static checks alone for a screen with any interactivity. If Playwright is available (see Commands), actually click through: open the screen, exercise its create/edit/delete/filter flows, and check `page.on('console')` for errors. See `changelogs/2026-08-15-modulos-operacionais.md` for the specific bug classes found this way — they're likely to recur in the same shapes (flex-child height collapse from a missing shared class, `[hidden]` losing to a `display` rule, popovers left open after a no-op menu action, dynamically-created triggers not wired up).
