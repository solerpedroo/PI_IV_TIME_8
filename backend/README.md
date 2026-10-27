# Backend Java do AgroGestão

<!-- Autor secundário: Pedro Henrique Contardi Soler - 25005592 -->

O ponto de entrada principal é `servidor/`: Java puro com `ServerSocket`, sem
Spring e sem Maven. Ele serve as páginas do frontend e a API na mesma origem.

O diretório `core/` concentra o modelo de dados (#100–#105) com o driver
oficial do MongoDB (Atlas Free): usuários/propriedades, talhões, culturas/safras,
atividades/histórico, insumos/movimentações, ocorrências/timeline e custos.
As relações, índices e consultas estão em [modelo de dados](../docs/modelo-dados.md).
Os endpoints HTTP de domínio e a autenticação seguem nas issues correspondentes;
no `core` os endpoints atuais são `/health` e `/ready`.

## Executar o servidor

Com JDK 21 no PATH, a partir da raiz do projeto:

```bash
bash backend/servidor/testar.sh
cd backend/servidor
bash compilar.sh
bash rodar.sh --memoria
```

Abra `http://127.0.0.1:8080/login.html`. O modo `--memoria` perde os registros
do servidor ao encerrar e serve apenas para desenvolvimento/testes. Para
persistência, configure `MONGODB_URI` (MongoDB local ou Atlas) e execute
`bash rodar.sh` sem `--memoria`. Não é necessário Docker para usar Atlas.
No Windows, use os scripts `.bat` equivalentes. Consulte
[servidor/README.md](servidor/README.md) para configuração de SMTP e MongoDB.

O `compose.yaml` na raiz sobe um Mongo local com replica set (loopback), útil
para o servidor e para `testar-mongo.sh`.

`GET /health` verifica o processo; `GET /ready` verifica também o banco quando
configurado. Cadastro, confirmação de e-mail, login e talhões usam `/api/**`.

## Gateway opcional

`gateway/` também é Java puro. Para usá-lo, mantenha o servidor principal na
porta 8080 e inicie o gateway em outra porta, por exemplo:

```bash
cd backend/gateway
bash testar.sh
bash compilar.sh
GATEWAY_PORT=8082 JAVA_BASE_URL=http://127.0.0.1:8080 bash rodar.sh
```

O gateway encaminha `/api/**`, preservando método, query, corpo e autenticação.
As páginas continuam sendo servidas pelo servidor principal na porta 8080.
O endpoint de compatibilidade `/actuator/health` do servidor Java permite que
o `/ready` do gateway consulte a prontidão sem depender de Spring.

## Modelo de dados (core)

Siga [o guia do MongoDB Atlas](../docs/mongodb-atlas.md) para criar o projeto,
o cluster gratuito, o usuário individual do banco e o arquivo `.env` local.
As credenciais não pertencem ao repositório.

Depois de carregar o `.env`:

```bash
cd backend/core
mvn -q compile exec:java -Dexec.mainClass=br.com.agrogestao.core.CoreApplication
```

Na raiz, execute `./backend/core/testar.sh` para os sete testes locais, sem
credenciais. Após carregar o `.env` com `MONGODB_DATABASE` terminado em `_teste`
(ex.: `agrogestao_ci_teste`), use `./backend/core/testar.sh --atlas` para a #100
e os ciclos persistentes das #101–#105. Requer JDK 21, Maven e Bash.

Roteiro prático: [teste-pratico-banco.md](../docs/teste-pratico-banco.md).

## Dados operacionais no navegador

Atividades, ocorrências e custos ainda usam armazenamento
local, separado pelo ID do usuário e da propriedade da sessão. Não são um
substituto para persistência/autorização no servidor nem sincronizam dispositivos.
Registros legados em `agro.operational.v1.*` sem identificação de dono permanecem
intactos, mas não são importados automaticamente: não é possível determinar a
qual conta pertencem. Uma eventual recuperação deve confirmar o dono antes de
copiar os registros para o contexto correspondente.

Insumos agora usa o estoque persistido do servidor Java: catálogo, movimentações,
histórico e indicadores, com isolamento da propriedade ativa. Veja
[estoque](../docs/estoque.md) para regras e testes com MongoDB real e reinício.

Perfil, preferências de conta, propriedade padrão e troca de senha usam a API
Java com persistência no servidor. Veja [docs/perfil.md](../docs/perfil.md).
