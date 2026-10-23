# Backend do AgroGestão

O gateway e o core são aplicações Java 21 sem framework. O gateway escuta na porta 8080 e consulta `GET /ready` do core na porta 8081. O core usa o driver oficial do MongoDB para acessar um cluster Atlas Free, sem banco local ou Docker.

O core atual contém somente a base de `usuarios` e `propriedades` da issue #100. Não há endpoints de produto nem autenticação; o gateway ainda expõe apenas `/health` e `/ready`. A integração do frontend permanece futura.

## Preparação e execução

Siga [o guia do MongoDB Atlas](../docs/mongodb-atlas.md) para criar o projeto, o cluster gratuito, o usuário individual do banco e o arquivo `.env` local. As credenciais não pertencem ao repositório.

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
