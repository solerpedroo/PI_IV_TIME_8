# Fundação do backend

Esta pasta inicia a arquitetura planejada em `RELATORIO_TECNICO_PROJETO.md`:

```text
frontend estático (ainda independente)
    ↓ integração futura
gateway/ — Node.js 24 LTS + TypeScript 7.0.2
    ↓ verificação de disponibilidade
core/ — Java 21 + Spring Boot 4.1.1
    ↓ conexão e health indicator
MongoDB 8.0.32 (compose.yaml na raiz)
```

O gateway ainda não expõe rotas de produto nem encaminha chamadas de domínio. Seu `GET /health` indica que o processo está ativo; `GET /ready` consulta o `GET /actuator/health` do Java e retorna 503 se o Java ou o MongoDB não estiver saudável. No Java, `GET /actuator/health/liveness` verifica o processo, enquanto `GET /actuator/health` inclui a disponibilidade do MongoDB. A conexão ao banco é configurada, mas nenhuma coleção, documento, modelo ou dado de exemplo é criado.

## Pré-requisitos

- Node.js 24.x e npm compatível;
- JDK 21 e Maven 3.6.3 ou superior;
- Docker com Compose para MongoDB local, ou MongoDB 8.0.x já disponível;
- portas 8080, 8081 e 27017 livres (ou ajuste no ambiente).

## Configuração e execução local

Na raiz do repositório, copie `.env.example` para `.env` e ajuste os valores. O arquivo `.env` é ignorado pelo Git. A configuração de exemplo usa um MongoDB **sem autenticação e acessível somente em loopback**; não a utilize em redes compartilhadas ou produção. Para outra instância, altere `MONGODB_URI` e configure autenticação e rede adequadas fora desta fundação.

Inicie o banco:

```bash
docker compose up -d mongo
docker compose ps
```

Carregue o ambiente no terminal que iniciará cada processo (na raiz do projeto):

```bash
set -a
. ./.env
set +a
```

Em um terminal, inicie o Java:

```bash
cd backend/core
mvn test
mvn spring-boot:run
```

Em outro terminal, após carregar o mesmo `.env`, inicie o gateway:

```bash
cd backend/gateway
npm ci
npm test
npm run build
npm start
```

Verificações manuais:

```bash
curl -i http://127.0.0.1:8080/health
curl -i http://127.0.0.1:8080/ready
curl -i http://127.0.0.1:8081/actuator/health/liveness
curl -i http://127.0.0.1:8081/actuator/health
```

Com todos os serviços ativos, as quatro consultas devem retornar HTTP 200. Se Java ou MongoDB estiver indisponível, o gateway continua vivo em `/health`, mas `/ready` retorna HTTP 503. Interrompa Java e Node com `Ctrl+C` em seus terminais. Para parar o MongoDB sem excluir o volume de dados, execute `docker compose stop mongo` na raiz. `docker compose down` também remove o contêiner e a rede, mas preserva o volume nomeado; **não use `down -v`** se quiser preservar dados.

## Limites atuais

- Nenhuma tela foi conectada ao gateway; o frontend segue com mocks locais.
- Não há autenticação, autorização, rotas de negócio, CRUD, esquema de coleções, migrações ou regras agrícolas.
- Não há configuração de produção (TLS, credenciais, observabilidade ou deploy).
- O arquivo `RELATORIO_TECNICO_PROJETO.md` descreve o estado do frontend **antes** desta fundação; este README registra o backend adicionado nesta branch.
