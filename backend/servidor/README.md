<!-- Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 -->
<!-- Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 -->

# Servidor AgroGestão (Java, HTTP feito à mão)

## Perfil e segurança

Configurações consome `GET/PUT /api/perfil`, `PUT /api/perfil/preferencias`,
`PUT /api/perfil/propriedade-padrao` e `PUT /api/perfil/senha`. A troca de e-mail
é confirmada por código em `POST /api/perfil/email/confirmar`; reenvio em
`POST /api/perfil/email/reenviar`. As rotas usam exclusivamente o dono da sessão.
Senha/e-mail confirmados novamente encerram os logins ativos. A propriedade
padrão vale no próximo login; a seleção da sessão atual permanece independente.
Contrato, limites de foto/equipe/plano e testes: [docs/perfil.md](../../docs/perfil.md).

## Ocorrências

O módulo usa API autenticada, registros e timeline em MongoDB, CRUD com versão,
acompanhamento e resolução, exclusão lógica e resumo mensal pela data real de
resolução. Detalhes do contrato, decisões e testes em [docs/ocorrencias.md](../../docs/ocorrencias.md).
`testar.sh`/`testar.bat` executam também `TesteOcorrencias` em memória. Para validar
MongoDB: `java -cp 'bin:lib/*' TesteOcorrencias --mongo`, com banco dedicado `_teste`.
Uma propriedade com histórico de ocorrências é preservada contra exclusão.

## Propriedades e contexto autenticado

`GET/POST /api/propriedades`, `GET/PUT/DELETE /api/propriedades/{id}` e
`POST /api/propriedades/{id}/selecionar` usam o usuário autenticado como dono.
Nome, área numérica em hectares, localização, município e safra são validados
no servidor. Configurações permite cadastrar, editar e selecionar a propriedade;
a safra escolhida no modal só é confirmada pelo botão **Salvar propriedade**.

A seleção vale para o token atual; `/api/me` devolve esse contexto ativo.
O navegador recarrega após selecionar e passa a usar o cache local separado
por usuário/propriedade. Talhões sempre são filtrados pelo contexto do servidor.
Não se exclui a última propriedade, uma propriedade com talhões ou custos, ou
em uso em qualquer sessão ativa. Se a propriedade padrão for excluída, o vínculo padrão
do usuário muda para uma das restantes na mesma operação de persistência.

No MongoDB, propriedades antigas sem `usuarioId` são vinculadas somente quando
o próprio documento do usuário comprova o vínculo por `propriedadeId`; um dono
já definido nunca é sobrescrito. As exclusões e a atualização da referência
padrão usam transação, exigindo replica set (como o cadastro).
Sessões e coordenação de contexto são locais ao processo Java; esta execução
não oferece implantação com múltiplas instâncias compartilhando sessões.

`bash testar.sh` valida o servidor em memória. `teste/TesteMongo.java` inclui
testes de migração e exclusão transacional, exige banco com nome terminado em
`_teste` e limpa somente esse banco de testes. A execução com MongoDB/SMTP
externos precisa de ambiente configurado e não é substituída pela suíte em memória.

Servidor do projeto em Java 21 puro. O HTTP é implementado manualmente sobre
`java.net.ServerSocket`/`Socket` (sem `HttpServer`, Spring, Netty etc.). A única
biblioteca externa é o driver oficial do MongoDB (`lib/`).

O mesmo servidor entrega:

- a **API** em `/api/**` (autenticação, perfil, propriedades, talhões, estoque, ocorrências, safras, custos e calendário);
- os **arquivos do front** (raiz do repositório), na mesma origem da API;
- `/health` e `/ready`.

## Requisitos

- JDK 21 (`java` e `javac` no PATH). Não precisa de Maven.
- MongoDB 8 em `127.0.0.1:27017`, só se for rodar com banco. Sem banco, use `--memoria`.

Os jars do driver já estão em `lib/`: `mongodb-driver-sync`, `mongodb-driver-core`
e `bson`, versão 5.13.0, baixados do Maven Central.

## Comandos

Todos rodam a partir de qualquer pasta. Os scripts entram em `backend/servidor/` sozinhos.

| Ação | Windows | Linux/macOS/Git Bash |
|---|---|---|
| Compilar | `compilar.bat` | `./compilar.sh` |
| Rodar com MongoDB | `rodar.bat` | `./rodar.sh` |
| Rodar sem banco | `rodar.bat --memoria` | `./rodar.sh --memoria` |
| Rodar em outra porta | `rodar.bat 9000` | `./rodar.sh 9000` |
| Testes automáticos | `testar.bat` | `./testar.sh` |
| Integração MongoDB | — | `./testar-mongo.sh` |

Rode `compilar` antes de `rodar`. O `testar` compila sozinho. Os scripts Unix
são executáveis e fixam a compilação em Java 21. Para validar a persistência
real, suba `docker compose up -d mongo` e execute `./testar-mongo.sh`; ele usa
exclusivamente o banco `agrogestao_teste` e o limpa ao terminar.

Com o servidor no ar:

- abra <http://localhost:8080> (redireciona para `login.html`);
- digite `desativar` + Enter no terminal para encerrar. O servidor fecha o
  `ServerSocket`, espera as conexões em andamento e fecha o cliente do MongoDB.

No modo `--memoria` os dados somem ao desligar. Com MongoDB, os dados persistem.

## Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `SERVIDOR_PORT` | `8080` | Porta. O argumento da linha de comando tem prioridade. |
| `SERVIDOR_HOST` | `127.0.0.1` | Interface onde o servidor escuta. |
| `MONGODB_URI` | `mongodb://127.0.0.1:27017/agrogestao?replicaSet=rs0&serverSelectionTimeoutMS=2000` (posto pelos scripts `rodar`) | Conexão com o banco. O replica set permite transações de cadastro. |
| `SERVIDOR_RAIZ` | `../../frontend` | Pasta do front, relativa a `backend/servidor/`. |
| `SMTP_HOST`, `SMTP_PORT` | —, `465` | Host e porta SMTP com TLS implícito para e-mail real. |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | — | Credenciais SMTP e remetente. As quatro variáveis SMTP devem ser configuradas juntas. |

Se o MongoDB estiver fora do ar na partida, o servidor não sobe e mostra
`MongoDB indisponivel`. Se cair depois, `/ready` responde 503.

Para subir o MongoDB com Docker (na raiz do repositório):

```
docker compose up -d mongo
```

## Testando manualmente

O front chama esta API para cadastro, login, confirmação de e-mail,
recuperação de senha, validação de sessão e logout. Pelo navegador, abra o
servidor em `http://localhost:8080`; assim interface e API ficam na mesma
origem. A API também pode ser testada com `curl`. No Windows, use o **Git Bash**:
no PowerShell as aspas do JSON dão problema.

```bash
B=http://localhost:8080

# saúde
curl -i $B/health
curl -i $B/ready

# cadastro
curl -i -H "Content-Type: application/json" \
  -d '{"name":"Ana","email":"ana@agro.com","phone":"(11) 91234-5678","password":"Senha@123","password-confirm":"Senha@123","farm":"Sítio Sol","area":"80 ha","terms":true}' \
  $B/api/auth/register

# login: copie o "token" da resposta
curl -i -H "Content-Type: application/json" -d '{"email":"ana@agro.com","password":"Senha@123"}' $B/api/auth/login
T=cole-o-token-aqui

# usuário logado
curl -i -H "Authorization: Bearer $T" $B/api/me

# talhões
curl -i -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"nome":"T-01 Norte","cultura":"Soja","hectares":124}' $B/api/talhoes
curl -i -H "Authorization: Bearer $T" $B/api/talhoes
curl -i -H "Authorization: Bearer $T" "$B/api/talhoes?cultura=Soja&q=norte"
curl -i -H "Authorization: Bearer $T" $B/api/talhoes/resumo

# safras (use os ids dos talhões devolvidos acima)
curl -i -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"nome":"Soja 2026/27","cultura":"Soja","dataInicio":"2026-09-01","dataFim":"2027-03-31","talhoes":["ID-DO-TALHAO"]}' $B/api/safras
curl -i -H "Authorization: Bearer $T" "$B/api/safras?etapa=Planejamento"
curl -i -X POST -H "Authorization: Bearer $T" $B/api/safras/COLE-O-ID/avancar
curl -i -H "Authorization: Bearer $T" $B/api/safras/COLE-O-ID/historico

# custos (talhaoId e safraId são opcionais)
curl -i -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"description":"Diesel 500 L","category":"Combustível","value":3100,"date":"2026-07-25","talhaoId":null,"safraId":null}' $B/api/custos
curl -i -H "Authorization: Bearer $T" "$B/api/custos?category=Combustível&de=2026-07-01&ate=2026-07-31"
curl -i -H "Authorization: Bearer $T" "$B/api/custos/resumo?safraId=COLE-O-ID"
curl -i -X PUT -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d '{"nome":"T-01 Norte","cultura":"Soja","hectares":130,"status":"Em preparo"}' $B/api/talhoes/COLE-O-ID

# logout (o token deixa de valer)
curl -i -X POST -H "Authorization: Bearer $T" $B/api/auth/logout
```

### E-mail: SMTP ou modo local

Com `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` e `SMTP_FROM` configurados,
o servidor envia códigos por SMTP com TLS implícito (porta 465 por padrão). Não
registre essas credenciais no repositório. Sem essas variáveis, o modo local
imprime os tokens no terminal:

```
[e-mail simulado] para ana@agro.com | Confirmação de e-mail | token: 3f2c...
```

```bash
# confirmar e-mail
curl -i -H "Content-Type: application/json" -d '{"token":"TOKEN-DO-CONSOLE"}' $B/api/auth/confirm-email

# reenviar confirmação (no máximo 1 a cada 30 s; antes disso, 429)
curl -i -H "Content-Type: application/json" -d '{"email":"ana@agro.com"}' $B/api/auth/resend-confirmation

# esqueci a senha -> token no console -> nova senha
curl -i -H "Content-Type: application/json" -d '{"email":"ana@agro.com"}' $B/api/auth/forgot-password
curl -i -H "Content-Type: application/json" \
  -d '{"token":"TOKEN-DO-CONSOLE","password":"Nova@1234","password-confirm":"Nova@1234"}' \
  $B/api/auth/reset-password
```

### Casos de erro para conferir

```bash
curl -i $B/api/talhoes                                                    # 401 sem token
curl -i -H "Content-Type: application/json" -d '{}' $B/api/auth/register  # 400 com os campos inválidos
curl -i --path-as-is $B/../.env.example                                   # 404 (bloqueado)
curl -i $B/.env                                                           # 404 (bloqueado)
curl -i $B/backend/servidor/src/Servidor.java                             # 404 (bloqueado)
curl -i -X DELETE $B/login.html                                           # 405
```

Repetir o mesmo cadastro devolve 409 (e-mail já existe). Criar dois talhões com
o mesmo nome na mesma propriedade também devolve 409.

## Testes automáticos

`testar.bat` / `./testar.sh` compila tudo e roda `teste/TesteServidor.java`
(sem JUnit). Ele sobe o servidor numa porta livre com os repositórios em
memória (não precisa de banco nem do `rodar` ligado) e faz requisições HTTP
reais por socket. Cobre:

- parse HTTP: 400, 404, 405, 413, 431, 501, 505, corpo incompleto, JSON inválido, HEAD, OPTIONS/CORS;
- estáticos: tipos de conteúdo e bloqueio de `../`, `.env`, `.git`, `backend/` e extensões fora da lista;
- cadastro: cada validação e e-mail duplicado;
- login, logout e token; confirmação de e-mail, reenvio com intervalo e redefinição de senha;
- talhões: validações, CRUD, filtros, resumo e isolamento entre dois usuários;
- safras: validações, conflito de talhão no mesmo período, ciclo completo até a
  finalização, histórico do período e isolamento entre dois usuários;
- custos: validações, vínculos com talhão e safra, filtros, indicadores sobre um
  conjunto conhecido, safra finalizada fechada e isolamento entre dois usuários;
- concorrência: 50 requisições paralelas, cadastros simultâneos com o mesmo e-mail,
  talhões simultâneos com o mesmo nome, exceção em handler (500), clientes que
  desconectam no meio e limite de conexões (503).

A saída termina com `N/N testes ok`. O código de saída é 1 se algo falhar.

## API

Todas as respostas são JSON e usam `Connection: close`. Os nomes dos campos
são os `name` dos formulários do front. Tudo sob `/api/**` exige
`Authorization: Bearer <token>`, exceto `/api/auth/*`.

Erro simples: `{"error": "mensagem"}`. Erro de validação (400) ou conflito (409):

```json
{"error": "Validation failed", "fields": {"email": "Digite um e-mail válido."}}
```

### Autenticação

| Método e rota | Corpo | Respostas |
|---|---|---|
| `POST /api/auth/register` | `name, email, phone, password, password-confirm` (ou `confirm`), `farm, area, terms` | 201 `{usuario}` · 400 · 409 |
| `POST /api/auth/login` | `email, password` | 200 `{token, usuario}` · 400 · 401 genérico · 403 se o e-mail não foi confirmado |
| `POST /api/auth/logout` | (token no cabeçalho) | 204 |
| `GET /api/me` | | 200 `{usuario, propriedade}` · 401 |
| `POST /api/auth/confirm-email` | `token` | 200 · 400 token inválido |
| `POST /api/auth/resend-confirmation` | `email` | 200 · 429 com `Retry-After` |
| `POST /api/auth/forgot-password` | `email` | sempre 200 (não revela se o e-mail existe) |
| `POST /api/auth/reset-password` | `token, password, password-confirm` | 200 · 400 |

Regras do cadastro:

- e-mail válido e único (sem diferenciar maiúsculas);
- telefone `(DD) 9XXXX-XXXX`;
- senha com 8+ caracteres, 1 maiúscula, 1 número e 1 símbolo (as mesmas regras de `frontend/scripts/auth.js`);
- confirmação igual à senha;
- `terms` igual a `true`;
- o cadastro cria o usuário (`emailConfirmado=false`) e a propriedade (`farm`, `area`).

O token de redefinição vale 30 minutos. Redefinir a senha derruba as sessões
abertas do usuário. As sessões duram 12 horas e ficam em memória: reiniciar o
servidor desloga todos.

### Talhões

| Método e rota | Respostas |
|---|---|
| `GET /api/talhoes?cultura=&q=` | 200 com a lista (`cultura` sem diferenciar maiúsculas, `todos` = sem filtro; `q` busca no nome) |
| `POST /api/talhoes` | 201 com o talhão · 400 · 409 nome repetido |
| `PUT /api/talhoes/{id}` | 200 com o talhão · 400 · 404 · 409 |
| `GET /api/talhoes/resumo` | 200 `{totalHectares, quantidadeTotal, quantidadeAtivos}` |

Campos do talhão:

- `nome`: obrigatório, único por propriedade, sem diferenciar maiúsculas;
- `cultura`: Soja, Milho, Trigo, Café ou Algodão;
- `hectares`: número maior que 0;
- `status`: Ativo, Irrigação pendente ou Em preparo. Padrão: Ativo.

Cada usuário só vê e edita os talhões da própria propriedade. Talhão de outra
propriedade responde 404. Não há DELETE nesta entrega.

### Estoque

| Método e rota | Respostas |
|---|---|
| `GET /api/insumos` | 200 com o catálogo da propriedade ativa |
| `GET /api/insumos/{id}` | 200 com um item ativo · 404 |
| `GET /api/insumos/atividades` | atividades persistidas acessíveis ao titular e propriedade da sessão |
| `POST /api/insumos` | 201 · 400 |
| `PUT /api/insumos/{id}` | 200 · 400 · 404 |
| `DELETE /api/insumos/{id}` | 204 · 404 · 409 se houver saldo |
| `GET /api/insumos/{id}/movimentacoes` | 200 com o histórico persistido |
| `POST /api/insumos/{id}/movimentacoes` | 201 · 400 · 404 · 409 por saldo insuficiente |
| `GET /api/insumos/resumo` | indicadores reais de produtos, críticos, entradas do mês e valor atual |

O catálogo guarda `nome`, `categoria`, `unidade`, `saldo`, `minimo`,
`limiarCritico` opcional e `custoUnitario`. A regra 0,6 do mock não é aplicada:
saldo abaixo de `minimo` fica Baixo; se houver `limiarCritico` explícito, saldo
menor ou igual a esse limiar fica Crítico. O limiar não pode superar o mínimo.

As movimentações são `entrada`, `saida` ou `ajuste`; registram data do servidor,
saldo anterior/posterior, unidade histórica, fornecedor, observação e custo.
O ajuste informa o **saldo contado**, pode zerar o item e exige motivo.
Uma saída manual pode omitir atividade; com `atividadeId`, o servidor exige
uma atividade persistida do titular na mesma propriedade e conserva ID/título
no histórico. A leitura aceita os IDs BSON da modelagem da #6. Não há criação
de atividades nem geração de custos de outro módulo por estas rotas de estoque.

Entradas com custo informado atualizam o custo médio ponderado; preço omitido
conserva o anterior. O valor em estoque é a soma de saldo × custo médio, com
arredondamento monetário para centavos. Entradas mensais comparam mês e ano
no fuso `America/Sao_Paulo`, incluindo entradas de produtos depois arquivados.

A saída nunca permite saldo negativo. O saldo inicial tem evento auditável.
Exclusão exige saldo zero e apenas arquiva o catálogo, preservando a rota de
histórico do ID antigo. Unidade não pode ser reinterpretada depois de saldo ou
movimentações: nesse caso, cadastre outro produto. A propriedade com estoque
ou histórico não pode ser excluída. Os repositórios em memória e Mongo usam as
mesmas regras numéricas; Mongo grava saldo/histórico em transação com retry de
conflitos transitórios. O Compose inicializa um replica set local de um membro.

Os comandos e evidências dos testes estão em [docs/estoque.md](../../docs/estoque.md).

### Safras

Uma safra é um ciclo produtivo de uma cultura em um ou mais talhões da
propriedade ativa, dentro de um período.

| Método e rota | Respostas |
|---|---|
| `GET /api/safras?etapa=&cultura=&q=` | 200 com a lista (`etapa` e `cultura` sem diferenciar maiúsculas, `todas`/`todos` = sem filtro; `q` busca no nome) |
| `POST /api/safras` | 201 com a safra · 400 · 409 nome repetido ou talhão ocupado |
| `GET /api/safras/{id}` | 200 · 404 |
| `PUT /api/safras/{id}` | 200 · 400 · 404 · 409 (inclusive safra finalizada) |
| `DELETE /api/safras/{id}` | 204 · 404 · 409 se já saiu do Planejamento |
| `POST /api/safras/{id}/avancar` | 200 com a safra na próxima etapa · 404 · 409 se já finalizada |
| `GET /api/safras/{id}/historico` | 200 com os eventos dos talhões da safra dentro do período · 404 |

Campos da safra:

- `nome`: obrigatório, até 80 caracteres, único por propriedade sem diferenciar maiúsculas;
- `cultura`: Soja, Milho, Trigo, Café ou Algodão (a mesma lista dos talhões);
- `dataInicio` e `dataFim`: `AAAA-MM-DD`, término igual ou depois do início;
- `talhoes`: lista com ao menos um id de talhão da propriedade, sem repetição.

A resposta traz também `etapa`, `historicoEtapas` (etapa e instante de cada
mudança), `finalizadaEm` (só depois de finalizar) e `areaHectares`, que é a soma
atual dos hectares dos talhões associados (não é digitada).

Regras do ciclo:

- etapas: Planejamento → Preparação → Plantio → Manejo → Colheita → Finalização;
- toda safra nasce em Planejamento; a etapa só muda por `/avancar`, uma por vez e sem voltar;
- safra finalizada fica só para consulta: não é editada, avançada nem excluída;
- só se exclui safra em Planejamento (depois disso ela já tem histórico);
- um talhão não pode estar em duas safras cujos períodos se cruzam (409 com o campo `talhoes`);
- safra com custos lançados não é excluída, e a edição não pode tirar dela um
  talhão que tenha custo nela (409 com o campo `talhoes`).

O histórico da safra junta os eventos (`atividade`, `ocorrencia`, `custo`) de
`/api/talhoes/{id}/historico` dos seus talhões cuja data, no fuso de São Paulo,
cai dentro do período. Vem do mais recente para o mais antigo, com `talhaoId` e
`talhaoNome`.

### Calendário

Atividades da propriedade ativa agrupadas por dia, para as visões de semana e
mês da tela de Atividades.

| Método e rota | Respostas |
|---|---|
| `GET /api/calendario?de=&ate=&status=&tipo=&talhao=&q=` | 200 com o período · 400 período ausente, inválido, invertido ou maior que 62 dias |

- `de` e `ate`: obrigatórios, `AAAA-MM-DD`, período inclusivo de até 62 dias;
- `status`, `tipo`, `talhao` e `q`: os mesmos filtros de `GET /api/atividades`, para
  as contagens baterem com o quadro filtrado.

A resposta traz `total`, `porStatus`, `porTipo` (todos os tipos, inclusive
zerados), `dias` (todos os dias do período, cada um com `total` e `porStatus`) e
`atividades` (só as do período, ordenadas por data, horário e nome, com a data
resolvida em `dataIso`). A data `12 ago`, do formato do protótipo, não tem ano:
vale a ocorrência mais próxima do dia em que a atividade foi criada (`10 jan`
criada em dezembro de 2026 cai em janeiro de 2027); em empate, prevalece o ano
da criação. A listagem no banco já descarta ISO fora de `de`/`ate` (índice
`propriedadeId` + `data`); protótipo e inválidas seguem para o resolvedor.
`semData` conta as atividades cuja data não pôde ser resolvida.

### Custos

Lançamentos de custo da propriedade ativa, para controle gerencial (não contábil).
Os nomes dos campos são os `name` do formulário de `custos.html`.

| Método e rota | Respostas |
|---|---|
| `GET /api/custos?category=&talhaoId=&safraId=&de=&ate=&q=` | 200 com a lista, da data mais recente para a mais antiga · 400 período inválido |
| `GET /api/custos/resumo?` (mesmos filtros) | 200 com os indicadores dos lançamentos filtrados · 400 |
| `POST /api/custos` | 201 com o lançamento · 400 · 409 safra finalizada |
| `GET /api/custos/{id}` | 200 · 404 |
| `PUT /api/custos/{id}` | 200 · 400 · 404 · 409 safra finalizada |
| `DELETE /api/custos/{id}` | 204 · 404 · 409 safra finalizada |

Campos do lançamento:

- `description`: obrigatório, até 120 caracteres;
- `category`: Insumos, Mão de Obra, Maquinário, Combustível ou Outros (as opções do formulário);
- `value`: número de 0,01 até 1 bilhão, guardado arredondado em centavos. Texto é recusado;
- `date`: `AAAA-MM-DD`;
- `talhaoId`: opcional. Vazio ou `null` = custo da propriedade toda;
- `safraId`: opcional. Com talhão e safra juntos, o talhão precisa fazer parte da safra.

A resposta traz também `talhaoNome`, `safraNome` e `cultura` (a da safra; sem
safra, a do talhão; sem os dois, `null`). Custo de safra finalizada não é criado,
alterado nem excluído: o histórico da safra fica fechado.

Filtros (todos opcionais e combináveis):

- `category`: sem diferenciar maiúsculas; `todos` = sem filtro;
- `talhaoId`: id do talhão, ou `propriedade` para os custos sem talhão;
- `safraId`: id da safra, ou `nenhuma` para os custos sem safra;
- `de` e `ate`: período inclusivo em `AAAA-MM-DD`;
- `q`: busca na descrição.

O resumo devolve `total`, `quantidade`, `areaHectares`, `custoPorHectare`,
`porCategoria` (as cinco, inclusive zeradas), `porTalhao` (`Propriedade` para
os sem talhão), `porSafra`, `porCultura` e `evolucao` (total por mês `AAAA-MM`,
em ordem). Os totais são somados em centavos. A área usada no custo por hectare é
a da safra filtrada, a do talhão filtrado ou, sem esses filtros, a soma dos
talhões da propriedade. Sem área, `custoPorHectare` é `null`.

### Saúde

- `GET /health` → `{"status":"UP"}`
- `GET /ready` → 200 UP se o MongoDB responde ao ping, 503 DOWN se não. No modo memória é sempre UP.

## Limites e segurança

- Cabeçalhos até 16 KB (431), corpo até 1 MB (413), timeout de leitura de 10 s (408).
- Máximo de 100 conexões simultâneas. Acima disso, responde 503 na hora.
- Rotas públicas sensíveis têm limite de 60 tentativas por IP/minuto; cada conta mantém no máximo 5 sessões simultâneas.
- Arquivos estáticos têm limite de 2 MB para não esgotar a heap do processo.
- `Transfer-Encoding` não é suportado (501). O corpo é sempre lido pelo `Content-Length`.
- Senhas com PBKDF2WithHmacSHA256 (JDK) e salt aleatório. O hash e os tokens nunca saem na resposta.
- Estáticos:
  - extensões permitidas: html, css, js, jpg, png, svg, ico, woff2;
  - bloqueados: `..`, `\`, `:`, `~`, arquivos e pastas iniciados por ponto (`.env*`, `.git`) e `backend/`.
- CORS liberado só para `http://localhost:*` e `http://127.0.0.1:*`. `OPTIONS` responde 204.
- Um erro em uma requisição nunca derruba o servidor: vira `500 {"error":"Internal server error"}`.

## Estrutura

```
backend/servidor/
  compilar | rodar | testar (.sh e .bat)
  lib/                          driver MongoDB (jars)
  src/
    Servidor.java               main, configuração, comando "desativar"
    AceitadoraDeConexao.java    thread do accept(); limite com Semaphore
    SupervisoraDeConexao.java   uma thread por conexão: lê, roteia, responde, fecha
    http/
      ParceiroHttp.java         socket + parse da requisição e escrita da resposta
      Requisicao.java           método, caminho, consulta, cabeçalhos, corpo
      Resposta.java             status, cabeçalhos, corpo
      Roteador.java             tabela método + padrão (/api/talhoes/{id}), filtro de token, CORS
      Manipulador.java          interface dos handlers
      ErroHttp.java             exceção que já carrega o status HTTP
    handlers/
      AuthHandler.java          rotas /api/auth/* e /api/me
      TalhoesHandler.java       rotas /api/talhoes
      SafrasHandler.java        rotas /api/safras (ciclo produtivo)
      CustosHandler.java        rotas /api/custos (lançamentos e indicadores)
      CalendarioHandler.java    rota /api/calendario (atividades por semana e mês)
      EstaticosHandler.java     arquivos do front
      SaudeHandler.java         /health e /ready
    dados/
      RepositorioUsuarios.java / RepositorioTalhoes.java    interfaces
      RepositorioSafras.java / RepositorioCustos.java       interfaces
      *Mongo.java               implementações com MongoDB
      *Memoria.java             implementações em memória (testes e --memoria)
      ConexaoMongo.java         MongoClient único (thread-safe)
    seguranca/
      Senhas.java               PBKDF2
      Sessoes.java, Sessao.java tokens em ConcurrentHashMap
    util/
      Json.java                 org.bson.Document <-> JSON (_id vira "id")
      Validacao.java            validação campo a campo
  teste/
    TesteServidor.java
```

### Coleções no MongoDB

- `usuarios`: índice único em `email`.
- `propriedades`.
- `talhoes`: índice único em `(propriedadeId, nomeNormalizado)`.
- `safras`: índice único em `(propriedadeId, nomeNormalizado)`.
- `custos`: índice em `(propriedadeId, date)`.

Os índices garantem as regras de unicidade mesmo com requisições simultâneas.

## Fora desta entrega

Atividades, Insumos, Ocorrências, Dashboard, Notificações e Configurações.
Os formulários de autenticação do front usam as rotas desta API.
A tela `frontend/safras.html` consome `/api/safras` (criar, avançar etapas,
histórico e vínculos). Escopo I10/I11 e o papel do `core`: [docs/safras.md](../../docs/safras.md).
A tela de Custos ainda pode gravar no navegador em alguns fluxos; o resumo por
safra usa `/api/custos` quando autenticado.
