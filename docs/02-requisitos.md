# Requisitos do MVP — AgroGestão

| Campo | Valor |
| --- | --- |
| Documento | Especificação consolidada de requisitos do MVP |
| Projeto | [PI_IV_TIME_8 · Project nº 3](https://github.com/users/solerpedroo/projects/3) — Sprint 3 |
| Versão | 1.0 — 12/11/2026 |
| Base de código conferida | branch `docs/requisitos`, igual a `origin/dev` em `60d7dda` |
| Fontes consolidadas | [README](../README.md), [documento de produto](AgroGestao_Projeto.md), [Definição do MVP](AgroGestao-Definicao-MVP.docx), [Lista de Funcionalidades](AgroGestao-Lista-Funcionalidades.docx), contratos por módulo em `docs/` e [contrato HTTP](../backend/api/README.md) |

## Sumário

1. [Introdução e objetivo](#1-introdução-e-objetivo)
2. [Escopo e não escopo](#2-escopo-e-não-escopo)
3. [Requisitos funcionais](#3-requisitos-funcionais)
4. [Requisitos não funcionais](#4-requisitos-não-funcionais)
5. [Regras de negócio](#5-regras-de-negócio)
6. [Restrições técnicas](#6-restrições-técnicas)
7. [Critérios de aceite](#7-critérios-de-aceite)
8. [Critérios específicos](#8-critérios-específicos)

---

## 1. Introdução e objetivo

O AgroGestão é uma aplicação web de gestão agrícola para pequenos e médios
produtores rurais. Ela centraliza propriedades, talhões, safras, atividades,
estoque de insumos, ocorrências e custos, e relaciona esses registros para que
o produtor acompanhe a operação em um único lugar
([documento de produto §1](AgroGestao_Projeto.md#1-visão-geral)).

Este documento é a **fonte consolidada dos requisitos do MVP**. Ele reúne em um
só lugar o que hoje está distribuído entre o README, o documento de produto, a
definição do MVP, a lista de funcionalidades e os contratos de cada módulo, com
três objetivos:

- dar à equipe e aos avaliadores uma referência única, identificada e rastreável;
- descrever o comportamento **observável** do sistema, conferido no código e nos
  testes existentes;
- separar com clareza o que o MVP entrega do que não faz parte dele.

### 1.1 Como ler este documento

- Cada item tem um identificador estável: `RF-nnn` (requisito funcional),
  `RNF-nnn` (requisito não funcional), `RN-nnn` (regra de negócio) e `RT-nnn`
  (restrição técnica).
- Todo requisito funcional informa **prioridade** e **critério de aceite
  verificável**, escrito em termos de rota, tela ou resultado conferível.
- Cada módulo indica as **fontes** (documentos de origem) e as **evidências**
  (testes automatizados e workflows que exercitam o requisito).
- Quando documentos de origem divergem entre si ou do código, este documento
  registra o comportamento implementado e lista a divergência em
  [§8.4](#84-divergências-identificadas-na-consolidação).

### 1.2 Hierarquia dos dados

Todos os registros operacionais pertencem a uma propriedade, e o acesso a eles
passa sempre pela propriedade ativa da sessão do usuário.

```text
USUÁRIO
  └── PROPRIEDADE (contexto ativo da sessão)
        ├── CULTURAS (catálogo)
        ├── TALHÕES ── histórico ── localização (opcional)
        ├── SAFRAS (cultura + talhões + período) ── colheitas ── orçamento
        ├── ATIVIDADES ── histórico ── foto (opcional)
        ├── INSUMOS ── movimentações
        ├── OCORRÊNCIAS ── timeline ── foto (opcional)
        ├── CUSTOS (manuais, de consumo de estoque e de manutenção)
        └── EQUIPAMENTOS ── preventivas ── intervenções
```

---

## 2. Escopo e não escopo

### 2.1 Escopo

O MVP entrega os módulos abaixo, todos operando sobre dados reais da
propriedade autenticada. A prioridade segue a classificação da
[Definição do MVP](AgroGestao-Definicao-MVP.docx): **essencial** (Alta),
**complementar** (Média) e **extensão implementada** além da lista original
(Baixa).

| Módulo | Telas | Classificação | Requisitos |
| --- | --- | --- | --- |
| Autenticação e sessão | `login`, `criar-conta`, `confirmar-email`, `recuperar-senha`, `redefinir-senha` | Essencial | RF-001 a RF-006 |
| Propriedades | Configurações | Essencial | RF-007 a RF-010 |
| Talhões e catálogo de culturas | Áreas de Cultivo, Culturas | Essencial | RF-011 a RF-015 |
| Safras | Safras | Essencial | RF-016 a RF-019 |
| Atividades e calendário | Atividades | Essencial (calendário: complementar) | RF-020 a RF-026 |
| Estoque | Insumos | Essencial | RF-027 a RF-031 |
| Ocorrências e fotos de evidência | Ocorrências | Essencial (fotos: complementar) | RF-032 a RF-036 |
| Custos | Custos | Essencial | RF-037 a RF-040 |
| Painel (Dashboard) | Painel Geral | Essencial | RF-041 a RF-044 |
| Notificações | Sino do Painel | Complementar | RF-045 a RF-047 |
| Perfil e configurações | Configurações | Complementar | RF-048 a RF-052 |
| Relatórios | Painel, Safras, Insumos, Atividades | Complementar | RF-053 a RF-056 |
| Orçamento, colheita, georreferenciamento e manutenção | Custos, Safras, Áreas de Cultivo, Manutenção | Extensão | RF-057 a RF-060 |

### 2.2 Não escopo

Os itens abaixo **não fazem parte** desta entrega. A lista é explícita para
delimitar a avaliação e não representa compromisso de implementação.

| Tema | Itens fora do escopo |
| --- | --- |
| Inteligência artificial | Diagnóstico automático de doenças, identificação de pragas por imagem, recomendações e previsão de produtividade |
| Automação e hardware | IoT, sensores, drones, automação de máquinas e integração com tratores |
| Dados externos | Imagens de satélite, NDVI, camadas de mapa e GIS; integração com serviços climáticos |
| Financeiro e fiscal | Contabilidade completa, integração bancária, folha de pagamento e gestão fiscal |
| Comercial | Marketplace e compra ou venda real de insumos |
| Plataforma | App mobile nativo, funcionamento offline (service worker) e implantação institucional em produção |
| Integração automática na conclusão | Concluir uma atividade **não** baixa estoque nem lança custo automaticamente (frente I20/#24, deliberadamente separada; ver RN-020) |
| Perfil | Upload de foto do usuário (o avatar usa as iniciais); equipe, convites, permissões, cobrança e plano (apenas informativos, sem efeito) |
| Alertas sem serviço | As preferências `weather` e `reports` são gravadas, mas não há alerta climático nem envio de relatórios por e-mail |
| Exclusões | Não há exclusão de talhões nem de equipamentos (o equipamento sai de uso com status Inativo) |
| Operação distribuída | Várias instâncias da API compartilhando sessões; sessões que sobrevivem a reinício; TLS no próprio servidor |

---

## 3. Requisitos funcionais

**Prioridade:** **Alta** = módulo essencial, sem o qual os fluxos centrais do
MVP não se fecham; **Média** = complementar, melhora a experiência sem bloquear
a validação; **Baixa** = extensão implementada além da lista original do MVP.

Salvo indicação em contrário, todas as rotas citadas exigem
`Authorization: Bearer <token>` e operam sobre a propriedade ativa da sessão.

### 3.1 Autenticação e sessão

Fontes: [README — Escopo](../README.md#-escopo-o-que-este-repositório-entrega),
[contrato de autenticação](../backend/api/README.md#autenticação),
[servidor Java §7.1](servidor-java.md#71-autenticação).
Evidências: [`TesteServidor.java`](../backend/api/teste/TesteServidor.java),
[`TestePerfil.java`](../backend/api/teste/TestePerfil.java),
[`ValidadorDeContaTest.java`](../backend/serversocket/src/test/java/algoritmos/ValidadorDeContaTest.java),
[`tests/login-real.cjs`](../tests/login-real.cjs),
[`auth-formularios-testes.yml`](../.github/workflows/auth-formularios-testes.yml).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-001 | Criar conta com nome, e-mail, telefone celular, senha e confirmação, nome e área da propriedade e aceite dos termos. A conta nasce com e-mail não confirmado e com a primeira propriedade. | Alta | `POST /api/auth/register` com dados válidos responde 201; o mesmo e-mail com outra caixa responde 409; cada campo inválido volta em `fields` com 400 (RN-001 a RN-003). |
| RF-002 | Confirmar o e-mail por token e reenviar a confirmação. | Alta | Login antes da confirmação responde 403; `POST /api/auth/confirm-email` com token válido responde 200 e libera o login; token já usado responde 400; reenvio em menos de 30 s responde 429 com `Retry-After`. |
| RF-003 | Entrar com e-mail e senha e manter a sessão por token Bearer. | Alta | Login válido responde 200 com `token` e `usuario`; senha errada e e-mail inexistente recebem o mesmo 401; `GET /api/me` devolve usuário e propriedade ativa; sem token, rotas `/api/**` fora de `/api/auth/*` respondem 401. |
| RF-004 | Encerrar a sessão (logout). | Alta | `POST /api/auth/logout` responde 204 e o mesmo token passa a receber 401. Se a resposta não chegar, a interface remove o token local e informa que o encerramento no servidor não foi confirmado. |
| RF-005 | Recuperar e redefinir a senha por token enviado ao e-mail. | Alta | `POST /api/auth/forgot-password` responde 200 exista ou não o e-mail; `POST /api/auth/reset-password` com token válido troca a senha e encerra as sessões abertas; token vencido (30 min) ou reutilizado responde 400. |
| RF-006 | Exibir um tour de boas-vindas no Painel para contas novas e registrar sua conclusão. | Média | Conta recém-criada traz `onboardingConcluido: false` no usuário público; `POST /api/perfil/onboarding` grava `true` e o tour não reaparece no login seguinte; contas antigas sem o campo são tratadas como concluídas. |

### 3.2 Propriedades

Fontes: [contrato de propriedades](../backend/api/README.md#propriedades-e-contexto-autenticado),
[documento de produto §8.2](AgroGestao_Projeto.md#82-propriedades).
Evidências: [`TesteServidor.java`](../backend/api/teste/TesteServidor.java),
[`TesteMongo.java`](../backend/api/teste/TesteMongo.java),
[`tests/storage-scope.cjs`](../tests/storage-scope.cjs).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-007 | Cadastrar, listar, consultar e editar as propriedades do usuário com nome, localização, município, safra atual e área em hectares. | Alta | `POST /api/propriedades` válido responde 201; nome já usado pelo mesmo usuário responde 409; área em texto, zero ou negativa responde 400 em `areaHectares`; safra fora de `AAAA/AA` responde 400 em `safraAtual` (RN-009). |
| RF-008 | Selecionar a propriedade ativa da sessão; os módulos operam somente sobre ela. | Alta | Após `POST /api/propriedades/{id}/selecionar`, `GET /api/me` devolve a nova propriedade e as listagens passam a conter só os registros dela; ID de propriedade de outro usuário responde 404. |
| RF-009 | Excluir propriedade sem registros dependentes. | Alta | `DELETE /api/propriedades/{id}` responde 204 para propriedade sem vínculos e 409 em cada caso de RN-008. |
| RF-010 | Sugerir períodos de safra a partir da data atual e dos períodos já usados na propriedade. | Baixa | `GET /api/propriedades/periodos-safra` devolve `padrao`, `sugeridos`, `emUso` e `opcoes`; `padrao` segue RN-009. |

### 3.3 Talhões e catálogo de culturas

Fontes: [contrato de talhões](../backend/api/README.md#talhões),
[documento de produto §8.3](AgroGestao_Projeto.md#83-talhões),
[revisão de áreas de cultivo](revisao-cultivo-plantio.md).
Evidências: [`TesteServidor.java`](../backend/api/teste/TesteServidor.java),
[`ValidadorDeTalhaoTest.java`](../backend/serversocket/src/test/java/algoritmos/ValidadorDeTalhaoTest.java),
[`CalculadoraDeTalhoesTest.java`](../backend/serversocket/src/test/java/algoritmos/CalculadoraDeTalhoesTest.java),
[`tests/talhoes-real.cjs`](../tests/talhoes-real.cjs),
[`tests/cultivo-plantio-audit.cjs`](../tests/cultivo-plantio-audit.cjs).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-011 | Manter o catálogo de culturas da propriedade: incluir, listar e excluir. | Alta | O catálogo de uma propriedade nova vem vazio; `POST /api/culturas` com nome de 2 a 40 caracteres responde 201; nome repetido com outra caixa responde 409; excluir cultura usada em talhão ou safra responde 409 (RN-010). |
| RF-012 | Cadastrar e editar talhões com nome, cultura, período de safra, hectares e status. | Alta | `POST /api/talhoes` válido responde 201 com status `Ativo` por padrão; nome repetido na propriedade responde 409; cultura fora do catálogo, hectares ≤ 0 ou status fora da lista respondem 400 no campo; não existe rota de exclusão (RN-011). |
| RF-013 | Listar talhões com filtro por cultura e busca por nome, com resumo de área. | Alta | `GET /api/talhoes?cultura=&q=` devolve só os talhões que atendem aos filtros; `GET /api/talhoes/resumo` devolve `totalHectares`, `quantidadeTotal` e `quantidadeAtivos` coerentes com a lista. |
| RF-014 | Registrar e consultar o histórico do talhão (eventos de atividade, ocorrência e custo). | Alta | Depois de uma atividade ou um custo com talhão confirmado nas telas de Atividades, Custos ou Painel, `GET /api/talhoes/{id}/historico` contém o evento; tipo diferente de `atividade`, `ocorrencia` ou `custo` responde 400. |
| RF-015 | Navegar de Áreas de Cultivo para Atividades, Ocorrências e Custos já filtrados pelo talhão. | Média | O menu do talhão abre `atividades.html?talhao=`, `ocorrencias.html?talhao=` e `custos.html?talhao=`, e cada tela exibe somente os registros daquele talhão. |

### 3.4 Safras

Fontes: [safras](safras.md), [contrato de safras](../backend/api/README.md#safras),
[documento de produto §8.4](AgroGestao_Projeto.md#84-safras-e-culturas).
Evidências: [`TesteServidor.java`](../backend/api/teste/TesteServidor.java),
[`tests/cultivo-plantio-audit.cjs`](../tests/cultivo-plantio-audit.cjs).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-016 | Cadastrar safra com nome, cultura do catálogo, período e talhões da propriedade. | Alta | `POST /api/safras` válido responde 201 na etapa `Planejamento`, com `areaHectares` igual à soma dos hectares dos talhões; término antes do início, lista vazia, talhão repetido ou de outra propriedade respondem 400; talhão já usado em período sobreposto responde 409 em `talhoes` (RN-013). |
| RF-017 | Avançar a safra pelas etapas do ciclo, registrando cada mudança. | Alta | Cada `POST /api/safras/{id}/avancar` move exatamente uma etapa e acrescenta um item em `historicoEtapas`; em safra finalizada responde 409 (RN-012). |
| RF-018 | Listar, editar e excluir safras respeitando os vínculos existentes. | Alta | `GET /api/safras?etapa=&cultura=&q=` aplica os filtros; `PUT` em safra finalizada responde 409; `DELETE` fora de `Planejamento` ou com custos responde 409 (RN-014 a RN-016). |
| RF-019 | Consultar o histórico do ciclo com os eventos dos talhões da safra no período. | Alta | `GET /api/safras/{id}/historico` devolve apenas eventos com data dentro do período (fuso de São Paulo), do mais recente ao mais antigo, com `talhaoId` e `talhaoNome`. |

### 3.5 Atividades e calendário

Fontes: [atividades](atividades.md), [calendário](calendario.md),
[documento de produto §8.5](AgroGestao_Projeto.md#85-atividades).
Evidências: [`TesteServidor.java`](../backend/api/teste/TesteServidor.java),
[`tests/atividades-real.cjs`](../tests/atividades-real.cjs),
[`tests/atividades-reinicio.cjs`](../tests/atividades-reinicio.cjs),
[`tests/calendario-real.cjs`](../tests/calendario-real.cjs),
[`atividades-testes.yml`](../.github/workflows/atividades-testes.yml),
[`calendario-testes.yml`](../.github/workflows/calendario-testes.yml).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-020 | Planejar atividade com nome, tipo, talhão, data, horário, responsável, prioridade e safra opcional. | Alta | `POST /api/atividades` válido responde 201 com status `Pendente` ou `Agendada`; tipo, prioridade, horário (`HH:MM`) ou data fora de `12 ago`/`AAAA-MM-DD` respondem 400; talhão de outra propriedade ou safra sem o talhão respondem 400; com safra, a resposta traz a cultura dela (RN-019). |
| RF-021 | Editar o planejamento de atividades ainda não finalizadas. | Alta | `PUT /api/atividades/{id}` altera os campos; em atividade concluída ou cancelada responde 409. |
| RF-022 | Executar a atividade pelas transições de status, a partir do menu, da lista ou do arraste no Kanban. | Alta | As transições de RN-018 respondem 200 e acrescentam um evento; qualquer outra responde 409 sem novo evento; o cartão só muda depois da resposta do servidor. |
| RF-023 | Cancelar atividade com motivo. | Alta | `POST /api/atividades/{id}/cancelar` antes da conclusão responde 200 e o motivo aparece no histórico; em atividade concluída ou já cancelada responde 409. |
| RF-024 | Consultar o histórico da atividade (criação e mudanças de status), inclusive após reiniciar o servidor. | Alta | `GET /api/atividades/{id}/historico` devolve os eventos em ordem, com ação, data e descrição ou motivo; outra propriedade recebe 404. Edições de planejamento não geram evento. |
| RF-025 | Visualizar atividades em Kanban e em lista, com filtros de status, tipo, talhão e busca. | Média | Filtrar por um talhão exibe apenas as atividades dele; recarregar a página mantém os registros vindos da API. |
| RF-026 | Visualizar o calendário de atividades por semana e por mês, com contagens por dia, status e tipo. | Média | `GET /api/calendario?de=&ate=` responde 200 com todos os dias do período, inclusive os zerados; período ausente, inválido, invertido ou maior que 62 dias responde 400; clicar em um dia filtra quadro e lista. |

### 3.6 Estoque (Insumos)

Fontes: [estoque](estoque.md), [contrato de estoque](../backend/api/README.md#estoque),
[documento de produto §8.6](AgroGestao_Projeto.md#86-estoque).
Evidências: [`TesteServidor.java`](../backend/api/teste/TesteServidor.java),
[`TesteEstoqueMongo.java`](../backend/api/teste/TesteEstoqueMongo.java),
[`CalculadoraDeEstoqueTest.java`](../backend/serversocket/src/test/java/algoritmos/CalculadoraDeEstoqueTest.java),
[`tests/estoque-real.cjs`](../tests/estoque-real.cjs),
[`estoque-testes.yml`](../.github/workflows/estoque-testes.yml).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-027 | Cadastrar, editar, consultar e arquivar insumos com nome, categoria, unidade, mínimo, limiar crítico opcional e custo unitário. | Alta | `POST /api/insumos` válido responde 201; categoria fora da lista ou limiar maior que o mínimo responde 400; `DELETE` com saldo responde 409; com saldo zero o item é arquivado e o histórico continua em `/api/insumos/{id}/movimentacoes` (RN-026). |
| RF-028 | Registrar entradas, saídas e ajustes de estoque. | Alta | Entrada ou saída com quantidade positiva responde 201 e atualiza o saldo; saída maior que o saldo responde 409 sem gravar histórico; ajuste sem motivo responde 400; ajuste para zero é aceito (RN-023, RN-024). |
| RF-029 | Vincular uma saída a uma atividade persistida da propriedade (consumo). | Alta | `GET /api/insumos/atividades` lista só atividades da propriedade; saída com `atividadeId` inexistente ou de outra propriedade responde 400 em `atividadeId` e nada é gravado; entrada ou ajuste com atividade também responde 400. |
| RF-030 | Consultar o histórico imutável de movimentações de cada insumo. | Alta | Cada movimentação traz data do servidor, tipo, quantidade, unidade, saldo anterior e posterior, fornecedor, custo e motivo quando informados e, no consumo, ID e título da atividade. |
| RF-031 | Exibir indicadores e a situação de cada item (Normal, Baixo, Crítico). | Alta | `GET /api/insumos/resumo` devolve itens ativos, críticos, valor em estoque e entradas do mês; a situação de cada item segue RN-022. |

### 3.7 Ocorrências e fotos de evidência

Fontes: [ocorrências](ocorrencias.md), [fotos de evidência](fotos-evidencia.md),
[documento de produto §8.7](AgroGestao_Projeto.md#87-ocorrências).
Evidências: [`TesteOcorrencias.java`](../backend/api/teste/TesteOcorrencias.java),
[`tests/ocorrencias-real.cjs`](../tests/ocorrencias-real.cjs),
[`ocorrencias-testes.yml`](../.github/workflows/ocorrencias-testes.yml).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-032 | Registrar ocorrência com título, tipo, data, prioridade, descrição e talhão opcional. | Alta | `POST /api/ocorrencias` válido responde 201 com status `ABERTA`; título acima de 160 caracteres, tipo ou prioridade fora da lista e data inválida respondem 400; a descrição é exibida como texto literal, sem interpretar HTML. |
| RF-033 | Acompanhar e resolver a ocorrência pela timeline. | Alta | `POST /api/ocorrencias/{id}/timeline` com `acompanhamento`, `atualizacao` ou `resolucao` e descrição responde 200 e acrescenta o evento; sem descrição responde 400; qualquer ação em ocorrência resolvida responde 409 (RN-028). |
| RF-034 | Editar a ficha com controle de versão e excluir logicamente, mantendo a consulta. | Alta | Gravação com `versao` desatualizada responde 409; após `DELETE`, a ocorrência sai da lista padrão, aparece com `incluirExcluidas=true` e mantém toda a timeline (RN-029). |
| RF-035 | Exibir o resumo mensal de ocorrências. | Alta | `GET /api/ocorrencias/resumo?mes=AAAA-MM` devolve abertas, em acompanhamento, de prioridade alta ainda ativas e resolvidas no mês, contando como resolvida no mês apenas a de `resolvidaEm` nesse mês (RN-030). |
| RF-036 | Anexar, consultar e remover foto de evidência em ocorrências e atividades. | Média | `POST .../foto` com JPEG, PNG, WebP ou GIF de até 5 MiB é aceito e a ficha passa a indicar `temFoto`; `GET .../foto` devolve a imagem; formato ou tamanho fora do limite é recusado sem perder o cadastro. |

### 3.8 Custos

Fontes: [custos](custos.md), [contrato de custos](../backend/api/README.md#custos),
[documento de produto §8.8](AgroGestao_Projeto.md#88-custos).
Evidências: [`TesteServidor.java`](../backend/api/teste/TesteServidor.java),
[`tests/custos-real.cjs`](../tests/custos-real.cjs),
[`custos-testes.yml`](../.github/workflows/custos-testes.yml).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-037 | Lançar, editar e excluir custos manuais com descrição, categoria, valor, data e talhão ou safra opcionais. | Alta | `POST /api/custos` válido responde 201; valor em texto, abaixo de R$ 0,01 ou acima de R$ 1 bilhão responde 400; talhão que não pertence à safra responde 400 no campo; safra finalizada responde 409; a exclusão pede confirmação na tela (RN-031). |
| RF-038 | Filtrar custos por categoria, busca, talhão, safra e período e exportar o recorte em CSV. | Média | Lista, cards, gráfico e CSV usam a mesma consulta; o CSV contém exatamente os lançamentos exibidos; período invertido é recusado na tela e pelo servidor (400). |
| RF-039 | Exibir indicadores: total, quantidade, por categoria, talhão, safra e cultura, custo por hectare e evolução mensal. | Alta | `GET /api/custos/resumo` sobre um conjunto conhecido devolve os totais somados em centavos; sem área no recorte, `custoPorHectare` é `null` e a tela mostra "-" (RN-033). |
| RF-040 | Gerar automaticamente o custo do consumo de insumo vinculado a uma atividade. | Alta | Uma saída de 50 kg de insumo com custo médio de R$ 2,50, vinculada a uma atividade, gera um custo de R$ 125,00 em Insumos com o talhão e a safra da atividade; repetir a sincronização não duplica; `PUT` ou `DELETE` desse custo responde 409 (RN-032). |

### 3.9 Painel (Dashboard)

Fontes: [documento de produto §8.1](AgroGestao_Projeto.md#81-dashboard),
[revisão de dados reais](revisao-dados-reais.md),
[`frontend/scripts/dashboard.js`](../frontend/scripts/dashboard.js).
Evidências: [`tests/login-real.cjs`](../tests/login-real.cjs),
[`tests/local-screens.cjs`](../tests/local-screens.cjs),
[`tests/todas-telas-audit.cjs`](../tests/todas-telas-audit.cjs),
[`tests/empty-state-origin.cjs`](../tests/empty-state-origin.cjs).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-041 | Exibir os indicadores da propriedade ativa: áreas ativas, atividades pendentes, estoque baixo e custos registrados. | Alta | Os cards mostram: talhões com status `Ativo`; atividades `Pendente` ou `Agendada`; insumos abaixo do mínimo ou críticos; total de `GET /api/custos/resumo` sem filtros. Sem resposta da API, o card permanece "—" ou "-". |
| RF-042 | Levar de cada indicador ao módulo correspondente, por mouse e teclado. | Alta | Clicar ou pressionar Enter ou Espaço em um card abre Áreas de Cultivo, Atividades, Insumos ou Custos. |
| RF-043 | Listar alertas de ocorrências abertas ou em acompanhamento, com acesso à ficha. | Alta | Cada alerta aponta para `ocorrencias.html?ocorrenciaId={id}`; uma ocorrência resolvida deixa de aparecer. |
| RF-044 | Exibir as atividades da propriedade e a área por cultura, com registro e conclusão rápida de atividade. | Alta | Uma atividade criada pelo painel aparece na lista depois da confirmação do servidor; a ação de concluir só atua em atividade `Em andamento`; "Culturas em Safra" soma os hectares dos talhões por cultura. |

### 3.10 Notificações

Fontes: [notificações](notificacoes.md),
[documento de produto §8.10](AgroGestao_Projeto.md#810-notificações).
Evidências: [`tests/notificacoes-real.cjs`](../tests/notificacoes-real.cjs),
[`notificacoes-testes.yml`](../.github/workflows/notificacoes-testes.yml).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-045 | Gerar alertas de estoque baixo ou crítico, atividade atrasada, atividades do dia e ocorrência aberta ou em acompanhamento a partir dos registros reais. | Média | Com um registro de cada tipo, o sino exibe os quatro alertas (RN-035); sem registros, nenhum alerta é exibido. |
| RF-046 | Abrir o registro relacionado ao clicar na notificação. | Média | Estoque abre `insumos.html?insumoId=`; atraso abre `atividades.html?atividadeId=`; atividades do dia abrem `atividades.html?hoje=1`; ocorrência abre `ocorrencias.html?ocorrenciaId=`. |
| RF-047 | Conservar o estado de leitura e respeitar as preferências de notificação. | Média | Um alerta lido permanece sem destaque após recarregar; desligar a preferência `stock` em Configurações remove os alertas de estoque do painel. |

### 3.11 Perfil e configurações

Fontes: [perfil](perfil.md), [contrato de perfil](../backend/api/README.md#perfil-e-segurança),
[documento de produto §8.11](AgroGestao_Projeto.md#811-perfil-e-configurações).
Evidências: [`TestePerfil.java`](../backend/api/teste/TestePerfil.java),
[`tests/perfil-real.cjs`](../tests/perfil-real.cjs),
[`perfil-testes.yml`](../.github/workflows/perfil-testes.yml).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-048 | Consultar e editar nome e telefone do usuário. | Média | `PUT /api/perfil` válido persiste e o valor permanece após recarregar; telefone fora de `(DD) 9XXXX-XXXX` responde 400 (telefone vazio é aceito). |
| RF-049 | Trocar o e-mail mediante senha atual e confirmação por código. | Média | Pedido sem a senha atual correta é recusado; o e-mail antigo continua válido até `POST /api/perfil/email/confirmar` com o código; após a confirmação, todas as sessões são encerradas (RN-004, RN-005). |
| RF-050 | Gravar as preferências de notificação (`stock`, `weather`, `activities`, `occurrences`, `reports`). | Média | `PUT /api/perfil/preferencias` persiste os cinco valores; conta sem preferências lê os quatro primeiros ligados e `reports` desligado. |
| RF-051 | Definir a propriedade padrão usada no próximo login. | Média | `PUT /api/perfil/propriedade-padrao` com propriedade do usuário persiste; o login seguinte abre nela; a sessão atual não muda (RN-006). |
| RF-052 | Trocar a senha informando a senha atual. | Média | Senha atual errada, nova senha igual à atual, fraca ou com confirmação diferente é recusada; após salvar, todas as sessões são encerradas (RN-001, RN-005). |

### 3.12 Relatórios

Fontes: [relatório da propriedade](relatorio-propriedade.md),
[relatório da safra](relatorio-safra.md), [relatório de estoque](relatorio-estoque.md),
[relatório de atividades](relatorio-atividades.md),
[documento de produto §8.9](AgroGestao_Projeto.md#89-relatórios).
Evidências: [`TesteRelatorioSafra.java`](../backend/api/teste/TesteRelatorioSafra.java),
[`ConsolidadorDeSafraTest.java`](../backend/serversocket/src/test/java/algoritmos/ConsolidadorDeSafraTest.java),
[`tests/relatorio-safra-real.cjs`](../tests/relatorio-safra-real.cjs) (cobre os quatro relatórios).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-053 | Gerar o relatório da propriedade por período, no Painel. | Média | `GET /api/relatorios/propriedade?de=&ate=` separa indicadores atuais (talhões, área, safras, produtos ativos) dos registros de atividades, ocorrências e custos do período; o CSV contém o mesmo resultado (RN-036). |
| RF-054 | Gerar o relatório da safra, na tela Safras. | Média | `GET /api/relatorios/safras/{id}` traz cultura, etapa, período, talhões, área, registros, contagens, total em centavos, colheitas e pendências; `?relatorio=<id>` reabre a consulta após recarregar; o CSV usa o mesmo objeto da tela. |
| RF-055 | Gerar o relatório de estoque por período, na tela Insumos. | Média | `GET /api/relatorios/estoque` apresenta separadamente saldos atuais e movimentações do período; ajustes não são contados como entrada ou saída; unidades distintas não são somadas. |
| RF-056 | Gerar o relatório de atividades por período, na tela Atividades. | Média | `GET /api/relatorios/atividades` classifica concluídas, pendentes (`Pendente` ou `Agendada`) e atrasadas conforme RN-037; o CSV contém o mesmo conjunto. |

### 3.13 Extensões operacionais implementadas

Estes requisitos ampliam módulos essenciais (custos, safras, talhões e
ocorrências) e já estão implementados. Não alteram o critério de sucesso do MVP.

Fontes: [orçamento](orcamento.md), [colheita](colheita.md),
[georreferenciamento](georreferenciamento.md), [manutenção](manutencao.md).
Evidências: [`TesteOrcamentos.java`](../backend/api/teste/TesteOrcamentos.java),
[`TesteColheitas.java`](../backend/api/teste/TesteColheitas.java),
[`TesteGeorreferenciamento.java`](../backend/api/teste/TesteGeorreferenciamento.java),
[`TesteManutencao.java`](../backend/api/teste/TesteManutencao.java),
[`GeometriaDeTalhaoTest.java`](../backend/serversocket/src/test/java/algoritmos/GeometriaDeTalhaoTest.java),
[`AgendaDeManutencaoTest.java`](../backend/serversocket/src/test/java/algoritmos/AgendaDeManutencaoTest.java),
[`tests/orcamento-real.cjs`](../tests/orcamento-real.cjs),
[`tests/colheita-real.cjs`](../tests/colheita-real.cjs),
[`tests/georreferenciamento-real.cjs`](../tests/georreferenciamento-real.cjs),
[`tests/manutencao-real.cjs`](../tests/manutencao-real.cjs).

| ID | Requisito | Prioridade | Critério de aceite |
| --- | --- | --- | --- |
| RF-057 | Definir orçamento por safra e comparar planejado com realizado. | Baixa | `PUT /api/orcamentos/{safraId}` responde 201 na primeira definição e 200 nas seguintes; `realizado` é igual ao total de `/api/custos/resumo?safraId=`; `situacao` segue RN-034; safra finalizada responde 409. |
| RF-058 | Registrar colheitas por safra e talhão, calcular a produtividade e comparar ciclos. | Baixa | 150 t colhidas em 50 ha resultam em 50 sc/ha em `GET /api/colheitas/resumo`; colheita antes da etapa Plantio ou em safra finalizada responde 409 (RN-038). |
| RF-059 | Definir a localização simplificada do talhão, marcar a posição da ocorrência e visualizar o mapa. | Baixa | Polígono válido responde 200 com `areaCalculadaHectares` e `centro`; latitude 95 responde 400; `GET /api/mapa` desenha só talhões com localização e lista os demais em `semLocalizacao` (RN-040). |
| RF-060 | Cadastrar equipamentos, programar preventivas e registrar intervenções com custo vinculado. | Baixa | Intervenção com valor gera custo em Maquinário e sem valor não gera; a agenda classifica preventivas em `atrasada`, `proxima` (até 7 dias) ou `futura`; nome de equipamento repetido responde 409 (RN-039). |

---

## 4. Requisitos não funcionais

Fontes: [limites e segurança da API](../backend/api/README.md#limites-e-segurança),
[servidor Java §9 e §10](servidor-java.md#9-segurança),
[revisão de dados reais](revisao-dados-reais.md), [polish da interface](polish-ui.md),
[revisão de todas as telas](revisao-todas-telas.md).

### 4.1 Segurança

| ID | Requisito | Critério de verificação |
| --- | --- | --- |
| RNF-001 | Senhas armazenadas com PBKDF2WithHmacSHA256 (120.000 iterações) e salt aleatório; hash e tokens nunca aparecem em respostas. | Nenhuma resposta de `/api/auth/*`, `/api/me` ou `/api/perfil` contém `senhaHash` ou tokens de e-mail; [`Senhas.java`](../backend/api/src/seguranca/Senhas.java). |
| RNF-002 | Toda rota `/api/**`, exceto `/api/auth/*`, exige token Bearer; a sessão dura 12 horas e cada conta mantém no máximo 5 sessões (a mais antiga é encerrada ao abrir a sexta). | Rota protegida sem token responde 401; [`Sessoes.java`](../backend/api/src/seguranca/Sessoes.java). |
| RNF-003 | O sistema não revela se um e-mail está cadastrado. | Login com e-mail inexistente e com senha errada recebem o mesmo 401; `forgot-password` sempre responde 200. |
| RNF-004 | Rotas públicas sensíveis limitam 60 tentativas por IP por minuto; cada operação de perfil limita 20 tentativas por conta por minuto. | Acima do limite, a resposta é 429 com `Retry-After`. |
| RNF-005 | Isolamento total entre usuários e propriedades: registro de outra propriedade é tratado como inexistente. | Consulta ou alteração com ID de outra propriedade responde 404, nunca 403 nem os dados. |
| RNF-006 | Arquivos estáticos limitados à lista de extensões permitidas (html, css, js, jpg, png, svg, ico, woff2), com bloqueio de `..`, arquivos iniciados por ponto (`.env`, `.git`) e da pasta `backend/`. | `GET /.env`, `GET /../.env.example` e `GET /backend/...` respondem 404. |
| RNF-007 | CORS liberado apenas para `localhost` e `127.0.0.1`; respostas com `X-Content-Type-Options: nosniff`. | `OPTIONS` responde 204 com cabeçalhos CORS só para essas origens. |
| RNF-008 | Conteúdo digitado pelo usuário é exibido como texto, sem interpretação de HTML; o CSV dos relatórios (RF-053 a RF-056) neutraliza fórmulas. | Descrição com HTML aparece literal na ficha; no CSV de relatório, célula iniciada por `=`, `+`, `-` ou `@` recebe prefixo `'` ([`relatorio-util.js`](../frontend/scripts/relatorio-util.js)). |

### 4.2 Confiabilidade e consistência

| ID | Requisito | Critério de verificação |
| --- | --- | --- |
| RNF-009 | A API é a fonte de verdade: a interface só anuncia sucesso depois da confirmação do servidor; falha de rede não confirma mutação no `localStorage`. | Com a API retornando erro, a tela mostra mensagem de falha e preserva o que foi digitado; nenhum toast de sucesso é exibido. |
| RNF-010 | Nenhuma tela exibe dados fictícios: contas e propriedades começam vazias; falha de consulta mostra erro e "Tentar novamente", sem restaurar exemplos nem inventar R$ 0,00. | [`tests/empty-state-origin.cjs`](../tests/empty-state-origin.cjs) e [`tests/todas-telas-audit.cjs`](../tests/todas-telas-audit.cjs); [`operational-defaults.js`](../frontend/scripts/data/operational-defaults.js) contém coleções vazias. |
| RNF-011 | Operações compostas são atômicas no MongoDB (replica set): cadastro de conta, movimentação de estoque, ocorrência e timeline, atividade e histórico, exclusão de propriedade. | Falha na gravação do histórico desfaz a alteração do registro ([`TesteOcorrencias.java`](../backend/api/teste/TesteOcorrencias.java), [`TesteEstoqueMongo.java`](../backend/api/teste/TesteEstoqueMongo.java)). |
| RNF-012 | Regras de unicidade e saldo valem sob concorrência. | Duas saídas simultâneas de 7 kg sobre saldo de 10 kg: só uma é aceita; cadastros simultâneos com o mesmo e-mail ou talhões com o mesmo nome geram um registro e conflitos 409. |
| RNF-013 | Controle otimista de versão em registros editáveis concorrentes (ocorrências, fotos). | Gravação com `versao` antiga responde 409 e a tela orienta nova consulta. |
| RNF-014 | Os dados persistem após recarregar a página e após reiniciar o processo Java com MongoDB. | [`tests/atividades-reinicio.cjs`](../tests/atividades-reinicio.cjs) e as execuções `--mongo` das suítes Java. |
| RNF-015 | Um erro em uma requisição não derruba o servidor; o estado do serviço é consultável. | Exceção em handler responde `500 {"error":"Erro interno do servidor."}`; `GET /health` responde UP e `GET /ready` responde 503 quando o MongoDB está indisponível. |
| RNF-016 | Respostas antigas nunca substituem respostas mais novas na interface (consultas concorrentes, troca de período ou de propriedade). | Calendário, manutenção e relatórios descartam respostas de consultas anteriores; a troca de propriedade invalida a exportação de relatórios. |

### 4.3 Exatidão de dados

| ID | Requisito | Critério de verificação |
| --- | --- | --- |
| RNF-017 | Valores monetários somados em centavos; quantidades de estoque com até 6 casas decimais; produção de colheita somada em gramas. | Somas de conjuntos conhecidos conferem ao centavo (ex.: total de R$ 999,30 no relatório da propriedade em [relatorio-propriedade.md](relatorio-propriedade.md)). |
| RNF-018 | "Hoje", mês corrente e datas de resolução calculados no fuso `America/Sao_Paulo`. | Entradas do mês, atraso de atividades, resolvidas no mês e relatórios respeitam a virada de data de São Paulo, não a de UTC. |
| RNF-019 | Exportações CSV (Custos e relatórios) reproduzem exatamente o resultado exibido, com BOM UTF-8 e delimitador `;`. | O CSV contém os mesmos registros da consulta exibida e o mesmo total. |

### 4.4 Desempenho e limites operacionais

| ID | Requisito | Critério de verificação |
| --- | --- | --- |
| RNF-020 | Limites de protocolo HTTP: cabeçalhos até 16 KB, corpo até 1 MB (8 MiB em rotas `/foto`), leitura com tempo limite de 10 s e até 100 conexões simultâneas. | Respostas 431, 413, 408 e 503 nos respectivos excessos ([`TesteServidor.java`](../backend/api/teste/TesteServidor.java)). |
| RNF-021 | O servidor atende requisições em paralelo, com uma thread por conexão. | 50 requisições paralelas são atendidas sem erro na suíte do servidor. |

### 4.5 Usabilidade, acessibilidade e responsividade

| ID | Requisito | Critério de verificação |
| --- | --- | --- |
| RNF-022 | Interface consistente com o design system Forest Sage, com feedback por toasts (sucesso, erro, aviso, informação). | [`docs/design/`](design/) e componentes compartilhados (`AgroToast`, filtros, menu "⋮", Kanban, timeline). |
| RNF-023 | Layout sem rolagem horizontal da página em sete larguras desktop (1024 a 1920 px) e no celular (390×844); tabelas extensas têm rolagem própria. | [`tests/ui-polish.cjs`](../tests/ui-polish.cjs) e [`tests/todas-telas-audit.cjs`](../tests/todas-telas-audit.cjs). |
| RNF-024 | Navegação por teclado: cards do painel acionáveis com Enter e Espaço; Tab permanece no modal aberto; Escape fecha primeiro o elemento sobreposto; o foco volta ao acionador ao fechar. | [`tests/ui-polish.cjs`](../tests/ui-polish.cjs) (fluxos de foco e modais). |
| RNF-025 | Erros de validação aparecem junto ao campo correspondente. | Resposta 400 com `fields` destaca o campo e a mensagem na tela. |

### 4.6 Manutenibilidade e testabilidade

| ID | Requisito | Critério de verificação |
| --- | --- | --- |
| RNF-026 | Cada módulo tem suíte automatizada reproduzível e workflow de CI correspondente. | Workflows em [`.github/workflows/`](../.github/workflows/); comandos em [§7.4](#74-como-reproduzir-a-verificação). |
| RNF-027 | Testes com MongoDB usam banco dedicado terminado em `_teste` e removem apenas os próprios dados, sem `drop` de banco ou coleção de desenvolvimento. | Ver [estoque.md](estoque.md#reproduzir) e [ocorrencias.md](ocorrencias.md#como-repetir-a-validação). |
| RNF-028 | Repositórios em memória e MongoDB implementam a mesma interface e as mesmas regras. | `backend/api/src/dados/Repositorio*.java` com variantes `*Memoria` e `*Mongo`; modo `--memoria` para testes. |

---

## 5. Regras de negócio

### 5.1 Conta e sessão

| ID | Regra |
| --- | --- |
| RN-001 | A senha tem no mínimo 8 caracteres, com pelo menos uma letra maiúscula, um número e um símbolo; a confirmação deve ser igual. A mesma regra vale no cadastro, na redefinição e na troca de senha. |
| RN-002 | O telefone é celular no formato `(DD) 9XXXX-XXXX`; telefone fixo é recusado. |
| RN-003 | O e-mail é único sem diferenciar maiúsculas e minúsculas; o login só é permitido após a confirmação do e-mail. |
| RN-004 | O token de redefinição de senha e o código de troca de e-mail valem 30 minutos e são de uso único; reenvios exigem intervalo mínimo de 30 segundos. |
| RN-005 | Redefinir a senha, trocar a senha ou confirmar um novo e-mail encerra todas as sessões da conta. Na troca de e-mail, o endereço antigo continua válido até a confirmação. |
| RN-006 | A propriedade padrão vale a partir do próximo login; a seleção feita na sessão atual é independente dela. |

### 5.2 Propriedade e contexto

| ID | Regra |
| --- | --- |
| RN-007 | Todo registro operacional pertence ao usuário e à propriedade ativa da sessão. O cliente não escolhe o dono do registro. |
| RN-008 | Uma propriedade não pode ser excluída se for a última do usuário, se for a propriedade ativa, se estiver em uso em outra sessão ou se tiver talhões, catálogo ou histórico de estoque, ocorrências, custos, atividades, safras ou equipamentos. Se a propriedade padrão for excluída, a referência passa para outra propriedade do usuário. |
| RN-009 | O período de safra usa o formato `AAAA/AA`, em que `AA` são os dois últimos dígitos do ano seguinte e o ano inicial fica entre 1900 e 2100 (ex.: `2026/27`). O período padrão começa no ano corrente a partir de julho e no ano anterior até junho. |

### 5.3 Talhões, culturas e safras

| ID | Regra |
| --- | --- |
| RN-010 | Talhões e safras só aceitam uma cultura cadastrada no catálogo da propriedade. O nome da cultura tem de 2 a 40 caracteres e é único sem diferenciar maiúsculas; cultura em uso em talhão ou safra não pode ser excluída. |
| RN-011 | O nome do talhão (até 80 caracteres) é único na propriedade, sem diferenciar maiúsculas. Hectares devem ser maiores que zero. Status permitidos: `Ativo` (padrão), `Irrigação pendente` e `Em preparo`. Talhão com atividades vinculadas não pode ser renomeado. Não há exclusão de talhão. |
| RN-012 | A safra percorre, em ordem e sem retorno, as etapas Planejamento → Preparação → Plantio → Manejo → Colheita → Finalização. Toda safra nasce em Planejamento e avança uma etapa por vez. |
| RN-013 | Um talhão não pode participar de duas safras cujos períodos se sobrepõem. |
| RN-014 | Só é possível excluir uma safra na etapa Planejamento e sem custos lançados; a exclusão remove também o orçamento da safra. |
| RN-015 | Safra finalizada é somente leitura: não é editada, avançada nem excluída, e não aceita novos custos, colheitas, orçamento ou intervenções vinculadas. |
| RN-016 | A edição de uma safra não pode retirar talhão que tenha custo ou colheita registrada nela, nem mover o início para depois de uma colheita existente. Safra com atividades vinculadas preserva nome, cultura e talhões já associados. |
| RN-017 | A área da safra é a soma atual dos hectares dos seus talhões; não é digitada. |

### 5.4 Atividades

| ID | Regra |
| --- | --- |
| RN-018 | Transições permitidas: `Pendente` → `Agendada` ou `Cancelada`; `Agendada` → `Em andamento` ou `Cancelada`; `Em andamento` → `Concluída` ou `Cancelada`. `Concluída` e `Cancelada` são terminais: não reabrem, não mudam de status e não são editadas. Uma atividade nasce `Pendente` (padrão) ou `Agendada`. |
| RN-019 | Tipos permitidos: Aplicação, Irrigação, Colheita, Manutenção e Manejo. Prioridades: Baixa, Média e Alta. A safra informada deve pertencer à propriedade e conter o talhão da atividade; a cultura é derivada da safra. |
| RN-020 | Concluir uma atividade não movimenta estoque nem gera custo. O consumo é registrado como saída de insumo vinculada à atividade (RF-029), que gera o custo (RN-032). |
| RN-021 | A data da atividade aceita o formato do protótipo sem ano (`12 ago`) ou ISO (`AAAA-MM-DD`). Sem ano, o calendário usa a ocorrência mais próxima da data de criação (em empate, o ano da criação); as notificações usam o ano corrente, ou o anterior se a data cair no futuro; os relatórios tratam a data como pendência e não presumem ano. |

### 5.5 Estoque

| ID | Regra |
| --- | --- |
| RN-022 | Situação do item: **Baixo** quando o saldo é menor que o mínimo; **Crítico** quando existe limiar crítico e o saldo é menor ou igual a ele. O limiar é opcional e não pode superar o mínimo; sem limiar, o item é apenas Normal ou Baixo. |
| RN-023 | Uma saída nunca deixa saldo negativo; saldo insuficiente não grava histórico. Entradas e saídas têm quantidade positiva. |
| RN-024 | O ajuste informa o saldo contado (valor absoluto, não diferença), exige motivo e pode zerar o item. |
| RN-025 | Uma entrada com preço recalcula o custo médio ponderado: `(saldoAnterior × custoAnterior + quantidade × preço) / saldoNovo`. Entrada sem preço conserva o custo anterior. |
| RN-026 | A unidade de um item não muda depois que há saldo ou histórico (para outra unidade, cria-se outro item). A exclusão exige saldo zero e é lógica: o histórico permanece consultável. |
| RN-027 | Categorias de insumo: Herbicida, Fertilizante, Semente, Fungicida e Inseticida. Somente saídas podem ser vinculadas a uma atividade, que deve estar persistida na mesma propriedade. |

### 5.6 Ocorrências

| ID | Regra |
| --- | --- |
| RN-028 | Fluxo: `ABERTA` → `EM_ACOMPANHAMENTO` → `RESOLVIDA`, ou `ABERTA` → `RESOLVIDA` diretamente. Iniciar acompanhamento, publicar atualização e resolver exigem descrição. Ocorrência resolvida não é reaberta, editada nem recebe novas publicações. Tipos: Praga, Doença, Clima, Equipamento e Outro; prioridades: Baixa, Média e Alta. |
| RN-029 | A edição preserva os dados anteriores na timeline. A exclusão é lógica: retira o registro da lista padrão e preserva todos os eventos. |
| RN-030 | "Resolvidas no mês" usa a data `resolvidaEm`, atribuída pelo servidor no fuso de São Paulo, e não a data de abertura. |

### 5.7 Custos e orçamento

| ID | Regra |
| --- | --- |
| RN-031 | Categorias de custo: Insumos, Mão de Obra, Maquinário, Combustível e Outros. Custo sem talhão é da propriedade toda; com talhão e safra, o talhão deve pertencer à safra. |
| RN-032 | O custo de consumo vale quantidade × custo médio do insumo no momento da baixa, com o talhão e a safra da atividade. Há no máximo um custo por movimentação; ele não é editado nem excluído em Custos (muda pela movimentação). Insumo sem custo unitário não gera lançamento; atividade de safra finalizada gera custo só no talhão. |
| RN-033 | O custo por hectare usa a área do recorte (talhões da safra filtrada, o talhão filtrado ou todos os talhões da propriedade); sem área, o indicador fica indisponível. O custo por cultura usa a cultura da safra ou, sem safra, a do talhão; sem nenhuma, vai para "Sem vínculo com cultura". |
| RN-034 | O orçamento da safra vale de R$ 0,01 a R$ 1 bilhão, com alerta entre 50% e 100% (padrão 80%). O realizado é a soma de todos os custos da safra. Situação: `sem-orcamento`, `dentro`, `proximo` (atingiu o percentual de alerta, inclusive 100% exato) ou `acima` (realizado maior que o planejado). Safra sem orçamento não recebe meta presumida. |

### 5.8 Notificações e relatórios

| ID | Regra |
| --- | --- |
| RN-035 | Atividade atrasada é a não concluída nem cancelada com data anterior a hoje; atividade do dia tem data igual a hoje; ocorrência gera alerta enquanto o status for diferente de `RESOLVIDA`; estoque gera alerta quando o item está Baixo ou Crítico. Preferência desligada remove a família de alertas. |
| RN-036 | Relatórios usam período inclusivo e a propriedade da sessão. Registros com data ausente, inválida ou sem ano aparecem como pendências e não entram nos totais. Falha em qualquer fonte interrompe o relatório (não é exibido zero fictício). O CSV contém o mesmo conjunto da tela. |
| RN-037 | No relatório de atividades, "atrasada" é qualquer atividade não concluída nem cancelada com data planejada anterior ao dia da consulta; pode coincidir com pendente ou em andamento. |

### 5.9 Extensões

| ID | Regra |
| --- | --- |
| RN-038 | Colheita só é registrada da etapa Plantio em diante, em talhão da safra, com quantidade mínima de 0,001 e unidade `kg`, `t` (1.000 kg) ou `sc` (60 kg); a data não pode ser anterior ao início da safra nem futura. A produtividade fica nula sem área, sem colheita ou quando há filtro de período. |
| RN-039 | Intervenção de manutenção sem valor não gera custo; com valor, gera um custo em Maquinário corrigido somente pela intervenção. Intervenção ligada a uma preventiva a marca como realizada e, se houver intervalo, programa a próxima. Equipamento não é excluído; sai de uso com status Inativo. |
| RN-040 | A localização do talhão é um ponto (1 vértice) ou um polígono de 3 a 50 vértices, sem lados cruzados e com área maior que zero; a área calculada é informativa e não substitui os hectares cadastrados. A posição de uma ocorrência só é aceita dentro do polígono do talhão, e o polígono não pode ser alterado ou removido deixando ocorrência marcada fora dele. |

---

## 6. Restrições técnicas

Fontes: [README — Tecnologias](../README.md#-tecnologias-utilizadas),
[backend](../backend/README.md), [servidor Java](servidor-java.md),
[ServerSocket](../backend/serversocket/README.md), [MongoDB Atlas](mongodb-atlas.md).

```text
Navegador ──HTTP (mesma origem)──> API Java (8080) ──driver oficial──> MongoDB 8 (replica set)
                                        │
                                        └──TCP + objetos serializados──> ServerSocket de algoritmos (3000)
```

| ID | Restrição |
| --- | --- |
| RT-001 | O servidor é Java 21 puro, com HTTP implementado manualmente sobre `ServerSocket`/`Socket`; não usa Spring, `HttpServer`, Netty nem Maven na API. A única biblioteca externa da API é o driver oficial do MongoDB 5.13.0, versionado em [`backend/api/lib/`](../backend/api/lib/). |
| RT-002 | A arquitetura segue o padrão Cliente–Servidor da disciplina: a API HTTP (porta 8080) delega validações e cálculos ao ServerSocket de algoritmos (porta 3000, TCP com objetos serializados). Sem o ServerSocket, as rotas delegadas respondem 503. |
| RT-003 | O frontend é HTML, CSS e JavaScript estáticos, sem framework SPA, servido pela própria API na mesma origem. A fonte Geist é local. |
| RT-004 | A persistência usa MongoDB 8 em replica set, exigido pelas transações (Docker Compose local ou Atlas). O modo `--memoria` serve apenas para testes e perde os dados ao encerrar. |
| RT-005 | Sessões e coordenação de contexto ficam na memória de um único processo: reiniciar a API encerra todas as sessões e não há suporte a várias instâncias compartilhando sessões. |
| RT-006 | O servidor escuta em `127.0.0.1` por padrão, não implementa TLS (HTTPS depende de proxy), não suporta `keep-alive` nem `Transfer-Encoding: chunked` (501) e atende uma requisição por conexão. |
| RT-007 | Configuração por variáveis de ambiente (`SERVIDOR_PORT`, `MONGODB_URI`, `SMTP_*` e outras). Credenciais não entram no Git; o arquivo `.env` não é carregado automaticamente. |
| RT-008 | O envio de e-mail usa SMTP com TLS implícito (porta 465 por padrão). Sem SMTP configurado, os códigos são impressos no console (modo de desenvolvimento). |
| RT-009 | Os testes de navegador usam Node.js 22+ e Playwright com Chromium; os testes do ServerSocket usam JUnit 5. |
| RT-010 | `backend/core` (Maven) valida o modelo de domínio e o Atlas, e `backend/gateway` é um proxy opcional; nenhum dos dois é usado diretamente pela interface. |
| RT-011 | Identificadores, comentários e documentação em português, com identificação de autoria nos arquivos, conforme o padrão do repositório. |
| RT-012 | Contexto acadêmico: equipe de cinco integrantes e cerca de dois meses de desenvolvimento ([Definição do MVP](AgroGestao-Definicao-MVP.docx)). |

---

## 7. Critérios de aceite

### 7.1 Critérios gerais

Os critérios abaixo valem para todos os requisitos funcionais, além do critério
específico de cada um:

1. **Confirmação pelo servidor:** a interface só anuncia sucesso depois de uma
   resposta 2xx da API (RNF-009).
2. **Erros previsíveis:** dados inválidos respondem 400 com o campo em
   `fields`; conflito de estado ou unicidade responde 409; recurso de outra
   propriedade responde 404; ausência de sessão responde 401.
3. **Persistência:** o registro continua disponível após recarregar a página
   e após reiniciar o processo Java com MongoDB (RNF-014).
4. **Isolamento:** um segundo usuário ou outra propriedade não vê nem altera o
   registro (RNF-005).
5. **Dados reais:** nenhuma tela exibe registros ou totais fictícios (RNF-010).

### 7.2 Fluxos centrais do MVP

A [Definição do MVP](AgroGestao-Definicao-MVP.docx) considera o MVP
bem-sucedido se o produtor executar de ponta a ponta os dois fluxos abaixo.

**Fluxo 1 — ciclo produtivo**

| Etapa | Ação do produtor | Requisitos | Como a entrega atende |
| --- | --- | --- | --- |
| 1 | Cadastrar propriedade | RF-001, RF-007 | A primeira propriedade nasce com a conta; outras são cadastradas em Configurações. |
| 2 | Cadastrar talhão | RF-011, RF-012 | Cultura incluída no catálogo e talhão criado em Áreas de Cultivo. |
| 3 | Cadastrar safra | RF-016 | Safra criada com cultura, período e talhões. |
| 4 | Planejar atividade | RF-020 | Atividade vinculada ao talhão e à safra. |
| 5 | Executar atividade | RF-022 | Agendada → Em andamento → Concluída, com histórico. |
| 6 | Registrar consumo de insumo | RF-029 | Saída do insumo vinculada à atividade (não automática na conclusão, RN-020). |
| 7 | Atualizar estoque | RF-028, RF-031 | Saldo e situação do item atualizados pela saída. |
| 8 | Registrar custo | RF-040 | Custo de consumo gerado automaticamente com talhão e safra da atividade. |
| 9 | Atualizar histórico | RF-014, RF-019, RF-024 | Eventos no histórico da atividade, do talhão e do ciclo da safra. |
| 10 | Visualizar no Painel | RF-041 | Indicadores de pendências, estoque baixo e custos recalculados pela API. |

**Fluxo 2 — gestão de ocorrências**

| Etapa | Ação do produtor | Requisitos | Como a entrega atende |
| --- | --- | --- | --- |
| 1 | Identificar problema | — | Ação de campo, fora do sistema. |
| 2 | Registrar ocorrência | RF-032, RF-036 | Ocorrência aberta com talhão, prioridade e foto opcional. |
| 3 | Acompanhar ocorrência | RF-033, RF-043 | Eventos de acompanhamento na timeline e alerta no Painel. |
| 4 | Resolver problema | RF-033 | Resolução com desfecho obrigatório; a ocorrência sai dos alertas ativos. |
| 5 | Manter histórico | RF-034, RF-035 | Timeline preservada após a resolução e a exclusão lógica; resumo mensal por data de resolução. |

### 7.3 Verificação da entrega

Critérios de verificação da [Definição do MVP](AgroGestao-Definicao-MVP.docx),
com os requisitos que os sustentam:

| Critério | Requisitos |
| --- | --- |
| Os dois fluxos rodam com dados reais inseridos pelo usuário, sem dados fixos no código. | RNF-010, RF-011 (catálogo começa vazio) |
| Cada etapa reflete no módulo seguinte: a baixa de insumo altera o estoque e o custo aparece no total da safra. | RF-028, RF-040, RF-039, RF-057 |
| O Painel apresenta os números resultantes das operações feitas nos demais módulos. | RF-041, RF-043, RF-044 |
| Todo registro criado permanece consultável no histórico depois de concluído ou resolvido. | RF-024, RF-030, RF-034, RN-018, RN-029 |

### 7.4 Como reproduzir a verificação

Na raiz do repositório, com JDK 21 (detalhes e variáveis em cada documento de
módulo):

```bash
sh backend/serversocket/testar.sh          # JUnit: algoritmos e protocolo do ServerSocket
bash backend/api/testar.sh                 # suítes HTTP em memória (TesteServidor e suítes por módulo)
docker compose up -d --wait mongo          # MongoDB local em replica set (opcional)
node tests/atividades-reinicio.cjs         # persistência após reinício com MongoDB
npm install --no-save playwright@1.49.1
npx playwright install chromium --with-deps
PLAYWRIGHT_MODULE=playwright node tests/atividades-real.cjs   # e demais tests/*-real.cjs
```

As mesmas suítes rodam nos workflows de
[`.github/workflows/`](../.github/workflows/), um por módulo.

---

## 8. Critérios específicos

### 8.1 Convenções adotadas

| Item | Convenção |
| --- | --- |
| Identificadores | `RF-nnn`, `RNF-nnn`, `RN-nnn` e `RT-nnn`, numerados em sequência e estáveis entre versões; um item removido não tem o número reaproveitado. |
| Prioridade | Alta, Média ou Baixa, derivadas da classificação essencial, complementar e extensão ([§3](#3-requisitos-funcionais)). |
| Critério de aceite | Resultado observável e conferível: código HTTP, campo da resposta, conteúdo de tela ou de CSV. |
| Evidência | Teste automatizado ou workflow existente no repositório, citado por link relativo. |
| Precedência | Em divergência entre documentos, prevalece o comportamento do código na base indicada no cabeçalho ([§8.4](#84-divergências-identificadas-na-consolidação)). |

### 8.2 Matriz de rastreabilidade

| Módulo | Requisitos funcionais | Regras | Contrato e documentação | Código principal | Evidência automatizada |
| --- | --- | --- | --- | --- | --- |
| Autenticação | RF-001 a RF-006 | RN-001 a RN-005 | [API](../backend/api/README.md#autenticação) | `AuthHandler`, `ValidadorDeConta` | `TesteServidor`, `login-real.cjs` |
| Propriedades | RF-007 a RF-010 | RN-006 a RN-009 | [API](../backend/api/README.md#propriedades-e-contexto-autenticado) | `PropriedadesHandler` | `TesteServidor`, `TesteMongo` |
| Talhões e culturas | RF-011 a RF-015 | RN-010, RN-011 | [API](../backend/api/README.md#talhões) | `TalhoesHandler`, `CulturasHandler`, `ValidadorDeTalhao` | `TesteServidor`, `talhoes-real.cjs` |
| Safras | RF-016 a RF-019 | RN-012 a RN-017 | [safras.md](safras.md) | `SafrasHandler` | `TesteServidor`, `cultivo-plantio-audit.cjs` |
| Atividades | RF-020 a RF-026 | RN-018 a RN-021 | [atividades.md](atividades.md), [calendario.md](calendario.md) | `AtividadesHandler`, `CalendarioHandler` | `atividades-real.cjs`, `calendario-real.cjs` |
| Estoque | RF-027 a RF-031 | RN-022 a RN-027 | [estoque.md](estoque.md) | `EstoqueHandler`, `CalculadoraDeEstoque` | `TesteEstoqueMongo`, `estoque-real.cjs` |
| Ocorrências | RF-032 a RF-036 | RN-028 a RN-030 | [ocorrencias.md](ocorrencias.md), [fotos-evidencia.md](fotos-evidencia.md) | `OcorrenciasHandler`, `FotoEvidencia` | `TesteOcorrencias`, `ocorrencias-real.cjs` |
| Custos | RF-037 a RF-040 | RN-031 a RN-033 | [custos.md](custos.md) | `CustosHandler`, `CustosDeConsumo` | `TesteServidor`, `custos-real.cjs` |
| Painel | RF-041 a RF-044 | RN-035 | [revisao-dados-reais.md](revisao-dados-reais.md) | `dashboard.js` | `login-real.cjs`, `todas-telas-audit.cjs` |
| Notificações | RF-045 a RF-047 | RN-035 | [notificacoes.md](notificacoes.md) | `notificacoes.js` | `notificacoes-real.cjs` |
| Perfil | RF-048 a RF-052 | RN-001, RN-004 a RN-006 | [perfil.md](perfil.md) | `PerfilHandler` | `TestePerfil`, `perfil-real.cjs` |
| Relatórios | RF-053 a RF-056 | RN-036, RN-037 | [relatorio-safra.md](relatorio-safra.md) e demais relatórios | `Relatorio*Handler`, `ConsolidadorDeSafra` | `TesteRelatorioSafra`, `relatorio-safra-real.cjs` |
| Extensões | RF-057 a RF-060 | RN-034, RN-038 a RN-040 | [orcamento.md](orcamento.md), [colheita.md](colheita.md), [georreferenciamento.md](georreferenciamento.md), [manutencao.md](manutencao.md) | `OrcamentosHandler`, `ColheitasHandler`, `GeorreferenciamentoHandler`, `ManutencaoHandler` | `TesteOrcamentos`, `TesteColheitas`, `TesteGeorreferenciamento`, `TesteManutencao` |

Os handlers ficam em [`backend/api/src/handlers/`](../backend/api/src/handlers/),
os algoritmos em [`backend/serversocket/src/algoritmos/`](../backend/serversocket/src/algoritmos/),
os scripts de tela em [`frontend/scripts/`](../frontend/scripts/) e os testes em
[`backend/api/teste/`](../backend/api/teste/), [`backend/serversocket/src/test/java/`](../backend/serversocket/src/test/java/)
e [`tests/`](../tests/).

### 8.3 Conferência desta entrega

| Critério da issue | Situação |
| --- | --- |
| Sumário com as oito seções obrigatórias | Atendido ([Sumário](#sumário)). |
| Cada requisito com identificador `RF`, `RNF` ou `RN` | Atendido: 60 RF, 28 RNF e 40 RN; restrições como `RT`. |
| Cada requisito funcional com prioridade e critério de aceite verificável | Atendido nas tabelas da [§3](#3-requisitos-funcionais). |
| Não escopo explícito, sem prometer recursos futuros | Atendido na [§2.2](#22-não-escopo). |
| Módulos da issue cobertos (autenticação, propriedades, talhões, safras, atividades, estoque, ocorrências, custos, dashboard, notificações, perfil e relatórios) | Atendido nas seções [3.1](#31-autenticação-e-sessão) a [3.12](#312-relatórios). |
| Links relativos para documentos e evidências | Atendido em cada módulo e na [§8.2](#82-matriz-de-rastreabilidade). |
| Sem funcionalidades, métricas, APIs ou resultados inventados | Rotas, campos e limites conferidos nos handlers; valores numéricos de exemplo vêm dos documentos de módulo citados. |

### 8.4 Divergências identificadas na consolidação

Na consolidação, alguns documentos de origem descrevem um estado anterior do
código. Nestes casos, este documento segue o código.

| Documento de origem | O que diz | Comportamento atual adotado |
| --- | --- | --- |
| [`backend/api/README.md`](../backend/api/README.md#talhões) (Talhões e Safras) e [`safras.md`](safras.md) | Cultura restrita a Soja, Milho, Trigo, Café ou Algodão (`TalhoesHandler.CULTURAS`). | Catálogo de culturas por propriedade, vazio no início ([`CulturasHandler.java`](../backend/api/src/handlers/CulturasHandler.java)); RF-011 e RN-010. |
| [`backend/api/README.md`](../backend/api/README.md#fora-desta-entrega) (Fora desta entrega) | Atividades, Insumos, Ocorrências, Dashboard, Notificações e Configurações fora da API. | Todos esses módulos têm rotas na mesma API (RF-020 a RF-052). |
| [`custos.md`](custos.md#o-que-continua-no-navegador) | Orçamento previsto guardado no navegador. | Orçamento por safra persistido em `/api/orcamentos` ([orcamento.md](orcamento.md)); RF-057. |
| [`backend/serversocket/README.md`](../backend/serversocket/README.md#decisões-e-limites) | Lista fixa de safras válidas (`2024/25`, `2025/26`, `2026/27`). | Qualquer período `AAAA/AA` consecutivo entre 1900 e 2100; RN-009. |
| [`persistencia-local.md`](persistencia-local.md) | Dados de demonstração carregados em `operational-defaults.js`. | Coleções iniciais vazias ([revisao-dados-reais.md](revisao-dados-reais.md)); RNF-010. |
| [`servidor-java.md`](servidor-java.md#12-pendências-e-limitações-conhecidas) §12 (17/10/2026) | Demais módulos pendentes. | Registro histórico; os módulos estão implementados. |
| [`AgroGestao_Projeto.md`](AgroGestao_Projeto.md#142-complementar) §14.2 | Complementares (notificações, calendário, relatórios, fotos, perfil) não marcados. | Implementados, exceto a foto do perfil (RF-026, RF-036, RF-045 a RF-056). |
| [`AgroGestao_Projeto.md`](AgroGestao_Projeto.md#6-conceito-central-do-sistema) §6 e §12.3 | Concluir a atividade baixa o estoque e lança o custo. | O consumo é uma saída de estoque vinculada à atividade, que gera o custo; a conclusão não movimenta estoque (RN-020). |
| Documento de produto e Lista de Funcionalidades | Vocabulários de tipos e categorias (ex.: atividades "plantio, adubação, pulverização, inspeção"; insumos "sementes, defensivos, combustível"; custo "manutenção"). | Listas validadas pela API: RN-019, RN-027, RN-028 e RN-031 (ex.: custo de manutenção na categoria Maquinário). |
| Documento de produto §8.5 | Status Agendada → Em andamento → Concluída. | Inclui o estado inicial `Pendente`, que precisa ser agendado antes de iniciar (RN-018). |

Também na data desta versão (12/11/2026), alguns testes automatizados ainda
esperam regras anteriores às listadas acima: em
[`TesteGeorreferenciamento.java`](../backend/api/teste/TesteGeorreferenciamento.java),
duas verificações editam um talhão para a cultura "Milho" sem incluí-la no
catálogo (RN-010); em
[`ValidadorDeTalhaoTest.java`](../backend/serversocket/src/test/java/algoritmos/ValidadorDeTalhaoTest.java),
[`AtendenteAlgoritmosTest.java`](../backend/serversocket/src/test/java/AtendenteAlgoritmosTest.java)
e [`ProtocoloTest.java`](../backend/serversocket/src/test/java/ProtocoloTest.java),
quatro casos esperam que o validador de talhão recuse a cultura "Banana" ou o
período `1999/00`, como fazia com as antigas listas fixas (RN-009, RN-010). A
atualização desses testes não faz parte desta entrega de documentação.
