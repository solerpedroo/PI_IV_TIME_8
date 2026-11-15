<!-- Autor principal: Pedro André do Carmo Chavier - RA: 25018639 -->
# Plano de testes do AgroGestão

| Campo | Valor |
| --- | --- |
| Documento | Estratégia de qualidade e cenários prioritários de teste |
| Projeto | [PI_IV_TIME_8 · Project nº 3](https://github.com/users/solerpedroo/projects/3), Sprint 3, issue #276 |
| Versão | 1.0, de 15/11/2026 |
| Base de código conferida | `dev` em `e80f08f`. `backend/`, `frontend/`, `tests/`, `.github/` e `compose.yaml` não mudaram desde `99ac03e`, a base da execução de 14/11/2026 |
| Documentos relacionados | [Requisitos](02-requisitos.md), [Casos de uso](03-casos-de-uso.md), [Contrato da API](06-contrato-api.md), [Relatório de testes](10-relatorio-de-testes.md), [Rastreabilidade](13-rastreabilidade.md) |
| Evidências | [`docs/evidencias/testes/`](evidencias/testes/) |

## Sumário

1. [Objetivo](#1-objetivo)
2. [Escopo e exclusões](#2-escopo-e-exclusões)
3. [Tipos de teste](#3-tipos-de-teste)
4. [Ambiente e ferramentas](#4-ambiente-e-ferramentas)
5. [Dados de teste](#5-dados-de-teste)
6. [Cenários e casos de teste](#6-cenários-e-casos-de-teste)
7. [Critérios de aprovação](#7-critérios-de-aprovação)
8. [Responsáveis e limitações](#8-responsáveis-e-limitações)

---

## 1. Objetivo

Este plano define o que é testado no AgroGestão, com quais ferramentas, em que
ambiente, com que dados e com que critério um resultado conta como aprovado. Ele
organiza as verificações que já existem no repositório. Não cria cobertura nova
nem antecipa resultados.

Três documentos tratam da qualidade, cada um com uma função:

| Documento | Pergunta que responde |
| --- | --- |
| Este plano (09) | O que testar, como, com que dados e quando considerar aprovado. |
| [Relatório de testes](10-relatorio-de-testes.md) (10) | O que foi executado, quando, onde, e qual foi o resultado. |
| [Rastreabilidade](13-rastreabilidade.md) (13) | Que teste cobre cada requisito (RF e RNF). |

O **status inicial** de cada caso na [§6](#6-cenários-e-casos-de-teste) vem de
execuções registradas, nunca da leitura do código:

- **Java:** execução de 14/11/2026 em `99ac03e`
  ([evidência](evidencias/testes/execucao-2026-11-14-resumo.txt)).
- **Navegador:** execução de 12/11/2026 em `60d7dda`
  ([relatório §4.3](10-relatorio-de-testes.md#43-navegador-testscjs-primeira-execução)).
  Entre essa base e a atual, `tests/` só recebeu `identidade-visual-real.cjs`.
- **GitHub Actions:** última rodada completa, no PR #303 (`710afc4`, 13/11/2026),
  registrada na mesma evidência de 14/11.

---

## 2. Escopo e exclusões

### 2.1 Em escopo

| Componente | O que se verifica | Onde ficam os testes |
| --- | --- | --- |
| ServerSocket de algoritmos ([`backend/serversocket`](../backend/serversocket/)) | Validações e cálculos de conta, talhão, estoque, safra, geometria e manutenção, e o protocolo de mensagens entre API e socket | [`src/test/java/`](../backend/serversocket/src/test/java/) |
| API HTTP Java ([`backend/api`](../backend/api/)) | Rotas, regras de negócio, autenticação, isolamento, limites HTTP e concorrência | [`teste/`](../backend/api/teste/) |
| Persistência MongoDB | Repositórios `*Mongo`, transações, reconexão e reinício do servidor | `TesteMongo`, `TesteEstoqueMongo`, opção `--mongo` das suítes e suítes de navegador com MongoDB |
| Gateway opcional ([`backend/gateway`](../backend/gateway/)) | `/health`, `/ready`, repasse de `/api/**`, limite de corpo e erros | [`teste/TesteGateway.java`](../backend/gateway/teste/TesteGateway.java) |
| Modelo de dados do core ([`backend/core`](../backend/core/)) | Regras de domínio das issues #100 a #105 e, com `--atlas`, gravação e releitura no Atlas | [`src/test/java/`](../backend/core/src/test/java/) |
| Frontend ([`frontend/`](../frontend/)) | Telas, cache local escopado, layout, identidade visual e fluxos ponta a ponta | [`tests/`](../tests/) |
| Integração contínua | Execução das suítes por módulo em pull request | [`.github/workflows/`](../.github/workflows/) |

A issue define seis áreas prioritárias: autenticação, isolamento por
propriedade, persistência, estoque, ocorrências e fluxos de navegador. Elas
ocupam as seções [§6.1](#61-autenticação-e-sessão) a
[§6.6](#66-fluxos-de-navegador). As demais verificações automatizadas ficam na
[§6.7](#67-servidor-http-algoritmos-e-gateway) e as manuais na
[§6.8](#68-validação-manual).

### 2.2 Exclusões

- **Desempenho e carga.** Não há suíte nem meta de tempo de resposta. Os testes
  de concorrência (`TesteServidor.testarConcorrencia`,
  `TesteServidor.testarLimiteDeConexoes` e saídas simultâneas de estoque)
  verificam correção sob paralelismo, não velocidade.
- **Teste de invasão e varredura de vulnerabilidades.** Ficam fora do plano. As
  proteções verificadas são as das seções [§6.1](#61-autenticação-e-sessão) e
  [§6.7](#67-servidor-http-algoritmos-e-gateway): hash de senha, sessão, limite
  de tentativas, limites HTTP, CORS e estáticos restritos.
- **TLS.** A API não implementa HTTPS. `empty-state-origin.cjs` confere apenas
  que o frontend usa a origem da página, inclusive quando ela é HTTPS.
- **Outros navegadores.** A automação usa o Chromium do Playwright. Outro
  executável, como o Edge, só com `BROWSER_PATH`, sem rotina definida. Firefox e
  Safari não são testados.
- **Leitor de tela.** Não é testado. `ui-polish.cjs` cobre layout e teclado
  (Tab e Escape nos modais).
- **Banco de desenvolvimento ou compartilhado.** Nenhum teste grava fora de um
  banco com nome terminado em `_teste` (ver a [§5.2](#52-mongodb)).
- **Envio real de e-mail.** Só pelo roteiro manual CT-MAN-01.
- **Workflows de automação do Project**, que não testam o produto:
  `auto-comment.yml`, `block-main-pr.yml`, `column-tracker.yml`,
  `column-tracker-backfill.yml` e `cleanup-old-tracker.yml`.

---

## 3. Tipos de teste

| Tipo | O que verifica | Ferramenta | Suítes | Execução |
| --- | --- | --- | --- | --- |
| Unidade de algoritmo | Regras do ServerSocket, chamadas direto ou pelo protocolo em portas efêmeras | JUnit 5; JaCoCo no `mvn verify` | 13 classes em `backend/serversocket/src/test/java/` | Automatizada, local |
| Integração HTTP em memória | API real em porta livre, repositórios em memória e requisições HTTP de verdade | `main` próprio, sem JUnit, com contagem de verificações | `TesteServidor`, `TesteOrcamentos`, `TestePerfil`, `TesteGeorreferenciamento`, `TesteOcorrencias`, `TesteColheitas`, `TesteManutencao`, `TesteRelatorioSafra` | Automatizada, local, com socket na 30100 |
| Integração com MongoDB | Repositórios `*Mongo`, transações e reconexão | `main` próprio | `TesteMongo`, `TesteEstoqueMongo` e a opção `--mongo` de 6 suítes | Automatizada, **depende de MongoDB** em replica set |
| Gateway | Saúde, repasse, limites e falhas do gateway | `main` próprio | `TesteGateway` | Automatizada, local |
| Domínio do core | Regras do modelo de dados e, opcionalmente, o ciclo no Atlas | Maven (`exec:exec`), `main` próprio | 7 classes locais; `MongoAtlasValidacaoTest` e `CicloDadosAtlasTest` com `--atlas` | Automatizada; `--atlas` **depende do Atlas** |
| Unidade Node | Escopo das chaves do `localStorage` por conta e propriedade | `node:test` e `node:vm`, sem navegador | `storage-scope.cjs` | Automatizada, local |
| Navegador com API simulada | Telas contra `mock-api.cjs` ou servidor estático, sem Java | Playwright e Chromium | `local-persistence`, `local-screens`, `empty-state-origin`, `ui-polish`, `identidade-visual-real` | Automatizada, local |
| Ponta a ponta com servidor real | Navegador, API Java em `--memoria` e socket | Playwright e Chromium | 12 suítes `*-real.cjs` e as auditorias `cultivo-plantio-audit` e `todas-telas-audit` | Automatizada, local, com socket na 3000 |
| Ponta a ponta com MongoDB | Persistência após reinício do processo Java | Node, Playwright, Docker e módulo `mongodb` | `atividades-reinicio` (só HTTP), `estoque-real`, `relatorio-safra-real`, `ocorrencias-real --mongo` e `perfil-real` com `PROFILE_MONGO=1` | Automatizada, **depende de MongoDB ou Docker** |
| Manual | O que não tem automação ou depende de serviço real | Roteiro e conferência visual | [§6.8](#68-validação-manual) | Manual |

Os arquivos `browser-launch.cjs`, `mock-api.cjs`, `audit-prereqs.cjs`,
`token-confirmacao.cjs` e `fixtures/plots.cjs` são módulos de apoio, não suítes.

---

## 4. Ambiente e ferramentas

### 4.1 Ferramentas e versões

| Ferramenta | Versão | Uso | Onde a versão é definida |
| --- | --- | --- | --- |
| JDK | 21 (Temurin no CI) | Compila e executa todo o Java, com `--release 21` | `setup-java` nos workflows e scripts `compilar`/`testar` |
| JUnit Jupiter | 5.11.4 | Suíte do socket via Maven | [`backend/serversocket/pom.xml`](../backend/serversocket/pom.xml) |
| JUnit Platform Console Standalone | 1.11.4 | Suíte do socket sem Maven; o `testar.sh` baixa o jar e confere o SHA-256 | [`backend/serversocket/testar.sh`](../backend/serversocket/testar.sh) |
| JaCoCo | 0.8.14 | Cobertura mínima do pacote `algoritmos`: 90% de linhas e 85% de ramos, verificada só no `mvn verify` | `backend/serversocket/pom.xml` |
| Maven | não fixada | Opcional no socket (o `testar.sh` usa `mvn -q test` quando encontra o Maven); obrigatório no core | [`backend/core/testar.sh`](../backend/core/testar.sh) (`MAVEN_BIN`) |
| MongoDB | 8.0.32, replica set `rs0` | Transações e persistência | [`compose.yaml`](../compose.yaml) e `docker run` nos workflows de estoque e perfil |
| Docker e Compose | não fixada | Sobe o MongoDB; `atividades-reinicio.cjs` usa `docker compose ps` e `docker exec` | — |
| Node.js | 22 no CI | Executa `tests/*.cjs` | `setup-node` nos workflows |
| Playwright e Chromium | 1.49.1 | Navegador. Não há `package.json`; o CI instala com `npm install --no-save` | Workflows |
| Módulo Node `mongodb` | não fixada | Só `relatorio-safra-real.cjs`, que também importa `playwright` diretamente | Nenhum workflow instala |
| GitHub Actions | `ubuntu-22.04` ou `ubuntu-latest` | CI | `.github/workflows/` |

### 4.2 Portas usadas pelos testes

| Porta | Quem usa |
| --- | --- |
| 30100 | Socket de algoritmos que `backend/api/testar.sh` sobe para as suítes Java. |
| 3000 | Socket esperado pela API que as suítes de navegador sobem. `login-real.cjs` sobe o seu; nas demais, ele precisa estar no ar antes. |
| 18080 | `login-real.cjs` (valor padrão de `AUTH_TEST_PORT`), `cultivo-plantio-audit.cjs` e `todas-telas-audit.cjs`. |
| 18081 e 18082 | Primeiro e segundo processo do `atividades-reinicio.cjs`. |
| 8080 | `estoque-real.cjs` (exige a porta livre) e `relatorio-safra-real.cjs`. |
| 27017 | MongoDB local, pelo Compose ou `docker run`. |
| 27172 | MongoDB padrão do `relatorio-safra-real.cjs` (`TEST_MONGO_URI`, replica set `rs172`). |
| livre | `TesteServidor` sobe a API em porta livre; o servidor estático de `identidade-visual-real.cjs` também. |

### 4.3 Variáveis de ambiente

| Variável | Efeito |
| --- | --- |
| `PLAYWRIGHT_MODULE` | Caminho ou nome do módulo Playwright usado pelas suítes de navegador (no CI, `playwright`). |
| `BROWSER_PATH` | Outro executável de navegador, como o Edge. |
| `UI_SCREENSHOTS`, `AUDIT_SCREENSHOTS`, `STOCK_SCREENSHOT` | Gravam capturas opcionais. Não versionar. |
| `SERVIDOR_ALGORITMOS_HOST`, `SERVIDOR_ALGORITMOS_PORT` | Endereço do socket para a API (padrão `127.0.0.1:3000`). |
| `MONGODB_URI`, `MONGODB_DATABASE` | Banco das suítes com MongoDB. O nome precisa terminar em `_teste`. |
| `PROFILE_MONGO=1` | Faz `perfil-real.cjs` usar MongoDB em vez de memória. |
| `TEST_MONGO_URI`, `JAVA_BIN`, `TEST_ARTIFACT_DIR` | Configuração do `relatorio-safra-real.cjs`. |
| `AUTH_TEST_PORT` | Porta da API no `login-real.cjs`. |
| `MAVEN_BIN`, `AGRO_BUILD_DIR` | Maven e pasta de compilação do `backend/core/testar.sh`. |

Todas as suítes de `tests/` que sobem a API limpam `SMTP_HOST` (a maioria,
todas as variáveis `SMTP_*`), para que o e-mail seja apenas simulado no log.

### 4.4 Comandos de execução

Na raiz do repositório, em Bash (Linux, macOS ou Git Bash), na ordem
recomendada:

```bash
# 1. Servidor de algoritmos (JUnit 5)
sh backend/serversocket/testar.sh
mvn -B -ntp -f backend/serversocket/pom.xml clean verify   # inclui o limite do JaCoCo, como no CI

# 2. API em memória: sobe o socket na 30100 e encadeia as 8 suítes com &&
sh backend/api/testar.sh
# Para rodar uma suíte isolada depois de compilar (o testar.sh encerra o socket ao sair):
(cd backend/serversocket && java -cp bin Servidor 30100 &)
(cd backend/api && SERVIDOR_ALGORITMOS_PORT=30100 java -cp "bin:lib/*" TesteColheitas)

# 3. Gateway e core
sh backend/gateway/testar.sh
./backend/core/testar.sh

# 4. Node e navegador com API simulada (Playwright instalado)
npm install --no-save playwright@1.49.1 && npx playwright install chromium --with-deps
node tests/storage-scope.cjs
for t in local-persistence local-screens empty-state-origin ui-polish identidade-visual-real; do
  PLAYWRIGHT_MODULE=playwright node tests/$t.cjs
done

# 5. Navegador com servidor real (API compilada e socket na 3000)
sh backend/serversocket/compilar.sh && (cd backend/serversocket && java -cp bin Servidor 3000 &)
sh backend/api/compilar.sh
PLAYWRIGHT_MODULE=playwright node tests/talhoes-real.cjs   # idem para as demais *-real.cjs e auditorias

# 6. MongoDB (banco terminado em _teste)
docker compose up -d --wait mongo
sh backend/api/testar-mongo.sh
MONGODB_URI='mongodb://127.0.0.1:27017/agro_estoque_teste?replicaSet=rs0' sh backend/api/testar-estoque-mongo.sh
(cd backend/api && MONGODB_URI='mongodb://127.0.0.1:27017/agro_ocorrencias_teste?replicaSet=rs0' \
  SERVIDOR_ALGORITMOS_PORT=30100 java -cp "bin:lib/*" TesteOcorrencias --mongo)
node tests/atividades-reinicio.cjs
docker compose down
```

No Windows há diferenças de script; ver a [§8.2](#82-limitações).

### 4.5 Integração contínua (GitHub Actions)

Todos os workflows abaixo rodam em `pull_request` com filtro de caminhos. Quatro
também rodam em `push`: `dados-testes.yml` e `gateway-testes.yml` em `main` e
`dev`, `ui-polish.yml` em `main` e `dev`, e `local-persistence.yml` só em `main`.

| Workflow | Passos de teste, na ordem | MongoDB |
| --- | --- | --- |
| `serversocket-testes.yml` | `compilar.sh`; `mvn clean verify` (JUnit e JaCoCo) | — |
| `dados-testes.yml` | `backend/core/testar.sh` | — |
| `gateway-testes.yml` | `backend/gateway/testar.sh` | — |
| `auth-formularios-testes.yml` | `backend/api/testar.sh`; `login-real.cjs` | — |
| `atividades-testes.yml` | `testar.sh`; `atividades-real.cjs`; `atividades-reinicio.cjs` | Compose |
| `calendario-testes.yml` | `testar.sh`; `calendario-real.cjs` | — |
| `colheita-testes.yml` | `testar.sh`; `colheita-real.cjs`; `TesteColheitas --mongo` | Compose |
| `custos-testes.yml` | `testar.sh`; `custos-real.cjs` | — |
| `estoque-testes.yml` | `testar.sh`; `testar-estoque-mongo.sh`; `estoque-real.cjs` | `docker run` |
| `georreferenciamento-testes.yml` | socket `testar.sh`; API `testar.sh`; socket na 3000; `georreferenciamento-real.cjs`; `TesteGeorreferenciamento --mongo` | Compose |
| `manutencao-testes.yml` | socket `testar.sh`; API `testar.sh`; socket na 3000; `manutencao-real.cjs`; `TesteManutencao --mongo` | Compose |
| `notificacoes-testes.yml` | `testar.sh`; `notificacoes-real.cjs` | — |
| `ocorrencias-testes.yml` | `testar.sh`; `ocorrencias-real.cjs`; `TesteOcorrencias --mongo`; `ocorrencias-real.cjs --mongo` | Compose |
| `orcamento-testes.yml` | `testar.sh`; `orcamento-real.cjs`; `TesteOrcamentos --mongo` | Compose |
| `perfil-testes.yml` | `testar.sh`; `TestePerfil --mongo`; `perfil-real.cjs` com `PROFILE_MONGO=1` | `docker run` |
| `identidade-visual-testes.yml` | `identidade-visual-real.cjs` | — |
| `local-persistence.yml` | `local-persistence.cjs`; `local-screens.cjs` | — |
| `ui-polish.yml` | `ui-polish.cjs`; `empty-state-origin.cjs` | — |

Fora do CI ficam `storage-scope.cjs`, `talhoes-real.cjs`,
`cultivo-plantio-audit.cjs`, `todas-telas-audit.cjs`, `relatorio-safra-real.cjs`,
`TesteMongo` e o modo `--atlas` do core.

---

## 5. Dados de teste

### 5.1 Princípios

- O aplicativo começa sem registros operacionais de exemplo. Dados fictícios
  existem só em `tests/`, fora do que o servidor entrega
  ([revisão de dados reais](revisao-dados-reais.md)).
- Cada execução cria os próprios dados e não depende de registros de execuções
  anteriores.
- As contas criadas pelas suítes são fictícias, como `maria@agro.com` em
  `TesteServidor` e `real@agro.test` em `talhoes-real.cjs`. O único destino
  real é o e-mail informado no roteiro manual CT-MAN-01.

### 5.2 MongoDB

Toda suíte com MongoDB exige um banco cujo nome termine em `_teste` e recusa
qualquer outro antes de gravar:

| Onde | Banco |
| --- | --- |
| `TesteMongo`, `TestePerfil`, `TesteOcorrencias`, `TesteOrcamentos`, `TesteColheitas`, `TesteGeorreferenciamento`, `TesteManutencao`, `CenarioEstoqueTeste` | Conferem o sufixo `_teste` no início da execução. |
| `backend/api/testar-mongo.sh` | `agrogestao_teste` local, quando `MONGODB_URI` não é informada. |
| Workflows | Um banco por módulo, como `agro_estoque_ci_teste` e `agro_ocorrencias_ci_teste`. |
| `atividades-reinicio.cjs` | Banco aleatório `agro_i135_<hex>_teste`. Confere que ele não existia e o apaga ao final. |
| `relatorio-safra-real.cjs` | Banco aleatório `agro_i172_<hex>_teste`. |
| `estoque-real.cjs` | Exige banco novo vazio; ao final roda `CenarioEstoqueTeste --limpar`. |
| `backend/core/testar.sh --atlas` | `MONGODB_DATABASE` terminado em `_teste` (por exemplo, `agro_ci_teste`). |

O banco do MongoDB local precisa estar em replica set (`rs0`), porque cadastro,
estoque, ocorrências e atividades usam transações. O `compose.yaml` já inicia
o replica set no healthcheck.

### 5.3 Contas e códigos de confirmação

Sem SMTP, a API imprime o e-mail simulado no log, e as suítes de navegador leem
o código de confirmação dali (`token-confirmacao.cjs`). Hoje o módulo procura
`token:` e o servidor imprime `codigo:`, o que bloqueia as suítes que
dependem dele ([relatório §5](10-relatorio-de-testes.md#5-bugs-e-correções),
item 1).

### 5.4 Catálogo de culturas

A API só aceita talhão com cultura cadastrada em `/api/culturas`. Por isso, o
dado de teste de qualquer fluxo com talhão precisa criar a cultura antes.
Nenhuma suíte em `tests/` chama `/api/culturas` hoje, e esse é o item 2 do
[relatório §5](10-relatorio-de-testes.md#5-bugs-e-correções).

### 5.5 Fixtures e API simulada

| Arquivo | Conteúdo |
| --- | --- |
| [`tests/fixtures/plots.cjs`](../tests/fixtures/plots.cjs) | Talhões fictícios (`T-01 Norte`, `T-02 Sul`, ...) usados só pelas suítes de interface. |
| [`tests/mock-api.cjs`](../tests/mock-api.cjs) | API simulada para `local-*`, `empty-state-origin` e `ui-polish`: `/api/me`, propriedades, talhões, safras, atividades, calendário, insumos, ocorrências, orçamentos, mapa e perfil. Não simula `/api/culturas` nem `/api/propriedades/periodos-safra` ([relatório §5](10-relatorio-de-testes.md#5-bugs-e-correções), item 3). |

### 5.6 Valores conhecidos

Algumas verificações comparam com resultados calculados à mão:

- `TesteServidor.testarCustos`: R$ 20.190,56 sobre 296,5 ha.
- `TesteServidor.testarCustosDeConsumo`: custo de R$ 125,00 no talhão e na safra.
- `TesteServidor.testarEstoque`: indicador de R$ 60 e um item abaixo do mínimo.
- `backend/core` com `--atlas`: os valores do roteiro em
  [teste prático do banco](teste-pratico-banco.md), como 12,75 ha, 500 kg de
  entrada e 50 kg de consumo, e custo de R$ 250,00 com total de R$ 300,00.

### 5.7 Evidências

Cada execução registrada gera arquivos em
[`docs/evidencias/testes/`](evidencias/testes/) com o nome
`execucao-AAAA-MM-DD-<conteúdo>.txt`. Eles trazem o commit, o ambiente, o
código de saída e a última linha de cada suíte, e trechos das falhas. Capturas
de tela são opcionais e não são versionadas.

---

## 6. Cenários e casos de teste

### Legenda

**Tipo:** *Unidade*, *Integração* (API em memória), *Integração MongoDB*,
*E2E simulado* (navegador e `mock-api.cjs`), *E2E real* (navegador, API em
memória e socket), *E2E MongoDB* e *Manual*.

**Status inicial**, com a mesma convenção da
[rastreabilidade §2.3](13-rastreabilidade.md#23-situação-dos-testes):

| Status | Significado |
| --- | --- |
| aprovado | A execução registrada passou. |
| parcial | A suíte rodou com falhas; a linha diz quais. |
| falha | A suíte rodou e reprovou no ponto descrito. |
| bloqueado | A suíte parou antes de exercitar o cenário por defeito do próprio teste. Não indica produto correto nem quebrado. |
| pendente | Depende de MongoDB, Docker ou Atlas e não tem execução registrada. |
| sem registro | Automatizado e sem dependência externa, mas sem execução registrada que meça o cenário. |
| não executado | Caso manual ainda sem registro. |

Os requisitos entre parênteses remetem a [02-requisitos.md](02-requisitos.md).

### 6.1 Autenticação e sessão

Prioridade alta.

| ID | Cenário | Tipo | Teste de referência | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-AUT-01 | Cadastro com dados inválidos e válidos (RF-001, RNF-001) | Integração; Unidade | `TesteServidor.testarCadastro`; `ValidadorDeContaTest` | Corpo vazio, e-mail inválido, senha fraca ou acima de 128 caracteres, telefone fixo e termos não aceitos → 400 apontando o campo. Cadastro válido → 201, sem hash nem token na resposta, com senha gravada como `pbkdf2$`. E-mail repetido, mesmo em maiúsculas → 409. | aprovado (14/11) |
| CT-AUT-02 | Confirmação de e-mail e reenvio (RF-002) | Integração | `TesteServidor.testarConfirmacaoERedefinicao` | Código errado → 400. Confirmação → 200, e o código não pode ser reutilizado. Reenvio antes de 30 s → 429 com `Retry-After`. Login liberado só depois da confirmação. | aprovado (14/11) |
| CT-AUT-03 | Login sem revelar se o e-mail existe (RF-003, RNF-003) | Integração | `TesteServidor.testarLogin` | Senha errada e e-mail inexistente → 401 com o mesmo corpo. Login sem senha → 400. E-mail não confirmado → 403. | aprovado (14/11) |
| CT-AUT-04 | Sessão Bearer e logout (RF-003, RF-004, RNF-002) | Integração | `TesteServidor.testarConfirmacaoERedefinicao` | `GET /api/me` sem token ou com token falso → 401. Rota `/api` inexistente → 401 sem token e 404 com token. Logout → 204, e o token deixa de valer. | aprovado (14/11) |
| CT-AUT-05 | Limite de sessões e de tentativas (RNF-002, RNF-004) | Integração | `TesteServidor.testarProtecoesDeAbuso`; `TestePerfil` | A sexta sessão derruba a mais antiga, também com logins simultâneos. Tentativa acima da janela do limitador é recusada. | aprovado (14/11) |
| CT-AUT-06 | Recuperação e redefinição de senha (RF-005) | Integração | `TesteServidor.testarConfirmacaoERedefinicao`; `TestePerfil` | "Esqueci a senha" com e-mail inexistente → 200, como para um e-mail cadastrado. E-mail inválido → 400. Senha fraca, confirmação diferente ou código errado → 400. | aprovado (14/11) |
| CT-AUT-07 | Formulários de autenticação no navegador (RF-001 a RF-005) | E2E real | `tests/login-real.cjs` | Cada formulário chama a rota certa; o login não cai em `forgot-password`. | bloqueado (12/11): o teste procura `token:` no log |

### 6.2 Isolamento por propriedade

Prioridade alta.

| ID | Cenário | Tipo | Teste de referência | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-ISO-01 | Propriedade de outro usuário (RF-007, RF-009, RNF-005) | Integração | `TesteServidor.testarPropriedades` | Consultar, alterar, selecionar ou excluir propriedade alheia → 404. | aprovado (14/11) |
| CT-ISO-02 | Contexto da propriedade ativa (RF-008) | Integração | `TesteServidor.testarPropriedades` | Depois de selecionar a propriedade, `/api/me` a informa e os talhões usam esse contexto. A API não exclui a propriedade ativa, a que está em uso em outra sessão nem a última do usuário. | aprovado (14/11) |
| CT-ISO-03 | Estoque de outra propriedade ou produtor | Integração | `TesteServidor.testarEstoque` | Insumos isolados por propriedade. Outro usuário não acessa, não edita e não exclui o produto. | aprovado (14/11) |
| CT-ISO-04 | Ocorrências de outra propriedade | Integração | `TesteOcorrencias` | A contagem de outra propriedade não recebe a ocorrência (verificação "outra propriedade sem contaminação"). | aprovado (14/11) |
| CT-ISO-05 | Equipe de outra propriedade | Integração | `TesteServidor.testarEquipe` | Outra propriedade não lista o integrante; alterar ou excluir → 404. | aprovado (14/11) |
| CT-ISO-06 | Isolamento com MongoDB | Integração MongoDB | `TesteEstoqueMongo`; `TesteMongo` | Estoque isolado por propriedade e safras filtradas pela propriedade também nos repositórios `*Mongo`. | pendente |
| CT-ISO-07 | Leitura por outro produtor no Atlas | Integração MongoDB | `backend/core/testar.sh --atlas` (`CicloDadosAtlasTest`) | Um segundo processo, consultando como outro produtor, recebe resultado vazio em todos os módulos. | pendente |
| CT-ISO-08 | Cache local separado por conta e propriedade (RNF-005, RNF-009) | Unidade | `tests/storage-scope.cjs` | Os dados locais de uma conta ou propriedade não aparecem para outra, inclusive depois do logout. | aprovado (12/11) |

A restrição de telas para colaboradores não tem teste automatizado; ver o caso
CT-MAN-07.

### 6.3 Persistência

Prioridade alta.

| ID | Cenário | Tipo | Teste de referência | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-PER-01 | Repositórios MongoDB da API (RNF-011, RNF-014) | Integração MongoDB | `sh backend/api/testar-mongo.sh` (`TesteMongo`) | O cadastro grava usuário e propriedade juntos. O índice único recusa e-mail duplicado. CRUD de propriedades, safras, atividades e custos funciona contra o banco. | pendente |
| CT-PER-02 | Suítes de módulo com MongoDB | Integração MongoDB | `TesteOrcamentos`, `TestePerfil`, `TesteGeorreferenciamento`, `TesteOcorrencias`, `TesteColheitas` e `TesteManutencao` com `--mongo` | As verificações da execução em memória também passam com um banco `_teste`. | pendente |
| CT-PER-03 | Atividades após reinício e com duas instâncias (RF-022 a RF-024, RF-036) | Integração MongoDB | `tests/atividades-reinicio.cjs` | Atividade com foto e edição grava no MongoDB. Duas instâncias da API disputando a mesma atividade: uma recebe 200, a outra 409, e o histórico tem só uma transição terminal. Depois de reiniciar o processo, status e histórico voltam completos e em ordem, com a descrição de execução e o motivo do cancelamento. | pendente (sem Docker) |
| CT-PER-04 | Perfil após recarga e novo login, com MongoDB (RF-048 a RF-052) | E2E MongoDB | `tests/perfil-real.cjs` com `PROFILE_MONGO=1` | Os dados enviados pelos formulários continuam depois de recarregar a página e entrar de novo. | pendente; em memória, ver CT-NAV-13 |
| CT-PER-05 | Relatório da safra com MongoDB e reinício (RF-054) | E2E MongoDB | `tests/relatorio-safra-real.cjs` | Dados isolados por usuário e relatório igual depois de reiniciar o processo. | pendente (sem o módulo `mongodb`) |
| CT-PER-06 | API como fonte de verdade no navegador (RNF-009, RNF-010) | E2E simulado | `tests/local-persistence.cjs` | Com a API indisponível, as telas não recuperam dados de exemplo nem cache legado. Falha de escrita local não confirma gravação. Abas concorrentes preservam a gravação mais recente. | aprovado (12/11, 9 verificações) |
| CT-PER-07 | Estados vazios, origem e sessão (RNF-010) | E2E simulado | `tests/empty-state-origin.cjs` | Conta vazia mostra telas sem registros. As requisições usam a origem da página, inclusive HTTPS. Falha temporária (503) não desloga, e 401 leva ao login. | aprovado (12/11) |
| CT-PER-08 | Regras de domínio do core | Unidade | `./backend/core/testar.sh` | As 7 classes locais terminam sem erro. | sem registro |

### 6.4 Estoque

Prioridade alta.

| ID | Cenário | Tipo | Teste de referência | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-EST-01 | Cadastro e validação de insumo (RF-027) | Integração | `TesteServidor.testarEstoque` | Criar → 201. Quantidade como texto ou negativa, estoque mínimo negativo, categoria fora do catálogo e limiar acima do mínimo são recusados. O catálogo exige autenticação. | aprovado (14/11) |
| CT-EST-02 | Entradas, saídas e ajustes (RF-028) | Integração; Unidade | `TesteServidor.testarEstoque`; `CalculadoraDeEstoqueTest` | A entrada atualiza o saldo. Saída acima do saldo → 409. O ajuste exige motivo e registra o saldo contado; ajuste zero é aceito com motivo. | aprovado (14/11) |
| CT-EST-03 | Saídas concorrentes (RNF-012) | Integração; Integração MongoDB | `TesteServidor.testarEstoque`; `TesteEstoqueMongo` | Consumos simultâneos não deixam o saldo negativo. Com duas conexões MongoDB, só uma saída é aceita, o saldo fica 3 e a rejeição não gera histórico. | memória: aprovado (14/11) · MongoDB: pendente |
| CT-EST-04 | Saída vinculada a atividade e custo automático (RF-029, RF-040) | Integração | `TesteServidor.testarEstoque`; `TesteServidor.testarCustosDeConsumo` | Só aparecem atividades persistidas do titular. O histórico guarda ID e título da atividade. Referência inexistente é recusada. O custo de R$ 125,00 aparece no talhão e na safra. | aprovado (14/11) |
| CT-EST-05 | Histórico imutável e exclusão lógica (RF-030) | Integração; Integração MongoDB | `TesteServidor.testarEstoque`; `TesteEstoqueMongo` | Produto com saldo não é excluído. Com saldo zero a exclusão é lógica e preserva o histórico. Produto excluído não recebe entrada. Saldo e vínculo do consumo continuam depois de reabrir a conexão. | memória: aprovado (14/11) · MongoDB: pendente |
| CT-EST-06 | Indicadores do estoque (RF-031) | Integração | `TesteServidor.testarEstoque` | O conjunto conhecido resulta em R$ 60 e um item abaixo do mínimo. O mesmo mês de outro ano não entra no indicador. | aprovado (14/11) |
| CT-EST-07 | Insumos no navegador com MongoDB e reinício | E2E MongoDB | `tests/estoque-real.cjs` | O banco de teste começa vazio. Só a atividade real do titular aparece. O histórico continua depois de arquivar, e as entradas do produto arquivado continuam no indicador. | pendente (sem MongoDB) |

### 6.5 Ocorrências

Prioridade alta.

| ID | Cenário | Tipo | Teste de referência | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-OCO-01 | Registro (RF-032) | Integração | `TesteOcorrencias` | A descrição é gravada literalmente, com o vínculo ao talhão. | aprovado (14/11) |
| CT-OCO-02 | Timeline e resolução (RF-033) | Integração | `TesteOcorrencias` | Os cinco eventos ficam completos e a descrição original é preservada. | aprovado (14/11) |
| CT-OCO-03 | Exclusão lógica (RF-034) | Integração | `TesteOcorrencias` | A ocorrência excluída sai da lista ativa e continua consultável, e o histórico mantém a resolução. A propriedade com histórico mantém o vínculo. | aprovado (14/11) |
| CT-OCO-04 | Concorrência e atomicidade (RNF-013) | Integração | `TesteOcorrencias` | Com dois clientes na mesma versão, exatamente um grava, sem duplicar nem perder evento. Falha no histórico desfaz a alteração do registro. | aprovado (14/11) |
| CT-OCO-05 | Resumo mensal (RF-035, RNF-018) | Integração | `TesteOcorrencias` | A resolução conta no mês pelo fuso de Brasília. Outro ano não herda a contagem. O indicador por talhão não inclui a ocorrência geral. | aprovado (14/11) |
| CT-OCO-06 | Foto de evidência (RF-036) | Integração | `TesteOcorrencias`; `TesteServidor.testarHttp` | Depois do upload, `temFoto` fica verdadeiro, os bytes PNG voltam iguais e o cadastro não muda. Corpo acima do limite é recusado. | aprovado (14/11) |
| CT-OCO-07 | Reconexão com MongoDB | Integração MongoDB | `TesteOcorrencias --mongo` | Depois de reconectar, descrição, resolução, exclusão e todos os eventos voltam em ordem. | pendente |
| CT-OCO-08 | Fluxo no navegador e alerta no painel (RF-032 a RF-035, RF-043) | E2E real; E2E MongoDB | `tests/ocorrencias-real.cjs` (com `--mongo`, inclui reinício do processo) | Registro, timeline e resumo pela tela; a descrição aparece como texto literal; o alerta do painel abre a ficha. | bloqueado (12/11) · `--mongo`: pendente |

### 6.6 Fluxos de navegador

Prioridade alta.

| ID | Cenário | Tipo | Teste de referência | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-NAV-01 | Telas operacionais com API simulada | E2E simulado | `tests/local-screens.cjs` | Áreas, atividades (criar, editar, arrastar, concluir e duplicar), custos, configurações, painel e notificações funcionam sem inventar dados. | parcial (12/11): o cenário "áreas" excede 30 s |
| CT-NAV-02 | Layout e interações das 12 telas (RNF-023, RNF-024) | E2E simulado | `tests/ui-polish.cjs` | Todas as telas passam nas verificações de layout, item ativo da barra lateral e teclado, sem erro no console ou recurso com falha. | falha (12/11): `mock-api.cjs` responde 404 em `/api/culturas` e `/api/propriedades/periodos-safra` |
| CT-NAV-03 | Identidade visual | E2E simulado | `tests/identidade-visual-real.cjs` | Símbolo, favicon e nome carregam em todas as telas, e a marca antiga não aparece. | aprovado (CI, PR #303) |
| CT-NAV-04 | Talhões e histórico (RF-012, RF-014) | E2E real | `tests/talhoes-real.cjs` | O talhão criado pela tela é gravado na API e aparece nos seletores. O histórico registra o evento. Falha do cache não duplica nem desfaz a operação remota. Ao trocar de propriedade, talhões e notificações locais da anterior não aparecem. | bloqueado (12/11) |
| CT-NAV-05 | Atividades: Kanban, lista e status (RF-020 a RF-025) | E2E real | `tests/atividades-real.cjs` | Planejar, editar, mudar status e cancelar pela tela, com confirmação da API. | bloqueado (12/11) |
| CT-NAV-06 | Calendário (RF-026) | E2E real | `tests/calendario-real.cjs` | Semana e mês mostram só as atividades do período, os filtros atualizam as contagens e a recarga mantém visão, período e dia. | bloqueado (12/11) |
| CT-NAV-07 | Custos e CSV (RF-037 a RF-040) | E2E real | `tests/custos-real.cjs` | O lançamento só aparece depois da confirmação da API, os totais batem com o conjunto conhecido e os filtros recortam lista, indicadores e CSV. | bloqueado (12/11) |
| CT-NAV-08 | Orçamento por safra (RF-057) | E2E real | `tests/orcamento-real.cjs` | Sem orçamento não há meta inventada. Um custo novo muda realizado, desvio e alerta. Safra finalizada fica somente leitura. | bloqueado (12/11) |
| CT-NAV-09 | Colheita e produtividade (RF-058) | E2E real | `tests/colheita-real.cjs` | Sem colheita a produtividade não é inventada; kg/ha, sc/ha e o comparativo usam a área real. | bloqueado (12/11) |
| CT-NAV-10 | Localização e mapa (RF-059) | E2E real | `tests/georreferenciamento-real.cjs` | Sem localização nada é desenhado e os talhões aparecem em "Sem localização". | bloqueado (12/11) |
| CT-NAV-11 | Manutenção de equipamentos (RF-060) | E2E real | `tests/manutencao-real.cjs` | Sem equipamentos, as métricas viram 0 depois da API e as listas mostram estado vazio. | bloqueado (12/11) |
| CT-NAV-12 | Notificações (RF-045 a RF-047) | E2E real | `tests/notificacoes-real.cjs` | Alertas vêm de registros reais, levam ao registro e mantêm o estado de leitura. | bloqueado (12/11) |
| CT-NAV-13 | Perfil e configurações em memória (RF-048 a RF-052) | E2E real | `tests/perfil-real.cjs` | Formulário → HTTP Java → repositório, com os dados mantidos após recarga e novo login. | bloqueado (12/11); passou na variante diagnóstica |
| CT-NAV-14 | Áreas de cultivo e plantio | E2E real | `tests/cultivo-plantio-audit.cjs` | Filtros e coluna Safra refletem a API. Falha no resumo de custos mostra "indisponível", sem inventar R$ 0,00. Áreas e Safras não transbordam no celular. | bloqueado (12/11) |
| CT-NAV-15 | Auditoria de todas as telas | E2E real | `tests/todas-telas-audit.cjs` | As 28 combinações de página e viewport ficam sem erro HTTP ou JavaScript, sem overflow e sem placeholder permanente. | bloqueado (12/11) |

As suítes bloqueadas param no cadastro pelo regex `token:`. Na variante
diagnóstica do relatório, só `perfil-real` passou; as demais avançaram e
quebraram em outro ponto, várias ao criar talhão com cultura fora do catálogo
([relatório §4.4](10-relatorio-de-testes.md#44-navegador-variante-diagnóstica-cópia-temporária-dos-testes)).

### 6.7 Servidor HTTP, algoritmos e gateway

Prioridade média.

| ID | Cenário | Tipo | Teste de referência | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-HTTP-01 | Requisições malformadas e limites (RNF-020) | Integração | `TesteServidor.testarHttp` | Linha de requisição inválida, HTTP/1.1 sem `Host`, `Content-Length` duplicado ou não numérico e JSON inválido → 400. Corpo acima de 1 MB → 413 (a rota de foto tem limite maior). Cabeçalhos acima de 16 KB → 431. `chunked` → 501. Método errado → 405 com `Allow`. | aprovado (14/11) |
| CT-HTTP-02 | CORS (RNF-007) | Integração | `TesteServidor.testarHttp` | CORS liberado para localhost e negado para outra origem. | aprovado (14/11) |
| CT-HTTP-03 | Estáticos restritos, `/health` e `/ready` (RNF-006, RNF-015) | Integração | `TesteServidor.testarSaudeEEstaticos` | `/health` → 200 `UP` e `/ready` → 200. `/` redireciona para `login.html`. São bloqueados `../` (também codificado), `.env`, `.git`, `backend/` (inclusive `Backend/` e `BACKEN~1`) e extensões fora da lista. | aprovado (14/11) |
| CT-HTTP-04 | Atendimento paralelo e erro isolado (RNF-012, RNF-015, RNF-021) | Integração | `TesteServidor.testarConcorrencia`; `TesteServidor.testarLimiteDeConexoes` | 50 requisições paralelas respondidas. Cadastros e talhões com o mesmo nome em paralelo: 1 criado e 19 conflitos. Exceção no handler → 500 genérico, e o servidor segue respondendo. Acima do limite de conexões → 503. | aprovado (14/11) |
| CT-HTTP-05 | Banco resolvido a partir da URI | Integração | `TesteServidor.testarResolucaoMongoBanco` | O path da URI tem prioridade sobre `MONGODB_DATABASE`, e URI e variável divergentes falham. | aprovado no Linux (12/11) · falha de DNS no Windows (14/11) |
| CT-ALG-01 | Algoritmos e protocolo do socket | Unidade | `sh backend/serversocket/testar.sh` (13 classes) | 0 falhas. | parcial (14/11): 276 de 280; as 4 falhas esperam que o período `1999/00` seja recusado |
| CT-ALG-02 | Cobertura mínima dos algoritmos | Unidade | `mvn -f backend/serversocket/pom.xml clean verify` | Pacote `algoritmos` com pelo menos 90% de linhas e 85% de ramos. | sem registro: no PR #303, o passo do CI falhou, e as falhas de JUnit (CT-ALG-01) encerram o `mvn verify` antes da verificação de cobertura |
| CT-MOD-01 | Suítes de módulo em memória | Integração | `TesteOrcamentos`, `TestePerfil`, `TesteManutencao`, `TesteRelatorioSafra` | Todas as verificações passam. | aprovado (14/11) |
| CT-MOD-02 | Georreferenciamento em memória (RF-059) | Integração | `TesteGeorreferenciamento` | 40/40. | parcial (14/11): 38/40; o teste edita o talhão para "Milho" sem a cultura no catálogo |
| CT-MOD-03 | Colheitas em memória (RF-018, RF-058) | Integração | `TesteColheitas` | 44/44. | parcial (14/11): 41/44, falhas na proteção da safra com colheita |
| CT-GW-01 | Gateway opcional | Integração | `sh backend/gateway/testar.sh` (`TesteGateway`) | `/health` responde sem o core; `/ready` reflete o core; `/api/**` é repassado preservando query e erro de negócio; corpo acima do limite é recusado; o gateway continua de pé após erro inesperado. | aprovado (CI, PR #303) |

### 6.8 Validação manual

Prioridade média. Casos sem automação ou que dependem de serviço real. A
execução de cada um deve ser registrada em
[`evidencias/testes/`](evidencias/testes/) (ver a [§7.1](#71-regras-gerais)).

| ID | Cenário | Tipo | Roteiro | Resultado esperado | Status inicial |
| --- | --- | --- | --- | --- | --- |
| CT-MAN-01 | Envio real de e-mail (RF-002, RF-005) | Manual, depende de SMTP | `sh backend/api/testar-smtp.sh [caminho/.env] destino@email.com` | A mensagem chega ao destino informado. | não executado |
| CT-MAN-02 | Ciclo de dados no Atlas | Manual, depende do Atlas | [Teste prático do banco](teste-pratico-banco.md): `./backend/core/testar.sh --demonstrar --pausar` | A saída mostra os valores do roteiro e os registros aparecem no Atlas durante a pausa. | sem registro de execução |
| CT-MAN-03 | Revisão visual das telas em desktop (1440×1000) e celular (390×844) | Manual | [Revisão de todas as telas](revisao-todas-telas.md) | Sem overflow horizontal, sem placeholder permanente, com menu móvel e indicadores legíveis. | revisão anterior registrada, sem data; repetir na entrega |
| CT-MAN-04 | Sugestão de períodos de safra (RF-010) | Manual | Com sessão ativa, abrir `configuracoes.html` e consultar `GET /api/propriedades/periodos-safra` | A resposta traz `padrao`, `sugeridos`, `emUso` e `opcoes`, com `padrao` conforme RN-009, e a tela oferece essas opções. | não executado (sem teste automatizado) |
| CT-MAN-05 | Cards do painel por mouse e teclado (RF-042) | Manual | Em `dashboard.html`, usar clique, Enter e Espaço em cada card | Cada card abre Áreas de Cultivo, Atividades, Insumos ou Custos. | não executado (sem teste automatizado) |
| CT-MAN-06 | Tour de boas-vindas (RF-006) | Manual | Criar conta nova, concluir o tour, sair e entrar de novo | O tour aparece no Painel da conta nova e não reaparece no login seguinte. | não executado (só há teste de API) |
| CT-MAN-07 | Telas liberadas para colaborador | Manual | Em Equipe, cadastrar um integrante com parte das telas, aceitar o convite com outra conta e entrar com ela | A navegação mostra só as telas liberadas. Registrar também o resultado de chamar direto na API a rota de uma tela não liberada (ver a [§8.2](#82-limitações)). | não executado |
| CT-MAN-08 | Relatórios da propriedade, do estoque e das atividades (RF-053, RF-055, RF-056) | Manual | Gerar cada relatório para um período com dados conhecidos | Os números batem com os registros do período, e o CSV traz o mesmo resultado da tela. No estoque, ajustes não contam como entrada nem saída, e unidades diferentes não são somadas. Nenhuma célula do CSV começa como fórmula (RNF-008). | não executado (sem suíte Java) |

---

## 7. Critérios de aprovação

### 7.1 Regras gerais

1. Um caso só é **aprovado** quando foi executado e o resultado esperado foi
   observado, com evidência em [`evidencias/testes/`](evidencias/testes/). Sem
   execução, o status é *pendente* ou *não executado*, nunca *aprovado*.
2. A evidência registra o commit, o sistema operacional, as versões de Java,
   Node e Playwright, o código de saída e a última linha de cada suíte. Em caso
   manual, registra o roteiro seguido e o que foi observado.
3. Falha é descrita como foi encontrada. A correção vem em outro pull request,
   e o caso volta a ser executado depois dela.
4. Uma execução feita com cópia modificada dos testes, como a variante
   diagnóstica do relatório, serve só para diagnóstico e não substitui a
   execução oficial.

### 7.2 Por suíte

| Suíte | Aprovada quando |
| --- | --- |
| JUnit do socket | `testar.sh` termina com código 0, sem falhas nem testes ignorados. No CI, o `mvn verify` também passa no limite do JaCoCo (90% de linhas e 85% de ramos em `algoritmos`). |
| `Teste*` da API | Código de saída 0 e última linha sem falha (por exemplo, `41/41 verificações OK`). No `testar.sh`, a cadeia completa chega à última suíte. |
| Variantes `--mongo` | As mesmas condições, contra um banco `_teste` em replica set. |
| `TesteGateway` e core | Código de saída 0. |
| `tests/*.cjs` | Código de saída 0. As suítes que imprimem resumo (`OK ...`) não relatam falha. |

### 7.3 Por pull request

- Os workflows acionados pelo PR terminam verdes em todos os passos, inclusive
  os de navegador e de MongoDB.
- Se o PR muda requisito, rota, handler ou teste, a
  [matriz de rastreabilidade](13-rastreabilidade.md#51-quando-atualizar) é
  atualizada no mesmo PR.
- Se o PR registra uma nova execução, o [relatório](10-relatorio-de-testes.md)
  e a evidência são atualizados juntos.

### 7.4 Para a entrega final

- Todos os casos das seções 6.1 a 6.7 com status *aprovado*, ou com a falha
  registrada e justificada no relatório.
- Os defeitos de teste que hoje bloqueiam a verificação corrigidos
  ([relatório §5](10-relatorio-de-testes.md#5-bugs-e-correções), itens 1 a 4).
- Os casos com MongoDB (CT-ISO-06, CT-PER-01 a CT-PER-05, CT-EST-03, CT-EST-05,
  CT-EST-07 e CT-OCO-07) executados ao menos uma vez em máquina com Docker ou
  pelo CI.
- Os casos manuais CT-MAN-03 a CT-MAN-08 executados e registrados.

### 7.5 Suspensão e retomada

Uma rodada é suspensa, sem registrar resultado, quando a compilação falha, o
socket não sobe na porta esperada ou o banco não é um `_teste` em replica set.
Ela recomeça do início depois de corrigido o ambiente.

---

## 8. Responsáveis e limitações

### 8.1 Responsáveis

Papéis no fluxo de pull request:

| Papel | Responsabilidade |
| --- | --- |
| Autor do pull request | Rodar localmente as suítes do módulo alterado, manter verdes os workflows acionados e atualizar a matriz quando cabível. |
| Revisor do pull request | Conferir os workflows e as linhas afetadas da matriz, conforme a [rastreabilidade §5.2](13-rastreabilidade.md#52-como-atualizar). |
| Quem registra a execução | Rodar a rodada completa, gravar a evidência e atualizar o [relatório](10-relatorio-de-testes.md) e a coluna Situação da matriz. |

Autoria registrada no cabeçalho dos arquivos de teste, como referência para
dúvidas sobre cada suíte:

| Integrante | Autor ou autor principal | Autor secundário |
| --- | --- | --- |
| Miguel Fernandes Costacurta | `TesteMongo`, `TesteRelatorioSafra`; `atividades-real`, `atividades-reinicio`, `local-persistence`, `local-screens`, `ui-polish`, `relatorio-safra-real`, `mock-api`, `browser-launch`; core `DadosCultivoTest`, `CicloDadosAtlasTest`. Também aparece no cabeçalho de `TesteServidor`. | — |
| Pedro André do Carmo Chavier | `TesteGeorreferenciamento`, `TesteManutencao`; `AtendenteGeometriaTest`, `AtendenteManutencaoTest`; `georreferenciamento-real`, `manutencao-real` | — |
| Pedro Henrique Contardi Soler | `TesteServidor`, `TesteOcorrencias`, `TestePerfil`, `TesteEstoqueMongo`, `CenarioEstoqueTeste`; `estoque-real`, `ocorrencias-real`, `perfil-real` | `TesteMongo`, `TesteGateway`; `login-real`, `notificacoes-real`, `local-persistence`, `local-screens`, `ui-polish`, `mock-api` |
| Tomás de Paula Michelon Toniato | `TesteOrcamentos`, `TesteColheitas`; `calendario-real`, `colheita-real`, `custos-real`, `orcamento-real`, `identidade-visual-real` | — |
| Vinicius Panutti Salgado | `token-confirmacao`; core `CoreApplicationTests`, `AcessoPropriedadeTest`, `MongoAtlasValidacaoTest` | — |

Os demais arquivos (a maioria das classes JUnit do socket, `storage-scope`,
`talhoes-real`, `empty-state-origin`, `cultivo-plantio-audit`,
`todas-telas-audit` e parte das classes do core) não têm autor no cabeçalho.

### 8.2 Limitações

**Defeitos dos testes que limitam o que se pode afirmar hoje** (detalhes no
[relatório §5](10-relatorio-de-testes.md#5-bugs-e-correções)):

- O regex `token:` bloqueia 14 suítes de navegador logo no cadastro.
- Nenhuma suíte de navegador cadastra cultura antes de criar o talhão.
- `mock-api.cjs` não simula `/api/culturas` nem `/api/propriedades/periodos-safra`.
- `PeriodoSafra.valida` aceita `1999/00`, e 4 testes JUnit esperam que ele seja recusado.
- `backend/api/testar.sh` encadeia as suítes com `&&`. A primeira falha esconde
  as seguintes e, no CI, interrompe o job antes dos passos de navegador e
  MongoDB. Na rodada do PR #303, só "Identidade visual" e "Testes do gateway"
  passaram.

**Ambiente:**

- As execuções registradas não tinham Docker nem MongoDB. Nenhum caso
  *pendente* da §6 tem resultado.
- O CI fixa Playwright 1.49.1; a execução local de 12/11 usou 1.62.1.
- Só os workflows de georreferenciamento e manutenção sobem o socket na
  porta 3000 antes do navegador (e `login-real.cjs` sobe o seu). Os demais
  workflows com passo de navegador não o sobem. Como o passo Java falha antes,
  o efeito disso ainda não foi observado no CI.
- `relatorio-safra-real.cjs` e `talhoes-real.cjs`, as duas auditorias,
  `storage-scope.cjs` e `TesteMongo` não estão em nenhum workflow.

**Windows:**

- `backend/serversocket/testar.sh` no Git Bash falha ao compilar os testes,
  porque o classpath usa `:`. Há `testar.bat` para o socket.
- `backend/api/testar.bat` não compila na base atual: ele não inclui os fontes
  de `backend/serversocket/src/protocolo`, que a API importa. Isso foi conferido
  em 15/11/2026 compilando a mesma lista de arquivos em uma pasta temporária.
  O script também não sobe o socket na 30100.
- `cultivo-plantio-audit`, `estoque-real`, `perfil-real`, `talhoes-real` e
  `todas-telas-audit` fixam o classpath `bin:lib/*`, que só funciona em Linux e
  macOS.
- `TesteServidor.testarResolucaoMongoBanco` dependeu de DNS e interrompeu a
  suíte na máquina Windows usada em 14/11.

**Cobertura:**

- Não há medição de cobertura da API nem do frontend; só o pacote `algoritmos`
  do socket tem limite.
- A expiração da sessão em 12 horas não é verificada.
- O cabeçalho `X-Content-Type-Options: nosniff` não é verificado por nenhum
  teste. A [rastreabilidade §3.14](13-rastreabilidade.md#314-requisitos-não-funcionais-com-verificação-automatizada)
  atribui RNF-007 a `TesteServidor.testarHttp`, mas esse grupo confere só o
  CORS.
- As telas liberadas a cada colaborador são aplicadas na navegação do
  frontend. Nenhum teste verifica se a API recusa a rota de uma tela não
  liberada, e por isso o caso CT-MAN-07 registra esse comportamento.
- A gestão de equipe tem teste (`TesteServidor.testarEquipe`), mas ainda não
  tem requisito em 02 ([rastreabilidade §4.3](13-rastreabilidade.md#43-implementado-sem-requisito-correspondente)).
- Os relatórios da propriedade, do estoque e das atividades não têm suíte Java.
  A única verificação automatizada é `relatorio-safra-real.cjs`, que depende do
  módulo `mongodb`.
- Os testes do core usam o modelo das issues #100 a #105, anterior aos
  repositórios da API, e não substituem os testes da API com MongoDB.
