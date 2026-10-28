# 🌾 AgroGestão — Plataforma Integrada de Gestão Agrícola (PI IV)

Aplicação web para **centralizar a operação de propriedades rurais**: propriedades, talhões, safras, atividades, estoque, ocorrências, custos e indicadores do painel. O servidor Java entrega a API e o frontend na mesma origem; o MongoDB persiste os dados por propriedade autenticada.

- **Repositório**: [`PI_IV_TIME_8`](https://github.com/solerpedroo/PI_IV_TIME_8)
- **Contexto acadêmico**: Projeto Integrador IV — Engenharia de Software — PUC-Campinas (2026)
- **Documento de produto (referência)**: [`docs/AgroGestao_Projeto.md`](docs/AgroGestao_Projeto.md)
- **Project no GitHub**: [PI_IV_TIME_8 · Project nº 3](https://github.com/users/solerpedroo/projects/3)

## 👥 Equipe

Time 8 — Projeto Integrador IV (2026)

```text
- Miguel Fernandes Costacurta — 25003110
- Pedro André do Carmo Chavier
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
- **Dashboard**: visão geral operacional a partir dos módulos integrados.
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
- Design system **Forest Sage** (`docs/design/`)
- Persistência local de apoio (`agro-local.js`), escopada por usuário/propriedade — **não** substitui a API

### Backend / API
- **Java 21** puro (`backend/servidor`) com HTTP manual (`ServerSocket`)
- Sem Spring e sem Maven no servidor principal
- Driver oficial **MongoDB** (`lib/`)
- Gateway Java opcional (`backend/gateway`)
- Núcleo de modelo/Atlas (`backend/core`, Maven) para validação do domínio Mongo

### Banco e infra local
- **MongoDB 8** (Docker Compose com replica set, ou Atlas Free)
- **Docker Compose** (`compose.yaml`) para Mongo local em loopback
- **GitHub Actions** para suítes de API, Mongo e navegador (Playwright)

## 📋 Pré-requisitos

- **JDK 21** (`java` e `javac` no PATH)
- **Node.js 22+** (testes de navegador / scripts em `tests/`)
- **Docker** (opcional, para Mongo local via Compose)
- Navegador moderno

## 🔧 Instalação e execução (ambiente de testes)

### 1) Clone o projeto

```bash
git clone https://github.com/solerpedroo/PI_IV_TIME_8.git
cd PI_IV_TIME_8
```

### 2) Compilar e testar o servidor (memória)

```bash
bash backend/servidor/testar.sh
cd backend/servidor
bash compilar.sh
bash rodar.sh --memoria
```

Abra `http://127.0.0.1:8080/login.html`. O modo `--memoria` perde os dados ao encerrar.

No Windows, use os scripts `.bat` equivalentes em `backend/servidor/`.

### 3) (Recomendado) MongoDB local com replica set

Na raiz do repositório:

```bash
docker compose up -d --wait mongo
```

Carregue `MONGODB_URI` (ex.: `mongodb://127.0.0.1:27017/agrogestao_dev?replicaSet=rs0`) e execute:

```bash
cd backend/servidor
bash rodar.sh
```

Guia Atlas e `.env` local: [`docs/mongodb-atlas.md`](docs/mongodb-atlas.md). Credenciais **não** entram no Git.

### 4) Frontend estático (opcional, sem API)

Só para inspecionar as páginas sem o servidor Java:

```bash
python -m http.server 5500 --directory frontend
```

Acesse `http://localhost:5500/login.html`. Fluxos autenticados e persistentes exigem o servidor na porta **8080**.

### 5) Saúde do serviço

- `GET /health` — processo no ar
- `GET /ready` — processo + banco (quando configurado)

## 📁 Estrutura do projeto

```text
PI_IV_TIME_8/
├── frontend/                 # HTML, CSS, JS e assets da interface
│   ├── scripts/              # auth, módulos operacionais, agro-local
│   ├── styles/               # design system e estilos por tela
│   └── Assets/               # imagens
├── backend/
│   ├── servidor/             # API HTTP + arquivos estáticos (ponto de entrada)
│   ├── gateway/              # proxy Java opcional para /api/**
│   └── core/                 # modelo Mongo/Atlas e testes de domínio
├── docs/                     # produto, contratos por módulo, Atlas, design
├── tests/                    # e2e Playwright e integração Node
├── slides/                   # pitch / apresentação
├── compose.yaml              # Mongo local (replica set)
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
bash backend/servidor/testar.sh
```

### Mongo + reinício (exemplos)

```bash
docker compose up -d --wait mongo
node tests/atividades-reinicio.cjs
```

### Navegador (Playwright)

```bash
npm install --no-save playwright@1.49.1
npx playwright install chromium --with-deps
PLAYWRIGHT_MODULE=playwright node tests/atividades-real.cjs
```

Outras suítes: `tests/ocorrencias-real.cjs`, workflows em `.github/workflows/`.

Documentação por módulo: [`docs/atividades.md`](docs/atividades.md), [`docs/estoque.md`](docs/estoque.md), [`docs/ocorrencias.md`](docs/ocorrencias.md), [`docs/perfil.md`](docs/perfil.md), [`docs/safras.md`](docs/safras.md).

## 🛟 Troubleshooting

### Porta 8080 ocupada
Encerre outro processo na porta ou suba o servidor em outra: `bash rodar.sh 9000` (ajuste o front se necessário).

### `/ready` falha com Mongo
Confirme o Compose (`docker compose ps`), o `replicaSet=rs0` na URI e que o healthcheck iniciou o RS. Veja também [`docs/servidor-java.md`](docs/servidor-java.md).

### Cadastro sem e-mail real
Sem SMTP configurado, o token de confirmação aparece no log do servidor (modo desenvolvimento). Detalhes em [`backend/servidor/README.md`](backend/servidor/README.md).

### Cache local vs API
Com sessão autenticada, a API é a fonte de verdade. O cache do navegador é cópia de leitura escopada; falha de rede não deve confirmar mutações só no `localStorage`.

## 🔗 Materiais de apoio

- **Documento de produto**: [`docs/AgroGestao_Projeto.md`](docs/AgroGestao_Projeto.md)
- **Modelo de dados**: [`docs/modelo-dados.md`](docs/modelo-dados.md)
- **Pitch / slides**: [`slides/`](slides/)
- **Backend (detalhe)**: [`backend/README.md`](backend/README.md)
- **MongoDB Atlas**: [`docs/mongodb-atlas.md`](docs/mongodb-atlas.md)

## 📄 Observação acadêmica

Este repositório é o entregável do **Projeto Integrador IV** (PUC-Campinas). Os documentos em `docs/` e o Project no GitHub são a referência de requisitos, escopo e evidência de aceite. Uso restrito ao contexto acadêmico da disciplina, salvo decisão explícita do grupo.
