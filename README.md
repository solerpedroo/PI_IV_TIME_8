# Agro — frontend estático

Implementação das telas de **autenticação** e do **Painel Geral (Dashboard)**, alinhada ao design system **Forest Sage** do pen.dev (`designSystemProjetoAgro`).

## Estrutura

```text
frontend_pi_iv/
├── login.html
├── criar-conta.html
├── recuperar-senha.html
├── redefinir-senha.html
├── confirmar-email.html
├── dashboard.html          # Painel Geral (frame ajDH7)
├── styles/
│   ├── base.css            # tokens + componentes compartilhados
│   ├── auth.css            # layout das telas de autenticação
│   ├── shell.css           # sidebar, header, popovers, chrome
│   ├── dashboard.css       # métricas, tabela, alertas, culturas
│   ├── feedback.css        # toasts, empty, skeleton, modal
│   └── motion.css          # animações / prefers-reduced-motion
└── scripts/
    ├── auth.js             # validação e fluxos de auth
    ├── toast.js            # API AgroToast (4 variantes do pen)
    ├── shell.js            # notificações, perfil, logout
    └── dashboard.js        # mock G4, busca, nova atividade
```

## Como executar

```bash
python -m http.server 5500
```

Acesse `http://localhost:5500/login.html` (login redireciona ao painel) ou `http://localhost:5500/dashboard.html`.

## Fluxos

```text
login.html ──sucesso──► dashboard.html
dashboard.html ──Sair──► login.html (+ toast “Você saiu com segurança.”)

Chrome no painel:
 · Sino → painel de notificações (marcar lidas / empty)
 · Avatar → Meu perfil / Configurações (preparatório) / Sair (modal)
 · Nova Atividade → modal mock (+ toast + métrica)
 · Concluir na tabela → badge + pendentes 8→7 + toast
```

## Toasts (Flow E)

| Tipo | Uso |
|------|-----|
| success | Atividade registrada, login, marcar lidas |
| error | Validação / falha mock |
| warning | Ocorrência / estoque |
| info | Módulo em preparação, notificação aberta |

API: `AgroToast.show({ type, title, description, duration })`.

## Fora desta entrega

Módulos lista/detalhe (áreas, atividades, insumos, ocorrências, custos, configurações), Relatórios, Safras, Meu Perfil e mobile — links do shell mostram toast “em preparação”.

## Dados

Mocks locais híbridos em `dashboard.js` / HTML. Substitua por `fetch` quando a API existir; a anatomia de métricas e toasts já está desacoplada.
