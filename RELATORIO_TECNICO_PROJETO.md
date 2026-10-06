# Relatório técnico completo do projeto AgroGestão

**Data da análise:** 25 de setembro de 2026 (atualizado em 6 de outubro de 2026 — fundação do backend)  
**Repositório analisado:** `frontend_pi_iv`  
**Finalidade:** servir como documentação técnica e contexto-base para prompts enviados a agentes de IA que continuarão o desenvolvimento.

## 1. Resumo executivo

O AgroGestão é uma proposta de plataforma integrada de gestão agrícola para pequenos e médios produtores rurais. O produto pretende centralizar propriedades, talhões, culturas, safras, atividades, insumos, ocorrências e custos, fazendo com que uma operação registrada em um módulo produza efeitos nos demais módulos relacionados.

O conteúdo principal deste repositório continua sendo um **protótipo frontend estático, navegável e interativo**, construído com HTML, CSS e JavaScript puro, com dados mock no HTML/DOM (nada persistido entre reloads). **Desde a fundação do backend** (`backend/`, `compose.yaml`), o monorepo também inclui um **gateway Node.js + TypeScript**, um **core Java 21 + Spring Boot** com conexão MongoDB configurada e **MongoDB local via Docker Compose** — apenas health/readiness, sem rotas de negócio, modelos ou integração com as telas HTML.

O frontend implementa:

- cinco telas de autenticação simulada;
- Dashboard;
- Áreas de Cultivo;
- Atividades;
- Insumos;
- Ocorrências;
- Custos;
- Configurações;
- infraestrutura visual e comportamental compartilhada;
- um deck de apresentação com 20 slides, exportado também em PDF;
- documentos de produto, MVP, funcionalidades, wireframes, protótipo e planejamento 5W2H/GUT.

O planejamento documental prevê uma arquitetura futura em três camadas:

```text
Frontend HTML CSS JavaScript
          ↓
Camada intermediária Node.js e TypeScript
          ↓
Servidor Java com regras de negócio
          ↓
MongoDB
```

A topologia acima **já está esboçada em código** (gateway TS → core Java → MongoDB), documentada em `backend/README.md`. Ainda **não** há rotas de produto, CRUD, esquema de coleções, autenticação real nem `fetch` do frontend para o gateway.

## 2. Escopo da análise e fontes consultadas

A análise cobriu todos os arquivos de código e documentação relevantes do repositório:

- 12 páginas HTML na raiz;
- 10 scripts JavaScript do aplicativo e 2 scripts do deck;
- 14 folhas CSS;
- `AgroGestao_Projeto.md`;
- `README.md`;
- `AGENTS.md` e `CLAUDE.md`;
- changelogs de 15 e 17 de agosto de 2026;
- o JSON de design `designSystemProjetoAgro`, versão 2.17, com cerca de 5 MB;
- quatro documentos Word em `docs/`;
- a planilha `AgroGestao_5W2H_GUT.xlsx`;
- o deck HTML e o PDF `AgroGestao-Pitch.pdf`, com 20 páginas;
- configurações do deck para Vercel.

Também foram realizados:

- verificação de sintaxe de todos os arquivos JavaScript e MJS;
- checagem de referências locais usadas por `src` e `href` nas páginas HTML;
- abertura das 12 páginas raiz em servidor HTTP local;
- inspeção do conteúdo renderizado e dos erros de console;
- teste de criação de talhão e criação de atividade;
- teste da navegação contextual por `?talhao=`.

### 2.1 Hierarquia recomendada das fontes

Ao executar tarefas futuras, um agente deve interpretar as fontes nesta ordem:

1. **Código atual:** define o comportamento realmente existente.
2. **`AGENTS.md`:** define regras obrigatórias de manutenção deste repositório.
3. **`designSystemProjetoAgro`:** fonte de verdade para tokens, conteúdo, ícones, estados e fluxos de interface.
4. **`README.md` e changelogs:** explicam decisões de implementação e histórico técnico.
5. **`AgroGestao_Projeto.md` e documentos em `docs/`:** definem visão do produto e escopo pretendido.
6. **Planilha 5W2H/GUT e pitch:** definem planejamento, responsabilidades e narrativa de apresentação, mas não comprovam implementação.

Há divergências entre essas fontes. O relatório destaca essas diferenças para evitar que uma tarefa futura trate intenção como funcionalidade pronta.

## 3. Visão do produto

### 3.1 Problema atendido

O projeto parte do problema de informações agrícolas dispersas em cadernos, planilhas, WhatsApp, anotações e sistemas isolados. Essa dispersão prejudica o acompanhamento da operação, a preservação do histórico, o controle de estoque, a visibilidade de custos e a identificação de pendências.

### 3.2 Público-alvo

Público principal:

- pequenos produtores rurais;
- médios produtores rurais;
- operações que ainda dependem de processos manuais.

Usuários secundários previstos:

- administradores de propriedades;
- técnicos agrícolas;
- responsáveis pela produção;
- gestores de fazenda;
- funcionários responsáveis pelo manejo.

### 3.3 Proposta de valor

O diferencial proposto não é apenas cadastrar fazendas. O sistema pretende representar a operação agrícola de forma conectada:

```text
Propriedade
  └── Talhão
       └── Cultura e Safra
            ├── Atividades
            ├── Ocorrências
            ├── Custos
            └── Histórico

Estoque
  └── Movimentações e consumo por atividade

Todos os módulos
  └── Indicadores e alertas no Dashboard
```

Exemplo conceitual central:

```text
Atividade concluída
  → consumo de insumo
  → atualização do estoque
  → registro de custo
  → atualização do histórico do talhão e da safra
  → atualização do Dashboard
```

No frontend atual, essa cadeia é apenas comunicada por textos e toasts. Ela ainda não ocorre de verdade entre as páginas.

## 4. Estado atual do repositório

### 4.1 Tecnologias efetivamente usadas

- HTML5 estático;
- CSS3;
- JavaScript ES moderno sem módulos;
- SVG inline com ícones baseados no conjunto Lucide;
- `sessionStorage` apenas para mensagens transitórias e e-mail pendente no fluxo de autenticação;
- API nativa de Drag and Drop no Kanban;
- `Intl.NumberFormat` e `Intl.DateTimeFormat` para formatação brasileira;
- Blob e Object URL para exportação CSV de custos;
- Playwright e `pdf-lib` somente dentro do projeto de slides;
- **fundação backend:** gateway TypeScript (`backend/gateway/`), core Spring Boot + Actuator + Data MongoDB (`backend/core/`), Compose MongoDB 8 (`compose.yaml`), variáveis em `.env.example`.

### 4.2 Tecnologias ausentes ou incompletas

- React, Vue, Angular ou outro framework no frontend;
- **API de domínio** (CRUD, auth, regras agrícolas) — o backend atual só expõe health/readiness;
- ORM/ODM com entidades e repositórios de negócio;
- `fetch`, Axios ou cliente HTTP **no frontend** do aplicativo;
- autenticação, autorização e sessão reais;
- testes automatizados versionados para o frontend principal;
- lint, formatter ou pipeline de build unificado na raiz;
- package manager na raiz do projeto (apenas em `slides/` e `backend/gateway/`);
- persistência em `localStorage`, IndexedDB ou banco **consumida pelas telas HTML**.

### 4.3 Estrutura resumida

```text
frontend_pi_iv/
├── *.html                         telas do aplicativo
├── scripts/                       comportamento do aplicativo
├── styles/                        design system e estilos de tela
├── Assets/Logo.jpg                marca
├── designSystemProjetoAgro        export JSON do pen.dev
├── docs/                          documentos acadêmicos e planejamento
├── slides/                        deck HTML, assets, PDF e exportador
├── changelogs/                    decisões e bugs históricos
├── backend/                       gateway TS + core Java (ver backend/README.md)
├── compose.yaml                   MongoDB local para desenvolvimento
├── .env.example                   variáveis compartilhadas gateway/core
├── AgroGestao_Projeto.md          especificação ampla do produto
├── README.md                      inventário da implementação
├── AGENTS.md                      regras para agentes
└── CLAUDE.md                      handoff técnico equivalente
```

## 5. Arquitetura do frontend

### 5.1 Modelo de páginas

Cada página HTML é autocontida e repete a estrutura de sidebar, cabeçalho, popovers e modal de logout. Não existe sistema de templates, include, componente compilado, roteador client-side ou SSR.

Consequências:

- alterações na navegação precisam ser replicadas em várias páginas;
- há risco de divergência entre shells;
- cada página pode ser aberta diretamente por URL;
- a navegação usa documentos HTML completos e reinicializa todo o estado.

### 5.2 Ordem obrigatória dos scripts

Nas páginas do sistema, a ordem padrão é:

```html
<script src="scripts/toast.js" defer></script>
<script src="scripts/select.js" defer></script>
<script src="scripts/shell.js" defer></script>
<script src="scripts/<pagina>.js" defer></script>
```

Essa ordem é relevante. `select.js` converte os selects presentes no DOM durante a inicialização. Selects populados dinamicamente precisam ser preenchidos antes de `AgroSelect.enhance()` ou ficar fora da inicialização automática.

### 5.3 Estado e dados

O padrão predominante é **DOM como fonte de verdade**:

- linhas de tabela e cards guardam dados em `dataset`;
- mutações alteram `dataset`, texto, badges e posição no DOM;
- métricas são recalculadas lendo o DOM;
- não há store global;
- cada página possui seu próprio estado isolado;
- navegar ou recarregar restaura os mocks originais.

Custos é uma exceção parcial: mantém um objeto `state.entries` em memória e renderiza tabela e gráfico a partir dele. Mesmo assim, o estado desaparece no reload.

### 5.4 Infraestrutura compartilhada

#### Toasts

`scripts/toast.js` expõe:

```js
window.AgroToast.show({
  type: "success" | "error" | "warning" | "info" | "inverse",
  title,
  description,
  duration
})
```

Os toasts usam `aria-live`, papéis adequados para status/alerta, fechamento manual e expiração automática.

#### Select customizado

`scripts/select.js` transforma `select[data-enhance-select]` em um dropdown Forest Sage. O select nativo permanece escondido como fonte do valor. O componente oferece:

- placeholder;
- seleção sincronizada;
- navegação por teclado;
- Escape;
- `aria-expanded`, `role=listbox` e `aria-selected`.

Limitação conhecida: como o menu visual permanece no fluxo do DOM, ele pode sofrer clipping dentro de contêineres com overflow. Por esse motivo, a troca de status na lista de Atividades usa select nativo.

#### Shell e popovers

`scripts/shell.js` controla:

- menus de perfil;
- notificações;
- logout;
- links ainda não implementados;
- popovers genéricos;
- navegação por teclado nos menus;
- fechamento por clique externo, item selecionado, foco externo ou Escape.

Os popovers usam delegação de eventos no `document`, portanto funcionam em linhas/cards criados dinamicamente. Quando um botão está dentro de tabela ou Kanban com overflow, o menu é promovido a `position: fixed` e posicionado em relação à viewport para não ser cortado.

`window.AgroPopover.closeAll()` fica disponível para scripts de página.

#### Modais

Não existe helper compartilhado para modais. Cada script implementa seu próprio `openModal` e `closeModal`, geralmente manipulando `.is-open` e `aria-hidden`.

#### CSS compartilhado

- `base.css`: reset, tokens, botões, inputs, selects, badges, cards e `[hidden]` global;
- `shell.css`: sidebar, cabeçalho, perfil e notificações;
- `modules.css`: métricas, tabelas, filtros, action menus, meta-grid, timeline, Kanban e controles segmentados;
- `feedback.css`: toasts, empty state, skeleton e modal;
- `motion.css`: animações e `prefers-reduced-motion`;
- folhas específicas: ajustes próprios de cada tela.

### 5.5 Design system Forest Sage

O arquivo `designSystemProjetoAgro` usa o formato pen.dev 2.17 e contém 16 frames principais, incluindo as 12 telas, Fluxos de Interação, Telas Navegáveis e Fluxos Complementares.

Tokens principais:

| Grupo | Tokens relevantes |
|---|---|
| Fundos | `#F4F7F2`, `#E8EFE4`, branco, verde escuro `#1A3324` |
| Primária | `#2D6A4F`, hover `#245A42`, sutil `#E6F2EC` |
| Acento | `#40916C` |
| Sucesso | `#2D6A4F` |
| Aviso | `#B8860B`, fundo `#FDF6E3` |
| Erro | `#C0392B`, fundo `#FCEAE8` |
| Informação | `#2980B9`, fundo `#E8F4FC` |
| Tipografia | Geist e Geist Mono |
| Raios | 6, 10, 14 e 999 px |
| Espaçamento | 4, 8, 16, 24, 32 e 48 px |

O board de interação também especifica estados de hover, ativo, desabilitado, loading, empty, erro e sucesso; ações por linha; confirmações destrutivas; click-through de notificações; jornada principal e expectativas mobile. Nem todas essas especificações estão implementadas.

## 6. Mapa de rotas e telas

| Arquivo | Finalidade | Estado |
|---|---|---|
| `login.html` | Entrada simulada | Implementado sem autenticação real |
| `criar-conta.html` | Cadastro simulado de usuário e propriedade | Implementado sem persistência |
| `recuperar-senha.html` | Solicitação simulada de recuperação | Implementado sem envio de e-mail |
| `redefinir-senha.html` | Nova senha simulada | Implementado sem token/API |
| `confirmar-email.html` | Espera e reenvio simulado | Implementado sem confirmação real |
| `dashboard.html` | Resumo geral da operação | Implementado com mocks isolados |
| `areas-cultivo.html` | Cadastro e consulta de talhões | Implementado em memória |
| `atividades.html` | Kanban/lista e gestão de atividades | Implementado em memória |
| `insumos.html` | Estoque e movimentações | Implementado em memória |
| `ocorrencias.html` | Registro, timeline e resolução | Implementado em memória |
| `custos.html` | Lançamentos, indicadores e exportação | Implementado em memória |
| `configuracoes.html` | Perfil, propriedade e preferências | Implementado em memória/stub |

Não existem páginas independentes de Propriedades, Safras, Relatórios, Meu Perfil ou detalhes por ID. Parte dessas experiências foi absorvida em Configurações ou em modais.

## 7. Funcionalidades detalhadas por tela

### 7.1 Autenticação

#### Login

- valida e-mail e senha obrigatórios;
- exige senha com pelo menos 8 caracteres;
- permite mostrar/ocultar senha;
- simula requisição com atraso de 550 ms;
- salva flash de sucesso em `sessionStorage`;
- redireciona para `dashboard.html` independentemente das credenciais.

Não existe verificação de usuário, hash de senha, token, cookie, proteção de rota ou logout no servidor.

#### Criar conta

Coleta:

- nome;
- e-mail;
- telefone com máscara brasileira;
- senha e confirmação;
- nome da propriedade;
- área;
- aceite de termos.

Valida todos os requisitos de força da senha: mínimo de oito caracteres, maiúscula, número e símbolo. Persiste apenas o e-mail pendente em `sessionStorage` e redireciona para confirmação.

Os links de Termos de Uso e Política de Privacidade apontam para âncoras inexistentes na mesma página.

#### Recuperar e redefinir senha

- recuperação valida e-mail e navega para a redefinição;
- redefinição valida senha forte e igualdade da confirmação;
- após sucesso, volta ao login com flash de sucesso.

Não existe token de recuperação, expiração ou comunicação com e-mail.

#### Confirmar e-mail

- obtém e-mail da query string ou de `sessionStorage`;
- exibe o e-mail;
- possui link `mailto:` para abrir cliente de e-mail;
- botão de reenvio simula envio e inicia cooldown de 30 segundos.

### 7.2 Dashboard

Apresenta:

- contexto da safra e propriedade;
- quatro métricas principais;
- tabela de atividades da semana;
- alertas;
- culturas em safra com progresso;
- busca local;
- notificações;
- modal de nova atividade.

Comportamentos:

- concluir atividade muda badge e decrementa a métrica local de pendentes;
- criar atividade incrementa a métrica local, mas não adiciona linha à tabela;
- busca esconde linhas da tabela;
- gatilhos de demonstração podem incrementar áreas e alertas;
- link “Ver todas” abre Atividades.

Limitação central: o Dashboard não lê dados dos demais módulos. Seus números são mocks próprios.

### 7.3 Áreas de Cultivo

Apresenta mapa esquemático, métricas e cinco talhões iniciais.

Funcionalidades:

- busca por nome;
- filtros por cultura;
- contagem de resultados;
- empty state;
- criação de talhão;
- edição de talhão;
- validação de nome, cultura e hectares;
- atualização local de hectares e quantidade de talhões;
- menus contextuais para Atividades, Ocorrências e Custos.

Regras atuais:

- “Talhões Ativos” é, na prática, a quantidade total de linhas, independentemente do status;
- edição atualiza hectares, mas não reduz a quantidade quando um status deixa de ser ativo;
- não há exclusão de talhão;
- não há propriedade ou safra persistida associada ao talhão;
- nomes dos talhões são usados como identificadores na URL.

### 7.4 Atividades

O Kanban é a fonte de verdade. A lista é reconstruída a partir dos cards visíveis.

Funcionalidades:

- busca;
- filtro por status;
- filtro por tipo;
- filtro por dia da semana;
- filtro contextual por `?talhao=`;
- chip removível do filtro contextual;
- visão Kanban e Lista com transição;
- criação e edição;
- detalhe em modal;
- duplicação com formulário preenchido;
- exclusão confirmada;
- conclusão confirmada;
- drag and drop entre colunas;
- troca de status por select nativo na Lista;
- recálculo das métricas e contadores das colunas.

Status:

- Pendente;
- Agendada;
- Em andamento;
- Concluída.

Diferença entre ações:

- drag/drop e select alteram status imediatamente;
- “Concluir” pelo menu abre confirmação e exibe texto de baixa de insumo.

O texto de sucesso afirma que houve baixa no estoque, mas nenhum dado de `insumos.html` ou `custos.html` é alterado.

O botão Exportar ainda mostra toast de “em preparação”.

### 7.5 Insumos

Apresenta 47 produtos como métrica geral, embora a tabela mock contenha sete linhas visíveis.

Funcionalidades:

- busca por produto;
- filtro por categoria;
- alerta de estoque crítico;
- detalhes com rastreio operacional ilustrativo;
- entrada de estoque pelo cabeçalho ou pela linha;
- saída com destino opcional;
- validação para impedir saldo negativo;
- edição de categoria e estoque mínimo;
- exclusão somente quando o saldo é zero;
- atualização de quantidade, status, alertas e métricas locais.

Regra aplicada depois de uma movimentação:

```text
saldo / mínimo < 0,6  → Crítico
saldo / mínimo < 1,0  → Baixo
saldo / mínimo >= 1,0 → Normal
```

Os badges iniciais do mock podem não obedecer a essa fórmula. A fórmula passa a valer quando o item sofre mutação.

Uma saída mostra a mensagem “custo lançado”, mas não cria lançamento em Custos.

### 7.6 Ocorrências

Funcionalidades:

- busca por título;
- filtro contextual por `?talhao=`;
- cadastro de ocorrência;
- tipo, talhão e prioridade;
- prioridade por controle segmentado;
- detalhes em modal;
- timeline por ocorrência mantida em `Map` JavaScript;
- publicação de atualização na timeline;
- resolução com confirmação;
- exclusão com confirmação;
- métricas recalculadas a partir das linhas.

Status:

- Aberta;
- Em análise;
- Resolvida.

Prioridades/severidades:

- Baixa;
- Média;
- Alta.

“Resolvidas no mês” conta todas as linhas atualmente resolvidas; não existe recorte temporal real.

### 7.7 Custos

Funcionalidades:

- indicadores de custo total, custo por hectare, insumos e mão de obra;
- gráfico horizontal por categoria;
- orçamento planejado, realizado, percentual e restante;
- custo por cultura;
- lançamentos recentes;
- busca;
- filtro por categoria;
- filtro contextual por `?talhao=`;
- criação e edição de lançamento;
- exclusão imediata;
- seletor de período;
- calendário customizado;
- edição do orçamento;
- exportação CSV dos lançamentos filtrados.

Categorias:

- Insumos;
- Mão de Obra;
- Maquinário;
- Combustível;
- Outros.

O total é calculado somando bases mock fixas por categoria aos lançamentos em memória. O seletor de período altera rótulos, mas não filtra ou recalcula os dados por período. O custo por cultura é estático e não é atualizado pelos lançamentos.

### 7.8 Configurações

Abas:

- Perfil;
- Propriedade;
- Notificações;
- Segurança;
- Equipe;
- Plano.

Funcionalidades:

- troca de abas com hash `#settings-<aba>`;
- validação e feedback dos formulários de perfil e propriedade;
- seleção customizada da safra atual;
- preferências de notificação com toggles;
- validação simulada de troca de senha;
- estados informativos de equipe e plano.

Limitações:

- salvar não persiste os dados;
- trocar senha não altera autenticação alguma;
- foto e convite de equipe são stubs com toast;
- alguns shells antigos ainda tratam “Meu Perfil” como módulo em preparação, apesar de a aba Perfil existir.

## 8. Comunicação entre módulos

### 8.1 Comunicação realmente implementada

| Origem | Destino | Mecanismo | Resultado atual |
|---|---|---|---|
| Login | Dashboard | redirecionamento | funciona |
| Logout | Login | `sessionStorage` + redirecionamento | funciona como simulação |
| Áreas | Atividades | `?talhao=<nome>` | abre e filtra |
| Áreas | Ocorrências | `?talhao=<nome>` | abre e filtra |
| Áreas | Custos | `?talhao=<nome>` | abre e filtra |
| Dashboard | Atividades | link “Ver todas” | funciona |
| Perfil/sidebar | Configurações | link/hash em parte das telas | parcialmente consistente |

### 8.2 Falha de identidade dos talhões

Os módulos não compartilham um catálogo único de talhões:

- Áreas usa `T-01 Norte`, `T-02 Sul`, `T-03 Leste`, `T-04 Oeste`, `T-05 Centro`;
- Atividades usa `Talhão A1`, `A3`, `B1`, `B4`, `C1`, `C2`, `D1` e `Oficina`;
- Ocorrências usa `Talhão A1`, `A3`, `B2`, `B4`, `C2`, `C4`, `D1` e `Oficina`;
- Custos usa principalmente os identificadores `T-01` a `T-05` e `Propriedade`.

Assim, o contrato por query string funciona tecnicamente, mas os links de Áreas para Atividades e Ocorrências normalmente produzem estado vazio. Em teste, `atividades.html?talhao=T-01%20Norte` exibiu o chip correto e zero cards visíveis.

### 8.3 Integrações apenas simuladas por mensagem

As seguintes operações não atravessam módulos:

- concluir atividade não reduz estoque real;
- concluir atividade não cria custo;
- saída de insumo não cria custo;
- criar ocorrência não atualiza alertas do Dashboard;
- criar atividade no Dashboard não cria atividade no módulo Atividades;
- editar propriedade em Configurações não altera cabeçalhos das demais páginas;
- salvar safra atual não atualiza dados dos demais módulos;
- métricas do Dashboard não refletem mutações dos módulos.

## 9. Backend e banco de dados

### 9.1 Resultado da varredura (atualizado)

Existe **fundação de backend e banco**, sem camada de produto nem integração com o frontend.

Implementado:

- `backend/gateway/` — Node.js + TypeScript, `GET /health` e `GET /ready` (probe do Actuator Java);
- `backend/core/` — Java 21, Spring Boot, Actuator, `spring-boot-starter-data-mongodb`, URI via `MONGODB_URI`;
- `compose.yaml` — serviço `mongo:8.0.32` em loopback com volume persistente;
- testes: gateway (`npm test`); core (`mvn test` — contexto sem exigir Mongo em execução).

Ainda ausente:

- rotas REST/GraphQL de negócio, DTOs, repositórios de domínio, migrações/esquema;
- autenticação e autorização;
- uso de `fetch`/Axios nas páginas HTML;
- dados persistentes consumidos pelas telas (mocks continuam só no browser).

### 9.2 Arquitetura prevista nos documentos

A planilha 5W2H/GUT define:

- frontend em HTML, CSS e JavaScript;
- camada intermediária Node.js + TypeScript;
- servidor principal Java;
- MongoDB;
- autenticação com sessão/token;
- CRUDs de propriedades, talhões, safras, atividades, estoque, ocorrências e custos;
- indicadores integrados do Dashboard;
- integração frontend/backend nas semanas finais.

Todos esses itens estão marcados como “A iniciar” na planilha analisada.

### 9.3 Modelo conceitual planejado

As entidades mínimas indicadas pela documentação são:

```text
Usuario
  └── Propriedade
       ├── Talhao
       │    └── Safra
       │         ├── Atividade
       │         ├── Ocorrencia
       │         └── Custo
       ├── ItemEstoque
       │    └── MovimentacaoEstoque
       └── Notificacao
```

Campos conceituais mínimos:

| Entidade | Campos esperados |
|---|---|
| Usuário | id, nome, e-mail, telefone, senha segura, função, preferências |
| Propriedade | id, usuário/organização, nome, localização, área total, safra padrão |
| Talhão | id, propriedade, nome/código, área, cultura atual, status |
| Safra | id, propriedade, cultura, período, status, talhões associados |
| Atividade | id, safra, talhão, tipo, título, responsável, data/hora, prioridade, status |
| Item de estoque | id, propriedade, nome, categoria, unidade, saldo, estoque mínimo |
| Movimentação | id, item, tipo entrada/saída/ajuste, quantidade, data, atividade opcional |
| Ocorrência | id, safra/talhão, título, tipo, severidade, descrição, status, data |
| Evento da ocorrência | id, ocorrência, data, autor, texto, tipo de evento |
| Custo | id, propriedade/safra/talhão, categoria, descrição, valor, data, origem opcional |
| Notificação | id, usuário, tipo, entidade relacionada, lida, data |

Esse quadro é uma consolidação conceitual da documentação, não um schema existente.

### 9.4 Regras que devem migrar para o servidor

Quando o backend for criado, estas regras não devem depender apenas do browser:

- autenticação, autorização e separação dos dados por usuário/propriedade;
- consistência de saldos de estoque;
- prevenção de saída maior que o saldo;
- atualização atômica de atividade, movimentação e custo;
- exclusão de item de estoque somente quando permitida pela regra de negócio;
- transições válidas de status;
- cálculo de métricas e agregados;
- histórico imutável/auditável de movimentações;
- resolução e timeline de ocorrências;
- validação de IDs relacionados;
- geração de notificações.

## 10. Requisitos funcionais consolidados

### 10.1 Núcleo essencial do MVP documental

- autenticação;
- Dashboard integrado;
- propriedades;
- talhões;
- safras;
- atividades;
- estoque;
- ocorrências;
- custos.

### 10.2 Complementares

- notificações;
- calendário;
- relatórios;
- upload de fotos;
- perfil.

### 10.3 Fora do escopo inicial

- diagnóstico automático por IA;
- identificação de pragas por IA;
- previsão de produtividade por IA;
- sensores e IoT;
- drones;
- integração com tratores;
- satélite e mapas avançados;
- contabilidade completa;
- integração bancária;
- folha de pagamento;
- gestão fiscal;
- marketplace.

## 11. Requisitos não funcionais e padrões de UX

### 11.1 Acessibilidade presente

- labels associados aos campos;
- `aria-hidden` em modais;
- `aria-expanded` em menus;
- papéis de menu, menuitem e listbox;
- navegação de popovers por setas, Home, End e Escape;
- foco inicial ao abrir modal;
- `aria-live` nos toasts;
- texto visualmente oculto em colunas de ação;
- suporte a `prefers-reduced-motion`.

### 11.2 Lacunas de acessibilidade

- não existe focus trap completo em modais;
- ao fechar modais nem sempre o foco retorna ao gatilho;
- não foi feita auditoria formal WCAG;
- a acessibilidade do drag and drop depende da alternativa em Lista, mas não há instrução explícita para leitores de tela;
- alguns botões/toggles usam semântica adaptada de checkbox em vez de tabs/radio;
- responsividade mobile não foi validada de ponta a ponta.

### 11.3 Responsividade

Existem breakpoints nas folhas CSS para 1200, 1100, 900, 760, 620, 600, 560 e 520 px. O shell reduz/ajusta navegação e layouts, e grades passam para menos colunas. Entretanto:

- não existe aplicação mobile dedicada;
- Kanban e timeline dependem de layouts genéricos;
- o board de design menciona bottom sheets e ações fixas mobile que não estão implementados;
- os changelogs registram que mobile não foi revisado de forma aprofundada.

## 12. Qualidade, verificação e histórico técnico

### 12.1 Resultado da verificação atual

- todos os scripts passaram na verificação de sintaxe;
- nenhuma referência local `src`/`href` inexistente foi encontrada nas 12 páginas raiz;
- todas as páginas carregaram com conteúdo significativo;
- não foram detectados erros ou warnings de console durante a navegação realizada;
- criar um talhão adicionou a sexta linha e alterou a métrica de 560 para 585 ha;
- criar uma atividade adicionou o nono card e alterou pendentes de 4 para 5;
- a filtragem contextual por talhão expôs a divergência de nomenclatura descrita neste relatório.

### 12.2 Bugs históricos já corrigidos

Os changelogs registram correções importantes:

- `[hidden]` perdia para componentes com `display`, deixando empty states visíveis;
- action menus eram cortados por contêineres com overflow;
- triggers criados dinamicamente não recebiam listeners;
- select de entrada de estoque era melhorado antes de receber opções;
- métricas empilhavam e comprimiam tabelas por falta de `.metrics-row` compartilhada;
- popovers permaneciam abertos após ações bloqueadas;
- botão de registrar ocorrência não estava conectado;
- transforms mantidos por `fill-mode: both/forwards` quebravam coordenadas de popovers fixed;
- atividade “Oficina” falhava ao duplicar porque o select não continha a opção.

### 12.3 Regra crítica para animações

Nunca manter `transform` com `animation-fill-mode: forwards` ou `both` em ancestrais de popovers `position: fixed`. Isso muda o containing block e desloca/corta os menus.

## 13. Riscos, divergências e dívida técnica

### Prioridade alta

1. **Persistência e API de negócio ainda ausentes.** A fundação (gateway, core Java, MongoDB local) existe, mas o MVP documental exige CRUD, auth e integração frontend↔backend; as telas HTML seguem como demonstração com mocks.
2. **Identidade inconsistente de talhões.** Quebra a integração contextual entre Áreas, Atividades e Ocorrências.
3. **Operações integradas são apenas mensagens.** Estoque, custos, histórico e Dashboard não são alterados em cadeia.
4. **Autenticação simulada.** Qualquer credencial válida no formato acessa o Dashboard, e páginas internas são públicas.
5. **Inserção de valores via `innerHTML`.** Alguns valores fornecidos pelo usuário são interpolados em templates HTML. Em uma aplicação conectada, isso cria risco de XSS se os dados não forem escapados/sanitizados.

### Prioridade média

6. Dados e métricas iniciais nem sempre correspondem à quantidade de registros visíveis.
7. “Talhões Ativos” conta todas as linhas, não apenas status ativo.
8. Período de Custos altera apenas rótulos.
9. Custo por cultura permanece estático.
10. Dashboard possui estado independente de todos os módulos.
11. Shell duplicado entre páginas favorece inconsistências, como Meu Perfil ainda em preparação em parte das telas.
12. Modais duplicam lógica e não possuem gestão uniforme de foco.
13. Exclusão de custo é imediata, enquanto outras exclusões destrutivas pedem confirmação.
14. Datas usam formatos mistos: ISO, “06 ago”, “hoje” e textos livres.
15. Nomes são usados como chave contextual; IDs estáveis seriam mais seguros.

### Prioridade baixa ou evolução

16. Não há paginação real.
17. Não há ordenação de tabelas, apesar de aparecer no design system.
18. Não há loading/skeleton ligado a operações reais.
19. Upload de imagem não existe.
20. Notificações não navegam ao registro real; exibem toast.
21. Relatórios e Safras não possuem páginas próprias.
22. Não há testes automatizados versionados.

## 14. Deck e materiais acadêmicos

O diretório `slides/` é um pequeno projeto independente de apresentação:

- 20 slides em `slides/index.html`;
- navegação por teclado, hash e gesto horizontal;
- tela cheia com tecla `F`;
- impressão com `P`;
- progresso e contador de slides;
- exportação em PNG 2x e montagem do PDF via Playwright e `pdf-lib`;
- deploy estático configurado para Vercel;
- PDF final de aproximadamente 21 MB.

O deck cobre contexto, problemas, evidências, impacto financeiro, solução, validação, funcionalidades, wireframes, protótipos, diferencial, mercado, stack, escopo, cronograma, integrantes e agradecimento.

Esses materiais fazem parte da narrativa acadêmica, mas não adicionam funcionalidade ao aplicativo.

## 15. Diretrizes obrigatórias para agentes futuros

Antes de implementar ou alterar uma tela:

1. ler `AGENTS.md` integralmente;
2. consultar `AgroGestao_Projeto.md` e `README.md`;
3. extrair o frame correspondente de `designSystemProjetoAgro` em vez de adivinhar conteúdo;
4. verificar changelogs para evitar regressões já conhecidas;
5. preservar mudanças não relacionadas existentes no working tree.

Ao implementar:

- reutilizar `styles/modules.css` e `scripts/shell.js`;
- manter a ordem dos scripts;
- usar `data-popover-trigger` e `data-popover` para action menus;
- reconstruir menus condicionais nas funções template de cada módulo;
- usar IDs estáveis para integração, não nomes exibidos;
- recalcular métricas a partir da fonte de verdade;
- não criar estado paralelo desnecessário;
- escapar todo valor inserido em HTML;
- não introduzir transforms persistentes em ancestrais de popovers;
- distinguir claramente operação real de feedback demonstrativo.

Ao verificar:

- executar verificação de sintaxe JavaScript;
- servir o projeto por HTTP;
- testar no navegador fluxos de criar, editar, filtrar, concluir/resolver, excluir e navegar;
- verificar erros de console;
- testar action menus próximos às bordas de tabelas e Kanban;
- testar elementos criados dinamicamente;
- testar empty states e `[hidden]`;
- testar pelo menos um breakpoint reduzido;
- conferir se uma mutação atualiza todas as métricas e módulos dependentes.

## 16. Caminho recomendado para transformar o protótipo em MVP integrado

### Etapa 1 — Contrato de dados

- definir IDs de usuário, propriedade, talhão, safra e demais entidades;
- unificar o catálogo de talhões usado por todas as telas;
- formalizar enums de status, tipos, unidades e categorias;
- decidir embutimento versus referência no MongoDB;
- definir datas em ISO e conversão apenas na camada de apresentação.

### Etapa 2 — Backend mínimo

- autenticação segura;
- CRUD de propriedade, talhão e safra;
- CRUD e transições de atividade;
- estoque e movimentações transacionais;
- ocorrências e timeline;
- custos e agregados;
- endpoint de Dashboard.

### Etapa 3 — Integração do frontend

- substituir mocks por cliente HTTP;
- criar estados de loading, erro e retry;
- manter os componentes atuais como camada de apresentação;
- fazer query string transportar IDs, preservando nome apenas como rótulo;
- atualizar dados após cada mutação sem depender de reload manual.

### Etapa 4 — Transação operacional central

Implementar uma operação de conclusão de atividade que, quando aplicável:

```text
valida atividade e saldo
  → conclui atividade
  → cria movimentação de saída
  → cria custo relacionado
  → registra histórico
  → gera notificações necessárias
  → recalcula agregados do Dashboard
```

A operação deve ser atômica no servidor para evitar atividade concluída com estoque ou custo inconsistente.

### Etapa 5 — Qualidade e segurança

- testes unitários de regras;
- testes de integração da API;
- testes end-to-end dos dois fluxos centrais;
- sanitização e proteção contra XSS;
- autorização por propriedade/organização;
- logs e tratamento padronizado de erros;
- auditoria de acessibilidade e responsividade.

## 17. Critérios de aceite do MVP completo

O projeto somente atende ao critério documental de MVP quando os fluxos abaixo funcionarem com dados persistidos e integrados.

### Fluxo produtivo

```text
Cadastrar propriedade
  → cadastrar talhão
  → cadastrar safra
  → planejar atividade
  → executar/concluir atividade
  → registrar consumo de insumo
  → atualizar saldo
  → registrar custo
  → atualizar histórico
  → refletir no Dashboard
```

### Fluxo de ocorrência

```text
Identificar problema
  → registrar ocorrência
  → publicar acompanhamento
  → resolver ocorrência
  → preservar histórico
  → refletir no Dashboard/notificações
```

### Requisitos de validação

- dados permanecem após reload e nova sessão;
- cada usuário acessa apenas suas propriedades autorizadas;
- vínculos usam IDs existentes;
- saldo nunca fica inconsistente;
- métricas são derivadas dos dados persistidos;
- registros concluídos/resolvidos permanecem consultáveis;
- erros do servidor são comunicados de forma humana;
- interface mantém os padrões do Forest Sage.

## 18. Estado final desta análise

O repositório contém um frontend visualmente consistente, amplo e funcional como demonstração. Os módulos possuem boa cobertura de interações locais, componentes reaproveitados e decisões cuidadosas para popovers, filtros, modais, toasts e Kanban. O trabalho realizado até aqui é uma base de interface madura para integração futura.

Entretanto, a proposta central do produto — integração persistente entre atividade, estoque, custos, histórico, safra e Dashboard — ainda não existe. O principal próximo marco técnico não é adicionar mais telas mock, mas criar um modelo de dados compartilhado e conectar o frontend a regras transacionais no backend.

Este relatório deve ser usado por agentes futuros como mapa do estado atual, e não como autorização para assumir que recursos planejados já estão implementados.
