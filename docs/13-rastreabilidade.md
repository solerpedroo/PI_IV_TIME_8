# Rastreabilidade do MVP — AgroGestão

| Campo | Valor |
| --- | --- |
| Documento | Matriz de rastreabilidade entre requisitos, implementação e testes |
| Projeto | [PI_IV_TIME_8 · Project nº 3](https://github.com/users/solerpedroo/projects/3) — Sprint 3, issue #278 |
| Versão | 1.0 — 14/11/2026 |
| Base de código conferida | `origin/dev` em `99ac03e` |
| Documentos de origem | [Requisitos](02-requisitos.md), [Casos de uso](03-casos-de-uso.md), [Contrato da API](06-contrato-api.md), [Relatório de testes](10-relatorio-de-testes.md) |
| Evidências de execução | [`execucao-2026-11-14-resumo.txt`](evidencias/testes/execucao-2026-11-14-resumo.txt) e [`execucao-2026-11-12-resumo.txt`](evidencias/testes/execucao-2026-11-12-resumo.txt) |

## Sumário

1. [Objetivo](#1-objetivo)
2. [Convenções usadas](#2-convenções-usadas)
3. [Matriz de rastreabilidade](#3-matriz-de-rastreabilidade)
4. [Lacunas e limitações](#4-lacunas-e-limitações)
5. [Processo de atualização](#5-processo-de-atualização)

---

## 1. Objetivo

Este documento liga cada requisito do MVP ao que o entrega e ao que o verifica.
Para cada requisito funcional (RF-001 a RF-060) e para os requisitos não
funcionais que têm verificação automatizada, a matriz informa:

- a **funcionalidade**: tela e rota HTTP onde o requisito aparece;
- a **evidência de implementação**: handler, serviço, algoritmo do ServerSocket
  ou script de tela que contém o comportamento;
- o **teste**: suíte automatizada e, quando existe, o grupo de verificação;
- a **situação** desse teste na última execução registrada.

A matriz serve para três usos: conferir a entrega (grupo e professor), achar
requisitos sem cobertura e medir o impacto de uma mudança antes de fazê-la.

O documento **não redefine requisitos**. O texto completo e o critério de aceite
de cada item continuam em [02-requisitos.md](02-requisitos.md); a
[§8.2](02-requisitos.md#82-matriz-de-rastreabilidade) daquele documento traz a
visão por módulo, e esta matriz desce ao nível de cada requisito. Os resultados
de teste vêm de execuções reais, registradas em
[10-relatorio-de-testes.md](10-relatorio-de-testes.md) e no arquivo de evidência
de 14/11/2026.

---

## 2. Convenções usadas

### 2.1 Colunas da matriz

| Coluna | Conteúdo |
| --- | --- |
| Requisito | Identificador estável de [02-requisitos.md](02-requisitos.md) e um resumo curto. |
| Funcionalidade | Tela em `frontend/` e rota HTTP que entregam o requisito. |
| Implementação | Classe e método para Java (ex.: `AuthHandler.cadastrar`) e nome do arquivo para scripts de tela. A marca "socket" indica algoritmo executado no ServerSocket (porta 3000), chamado pela API via `ClienteDeAlgoritmos`. Quando um método atende várias operações, a operação aparece entre aspas (ex.: `PerfilHandler.gravar` "senha"). |
| Teste | Suíte e grupo. Para `TesteServidor`, o grupo é o método `testar*` (ex.: `TesteServidor.testarCadastro`). As demais suítes da API e as classes JUnit são citadas pelo nome; as de navegador, pelo arquivo em `tests/`. |
| Situação | Resultado das suítes citadas, separado em **Java** (API em memória e JUnit do socket) e **E2E** (navegador, Playwright). |

Cada módulo abre com o caso de uso de [03-casos-de-uso.md](03-casos-de-uso.md),
a seção do [contrato da API](06-contrato-api.md) e o workflow de CI que executa
as suítes do módulo.

### 2.2 Onde ficam os artefatos citados

| Tipo | Pasta | Exemplos |
| --- | --- | --- |
| Handler HTTP (rotas) | [`backend/api/src/handlers/`](../backend/api/src/handlers/) | `AuthHandler`, `SafrasHandler` |
| Serviço, utilitário e segurança da API | [`servicos/`](../backend/api/src/servicos/), [`util/`](../backend/api/src/util/), [`seguranca/`](../backend/api/src/seguranca/), [`http/`](../backend/api/src/http/) | `CustosDeConsumo`, `FotoEvidencia`, `Sessoes`, `Roteador` |
| Repositórios (memória e MongoDB) | [`backend/api/src/dados/`](../backend/api/src/dados/) | `RepositorioAtividadesMongo` |
| Algoritmo do ServerSocket | [`backend/serversocket/src/algoritmos/`](../backend/serversocket/src/algoritmos/) | `ValidadorDeConta`, `CalculadoraDeEstoque` |
| Tela e script de tela | [`frontend/`](../frontend/) e [`frontend/scripts/`](../frontend/scripts/) | `dashboard.html`, `dashboard.js` |
| Suíte Java da API | [`backend/api/teste/`](../backend/api/teste/) | `TesteServidor`, `TesteOcorrencias` |
| Suíte JUnit do socket | [`backend/serversocket/src/test/java/`](../backend/serversocket/src/test/java/) | `ValidadorDeContaTest` |
| Suíte de navegador | [`tests/`](../tests/) | `login-real.cjs` |
| Workflow de CI | [`.github/workflows/`](../.github/workflows/) | `perfil-testes.yml` |

Todos os nomes citados na matriz existem na base `99ac03e`; a
[§5.3](#53-conferência-automática-das-referências) traz o comando que confere
arquivos, classes, métodos e links.

### 2.3 Situação dos testes

| Situação | Significado |
| --- | --- |
| **aprovada** | A suíte rodou sem falhas. |
| **parcial** | A suíte rodou com falhas. A linha diz se a falha atinge o requisito. |
| **bloqueada** | A suíte de navegador para antes de exercitar o requisito por defeito do próprio teste ([§4.2](#42-testes-que-hoje-não-comprovam-o-requisito)). Não indica que o produto está correto nem quebrado. |
| **falha** | A suíte de navegador roda e reprova, sem relação direta com o requisito da linha. |
| **pendente** | Depende de MongoDB, Docker ou do módulo Node `mongodb` e não foi executada. |
| **sem teste** | Nenhum teste automatizado exercita o requisito; só há evidência de implementação. |

Fontes da situação:

| Grupo de suítes | Execução considerada | Resultado |
| --- | --- | --- |
| JUnit do socket | 14/11/2026, `99ac03e` | 276 de 280; as 4 falhas usam o período `1999/00` (RN-009). |
| `TesteServidor` | 14/11/2026, `99ac03e` | 439/439, sem o grupo `testarResolucaoMongoBanco`, que depende de DNS e falhou nesta máquina (ver evidência). Em 12/11, no Linux, a suíte inteira passou (420/420, antes dos testes de equipe). |
| `TesteOrcamentos`, `TestePerfil`, `TesteOcorrencias`, `TesteManutencao`, `TesteRelatorioSafra` | 14/11/2026, `99ac03e` | Aprovadas: 41/41, 56, 69, 61/61 e 24 verificações. |
| `TesteGeorreferenciamento` | 14/11/2026, `99ac03e` | 38/40 — as mesmas 2 falhas de 12/11. |
| `TesteColheitas` | 14/11/2026, `99ac03e` | 41/44 — as mesmas 3 falhas de 12/11. |
| `TesteMongo`, `TesteEstoqueMongo` e variantes `--mongo` | — | Pendentes (sem MongoDB). |
| Navegador (`tests/*.cjs`) | 12/11/2026, `60d7dda` ([relatório §4.3](10-relatorio-de-testes.md#43-navegador-testscjs-primeira-execução)) | Não reexecutadas. Entre `60d7dda` e `99ac03e`, `tests/` só recebeu `identidade-visual-real.cjs`, e as causas de bloqueio continuam no código ([§4.2](#42-testes-que-hoje-não-comprovam-o-requisito)). |

---

## 3. Matriz de rastreabilidade

### 3.1 Autenticação e sessão

Caso de uso: UC01, UC02, UC03 e UC14 (tour) · Contrato: [§5.1](06-contrato-api.md#51-autenticação-e-sessão) · CI: `auth-formularios-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-001 Criar conta | `criar-conta.html` · `POST /api/auth/register` | `AuthHandler.cadastrar`; socket `ValidadorDeConta`; `Senhas`; `auth.js`, `senha-regras.js` | `TesteServidor.testarCadastro`, `TesteServidor.testarConcorrencia`; `ValidadorDeContaTest`; `login-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-002 Confirmar e-mail e reenviar | `confirmar-email.html` · `POST /api/auth/confirm-email`, `POST /api/auth/resend-confirmation` | `AuthHandler.confirmarEmail`, `AuthHandler.reenviarConfirmacao`; `CodigosVerificacao`; `ServicoEmail` | `TesteServidor.testarConfirmacaoERedefinicao`; `login-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-003 Entrar e manter sessão Bearer | `login.html` · `POST /api/auth/login`, `GET /api/me` | `AuthHandler.entrar`, `AuthHandler.eu`; `Sessoes`; `auth.js` | `TesteServidor.testarLogin`, `TesteServidor.testarProtecoesDeAbuso`; `login-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-004 Encerrar sessão | menu da barra lateral · `POST /api/auth/logout` | `AuthHandler.sair`; `shell.js` | `TesteServidor.testarConfirmacaoERedefinicao`, `TesteServidor.testarPropriedades`; `TestePerfil`; `perfil-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-005 Recuperar e redefinir senha | `recuperar-senha.html`, `redefinir-senha.html` · `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` | `AuthHandler.esqueciSenha`, `AuthHandler.redefinirSenha`; socket `ValidadorDeConta.validarSenha` | `TesteServidor.testarConfirmacaoERedefinicao`; `TestePerfil`; `login-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-006 Tour de boas-vindas | `dashboard.html` · `POST /api/perfil/onboarding` | `PerfilHandler.gravar` "onboarding"; `onboarding.js` | `TesteServidor.testarCadastro`, `TesteServidor.testarConfirmacaoERedefinicao`; `TestePerfil` | Java: aprovada · E2E: sem teste |

### 3.2 Propriedades

Caso de uso: UC04 · Contrato: [§5.3](06-contrato-api.md#53-propriedades-e-culturas) · CI: nenhum workflow dedicado (as suítes Java rodam nos workflows dos demais módulos)

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-007 Cadastrar, listar e editar propriedades | `configuracoes.html` · `GET`/`POST /api/propriedades`, `GET`/`PUT /api/propriedades/{id}` | `PropriedadesHandler.criar`, `PropriedadesHandler.listar`, `PropriedadesHandler.consultar`, `PropriedadesHandler.atualizar`; `propriedades-api.js`, `configuracoes.js` | `TesteServidor.testarPropriedades` | Java: aprovada |
| RF-008 Selecionar a propriedade ativa | seletor da barra lateral · `POST /api/propriedades/{id}/selecionar`, `GET /api/me` | `PropriedadesHandler.selecionar`; `Sessao`; `propriedades-contexto.js`, `agro-local.js` | `TesteServidor.testarPropriedades`, `TesteServidor.testarCustos`; `storage-scope.cjs` | Java: aprovada · E2E: aprovada |
| RF-009 Excluir propriedade sem dependentes | `configuracoes.html` · `DELETE /api/propriedades/{id}` | `PropriedadesHandler.excluir` | `TesteServidor.testarPropriedades`, `TesteServidor.testarCustos`; `TesteOcorrencias`; `TesteMongo` | Java: aprovada · MongoDB: pendente |
| RF-010 Sugerir períodos de safra | `configuracoes.html` · `GET /api/propriedades/periodos-safra` | `PropriedadesHandler.periodosSafra`; `periodos-safra.js` | — | sem teste |

### 3.3 Talhões e catálogo de culturas

Caso de uso: UC05 · Contrato: [§5.3](06-contrato-api.md#53-propriedades-e-culturas) e [§5.4](06-contrato-api.md#54-talhões-e-georreferenciamento) · CI: nenhum workflow executa `talhoes-real.cjs` nem `cultivo-plantio-audit.cjs`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-011 Catálogo de culturas | `culturas.html` · `GET`/`POST /api/culturas`, `DELETE /api/culturas/{id}` | `CulturasHandler.listar`, `CulturasHandler.criar`, `CulturasHandler.excluir`; `culturas.js`, `culturas-catalog.js` | `TesteServidor.testarTalhoes` (catálogo vazio, cadastro e cultura fora do catálogo) | Java: aprovada · cobertura incompleta ([§4.1](#41-requisitos-sem-teste-ou-com-cobertura-incompleta)) |
| RF-012 Cadastrar e editar talhões | `areas-cultivo.html` · `POST /api/talhoes`, `PUT /api/talhoes/{id}` | `TalhoesHandler.criar`, `TalhoesHandler.atualizar`; socket `ValidadorDeTalhao`; `areas-cultivo.js`, `talhoes-api.js` | `TesteServidor.testarTalhoes`, `TesteServidor.testarConcorrencia`; `ValidadorDeTalhaoTest`; `talhoes-real.cjs` | Java: aprovada (API), parcial (socket: 2 casos de `1999/00`) · E2E: bloqueada |
| RF-013 Filtrar talhões e resumir área | `areas-cultivo.html` · `GET /api/talhoes?cultura=&q=`, `GET /api/talhoes/resumo` | `TalhoesHandler.listar`, `TalhoesHandler.resumo`; socket `CalculadoraDeTalhoes` | `TesteServidor.testarTalhoes`; `CalculadoraDeTalhoesTest` | Java: aprovada |
| RF-014 Histórico do talhão | `areas-cultivo.html` · `GET`/`POST /api/talhoes/{id}/historico` | `TalhoesHandler.historico`, `TalhoesHandler.registrarHistorico` | `TesteServidor.testarTalhoes`; `talhoes-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-015 Navegar já filtrado pelo talhão | menu "⋮" do talhão → `atividades.html?talhao=`, `ocorrencias.html?talhao=`, `custos.html?talhao=` | `areas-cultivo.js` (menu); filtros em `atividades.js`, `ocorrencias.js`, `custos.js` | `ocorrencias-real.cjs`; `ui-polish.cjs` (atividades, API simulada) | E2E: bloqueada (`ocorrencias-real.cjs`), falha (`ui-polish.cjs`); custos sem teste |

### 3.4 Safras

Caso de uso: UC06 · Contrato: [§5.5](06-contrato-api.md#55-safras-e-colheitas) · CI: nenhum workflow dedicado

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-016 Cadastrar safra | `safras.html` · `POST /api/safras` | `SafrasHandler.criar` (`SafrasHandler.conflitoDeTalhao`, `SafrasHandler.area`); `safras.js` | `TesteServidor.testarSafras`; `cultivo-plantio-audit.cjs` | Java: aprovada · E2E: bloqueada |
| RF-017 Avançar etapas | `safras.html` · `POST /api/safras/{id}/avancar` | `SafrasHandler.avancar` | `TesteServidor.testarSafras` | Java: aprovada |
| RF-018 Listar, editar e excluir respeitando vínculos | `safras.html` · `GET /api/safras`, `PUT`/`DELETE /api/safras/{id}` | `SafrasHandler.listar`, `SafrasHandler.atualizar`, `SafrasHandler.excluir` (`SafrasHandler.talhaoComCusto`, `SafrasHandler.conflitoComColheita`) | `TesteServidor.testarSafras`; `TesteColheitas` | Java: aprovada (`TesteServidor`), **parcial** (`TesteColheitas`: as 3 falhas são a proteção de colheita na edição, RN-016) |
| RF-019 Histórico do ciclo | `safras.html` · `GET /api/safras/{id}/historico` | `SafrasHandler.historico` | `TesteServidor.testarSafras` | Java: aprovada |

### 3.5 Atividades e calendário

Caso de uso: UC07 · Contrato: [§5.6](06-contrato-api.md#56-atividades-e-calendário) · CI: `atividades-testes.yml`, `calendario-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-020 Planejar atividade | `atividades.html` · `POST /api/atividades` | `AtividadesHandler.criar`; `atividades.js`, `date-picker.js`, `time-picker.js` | `TesteServidor.testarAtividadesEHistorico`; `atividades-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-021 Editar planejamento | `atividades.html` · `PUT /api/atividades/{id}` | `AtividadesHandler.atualizar` | `TesteServidor.testarAtividadesEHistorico`; `atividades-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-022 Transições de status (menu, lista e arraste) | `atividades.html` · `POST /api/atividades/{id}/status`, `POST /api/atividades/{id}/concluir` | `AtividadesHandler.alterarStatus`, `AtividadesHandler.concluir`; `atividades.js` | `TesteServidor.testarAtividadesEHistorico`; `atividades-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-023 Cancelar com motivo | `atividades.html` · `POST /api/atividades/{id}/cancelar` | `AtividadesHandler.cancelar` | `TesteServidor.testarAtividadesEHistorico`; `atividades-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-024 Histórico, inclusive após reinício | `atividades.html` · `GET /api/atividades/{id}/historico` | `AtividadesHandler.historico`; `RepositorioAtividadesMongo` | `TesteServidor.testarAtividadesEHistorico`; `atividades-reinicio.cjs` | Java: aprovada · reinício com MongoDB: pendente |
| RF-025 Kanban e lista com filtros | `atividades.html` · `GET /api/atividades` | `AtividadesHandler.listar`; `atividades.js` | `atividades-real.cjs`; `ui-polish.cjs` | E2E: bloqueada (`atividades-real.cjs`), falha (`ui-polish.cjs`) |
| RF-026 Calendário semanal e mensal | `atividades.html` · `GET /api/calendario?de=&ate=` | `CalendarioHandler`; `atividades.js` | `TesteServidor.testarCalendario`; `calendario-real.cjs` | Java: aprovada · E2E: bloqueada |

### 3.6 Estoque (Insumos)

Caso de uso: UC08 · Contrato: [§5.7](06-contrato-api.md#57-estoque-insumos) · CI: `estoque-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-027 Cadastrar, editar e arquivar insumos | `insumos.html` · `GET`/`POST /api/insumos`, `GET`/`PUT`/`DELETE /api/insumos/{id}` | `EstoqueHandler.listar`, `EstoqueHandler.consultar`, `EstoqueHandler.gravar` "criar", "editar" e "excluir"; `insumos.js` | `TesteServidor.testarEstoque`; `estoque-real.cjs` | Java: aprovada · E2E: pendente (exige MongoDB de teste) |
| RF-028 Entradas, saídas e ajustes | `insumos.html` · `POST /api/insumos/{id}/movimentacoes` | `EstoqueHandler.gravar` "movimentar"; socket `CalculadoraDeEstoque`; `CalculosEstoque` | `TesteServidor.testarEstoque`; `CalculadoraDeEstoqueTest`; `TesteEstoqueMongo`; `estoque-real.cjs` | Java: aprovada · MongoDB e E2E: pendentes |
| RF-029 Saída vinculada a atividade | `insumos.html` · `GET /api/insumos/atividades`; saída com `atividadeId` | `EstoqueHandler.atividades`, `EstoqueHandler.gravar` "movimentar" | `TesteServidor.testarEstoque`, `TesteServidor.testarCustosDeConsumo`; `estoque-real.cjs` | Java: aprovada · E2E: pendente |
| RF-030 Histórico imutável | `insumos.html` · `GET /api/insumos/{id}/movimentacoes` | `EstoqueHandler.historico`; `RepositorioEstoqueMongo` | `TesteServidor.testarEstoque`; `TesteEstoqueMongo` | Java: aprovada · MongoDB: pendente |
| RF-031 Indicadores e situação do item | `insumos.html` · `GET /api/insumos/resumo` | `EstoqueHandler.resumo`; socket `CalculadoraDeEstoque` | `TesteServidor.testarEstoque`; `CalculadoraDeEstoqueTest`; `estoque-real.cjs` | Java: aprovada · E2E: pendente |

### 3.7 Ocorrências e fotos de evidência

Caso de uso: UC09 · Contrato: [§5.8](06-contrato-api.md#58-ocorrências) · CI: `ocorrencias-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-032 Registrar ocorrência | `ocorrencias.html` · `POST /api/ocorrencias` | `OcorrenciasHandler.mutar` "criar"; `ocorrencias.js` | `TesteOcorrencias`; `ocorrencias-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-033 Acompanhar e resolver pela timeline | `ocorrencias.html` · `GET`/`POST /api/ocorrencias/{id}/timeline` | `OcorrenciasHandler.mutar` "evento", `OcorrenciasHandler.timeline` | `TesteOcorrencias`; `ocorrencias-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-034 Versão e exclusão lógica | `ocorrencias.html` · `PUT`/`DELETE /api/ocorrencias/{id}`, `GET /api/ocorrencias?incluirExcluidas=true` | `OcorrenciasHandler.mutar` "editar" e "excluir", `OcorrenciasHandler.listar` | `TesteOcorrencias` | Java: aprovada |
| RF-035 Resumo mensal | `ocorrencias.html` · `GET /api/ocorrencias/resumo?mes=` | `OcorrenciasHandler.resumo` | `TesteOcorrencias`; `ocorrencias-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-036 Foto de evidência | `ocorrencias.html`, `atividades.html` · `GET`/`POST`/`DELETE /api/ocorrencias/{id}/foto` e `/api/atividades/{id}/foto` | `OcorrenciasHandler.enviarFoto`, `AtividadesHandler.enviarFoto` (e `obterFoto`, `removerFoto`); `FotoEvidencia` | `TesteOcorrencias`; `TesteServidor.testarAtividadesEHistorico`, `TesteServidor.testarHttp` (limite de corpo); `atividades-reinicio.cjs` | Java: aprovada · reinício com MongoDB: pendente |

### 3.8 Custos

Caso de uso: UC10 · Contrato: [§5.9](06-contrato-api.md#59-custos-e-orçamento) · CI: `custos-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-037 Lançar, editar e excluir custos | `custos.html` · `GET`/`POST /api/custos`, `GET`/`PUT`/`DELETE /api/custos/{id}` | `CustosHandler.criar`, `CustosHandler.atualizar`, `CustosHandler.excluir`; `custos.js` | `TesteServidor.testarCustos`; `custos-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-038 Filtros e exportação CSV | `custos.html` · `GET /api/custos?categoria=&talhaoId=&safraId=&de=&ate=` | `CustosHandler.consultarLista`, `CustosHandler.filtro`; exportação em `custos.js` | `TesteServidor.testarCustos` (filtros e período invertido); `custos-real.cjs` (CSV) | Java: aprovada · E2E: bloqueada (o CSV só é verificado no navegador) |
| RF-039 Indicadores de custo | `custos.html` · `GET /api/custos/resumo` | `CustosHandler.resumo` | `TesteServidor.testarCustos` (conjunto conhecido de R$ 20.190,56 sobre 296,5 ha); `TesteOrcamentos`; `custos-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-040 Custo automático do consumo | saída vinculada em `insumos.html` · `POST /api/custos/sincronizar-consumo` | `CustosDeConsumo.registrar`, `CustosDeConsumo.sincronizar`; `CustosHandler.sincronizarConsumo` | `TesteServidor.testarCustosDeConsumo`; `custos-real.cjs` | Java: aprovada · E2E: bloqueada |

### 3.9 Painel (Dashboard)

Caso de uso: UC14 · Contrato: rotas dos módulos consultados · CI: nenhum workflow dedicado (o painel é exercitado por `custos-testes.yml`, `ocorrencias-testes.yml`, `local-persistence.yml` e `ui-polish.yml`)

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-041 Indicadores da propriedade ativa | `dashboard.html` · `GET /api/talhoes`, `/api/atividades`, `/api/insumos`, `/api/custos/resumo` | `dashboard.js` (refresh, loadCosts, setMetric) | `empty-state-origin.cjs`; `local-screens.cjs`; `talhoes-real.cjs` (áreas); `custos-real.cjs` (custos) | E2E: aprovada (`empty-state-origin.cjs` e cenários do painel em `local-screens.cjs`), bloqueada (`*-real.cjs`) |
| RF-042 Levar do indicador ao módulo, por mouse e teclado | cards de `dashboard.html` | `dashboard.js` (initMetricNavigation: clique, Enter e Espaço) | — | sem teste |
| RF-043 Alertas de ocorrências ativas | `dashboard.html` → `ocorrencias.html?ocorrenciaId=` · `GET /api/ocorrencias` | `dashboard.js` (loadOccurrences, renderAlerts) | `ocorrencias-real.cjs` | E2E: bloqueada |
| RF-044 Atividades e área por cultura, com registro e conclusão rápida | `dashboard.html` · `POST /api/atividades`, `POST /api/atividades/{id}/status` | `dashboard.js` (renderCultures e ações da lista) | `local-screens.cjs` ("dashboard: criar, concluir e compartilhar atividade", API simulada); `talhoes-real.cjs` | E2E: aprovada (`local-screens.cjs`), bloqueada (`talhoes-real.cjs`) |

### 3.10 Notificações

Caso de uso: UC14 · Contrato: rotas de insumos, atividades, ocorrências e perfil · CI: `notificacoes-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-045 Alertas a partir de registros reais | sino de `dashboard.html` · `GET /api/insumos`, `/api/atividades`, `/api/ocorrencias` | `notificacoes.js` (carregarFontes, montarAlertas) | `notificacoes-real.cjs` | E2E: bloqueada |
| RF-046 Abrir o registro da notificação | links `insumos.html?insumoId=`, `atividades.html?atividadeId=`, `atividades.html?hoje=1`, `ocorrencias.html?ocorrenciaId=` | `notificacoes.js` (montarAlertas) | `notificacoes-real.cjs` | E2E: bloqueada |
| RF-047 Estado de leitura e preferências | sino · `PUT /api/perfil/preferencias` | `notificacoes.js` (lerLidas, gravarLidas, preferenciasPadrao); `PerfilHandler.gravar` "preferencias" | `TestePerfil` (preferências); `notificacoes-real.cjs` | Java: aprovada · E2E: bloqueada |

### 3.11 Perfil e configurações

Caso de uso: UC12 · Contrato: [§5.2](06-contrato-api.md#52-perfil-e-segurança) · CI: `perfil-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-048 Editar nome e telefone | `configuracoes.html` · `GET`/`PUT /api/perfil` | `PerfilHandler.consultar`, `PerfilHandler.gravar` "perfil"; `perfil-api.js`, `configuracoes.js` | `TestePerfil`; `perfil-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-049 Trocar e-mail com senha e código | `configuracoes.html` · `PUT /api/perfil` (novo e-mail e senha atual), `POST /api/perfil/email/confirmar`, `POST /api/perfil/email/reenviar` | `PerfilHandler.gravar` "perfil", "confirmar" e "reenviar"; `CodigosVerificacao` | `TestePerfil`; `perfil-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-050 Preferências de notificação | `configuracoes.html` · `PUT /api/perfil/preferencias` | `PerfilHandler.gravar` "preferencias"; `PerfilUsuario` | `TestePerfil`; `perfil-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-051 Propriedade padrão | `configuracoes.html` · `PUT /api/perfil/propriedade-padrao` | `PerfilHandler.gravar` "padrao"; `perfil-api.js` | `TestePerfil`; `perfil-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-052 Trocar senha | `configuracoes.html` · `PUT /api/perfil/senha` | `PerfilHandler.gravar` "senha"; socket `ValidadorDeConta.validarSenha` | `TestePerfil`; `perfil-real.cjs` | Java: aprovada · E2E: bloqueada |

### 3.12 Relatórios

Caso de uso: UC11 · Contrato: [§5.11](06-contrato-api.md#511-relatórios) · CI: nenhum workflow executa `relatorio-safra-real.cjs`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-053 Relatório da propriedade | `dashboard.html` · `GET /api/relatorios/propriedade?de=&ate=` | `RelatorioPropriedadeHandler`; `relatorio-propriedade.js`, `relatorio-util.js` | `relatorio-safra-real.cjs` | E2E: pendente · Java: sem teste |
| RF-054 Relatório da safra | `safras.html` · `GET /api/relatorios/safras/{id}` | `RelatorioSafraHandler`; `RelatorioSafra`, `RelatorioSafraAlgoritmos`; socket `ConsolidadorDeSafra`; `relatorio-safra.js` | `TesteRelatorioSafra`; `ConsolidadorDeSafraTest`; `relatorio-safra-real.cjs` | Java: aprovada · E2E: pendente |
| RF-055 Relatório de estoque | `insumos.html` · `GET /api/relatorios/estoque?de=&ate=` | `RelatorioEstoqueHandler`; `relatorio-estoque.js`, `relatorio-operacional.js` | `relatorio-safra-real.cjs` | E2E: pendente · Java: sem teste |
| RF-056 Relatório de atividades | `atividades.html` · `GET /api/relatorios/atividades?de=&ate=` | `RelatorioAtividadesHandler`; `relatorio-atividades.js`, `relatorio-operacional.js` | `relatorio-safra-real.cjs` | E2E: pendente · Java: sem teste |

### 3.13 Extensões operacionais

Casos de uso: UC10 (orçamento), UC06 (colheita), UC05 (localização) e UC13 (manutenção) · Contrato: [§5.4](06-contrato-api.md#54-talhões-e-georreferenciamento), [§5.5](06-contrato-api.md#55-safras-e-colheitas), [§5.9](06-contrato-api.md#59-custos-e-orçamento) e [§5.10](06-contrato-api.md#510-manutenção-de-equipamentos) · CI: `orcamento-testes.yml`, `colheita-testes.yml`, `georreferenciamento-testes.yml`, `manutencao-testes.yml`

| Requisito | Funcionalidade | Implementação | Teste | Situação |
| --- | --- | --- | --- | --- |
| RF-057 Orçamento por safra | `custos.html` · `GET /api/orcamentos`, `GET`/`PUT`/`DELETE /api/orcamentos/{safraId}` | `OrcamentosHandler.salvar`, `OrcamentosHandler.listar`, `OrcamentosHandler.situacao`, `OrcamentosHandler.excluir`; `custos.js` | `TesteOrcamentos`; `orcamento-real.cjs` | Java: aprovada · E2E: bloqueada |
| RF-058 Colheitas e produtividade | `safras.html` · `GET`/`POST /api/colheitas`, `GET /api/colheitas/resumo`, `GET /api/colheitas/comparativo` | `ColheitasHandler.criar`, `ColheitasHandler.resumo`, `ColheitasHandler.comparativo`; `colheitas.js` | `TesteColheitas`; `colheita-real.cjs` | Java: **parcial** (3 falhas na edição da safra com colheita, RN-016) · E2E: bloqueada |
| RF-059 Localização do talhão e mapa | `areas-cultivo.html`, `ocorrencias.html` · `PUT`/`DELETE /api/talhoes/{id}/localizacao`, `GET /api/mapa` | `GeorreferenciamentoHandler.definir`, `GeorreferenciamentoHandler.remover`, `GeorreferenciamentoHandler.mapa`; `AlgoritmosDeGeo`; socket `GeometriaDeTalhao`; `georreferenciamento.js` | `TesteGeorreferenciamento`; `GeometriaDeTalhaoTest`; `AtendenteGeometriaTest`; `georreferenciamento-real.cjs` | Java: **parcial** (2 falhas: o teste edita talhão para "Milho" sem a cultura no catálogo, RN-010) · E2E: bloqueada |
| RF-060 Equipamentos, preventivas e intervenções | `manutencao.html` · `/api/equipamentos`, `/api/preventivas`, `/api/intervencoes`, `GET /api/manutencao/resumo` | `ManutencaoHandler.criarEquipamento`, `ManutencaoHandler.criarPreventiva`, `ManutencaoHandler.criarIntervencao`, `ManutencaoHandler.agenda`; `AlgoritmosDeManutencao`, `CustosDeManutencao`; socket `AgendaDeManutencao`, `ValidadorDeManutencao`; `manutencao.js` | `TesteManutencao`; `AgendaDeManutencaoTest`; `ValidadorDeManutencaoTest`; `AtendenteManutencaoTest`; `manutencao-real.cjs` | Java: aprovada · E2E: bloqueada |

### 3.14 Requisitos não funcionais com verificação automatizada

| Requisito | Implementação | Teste | Situação |
| --- | --- | --- | --- |
| RNF-001 Senha com PBKDF2; hash e tokens fora das respostas | `Senhas` | `TesteServidor.testarCadastro` (prefixo `pbkdf2$`, resposta sem `senhaHash`) | Java: aprovada |
| RNF-002 Bearer obrigatório; até 5 sessões por conta | `Sessoes`, `Roteador` | `TesteServidor.testarLogin`, `TesteServidor.testarProtecoesDeAbuso` | Java: aprovada (a expiração de 12 h não tem verificação dedicada) |
| RNF-003 Não revelar e-mail cadastrado | `AuthHandler.entrar`, `AuthHandler.esqueciSenha` | `TesteServidor.testarLogin`, `TesteServidor.testarConfirmacaoERedefinicao` | Java: aprovada |
| RNF-004 Limite de tentativas (429 com `Retry-After`) | `LimitadorDeTaxa` | `TesteServidor.testarProtecoesDeAbuso`, `TesteServidor.testarConfirmacaoERedefinicao`; `TestePerfil` | Java: aprovada |
| RNF-005 Isolamento entre usuários e propriedades | filtro por usuário e propriedade nos repositórios de `dados/`; `Sessao` | verificações de isolamento em todas as suítes da API; `storage-scope.cjs` | Java: aprovada · E2E: aprovada |
| RNF-006 Estáticos restritos | `EstaticosHandler` | `TesteServidor.testarSaudeEEstaticos` | Java: aprovada |
| RNF-007 CORS restrito e `nosniff` | `Roteador`, `Resposta` | `TesteServidor.testarHttp` | Java: aprovada |
| RNF-008 Texto literal e CSV sem fórmulas | `relatorio-util.js`; renderização em `ocorrencias.js` | `ocorrencias-real.cjs` (descrição literal); `relatorio-safra-real.cjs` (CSV) | E2E: bloqueada e pendente |
| RNF-009 e RNF-010 API como fonte de verdade, sem dados fictícios | `operational-defaults.js`, `agro-local.js` | `empty-state-origin.cjs`, `local-persistence.cjs`, `local-screens.cjs`, `todas-telas-audit.cjs` | E2E: aprovada (`empty-state-origin.cjs`, `local-persistence.cjs`), parcial (`local-screens.cjs`, 5 de 6), bloqueada (`todas-telas-audit.cjs`) |
| RNF-011 e RNF-014 Atomicidade e persistência com MongoDB | repositórios `*Mongo` de `dados/`; `ConexaoMongo` | `TesteMongo`, `TesteEstoqueMongo`, variantes `--mongo`; `atividades-reinicio.cjs` | pendente |
| RNF-012 Unicidade e saldo sob concorrência | repositórios de `dados/` | `TesteServidor.testarConcorrencia`, `TesteServidor.testarEstoque` | Java: aprovada |
| RNF-013 Controle otimista de versão | `OcorrenciasHandler`, `FotoEvidencia` | `TesteOcorrencias` | Java: aprovada |
| RNF-015 Erro isolado; `/health` e `/ready` | `Roteador`, `SupervisoraDeConexao`, `SaudeHandler` | `TesteServidor.testarConcorrencia` (rota que lança exceção responde 500), `TesteServidor.testarSaudeEEstaticos` | Java: aprovada |
| RNF-017 Somas exatas (centavos, gramas) | `CustosHandler.centavos`, `ColheitasHandler.gramas` | `TesteServidor.testarCustos`; `TesteColheitas` | Java: aprovada (`TesteServidor`), parcial (`TesteColheitas`, falhas sem relação com a soma) |
| RNF-018 Fuso de São Paulo | `CustosHandler`, `OcorrenciasHandler`, `CalendarioHandler` | `TesteOcorrencias` (resolução pelo fuso de Brasília); `TesteServidor.testarCalendario` | Java: aprovada |
| RNF-020 e RNF-021 Limites HTTP e atendimento paralelo | `ParceiroHttp`, `AceitadoraDeConexao`, `Servidor` | `TesteServidor.testarHttp`, `TesteServidor.testarLimiteDeConexoes`, `TesteServidor.testarConcorrencia` | Java: aprovada |
| RNF-023 e RNF-024 Layout responsivo e teclado | estilos em `frontend/styles/`; `shell.js` | `ui-polish.cjs`, `todas-telas-audit.cjs` | E2E: falha (`ui-polish.cjs`), bloqueada (`todas-telas-audit.cjs`) |

RNF-016, RNF-019, RNF-022 e RNF-025 a RNF-028 não têm teste dedicado; ver
[§4.1](#41-requisitos-sem-teste-ou-com-cobertura-incompleta).

### 3.15 Fluxos centrais do MVP

Os dois fluxos de sucesso da [Definição do MVP](AgroGestao-Definicao-MVP.docx),
detalhados em [02-requisitos §7.2](02-requisitos.md#72-fluxos-centrais-do-mvp),
com o teste que mais se aproxima de cada um de ponta a ponta:

| Fluxo | Requisitos | Evidência ponta a ponta | Situação |
| --- | --- | --- | --- |
| 1 — Ciclo produtivo (propriedade → talhão → safra → atividade → consumo → estoque → custo → histórico → painel) | RF-001, RF-007, RF-011, RF-012, RF-016, RF-020, RF-022, RF-028, RF-029, RF-031, RF-040, RF-014, RF-019, RF-024, RF-041 | API: a cadeia de `TesteServidor` cria propriedade, talhões e safras (`testarCadastro`, `testarTalhoes`, `testarSafras`), executa atividades (`testarAtividadesEHistorico`) e, em `TesteServidor.testarCustosDeConsumo`, registra a saída vinculada e confere o custo de R$ 125,00 no talhão e na safra. Nesta última etapa a atividade é inserida direto no repositório de teste. Navegador: `custos-real.cjs` faz o consumo pela tela de Insumos e confere o painel. | Java: aprovada · E2E: bloqueada |
| 2 — Gestão de ocorrências (registrar → acompanhar → resolver → manter histórico) | RF-032 a RF-036, RF-043 | API: `TesteOcorrencias` cobre registro, timeline, resolução, exclusão lógica e resumo mensal. Navegador: `ocorrencias-real.cjs` inclui o alerta no painel e a navegação até a ficha. | Java: aprovada · E2E: bloqueada |

---

## 4. Lacunas e limitações

### 4.1 Requisitos sem teste ou com cobertura incompleta

| Requisito | Lacuna |
| --- | --- |
| RF-010 | Nenhuma suíte chama `GET /api/propriedades/periodos-safra`. O `mock-api.cjs` também não simula a rota, e por isso o `ui-polish.cjs` registra erro 404 (relatório §5, item 3). |
| RF-011 | Duplicidade de cultura (409) e exclusão de cultura em uso (409) não são verificadas; só há verificação de catálogo vazio, cadastro e talhão com cultura fora do catálogo. |
| RF-015 | A navegação para `custos.html?talhao=` não tem teste; a de atividades só roda contra a API simulada (`ui-polish.cjs`). |
| RF-006 | O tour tem teste de API (gravação de `onboardingConcluido`), mas nenhum teste de navegador abre o tour. |
| RF-041 a RF-047 | Painel e notificações são verificados apenas no navegador; não há workflow dedicado ao painel. |
| RF-042 | Não há teste de clique nem de Enter/Espaço nos cards. O `ui-polish.cjs` testa Tab e Escape nos modais, não os cards. |
| RF-053, RF-055, RF-056 | Os handlers de relatório da propriedade, de estoque e de atividades não têm suíte Java. A única verificação é `relatorio-safra-real.cjs`, que depende do módulo `mongodb` e não está em nenhum workflow. |
| RNF-002 | A expiração da sessão em 12 horas não é verificada. |
| RNF-016, RNF-019, RNF-022, RNF-025 a RNF-028 | Sem teste dedicado. RNF-019 (CSV) depende de `custos-real.cjs` e `relatorio-safra-real.cjs`; RNF-026 a RNF-028 são verificados pela existência dos workflows e repositórios, não por teste. |

### 4.2 Testes que hoje não comprovam o requisito

Situação conferida em `99ac03e`. Detalhes da execução no
[relatório de testes](10-relatorio-de-testes.md#5-bugs-e-correções).

| Causa | Efeito na matriz |
| --- | --- |
| `token-confirmacao.cjs` e outros testes procuram `token:` no log, mas `ServicoEmail` imprime `codigo:` no e-mail simulado. | 14 suítes de navegador param no cadastro: `login-real`, `atividades-real`, `calendario-real`, `colheita-real`, `custos-real`, `notificacoes-real`, `ocorrencias-real`, `orcamento-real`, `perfil-real`, `talhoes-real`, `cultivo-plantio-audit`, `georreferenciamento-real`, `manutencao-real` e `todas-telas-audit`. Todas as linhas com E2E bloqueada vêm daqui. |
| Testes de navegador e `TesteGeorreferenciamento` criam ou editam talhão com cultura fora do catálogo (RN-010). | Na variante diagnóstica do relatório, as suítes passam do cadastro e quebram nesse ponto; explica as 2 falhas de RF-059. |
| `PeriodoSafra.valida` aceita `1999/00`; 4 testes JUnit esperam recusa. | Parcial em RF-012 (socket). |
| `TesteColheitas`: 3 falhas na edição de safra com colheita, causa não investigada. | Parcial em RF-018 e RF-058. |
| `mock-api.cjs` não responde `/api/culturas` nem `/api/propriedades/periodos-safra`. | `ui-polish.cjs` em falha (RF-015, RF-025, RNF-023, RNF-024); o workflow "Polish de interface" falhou também no push `b0c5292` da `dev` (14/11/2026). |
| `backend/api/testar.sh` encadeia as suítes com `&&`. | No CI, os workflows dos módulos param no passo Java e não chegam ao navegador. Na última rodada completa (PR #303, `710afc4`, 13/11/2026), só "Identidade visual" e "Testes do gateway" passaram. |

### 4.3 Implementado sem requisito correspondente

| Funcionalidade | Evidência | Observação |
| --- | --- | --- |
| Gestão de equipe e convites (PR #310 e #311) | `EquipeHandler` (7 rotas `/api/equipe*`), `ConviteEquipe`, `PapelNaPropriedade`, `TelasAcesso`; `equipe.js`, `equipe-convite.html`, `equipe-convite.js`; teste `TesteServidor.testarEquipe` (aprovado em 14/11) | Entrou depois da versão 1.0 dos requisitos. [02-requisitos §2.2](02-requisitos.md#22-não-escopo) ainda lista equipe, convites e permissões como não escopo "apenas informativos, sem efeito", e as rotas `/api/equipe*` não estão no [contrato da API](06-contrato-api.md). Fica sem linha na matriz até receber um `RF` em 02. |
| Identidade visual (PR #301) | `identidade-visual-real.cjs`, `identidade-visual-testes.yml` (aprovado no CI em 13/11) | Relaciona-se a RNF-022 (design system), sem requisito próprio. |

### 4.4 Limitações desta matriz

- A rastreabilidade vai até a suíte e o grupo de teste, não até cada asserção.
  O critério de aceite completo de cada requisito continua em 02.
- A coluna Implementação cita os pontos de entrada principais (rota, método e
  script), não todo o código envolvido; repositórios e validações auxiliares
  aparecem só quando são o centro do requisito.
- Os resultados de navegador são de 12/11/2026 e não foram reexecutados nesta
  conferência (Playwright não estava instalado na máquina). A conclusão de que
  continuam bloqueados vem da leitura do código em `99ac03e`.
- Nada que depende de MongoDB real, Docker ou SMTP foi executado neste ciclo.
- A execução Java de 14/11 foi feita no Windows; o grupo
  `testarResolucaoMongoBanco` do `TesteServidor` não rodou por falha de DNS
  nesta máquina, e o restante da suíte rodou em uma cópia temporária, fora do
  repositório.

---

## 5. Processo de atualização

### 5.1 Quando atualizar

Atualize esta matriz no **mesmo pull request** que:

1. cria, altera ou remove um requisito em [02-requisitos.md](02-requisitos.md);
2. cria ou renomeia rota, handler, método citado, algoritmo do socket ou script de tela;
3. cria, renomeia ou remove uma suíte de teste ou um grupo `testar*`;
4. registra uma nova execução em [10-relatorio-de-testes.md](10-relatorio-de-testes.md) (coluna Situação e [§2.3](#23-situação-dos-testes)).

### 5.2 Como atualizar

1. Localize a linha pelo identificador. Requisito novo entra no módulo
   correspondente, na ordem do identificador; requisito removido em 02 sai da
   matriz, e o número não é reaproveitado.
2. Preencha as quatro colunas a partir do código, nunca de outro documento:
   rota nos `roteador.adicionar` do handler, método no handler e teste no
   arquivo da suíte.
3. Só registre situação "aprovada" com execução comprovada e arquivo de
   evidência em [`evidencias/testes/`](evidencias/testes/). Sem execução, use
   "pendente".
4. Atualize [§4](#4-lacunas-e-limitações): remova a lacuna resolvida e
   registre as novas.
5. Atualize o cabeçalho: versão, data e commit conferido.
6. Rode a conferência da [§5.3](#53-conferência-automática-das-referências) e
   cite o resultado na descrição do pull request.

O revisor do pull request confere se as linhas afetadas pela mudança foram
atualizadas, conforme o checklist de revisão das issues do Project.

### 5.3 Conferência automática das referências

Na raiz do repositório, em Bash (Linux, macOS ou Git Bash), o comando abaixo
confere se cada arquivo, classe e método Java citado entre crases existe e se
cada link relativo aponta para um arquivo existente. Saída vazia significa que
não há referência quebrada.

```bash
doc=docs/13-rastreabilidade.md
grep -oE '`[A-Za-z][A-Za-z0-9_.-]*`' "$doc" | tr -d '`' | sort -u | while read -r ref; do
  case "$ref" in
    *.java|*.js|*.cjs|*.html|*.yml|*.sh|*.md|*.txt)
      git ls-files | grep -qE "(^|/)${ref//./\\.}$" || echo "arquivo inexistente: $ref" ;;
    *-*) ;;
    *[a-z]*)
      case "$ref" in [A-Z]*) ;; *) continue ;; esac
      classe=${ref%%.*}; metodo=${ref#"$classe"}; metodo=${metodo#.}
      arq=$(git ls-files "*/$classe.java" | head -n 1)
      if [ -z "$arq" ]; then echo "classe inexistente: $ref"
      elif [ -n "$metodo" ] && ! grep -qE "[ .]$metodo ?\(" "$arq"; then echo "método inexistente: $ref"; fi ;;
  esac
done
grep -oE '\]\([^)#]+' "$doc" | cut -c3- | grep -v '^http' | sort -u | while read -r alvo; do
  [ -e "docs/$alvo" ] || echo "link quebrado: $alvo"
done
```

O comando não confere âncoras (`#seção`) nem se a rota está registrada; para
as rotas, compare com o `roteador.adicionar` do handler ou com o
[contrato da API](06-contrato-api.md#conferência-deste-documento).
