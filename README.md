# 🌾 AgroGestão — Plataforma Integrada de Gestão Agrícola (PI IV)

Aplicação web para **centralizar a operação de propriedades rurais**: propriedades, talhões, safras, atividades, estoque, ocorrências, custos e indicadores do painel. O servidor Java entrega a API e o frontend na mesma origem; o MongoDB persiste os dados por propriedade autenticada.

- **Repositório**: [`PI_IV_TIME_8`](https://github.com/solerpedroo/PI_IV_TIME_8)
- **Manual do usuário**: [`docs/15-manual-do-usuario.md`](docs/15-manual-do-usuario.md)
- **Contexto acadêmico**: Projeto Integrador IV — Engenharia de Software — PUC-Campinas (2026)
- **Documento de produto (referência)**: [`docs/AgroGestao_Projeto.md`](docs/AgroGestao_Projeto.md)
- **Requisitos do MVP (RF, RNF, RN)**: [`docs/02-requisitos.md`](docs/02-requisitos.md)
- **Casos de uso**: [`docs/03-casos-de-uso.md`](docs/03-casos-de-uso.md)
- **Arquitetura (componentes, portas e fluxo)**: [`docs/04-arquitetura.md`](docs/04-arquitetura.md)
- **Dicionário de dados**: [`docs/05-dicionario-de-dados.md`](docs/05-dicionario-de-dados.md)
- **Plano de testes (estratégia, ambiente e casos)**: [`docs/09-plano-de-testes.md`](docs/09-plano-de-testes.md)
- **Rastreabilidade (requisito → implementação → teste)**: [`docs/13-rastreabilidade.md`](docs/13-rastreabilidade.md)
- **Project no GitHub**: [PI_IV_TIME_8 · Project nº 3](https://github.com/users/solerpedroo/projects/3)

## 👥 Equipe

Time 8 — Projeto Integrador IV (2026)

```text
- Miguel Fernandes Costacurta — 25003110
- Pedro André do Carmo Chavier - 25018639
- Pedro Henrique Contardi Soler — 25005592
- Tomás de Paula Michelon Toniato — 25004211
- Vinicius Panutti Salgado — 25007329
```

## 🎯 Escopo (o que este repositório entrega)

Com base no documento de produto e nas issues do Project, o MVP contempla:

- **Autenticação**: cadastro, confirmação de e-mail, login, recuperação/redefinição de senha e sessão Bearer.
- **Propriedades**: CRUD, seleção do contexto ativo e isolamento dos dados por propriedade.
- **Talhões (Áreas de Cultivo)**: cadastro/edição, vínculos com atividades/ocorrências/custos e filtros entre telas.
- **Safras e culturas**: planejamento de ciclo, vínculo com talhões e proteções contra exclusão/edição incompatível.
- **Atividades**: Kanban/lista, planejamento (responsável, horário, prioridade, safra), transições de status, cancelamento e histórico persistente.
- **Estoque (Insumos)**: catálogo, entradas/saídas/ajustes, histórico e indicadores (Normal/Baixo/Crítico), sem negativar saldo.
- **Ocorrências**: registro, timeline, resolução, exclusão lógica e resumo mensal.
- **Custos**: lançamentos, indicadores e vínculo com a operação da propriedade.
- **Manutenção**: equipamentos, preventivas, intervenções e custos em Maquinário vinculados à operação.
- **Dashboard**: visão geral operacional a partir dos módulos integrados.
- **Notificações**: sino com alertas reais (atraso, estoque, ocorrência, atividades do dia), navegação ao registro e leitura/preferências persistentes.
- **Configurações / Perfil**: dados pessoais, preferências, propriedade padrão, troca de e-mail/senha e logout.

## 🚫 Não-escopo (MVP)

Este projeto **não** implementa no MVP:

- Diagnóstico automático de doenças, IA para pragas ou previsão de produtividade.
- IoT, sensores, drones, imagens de satélite ou integração com máquinas.
- Contabilidade completa, marketplace, compra real de insumos ou integração bancária/fiscal.
- App mobile nativo ou implantação institucional em produção.
- Baixa automática de estoque/custo na conclusão de atividade (frente I20/#24, deliberadamente separada).

## 🚀 Tecnologias utilizadas

### Frontend
- **HTML / CSS / JavaScript** estáticos (sem framework SPA)
- Design system **Forest Sage** (`docs/design/`), com Geist local no aplicativo e nos slides
- Persistência local de apoio (`agro-local.js`), escopada por usuário/propriedade — **não** substitui a API

### Backend / API
- **Java 21** puro: dois processos complementares
  - **`backend/api`** — HTTP manual na porta **8080** (rotas `/api/**`, estáticos, MongoDB)
  - **`backend/serversocket`** — TCP + objetos serializados na porta **3000** (validações e cálculos; padrão Cliente–Servidor da disciplina)
- A API HTTP delega ao ServerSocket via `servicos/ClienteDeAlgoritmos` (ex.: cadastro). Ver [`backend/serversocket/README.md`](backend/serversocket/README.md).
- Sem Spring e sem Maven no servidor principal
- Driver oficial **MongoDB** (`lib/`)
- Gateway Java opcional (`backend/gateway`)
- Núcleo de modelo/Atlas (`backend/core`, Maven) para validação do domínio Mongo
- Catálogo dos endpoints: [`docs/06-contrato-api.md`](docs/06-contrato-api.md)

### Banco e infra
- **MongoDB Atlas** (cluster compartilhado; persistência via `MONGODB_URI` no `.env`)
- **GitHub Actions** apenas para automações de projeto (tracker, proteção de branch)

## 📋 Pré-requisitos

Instale antes de emular:

| Item | Para quê | Como conferir |
|------|----------|----------------|
| **JDK 21** | compilar e rodar o servidor | `java -version` e `javac -version` |
| **MongoDB Atlas** | persistir dados (obrigatório) | URI no `.env` — ver passo a passo abaixo |
| **Navegador moderno** | usar o sistema | Chrome, Firefox ou Edge atual |
| **Node.js 22+** | só se for rodar testes em `tests/` | `node -version` |
| **`mongosh`** | opcional; suítes que limpam banco `_teste` | — |

O projeto **não** usa Docker local: o banco é sempre o **Atlas** (nuvem).

---

## 🖥️ Como emular o projeto (passo a passo)

Ao final deste roteiro você terá o AgroGestão no navegador em **http://127.0.0.1:8080**, com login, cadastro e todos os módulos gravando no MongoDB online.

O script **`emular.sh`** (ou **`emular.bat`** no Windows) faz o trabalho pesado: lê o `.env`, compila o Java se ainda não existir `backend/api/bin/`, sobe o **ServerSocket** de algoritmos na porta **3000** e a **API + telas** na porta **8080**.

### Passo 1 — Obter o código

```bash
git clone https://github.com/solerpedroo/PI_IV_TIME_8.git
cd PI_IV_TIME_8
```

Todos os comandos seguintes assumem que você está **na raiz** do repositório (onde ficam `emular.sh`, `frontend/` e `backend/`).

### Passo 2 — Criar o arquivo `.env` (só na primeira vez)

```bash
cp .env.example .env
```

Edite `.env` e preencha pelo menos:

1. **`MONGODB_URI`** — connection string do Atlas (usuário, senha, cluster e banco `agrogestao_dev`).
2. **`MONGODB_DATABASE`** — deve ser o **mesmo nome** do banco no path da URI (ex.: `agrogestao_dev`).

Passo a passo completo (criar usuário no cluster, liberar IP, copiar URI): **[docs/mongodb-atlas.md](docs/mongodb-atlas.md)**.

> **Segurança:** nunca commite o `.env`. Cada integrante usa a própria URI.

SMTP (`SMTP_*` no `.env`) é **opcional**. Sem e-mail configurado, o código de confirmação de cadastro aparece no **terminal** onde o servidor está rodando.

### Passo 3 — Subir o servidor

Na raiz do projeto:

| Sistema | Comando |
|---------|---------|
| Linux, macOS ou Git Bash | `bash emular.sh` |
| Windows (Prompt de Comando) | `emular.bat` |

Na **primeira execução**, a compilação pode levar alguns segundos. Você verá no terminal as URLs do app e do diagnóstico.

| Situação | O que fazer |
|----------|-------------|
| Alterou código Java | `bash emular.sh --build` (ou `emular.bat --build`) |
| Porta 8080 ocupada | `bash emular.sh 9000` e abra `http://127.0.0.1:9000/login.html` |
| Encerrar o servidor | no terminal do servidor, digite `desativar` e Enter |

### Passo 4 — Abrir no navegador

1. Acesse **http://127.0.0.1:8080/login.html** (ou a porta que escolheu).
2. Crie uma conta ou entre com usuário existente.
3. Use o sistema normalmente; os dados ficam no Atlas.

Use o **mesmo endereço** (`127.0.0.1:8080`): o servidor entrega API e HTML na mesma origem. Abrir só os arquivos HTML ou usar `python -m http.server` na pasta `frontend/` **não** permite login nem persistência.

### Passo 5 — Confirmar que o banco respondeu (recomendado na primeira vez)

Em **outro terminal** (com o servidor ainda ligado):

```bash
curl -i http://127.0.0.1:8080/ready
```

- **HTTP 200** — API conectada ao Atlas; pode usar o app.
- **HTTP 503** — revise IP liberado no Atlas, URI, senha e nome do banco; veja a seção **Troubleshooting** abaixo e [mongodb-atlas.md](docs/mongodb-atlas.md).

Endpoints úteis:

- `GET /health` — só verifica se o processo Java está no ar.
- `GET /ready` — verifica processo **e** MongoDB.

### Uso no dia a dia

Depois que o `.env` já estiver configurado, o fluxo é sempre o mesmo:

```bash
cd PI_IV_TIME_8    # se ainda não estiver na pasta
bash emular.sh     # ou emular.bat no Windows
```

Não é preciso `set -a && . ./.env`: o `emular.sh` carrega o `.env` automaticamente.

### Referência rápida (desenvolvedores)

| Objetivo | Comando |
|----------|---------|
| Mesmo que `emular`, só pela pasta da API | `bash backend/api/rodar.sh` (também lê `.env` na raiz) |
| Testes HTTP automáticos (sem Atlas, uso interno) | `bash backend/api/testar.sh` |
| Testes do socket (JUnit) | `sh backend/serversocket/testar.sh` |
| Só olhar layout das páginas, sem backend | `python -m http.server 5500 --directory frontend` |

O modo `--memoria` existe **apenas** para suítes de teste; **não** emule o produto assim — use sempre Atlas + `emular.sh`.

## 📁 Estrutura do projeto

```text
PI_IV_TIME_8/
├── emular.sh / emular.bat    # subir app com MongoDB Atlas (comando principal)
├── scripts/                  # apoio Windows (carregar-env.ps1, emular.ps1)
├── frontend/                 # HTML, CSS, JS e assets da interface
│   ├── scripts/              # auth, módulos operacionais, agro-local
│   ├── styles/               # design system e estilos por tela
│   └── Assets/               # imagens
├── backend/
│   ├── api/                  # API HTTP + arquivos estáticos (ponto de entrada)
│   ├── gateway/              # proxy Java opcional para /api/**
│   └── core/                 # modelo Mongo/Atlas e testes de domínio
├── docs/                     # produto, contratos por módulo, Atlas, design
├── tests/                    # e2e Playwright e integração Node
├── slides/                   # pitch / apresentação
└── README.md
```

## ✅ Funcionalidades (visão por módulos)

### 🔐 Autenticação e sessão
- Cadastro, confirmação de e-mail, login e recuperação de senha
- Token Bearer e contexto de propriedade ativa

### 🏡 Propriedades e talhões
- Múltiplas propriedades por usuário
- Áreas de cultivo com cultura, hectares e status
- Navegação filtrada entre atividades, ocorrências e custos (`?talhao=`)

### 🌱 Safras
- Ciclo de safra vinculado a talhões
- Cultura derivada e proteções de vínculo com atividades

### ✅ Atividades
- Planejamento completo e execução (Pendente → Agendada → Em andamento → Concluída)
- Cancelamento com motivo e histórico após reinício
- Menu, lista e arraste usam a mesma regra/API de status

### 📦 Estoque
- Entrada, saída e ajuste com histórico
- Concorrência sem saldo negativo

### 🚨 Ocorrências
- Timeline, resolução e exclusão lógica
- Resumo mensal por data real de resolução

### 💰 Custos e dashboard
- Lançamentos e indicadores da safra/operação
- Painel geral com visão consolidada

### ⚙️ Configurações
- Perfil, preferências, propriedade padrão e segurança da conta

## 🎨 Design

- Interface alinhada ao design system **Forest Sage**
- Componentes compartilhados: filtros, menu “⋮”, Kanban, timeline, toasts (`AgroToast`)
- Feedback por toast (success / error / warning / info)

## 🧪 Testes

### Servidor Java (memória)

```bash
bash backend/api/testar.sh
```

### Mongo Atlas + reinício (exemplo)

```bash
set -a && . ./.env && set +a
node tests/atividades-reinicio.cjs
```

### Navegador (Playwright)

```bash
npm install --no-save playwright@1.49.1
npx playwright install chromium --with-deps
PLAYWRIGHT_MODULE=playwright node tests/atividades-real.cjs
```

Outras suítes: `tests/ocorrencias-real.cjs`, `tests/identidade-visual-real.cjs`, scripts `*-real.cjs` em `tests/`. As suítes rodam **localmente**; o repositório não mantém workflows de CI de testes (apenas automações de projeto em `.github/workflows/`).

Plano de testes (tipos, ambiente, dados, casos e critérios de aprovação): [`docs/09-plano-de-testes.md`](docs/09-plano-de-testes.md).

Relatório da última execução: [`docs/10-relatorio-de-testes.md`](docs/10-relatorio-de-testes.md).

Documentação por módulo: [`docs/atividades.md`](docs/atividades.md), [`docs/estoque.md`](docs/estoque.md), [`docs/ocorrencias.md`](docs/ocorrencias.md), [`docs/notificacoes.md`](docs/notificacoes.md), [`docs/perfil.md`](docs/perfil.md), [`docs/safras.md`](docs/safras.md).

## 🛟 Troubleshooting

### Porta 8080 ocupada
Encerre outro processo na porta ou use `bash emular.sh 9000`.

### `/ready` falha com Mongo
Revise o `.env` na raiz, o IP liberado no **Network Access** do Atlas, `MONGODB_DATABASE` alinhado ao path da URI e [`docs/mongodb-atlas.md`](docs/mongodb-atlas.md). O `emular.sh` carrega o `.env` automaticamente.

### Cadastro sem e-mail real
Sem SMTP configurado, o token de confirmação aparece no log do servidor (modo desenvolvimento). Detalhes em [`backend/api/README.md`](backend/api/README.md).

### Dados iniciais e cache local vs API

Contas e propriedades começam sem registros operacionais de exemplo. Painel, talhões e atividades consultam a API; falhas de consulta não restauram demonstrações antigas. Fixtures fictícias ficam exclusivamente em `tests/`, fora dos arquivos servidos pelo aplicativo.

Com sessão autenticada, a API é a fonte de verdade. O cache do navegador é cópia de leitura escopada; falha de rede não deve confirmar mutações só no `localStorage`.

## 🔗 Materiais de apoio

- **Documento de produto**: [`docs/AgroGestao_Projeto.md`](docs/AgroGestao_Projeto.md)
- **Requisitos do MVP**: [`docs/02-requisitos.md`](docs/02-requisitos.md) — requisitos funcionais e não funcionais, regras de negócio, critérios de aceite e rastreabilidade
- **Arquitetura**: [`docs/04-arquitetura.md`](docs/04-arquitetura.md) — frontend, API HTTP, ServerSocket, MongoDB e gateway opcional, com portas, fluxo de requisição, segurança e limitações
- **Matriz de rastreabilidade**: [`docs/13-rastreabilidade.md`](docs/13-rastreabilidade.md) — cada requisito ligado à tela, à rota, ao código e ao teste, com a situação da última execução
- **Plano de testes**: [`docs/09-plano-de-testes.md`](docs/09-plano-de-testes.md) — o que é automatizado, manual ou depende de ambiente externo, com casos, status inicial e critérios de aprovação
- **Modelo de dados**: [`docs/modelo-dados.md`](docs/modelo-dados.md)
- **Dicionário de dados**: [`docs/05-dicionario-de-dados.md`](docs/05-dicionario-de-dados.md)
- **Pitch / slides**: [`slides/`](slides/)
- **Backend (detalhe)**: [`backend/README.md`](backend/README.md)
- **MongoDB Atlas**: [`docs/mongodb-atlas.md`](docs/mongodb-atlas.md)

## 📄 Observação acadêmica

Este repositório é o entregável do **Projeto Integrador IV** (PUC-Campinas). Os documentos em `docs/` e o Project no GitHub são a referência de requisitos, escopo e evidência de aceite. Uso restrito ao contexto acadêmico da disciplina, salvo decisão explícita do grupo.
