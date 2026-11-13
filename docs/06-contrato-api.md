# Contrato da API do AgroGestão

Catálogo único dos endpoints HTTP usados pelo sistema. Ele consolida o
[README da API](../backend/api/README.md) e os documentos de cada módulo, e foi
conferido contra o código dos handlers (`backend/api/src/handlers`) e contra o
servidor em modo memória. Os exemplos de request e response abaixo foram
capturados de uma execução real do servidor (`Servidor 18998 --memoria`), com
os identificadores e o token encurtados quando necessário.

Documentos relacionados: [casos de uso](03-casos-de-uso.md),
[manual do usuário](15-manual-do-usuario.md) e
[relatório de testes](10-relatorio-de-testes.md).

## 1. Convenções da API

- **Base**: o mesmo servidor entrega a API e o frontend. Em desenvolvimento, `http://127.0.0.1:8080` (porta configurável por `SERVIDOR_PORT` ou argumento).
- **Formato**: JSON em UTF-8 (`Content-Type: application/json; charset=utf-8`). Toda resposta usa `Connection: close` e `X-Content-Type-Options: nosniff`.
- **Identificadores**: strings hexadecimais de 24 caracteres (`"id": "6af6538fbd38b45bf61637d5"`).
- **Datas**: `AAAA-MM-DD` nos campos de data. Instantes (`criadoEm`, `atualizadoEm`) em ISO 8601 UTC. Cálculos por mês ou dia usam o fuso `America/Sao_Paulo`.
- **Dinheiro**: nos custos e orçamentos o servidor devolve valores em reais como número (`"value": 3100.0`) e guarda internamente em centavos. Os relatórios trazem `totalCentavos`.
- **Escopo**: todo dado pertence ao usuário autenticado e à **propriedade ativa da sessão**. Um registro de outra propriedade responde 404, como se não existisse.
- **Sem paginação**: as listagens devolvem todos os registros da propriedade ativa, filtrados pelos parâmetros de consulta.
- **Parâmetros de consulta**: opcionais e combináveis. Filtros de texto não diferenciam maiúsculas; `todos` ou `todas` equivale a sem filtro.
- **Cache**: respostas com dados do usuário não são armazenadas (`Cache-Control: private, no-store`).
- **CORS**: liberado somente para `http://localhost:*` e `http://127.0.0.1:*`. `OPTIONS` responde 204 com `Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS`.
- **Concorrência otimista**: ocorrências e atividades carregam `versao`. Escritas devem enviar a versão lida; versão antiga resulta em 409.

## 2. Autenticação Bearer

1. `POST /api/auth/login` devolve `token` (UUID) e `usuario`.
2. Toda rota autenticada exige o cabeçalho `Authorization: Bearer <token>`.
3. O token vale **12 horas** e fica em memória do servidor: reiniciar o servidor encerra todas as sessões. Cada conta mantém no máximo **5 sessões** simultâneas.
4. `POST /api/auth/logout` encerra o token. Trocar a senha, redefinir a senha ou confirmar novo e-mail encerra todas as sessões do usuário.
5. O frontend guarda o token no `sessionStorage` e valida a sessão em `GET /api/me` a cada tela.
6. Rotas públicas: `/api/auth/*`, `/health`, `/ready` e `/actuator/health`. Todas as demais rotas `/api/**`, inclusive `/api/me`, exigem o token.
7. Rotas públicas sensíveis têm limite de **60 tentativas por IP por minuto** (429).

```http
GET /api/me HTTP/1.1
Authorization: Bearer 7e175fe1-48e3-45c7-9853-75ca68e6616f
```

Sem token ou com token inválido, a resposta é sempre:

```json
401  {"error": "Não autorizado."}
```

## 3. Formato de requisições e respostas

- Requisições com corpo usam `Content-Type: application/json`. Os nomes dos campos seguem os `name` dos formulários do frontend (`name`, `email`, `password`, `description`, `category`, `value` e `date` em autenticação e custos; `nome`, `cultura`, `hectares` e similares nos demais módulos).
- Sucesso devolve o recurso (200 ou 201), uma lista, ou 204 sem corpo.
- Erro simples: `{"error": "mensagem"}`.
- Erro de validação ou conflito, com os campos afetados:

```json
400  {"error": "Dados inválidos.", "fields": {"nome": "Informe o nome do talhão.", "hectares": "Informe um valor de hectares maior que zero."}}
409  {"error": "Já existe um talhão com esse nome.", "fields": {"nome": "Já existe um talhão com esse nome."}}
```

## 4. Códigos HTTP e erros

| Código | Quando acontece |
|---|---|
| 200 | Consulta ou alteração concluída. |
| 201 | Recurso criado. |
| 204 | Operação sem corpo (logout, exclusões). |
| 400 | Dado inválido (com `fields` quando há campo), JSON inválido, período inválido. |
| 401 | Token ausente, inválido, expirado ou encerrado; credenciais erradas no login. |
| 403 | Login com e-mail ainda não confirmado. |
| 404 | Rota inexistente, ou registro inexistente ou de outra propriedade. |
| 405 | Método não permitido para a rota. |
| 408 | Leitura da requisição passou de 10 s. |
| 409 | Conflito de regra: duplicidade, vínculo, versão antiga, transição inválida, saldo insuficiente, safra finalizada, propriedade que mudou durante a consulta. |
| 413 | Corpo acima do limite. |
| 429 | Limite de tentativas ou intervalo de reenvio (cabeçalho `Retry-After`). |
| 431 | Cabeçalhos acima de 16 KB. |
| 500 | Erro interno. A resposta é `{"error": "Erro interno do servidor."}` e o servidor continua no ar. |
| 501 | `Transfer-Encoding` não suportado (o corpo é lido por `Content-Length`). |
| 503 | Servidor de algoritmos indisponível nas rotas delegadas, limite de 100 conexões simultâneas, ou `/ready` com MongoDB fora. |

**Limites**: corpo até **1 MiB** (413); em URLs terminadas em `/foto`, até **8 MiB**. Foto decodificada até **5 MiB**, em JPEG, PNG, WebP ou GIF. Cabeçalhos até 16 KB. Timeout de leitura de 10 s. Estáticos até 2 MB.

**Regra geral das tabelas**: toda rota autenticada pode responder 401. Toda rota com corpo ou filtros validados pode responder 400. Toda rota com `{id}` pode responder 404. As tabelas abaixo listam, além disso, os códigos específicos de cada rota.

## 5. Endpoints por módulo

Legenda de autenticação: **Pública** = sem token; **Bearer** = exige `Authorization: Bearer <token>`.

### 5.1 Autenticação e sessão

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| POST | `/api/auth/register` | Pública | Cria usuário (e-mail não confirmado) e a propriedade inicial. Validação delegada ao servidor de algoritmos. | 201 · 400 · 409 e-mail já cadastrado · 429 |
| POST | `/api/auth/login` | Pública | Abre sessão. | 200 `{token, usuario}` · 400 · 401 genérico · 403 e-mail não confirmado · 429 |
| POST | `/api/auth/logout` | Pública | Encerra o token enviado no cabeçalho. | 204 |
| POST | `/api/auth/confirm-email` | Pública | Confirma o e-mail com o código de 6 dígitos enviado (campo `token`). | 200 · 400 código inválido ou expirado · 429 |
| POST | `/api/auth/resend-confirmation` | Pública | Reenvia o código. Intervalo mínimo de 30 s. | 200 · 429 com `Retry-After` |
| POST | `/api/auth/forgot-password` | Pública | Envia o código de redefinição. Sempre 200, sem revelar se o e-mail existe. | 200 · 429 |
| POST | `/api/auth/reset-password` | Pública | Define nova senha com `token` (código de 6 dígitos, válido por 30 min), `password` e `password-confirm`. Encerra as sessões do usuário. | 200 · 400 · 429 |
| GET | `/api/me` | Bearer | Usuário e propriedade ativa da sessão. | 200 · 401 |

Regras do cadastro: e-mail válido e único (sem diferenciar maiúsculas); telefone `(DD) 9XXXX-XXXX`; senha com 8 ou mais caracteres, 1 maiúscula, 1 número e 1 símbolo; confirmação igual (`password-confirm` ou `confirm`); `terms` igual a `true`.

**Exemplo: cadastro**

```http
POST /api/auth/register
Content-Type: application/json

{"name":"Ana Souza","email":"ana@agro.com","phone":"(11) 91234-5678","password":"Senha@123","password-confirm":"Senha@123","farm":"Sitio Sol","area":"80 ha","terms":true}
```

```json
201  {"usuario": {"id": "6af65380bd38b45bf61637d3", "name": "Ana Souza", "email": "ana@agro.com", "phone": "(11) 91234-5678", "emailConfirmado": false, "emailPendente": null, "propriedadeId": "6af65380bd38b45bf61637d2", "propriedadePadraoId": "6af65380bd38b45bf61637d2", "onboardingConcluido": false}}
```

**Exemplo: login com e-mail não confirmado e depois confirmado**

```json
403  {"error": "Confirme o e-mail antes de entrar."}
```

```http
POST /api/auth/confirm-email
{"token":"504030"}
```

```json
200  {"message": "E-mail confirmado."}
```

```http
POST /api/auth/login
{"email":"ana@agro.com","password":"Senha@123"}
```

```json
200  {"token": "7e175fe1-48e3-45c7-9853-75ca68e6616f", "usuario": {"id": "6af65380bd38b45bf61637d3", "name": "Ana Souza", "email": "ana@agro.com", "emailConfirmado": true, "onboardingConcluido": false}}
401  {"error": "E-mail ou senha inválidos."}
```

### 5.2 Perfil e segurança

Detalhes e testes em [perfil.md](perfil.md). O usuário vem sempre da sessão.

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/perfil` | Bearer | `usuario` e `preferencias` da conta. | 200 |
| PUT | `/api/perfil` | Bearer | Altera `name`, `email` e `phone`. Trocar e-mail exige `senhaAtual` e deixa o novo endereço pendente. | 200 · 400 · 409 · 429 |
| PUT | `/api/perfil/preferencias` | Bearer | Grava os booleanos `stock`, `weather`, `activities`, `occurrences`, `reports`. | 200 · 400 · 409 |
| PUT | `/api/perfil/propriedade-padrao` | Bearer | Define a propriedade usada no próximo login (`propriedadeId`). | 200 · 400 · 404 |
| PUT | `/api/perfil/senha` | Bearer | Troca a senha (`current`, `new`, `confirm`). Encerra todas as sessões. | 200 · 400 · 409 · 429 |
| POST | `/api/perfil/email/confirmar` | Bearer | Confirma o novo e-mail com `codigo`. Encerra todas as sessões. | 200 · 400 · 409 |
| POST | `/api/perfil/email/reenviar` | Bearer | Renova o código do e-mail pendente (intervalo de 30 s). | 200 · 400 · 409 · 429 |
| POST | `/api/perfil/onboarding` | Bearer | Marca o tour inicial como concluído. | 200 · 409 |

**Exemplo**

```json
GET /api/perfil
200  {"usuario": {"id": "6af65380bd38b45bf61637d3", "name": "Ana Souza", "email": "ana@agro.com", "phone": "(11) 91234-5678", "emailConfirmado": true, "emailPendente": null, "onboardingConcluido": false}, "preferencias": {"stock": true, "weather": true, "activities": true, "occurrences": true, "reports": false}}
```

### 5.3 Propriedades e culturas

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/propriedades` | Bearer | Propriedades do usuário, com `selecionada` na ativa. | 200 |
| GET | `/api/propriedades/periodos-safra` | Bearer | Períodos `AAAA/AA` em uso na propriedade ativa. | 200 |
| POST | `/api/propriedades` | Bearer | Cria propriedade (`nome`, `localizacao`, `municipio`, `areaHectares`, `safraAtual`). | 201 · 400 · 409 |
| GET | `/api/propriedades/{id}` | Bearer | Consulta uma propriedade. | 200 · 404 |
| PUT | `/api/propriedades/{id}` | Bearer | Atualiza. | 200 · 400 · 404 |
| DELETE | `/api/propriedades/{id}` | Bearer | Exclui. Recusa a última propriedade, uma com talhões, custos, estoque ou histórico, ou em uso em outra sessão. | 204 · 404 · 409 |
| POST | `/api/propriedades/{id}/selecionar` | Bearer | Torna a propriedade ativa para o token atual. | 200 · 404 |
| GET | `/api/culturas` | Bearer | Catálogo de culturas da propriedade. | 200 |
| POST | `/api/culturas` | Bearer | Cadastra cultura (`nome`). | 201 · 400 · 409 |
| DELETE | `/api/culturas/{id}` | Bearer | Remove cultura. | 204 · 404 · 409 em uso |

**Exemplo**

```http
POST /api/propriedades
{"nome":"Fazenda Boa Vista","localizacao":"Campinas, SP","municipio":"Campinas","areaHectares":120,"safraAtual":"2026/27"}
```

```json
201  {"id": "6af6538fbd38b45bf61637d6", "nome": "Fazenda Boa Vista", "localizacao": "Campinas, SP", "municipio": "Campinas", "safraAtual": "2026/27", "areaHectares": 120.0, "atualizadoEm": "2026-11-13T00:02:23.087849929Z", "criadaEm": "2026-11-13T00:02:23.087849929Z"}
```

### 5.4 Talhões e georreferenciamento

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/talhoes?cultura=&q=` | Bearer | Lista os talhões da propriedade ativa. | 200 |
| POST | `/api/talhoes` | Bearer | Cria talhão. A cultura precisa estar no catálogo. Validação delegada ao servidor de algoritmos. | 201 · 400 · 409 nome repetido · 503 |
| PUT | `/api/talhoes/{id}` | Bearer | Edita. Preserva a localização. | 200 · 400 · 404 · 409 · 503 |
| GET | `/api/talhoes/resumo` | Bearer | `totalHectares`, `quantidadeTotal`, `quantidadeAtivos`. | 200 · 503 |
| GET | `/api/talhoes/{id}/historico` | Bearer | Eventos do talhão (atividades, ocorrências e custos). | 200 · 404 |
| POST | `/api/talhoes/{id}/historico` | Bearer | Registra evento no histórico. | 201 · 404 |
| PUT | `/api/talhoes/{id}/localizacao` | Bearer | Grava ponto ou polígono (`tipo`, `pontos`). | 200 · 400 · 404 · 409 |
| DELETE | `/api/talhoes/{id}/localizacao` | Bearer | Remove a localização. | 204 · 404 · 409 há ocorrência marcada |
| GET | `/api/mapa?cultura=&ocorrencias=abertas\|todas` | Bearer | Talhões desenhados e ocorrências no mapa. | 200 · 400 · 503 |

Campos do talhão: `nome` (até 80, único por propriedade sem diferenciar maiúsculas), `cultura` (do catálogo), `hectares` (maior que 0), `status` (Ativo, Irrigação pendente ou Em preparo; padrão Ativo) e `safra` no formato `AAAA/AA` (padrão: a atual). Não há `DELETE` de talhão. Polígono: de 3 a 50 vértices, sem lados cruzados. Detalhes em [georreferenciamento.md](georreferenciamento.md).

**Exemplo**

```http
POST /api/talhoes
{"nome":"T-01 Norte","cultura":"Soja","hectares":124}
```

```json
201  {"id": "6af6538fbd38b45bf61637d5", "nome": "T-01 Norte", "cultura": "Soja", "safra": "2026/27", "hectares": 124.0, "status": "Ativo", "atualizadoEm": "2026-11-13T00:02:23.032929327Z", "criadoEm": "2026-11-13T00:02:23.032929327Z"}
400  {"error": "Dados inválidos.", "fields": {"cultura": "Selecione uma cultura cadastrada ou inclua uma nova em Culturas."}}
```

### 5.5 Safras e colheitas

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/safras?etapa=&cultura=&q=` | Bearer | Lista safras. | 200 |
| POST | `/api/safras` | Bearer | Cria safra em Planejamento. | 201 · 400 · 409 nome repetido ou talhão ocupado |
| GET | `/api/safras/{id}` | Bearer | Consulta. | 200 · 404 |
| PUT | `/api/safras/{id}` | Bearer | Edita. | 200 · 400 · 404 · 409 inclusive finalizada |
| DELETE | `/api/safras/{id}` | Bearer | Exclui, só em Planejamento. | 204 · 404 · 409 |
| POST | `/api/safras/{id}/avancar` | Bearer | Avança uma etapa. | 200 · 404 · 409 já finalizada |
| GET | `/api/safras/{id}/historico` | Bearer | Eventos dos talhões dentro do período, do mais recente ao mais antigo. | 200 · 404 |
| GET | `/api/colheitas?safraId=&talhaoId=&cultura=&de=&ate=` | Bearer | Lista colheitas. | 200 · 400 |
| GET | `/api/colheitas/resumo` | Bearer | Produção e produtividade (mesmos filtros). | 200 · 400 |
| GET | `/api/colheitas/comparativo?cultura=` | Bearer | Um item por safra. | 200 |
| POST | `/api/colheitas` | Bearer | Registra colheita. | 201 · 400 · 409 |
| GET | `/api/colheitas/{id}` | Bearer | Consulta. | 200 · 404 |
| PUT | `/api/colheitas/{id}` | Bearer | Edita. | 200 · 400 · 404 · 409 |
| DELETE | `/api/colheitas/{id}` | Bearer | Exclui. | 204 · 404 · 409 safra finalizada |

Campos da safra: `nome` (até 80, único), `cultura`, `dataInicio`, `dataFim` (término igual ou depois do início) e `talhoes` (ao menos um id). Etapas: Planejamento, Preparação, Plantio, Manejo, Colheita e Finalização, sem voltar. Safra finalizada só aceita consulta. Campos da colheita: `safraId`, `talhaoId`, `quantidade` (mínimo 0,001), `unidade` (`kg`, `t` ou `sc`), `data` e `observacao` opcional. Detalhes em [safras.md](safras.md) e [colheita.md](colheita.md).

**Exemplo**

```http
POST /api/safras
{"nome":"Soja 2026/27","cultura":"Soja","dataInicio":"2026-09-01","dataFim":"2027-03-31","talhoes":["6af6538fbd38b45bf61637d5"]}
```

```json
201  {"id": "6af6539bbd38b45bf61637d9", "nome": "Soja 2026/27", "cultura": "Soja", "talhoes": ["6af6538fbd38b45bf61637d5"], "dataInicio": "2026-09-01", "dataFim": "2027-03-31", "etapa": "Planejamento", "historicoEtapas": [{"etapa": "Planejamento", "em": "2026-11-13T00:02:35.330836093Z"}], "areaHectares": 124.0}
```

### 5.6 Atividades e calendário

Detalhes em [atividades.md](atividades.md) e [calendario.md](calendario.md).

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/atividades?status=&tipo=&talhao=&q=` | Bearer | Lista atividades. | 200 |
| POST | `/api/atividades` | Bearer | Cria atividade com status Pendente. | 201 · 400 |
| GET | `/api/atividades/{id}` | Bearer | Consulta. | 200 · 404 |
| PUT | `/api/atividades/{id}` | Bearer | Edita (com `versao`). | 200 · 400 · 404 · 409 |
| POST | `/api/atividades/{id}/status` | Bearer | Muda o status (`status`). Sequência Pendente, Agendada, Em andamento, Concluída. | 200 · 404 · 409 transição não permitida |
| POST | `/api/atividades/{id}/concluir` | Bearer | Conclui. | 200 · 404 · 409 |
| POST | `/api/atividades/{id}/cancelar` | Bearer | Cancela antes da conclusão. | 200 · 404 · 409 já concluída |
| GET | `/api/atividades/{id}/historico` | Bearer | Evento inicial e mudanças de status. | 200 · 404 |
| POST | `/api/atividades/{id}/foto` | Bearer | Envia foto (`versao`, `conteudoBase64`, `tipoMime`, `nomeArquivo`). | 200 · 400 · 404 · 409 |
| GET | `/api/atividades/{id}/foto` | Bearer | Recupera a imagem. | 200 · 404 |
| DELETE | `/api/atividades/{id}/foto` | Bearer | Remove a foto (`versao`). | 200 · 404 · 409 |
| GET | `/api/calendario?de=&ate=&status=&tipo=&talhao=&q=` | Bearer | Atividades por dia. `de` e `ate` obrigatórios, até 62 dias. | 200 · 400 |

Campos da atividade: `nome`, `tipo` (Aplicação, Irrigação, Colheita, Manutenção ou Manejo), `talhao` (nome do talhão), `data`, `horario`, `responsavel`, `prioridade` e `safraId` opcional (a safra precisa conter o talhão). Status possíveis: Pendente, Agendada, Em andamento, Concluída e Cancelada. Concluir não baixa estoque nem gera custo automaticamente.

**Exemplo**

```http
POST /api/atividades
{"nome":"Adubacao de cobertura","tipo":"Aplicação","talhao":"T-01 Norte","data":"2026-11-20","horario":"08:00","responsavel":"Carlos","prioridade":"Alta","safraId":"6af6539bbd38b45bf61637d9"}
```

```json
201  {"nome": "Adubacao de cobertura", "tipo": "Aplicação", "talhao": "T-01 Norte", "data": "2026-11-20", "status": "Pendente", "responsavel": "Carlos", "prioridade": "Alta", "horario": "08:00", "safraId": "6af6539bbd38b45bf61637d9", "safra": "Soja 2026/27", "cultura": "Soja", "id": "6af653a5bd38b45bf61637df", "versao": 1, "temFoto": false}
409  {"error": "Transição de status não permitida."}
```

### 5.7 Estoque (insumos)

Detalhes em [estoque.md](estoque.md).

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/insumos` | Bearer | Catálogo da propriedade ativa. | 200 |
| POST | `/api/insumos` | Bearer | Cadastra produto. O saldo inicial gera evento auditável. | 201 · 400 |
| GET | `/api/insumos/{id}` | Bearer | Consulta um item ativo. | 200 · 404 |
| PUT | `/api/insumos/{id}` | Bearer | Edita. | 200 · 400 · 404 |
| DELETE | `/api/insumos/{id}` | Bearer | Arquiva o item. Exige saldo zero. | 204 · 404 · 409 há saldo |
| GET | `/api/insumos/{id}/movimentacoes` | Bearer | Histórico do item. | 200 · 404 |
| POST | `/api/insumos/{id}/movimentacoes` | Bearer | Registra `entrada`, `saida` ou `ajuste`. Validação delegada ao servidor de algoritmos. | 201 · 400 · 404 · 409 saldo insuficiente · 503 |
| GET | `/api/insumos/resumo` | Bearer | Produtos, críticos, abaixo do mínimo, entradas do mês e valor em estoque. | 200 |
| GET | `/api/insumos/atividades` | Bearer | Atividades da propriedade que podem ser vinculadas a uma saída. | 200 |

Campos do produto: `nome`, `categoria`, `unidade`, `saldo`, `minimo`, `limiarCritico` opcional (não pode superar o mínimo) e `custoUnitario`. Status: Normal, Baixo (saldo abaixo do mínimo) ou Crítico (saldo menor ou igual ao limiar). O saldo nunca fica negativo. Saída com `atividadeId` gera um custo em Insumos.

**Exemplo**

```http
POST /api/insumos/6af6538fbd38b45bf61637d7/movimentacoes
{"tipo":"saida","quantidade":30}
```

```json
201  {"id": "6af6539bbd38b45bf61637da", "tipo": "saida", "quantidade": 30.0, "atividadeId": null, "saldoAnterior": 120.0, "saldoPosterior": 90.0, "unidade": "kg"}
409  {"error": "Saldo insuficiente.", "fields": {"quantidade": "A saída supera o saldo disponível."}}
```

### 5.8 Ocorrências

Detalhes em [ocorrencias.md](ocorrencias.md) e [fotos-evidencia.md](fotos-evidencia.md).

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/ocorrencias?talhaoId=&q=&incluirExcluidas=` | Bearer | Lista da propriedade ativa. | 200 · 400 |
| POST | `/api/ocorrencias` | Bearer | Cria, sempre com status inicial aberto. | 201 · 400 |
| GET | `/api/ocorrencias/{id}` | Bearer | Ficha, inclusive após exclusão lógica. | 200 · 404 |
| PUT | `/api/ocorrencias/{id}` | Bearer | Edita (com `versao`). | 200 · 400 · 404 · 409 |
| DELETE | `/api/ocorrencias/{id}` | Bearer | Exclusão lógica (`{"versao": N}`). | 200 · 404 · 409 |
| GET | `/api/ocorrencias/{id}/timeline` | Bearer | Eventos em ordem crescente. | 200 · 404 |
| POST | `/api/ocorrencias/{id}/timeline` | Bearer | Acrescenta evento (`versao`, `descricao`, `acao`: `acompanhamento`, `atualizacao` ou `resolucao`). | 200 · 400 · 404 · 409 |
| GET | `/api/ocorrencias/resumo?mes=AAAA-MM` | Bearer | Abertas, em acompanhamento, prioridade alta e resolvidas no mês. | 200 · 400 |
| POST | `/api/ocorrencias/{id}/foto` | Bearer | Envia foto. | 200 · 400 · 404 · 409 |
| GET | `/api/ocorrencias/{id}/foto` | Bearer | Recupera a imagem. | 200 · 404 |
| DELETE | `/api/ocorrencias/{id}/foto` | Bearer | Remove a foto (`versao`). | 200 · 404 · 409 |

Campos: `titulo`, `tipo`, `talhaoId` opcional, `data`, `prioridade`, `descricao`, `localizacao` opcional (`{lat, lng}`, só dentro do polígono do talhão) e `contextoPropriedadeId`, que o frontend envia para impedir gravação na propriedade errada. Status: `ABERTA`, `EM_ACOMPANHAMENTO` e resolvida. O mês do resumo usa `resolvidaEm`, atribuído pelo servidor.

**Exemplo**

```http
POST /api/ocorrencias
{"titulo":"Ferrugem nas folhas","tipo":"Praga","talhaoId":"6af6538fbd38b45bf61637d5","data":"2026-11-10","prioridade":"Alta","descricao":"Manchas na lateral norte"}
```

```json
201  {"titulo": "Ferrugem nas folhas", "tipo": "Praga", "prioridade": "Alta", "data": "2026-11-10", "talhaoNome": "T-01 Norte", "status": "ABERTA", "resolvidaEm": null, "excluida": false, "versao": 1, "temFoto": false, "id": "6af6539bbd38b45bf61637db"}
```

```json
POST /api/ocorrencias/{id}/timeline  {"versao":1,"acao":"acompanhamento","descricao":"Aplicado fungicida"}
200  {"status": "EM_ACOMPANHAMENTO", "versao": 2, "...": "..."}
```

### 5.9 Custos e orçamento

Detalhes em [custos.md](custos.md) e [orcamento.md](orcamento.md).

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/custos?category=&talhaoId=&safraId=&de=&ate=&q=` | Bearer | Lista, do mais recente ao mais antigo. | 200 · 400 |
| GET | `/api/custos/resumo` | Bearer | Indicadores dos lançamentos filtrados. | 200 · 400 |
| POST | `/api/custos` | Bearer | Cria lançamento. | 201 · 400 · 409 safra finalizada |
| GET | `/api/custos/{id}` | Bearer | Consulta. | 200 · 404 |
| PUT | `/api/custos/{id}` | Bearer | Edita. | 200 · 400 · 404 · 409 |
| DELETE | `/api/custos/{id}` | Bearer | Exclui. | 204 · 404 · 409 |
| POST | `/api/custos/sincronizar-consumo` | Bearer | Recupera custos de consumo de estoque pendentes (`{"criados": N}`). | 200 |
| GET | `/api/orcamentos` | Bearer | Planejado versus realizado de todas as safras. | 200 |
| GET | `/api/orcamentos/{safraId}` | Bearer | Item de uma safra. | 200 · 404 |
| PUT | `/api/orcamentos/{safraId}` | Bearer | Define `valor` e `alerta` (50 a 100). | 201 primeira definição · 200 · 400 · 404 · 409 |
| DELETE | `/api/orcamentos/{safraId}` | Bearer | Remove o orçamento. | 204 · 404 · 409 |

Campos do custo: `description` (até 120), `category` (Insumos, Mão de Obra, Maquinário, Combustível ou Outros), `value` (0,01 até 1 bilhão), `date`, `talhaoId` e `safraId` opcionais. Custo de safra finalizada, de consumo de estoque ou de manutenção não é alterado diretamente (409).

**Exemplo**

```http
POST /api/custos
{"description":"Diesel 500 L","category":"Combustível","value":3100,"date":"2026-11-05","talhaoId":"6af6538fbd38b45bf61637d5","safraId":"6af6539bbd38b45bf61637d9"}
```

```json
201  {"id": "6af6539bbd38b45bf61637dd", "description": "Diesel 500 L", "category": "Combustível", "value": 3100.0, "date": "2026-11-05", "talhaoNome": "T-01 Norte", "safraNome": "Soja 2026/27", "cultura": "Soja"}
```

```http
PUT /api/orcamentos/6af6539bbd38b45bf61637d9
{"valor":50000,"alerta":80}
```

```json
201  {"safraNome": "Soja 2026/27", "planejado": 50000.0, "alerta": 80, "realizado": 3100.0, "saldo": 46900.0, "desvio": -46900.0, "percentualUsado": 6.2, "situacao": "dentro", "planejadoPorHectare": 403.23, "realizadoPorHectare": 25.0}
```

### 5.10 Manutenção de equipamentos

Detalhes em [manutencao.md](manutencao.md).

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/equipamentos?status=&tipo=&q=` | Bearer | Lista com `proximaPreventiva` e `ultimaIntervencao`. | 200 |
| POST | `/api/equipamentos` | Bearer | Cria equipamento. | 201 · 400 · 409 nome repetido |
| GET | `/api/equipamentos/{id}` | Bearer | Consulta. | 200 · 404 |
| PUT | `/api/equipamentos/{id}` | Bearer | Edita. | 200 · 400 · 404 · 409 |
| GET | `/api/equipamentos/{id}/historico` | Bearer | Intervenções do equipamento. | 200 · 404 |
| GET | `/api/preventivas?equipamentoId=&de=&ate=&situacao=` | Bearer | Agenda de preventivas. | 200 · 400 · 404 |
| POST | `/api/preventivas` | Bearer | Programa preventiva. | 201 · 400 · 404 |
| PUT | `/api/preventivas/{id}` | Bearer | Edita ou cancela. | 200 · 400 · 404 · 409 já realizada |
| DELETE | `/api/preventivas/{id}` | Bearer | Exclui. | 204 · 404 · 409 já realizada |
| GET | `/api/intervencoes?equipamentoId=&tipo=&de=&ate=` | Bearer | Lista intervenções. | 200 · 400 · 404 |
| POST | `/api/intervencoes` | Bearer | Registra intervenção. Com `valor`, gera custo em Maquinário. | 201 · 400 · 404 · 409 safra finalizada |
| PUT | `/api/intervencoes/{id}` | Bearer | Edita e atualiza o custo ligado. | 200 · 400 · 404 · 409 |
| DELETE | `/api/intervencoes/{id}` | Bearer | Exclui junto com o custo. | 204 · 404 · 409 |
| GET | `/api/manutencao/resumo` | Bearer | Indicadores do módulo. | 200 |

### 5.11 Relatórios

Todos exigem Bearer e não são armazenados em cache. Uma falha de fonte interrompe o relatório, sem zero fictício.

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/api/relatorios/propriedade?de=&ate=` | Bearer | Consolidado da propriedade no período. | 200 · 400 período inválido · 409 propriedade mudou |
| GET | `/api/relatorios/atividades?de=&ate=` | Bearer | Atividades do período. | 200 · 400 · 409 |
| GET | `/api/relatorios/estoque?de=&ate=` | Bearer | Movimentação de estoque do período. | 200 · 400 · 409 |
| GET | `/api/relatorios/safras/{id}` | Bearer | Relatório do ciclo da safra. Cálculo delegado ao servidor de algoritmos. | 200 · 400 id inválido · 404 · 409 |

**Exemplo**

```http
GET /api/relatorios/propriedade?de=2026-11-01&ate=2026-11-30
```

```json
200  {"propriedade": {"id": "6af65380bd38b45bf61637d2", "nome": "Sitio Sol"}, "periodo": {"inicio": "2026-11-01", "fim": "2026-11-30"}, "atuais": {"talhoes": 1, "areaHectares": 124.0, "safras": 1, "produtos": 1}, "contagens": {"atividade": 0, "ocorrencia": 1, "custo": 1}, "totalCentavos": 310000, "registros": [{"tipo": "ocorrencia", "descricao": "Ferrugem nas folhas", "data": "2026-11-10"}]}
```

```http
GET /api/relatorios/propriedade?de=2026-11-30&ate=2026-11-01
```

```json
400  {"error": "Informe um período válido, com início até o fim."}
```

## 6. Health e readiness

| Método | Rota | Auth | Descrição | Respostas |
|---|---|---|---|---|
| GET | `/health` | Pública | O processo está de pé. | 200 `{"status":"UP"}` |
| GET | `/ready` | Pública | Pronto para atender: com MongoDB, faz ping no banco. Em modo memória é sempre UP. | 200 `{"status":"UP"}` · 503 `DOWN` |
| GET | `/actuator/health` | Pública | Alias de `/ready`, mantido por compatibilidade com o gateway Java. | 200 · 503 |

## Conferência deste documento

- As rotas foram extraídas dos `registrar (Roteador ...)` de cada handler e comparadas com as tabelas por script: 113 rotas no código e as mesmas 113 neste documento, sem falta nem sobra. Os arquivos estáticos (`EstaticosHandler`) não fazem parte do contrato da API.
- Os exemplos foram executados no servidor em memória. Os códigos listados por rota combinam os contratos dos módulos, a leitura do código e as respostas obtidas na execução. Onde uma resposta não foi exercitada ao vivo (por exemplo, o 503 do servidor de algoritmos), ela vem da leitura do código e dos documentos dos módulos.
- Divergência encontrada: o [README da API](../backend/api/README.md) ainda descreve o talhão com cinco culturas fixas. No código atual a cultura precisa estar no catálogo da propriedade (`/api/culturas`), e este contrato segue o código.
