# Backend do AgroGestão

O gateway e o core são aplicações Java 21 sem framework. O gateway escuta na porta 8080 e consulta `GET /ready` do core na porta 8081. O core usa o driver oficial do MongoDB para acessar o cluster Atlas Free do grupo.

O core contém a base de usuários/propriedades da #100 e os modelos operacionais das #101–#105: talhões, culturas/safras, atividades/histórico, insumos/movimentações, ocorrências/timeline e custos. As relações, índices e consultas estão descritos em [modelo de dados](../docs/modelo-dados.md). Os endpoints HTTP de domínio, a autenticação e a integração das telas permanecem nas respectivas issues; os endpoints atuais são `/health` e `/ready`.

## Preparação e execução

Siga [o guia do MongoDB Atlas](../docs/mongodb-atlas.md) para criar o projeto, o cluster gratuito, o usuário individual do banco e o arquivo `.env` local. As credenciais não pertencem ao repositório.

O `compose.yaml` na raiz sobe um Mongo local com replica set (loopback) — opcional, útil para o servidor HTTP de outras branches. O caminho principal desta base de dados continua sendo o Atlas Free.

Depois de carregar o `.env` conforme o guia, em um terminal:

```bash
cd backend/core
mvn -q compile exec:java -Dexec.mainClass=br.com.agrogestao.core.CoreApplication
```

Em outro terminal, a partir da raiz, carregue o mesmo `.env` e execute:

```bash
cd backend/gateway
./testar.sh
./compilar.sh
./rodar.sh
```

No Windows, use `testar.bat`, `compilar.bat` e `rodar.bat` para o gateway. Maven é usado somente no core para baixar o driver e compilar; não é um framework.

Verifique `http://127.0.0.1:8081/health`, `http://127.0.0.1:8081/ready`, `http://127.0.0.1:8080/health` e `http://127.0.0.1:8080/ready`. O `/health` só verifica o processo; o `/ready` do core faz `ping` no Atlas. O gateway retorna 503 em `/ready` quando o core ou o banco não está disponível.

## Validar o modelo de dados

Na raiz do repositório, execute `./backend/core/testar.sh` para os sete testes locais, sem credenciais. Após carregar o `.env` com `MONGODB_DATABASE` terminado em `_teste` (ex.: `agrogestao_ci_teste`), use `./backend/core/testar.sh --atlas` para repetir também a #100 e os dois ciclos persistentes das #101–#105, com dados fictícios removidos ao final. Requer JDK 21, Maven e Bash (no Windows, Git Bash ou WSL).

## Demonstração prática e revisão do PR #198

Veja o [roteiro de teste prático](../docs/teste-pratico-banco.md) para executar `--demonstrar`, conferir os dados em outro processo Java e inspecionar no Atlas com `--pausar`. O comando aceita `AGRO_BUILD_DIR` para escolher uma pasta de compilação gravável.
