<!-- Autor Secundário: Pedro Henrique Contardi Soler - 25005592 -->
# Agro — frontend estático

> O servidor principal está em `backend/servidor`: Java 21 puro com HTTP manual
> e MongoDB. O frontend consome suas APIs de autenticação, propriedades, talhões
> estoque e ocorrências. Consulte [backend/README.md](backend/README.md) para execução.

Perfil em Configurações está integrado ao servidor Java puro: dados pessoais,
preferências, propriedade padrão, confirmação de novo e-mail, senha e logout.
Contrato, limites de foto/equipe/plano e validação: [docs/perfil.md](docs/perfil.md).

O módulo de ocorrências e sua validação estão descritos em
[docs/ocorrencias.md](docs/ocorrencias.md).

Implementação das telas de **autenticação**, do **Painel Geral (Dashboard)** e dos módulos operacionais **Áreas de Cultivo**, **Atividades**, **Insumos**, **Ocorrências**, **Custos** e **Configurações**, alinhada ao design system **Forest Sage** do pen.dev (`docs/design/designSystemProjetoAgro`).

## Estrutura

```text
frontend_pi_iv/
├── frontend/              # páginas HTML, recursos e código da interface
│   ├── Assets/            # imagens
│   ├── scripts/           # lógica da interface e dados locais
│   └── styles/            # estilos compartilhados e por tela
├── backend/               # servidor, gateway e núcleo Java
├── docs/                  # documentação e design system
├── slides/                # apresentação do projeto
├── tests/                 # testes de navegador e integração
└── compose.yaml           # serviços locais
```

## Como executar

```bash
python -m http.server 5500 --directory frontend
```

Acesse `http://localhost:5500/login.html` (login redireciona ao painel) ou diretamente qualquer tela, ex.: `http://localhost:5500/dashboard.html`.

## Fluxos

```text
login.html ──sucesso──► dashboard.html
dashboard.html ──Sair──► login.html (+ toast “Você saiu com segurança.”)

Sidebar (Painel · Áreas de Cultivo · Atividades · Insumos · Ocorrências ·
Custos · Configurações): todas navegáveis de verdade entre si.

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

Custos:
 · Indicadores de custo total, custo por hectare, insumos e mão de obra
 · Gráfico por categoria, orçamento da safra e custo por cultura
 · Buscar, filtrar, registrar, editar, excluir e exportar lançamentos

Configurações:
 · Abas de perfil, propriedade, notificações e segurança
 · Preferências com toggles e estados informativos de equipe e plano
 · Validação dos formulários e feedback por toast

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

## Padrões reaproveitados entre os módulos (`frontend/styles/modules.css`)

- **Barra de filtros**: chips (`.filter-chip`) para categoria/cultura e
  selects Forest Sage para status/tipo.
- **Menu de ações "⋮"**: usa a mesma infraestrutura de popover do
  `shell.js` (`data-popover-trigger` / `data-popover`) — nenhum JS extra
  é necessário para abrir/fechar/clicar fora/Escape.
- **Meta-grid, Textarea Group, Segmented control, Timeline, Kanban**:
  componentes descritos nas seções G7/G8/G9 e nos boards "Fluxos de
  Interação" do pen.dev, implementados uma vez e reaproveitados.

## Fora desta entrega

Relatórios, Safras, Meu Perfil independente e mobile continuam fora do
escopo. Páginas de detalhe em
tela cheia (ex.: `/talhao/:id`, `/atividade/:id`) foram implementadas
como **modais** dentro da própria lista, não como rotas separadas —
decisão de escopo para manter a entrega em HTML/CSS/JS estático sem
roteador client-side.

## Dados

Estoque usa catálogo e movimentações persistidos pela API Java, sem linhas ou
indicadores mock e sem cache operacional local. As regras e os testes de
reload/reinício estão em [docs/estoque.md](docs/estoque.md). Atividades usam a
API Java para planejamento, status e histórico; o cache local serve somente como
cópia de leitura. Ocorrências e custos têm documentação em
[docs/persistencia-local.md](docs/persistencia-local.md).
