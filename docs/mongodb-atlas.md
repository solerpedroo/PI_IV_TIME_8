<!-- Autor: Vinícius Panutti Salgado — R.A. 25007329 -->
# MongoDB Atlas com Java puro

O projeto utiliza MongoDB Atlas Free e o driver oficial `mongodb-driver-sync` para Java. Não utiliza Spring, Spring Data, Hibernate, Docker ou outro framework para acessar o banco. Maven serve apenas para baixar o driver e compilar o Java.

## O que existe nesta branch

A base da issue #100 contém:

- conexão com o Atlas por `MONGODB_URI`;
- requisição `ping` feita diretamente pelo driver Java;
- coleções `usuarios` e `propriedades`;
- operações Java de inserção e consulta;
- índices únicos para e-mail e para nome da propriedade por proprietário;
- consulta de propriedade sempre filtrada por `_id` e `proprietarioId`;
- servidor HTTP do próprio Java, com `GET /health` e `GET /ready`.

Ainda faltam criar ou selecionar um cluster Atlas Free do grupo, configurar credenciais reais, validar a conexão e implementar autenticação e as demais coleções das issues #101 a #105. Portanto, a comprovação persistente exigida pela #100 ainda está pendente.

## Por que as credenciais não ficam no código

Ninguém deve enviar ao repositório, ao assistente ou aos colegas a senha da conta Atlas, a senha do usuário do banco ou a URI completa. Cada integrante mantém sua URI no próprio `.env`, que é ignorado pelo Git.

Existem duas contas diferentes:

1. **Conta Atlas:** entra no painel `cloud.mongodb.com` e permite administrar o projeto.
2. **Usuário do banco:** aparece dentro do projeto em **Database Access** e é usado pela aplicação Java na URI.

## Etapa 1 — criar o Atlas Free

1. Acesse <https://cloud.mongodb.com/> e entre ou crie sua conta pessoal.
2. Crie uma organização para o grupo, se o Atlas solicitar.
3. Crie o projeto `PI-IV-TIME-08`.
4. Escolha **Create/Build a Database** e selecione a opção **Free**.
5. Nomeie o cluster `agrogestao-dev` e escolha uma região próxima do grupo. Se sua conta já tiver um cluster Free, confirme no painel se a organização permite outro cluster gratuito antes de criar um; nunca aceite cobrança sem decisão do grupo.
6. Confirme a criação somente depois de verificar que o custo exibido é `FREE` ou `$0`.

O Atlas pode mudar os nomes e a posição dos botões, mas não deve ser escolhido nenhum plano pago.

## Etapa 2 — permitir a conexão do computador

Em **Network Access**, adicione o IP público atual da máquina que executará o Java. Cada integrante adiciona o próprio IP ou pede ao responsável pelo projeto para adicioná-lo. Evite `0.0.0.0/0`, pois esse valor aceita tentativas de conexão vindas de qualquer endereço da internet.

## Etapa 3 — criar usuários individuais do banco

Em **Database Access**, crie um usuário por integrante, por exemplo `dev_vinicius`. Conceda `readWrite` somente ao banco `agrogestao_dev`.

Não use uma credencial compartilhada. Se alguém sair do grupo, seu acesso poderá ser removido sem trocar a senha dos demais.

## Etapa 4 — obter e guardar a URI

1. No cluster, selecione **Connect**.
2. Selecione **Drivers** e depois **Java**.
3. Copie a URI fornecida pelo Atlas.
4. Na raiz do repositório, crie o arquivo local:

   ```bash
   cp .env.example .env
   ```

5. Edite somente o `.env`. Substitua `USUARIO`, `SENHA` e `CLUSTER` pelos valores do seu usuário de banco e da URI do Atlas:

   ```dotenv
   MONGODB_DATABASE=agrogestao_dev
   MONGODB_URI='mongodb+srv://dev_vinicius:SENHA@CLUSTER.mongodb.net/?retryWrites=true&w=majority&serverSelectionTimeoutMS=5000&connectTimeoutMS=5000'
   ```

Se a senha tiver caracteres reservados de URL, use a versão codificada indicada pelo Atlas. As aspas simples protegem os caracteres da URI ao carregar o arquivo no terminal.

Restrinja a leitura do arquivo ao seu usuário:

```bash
chmod 600 .env
```

## Etapa 5 — compilar e testar sem framework

Pré-requisitos: JDK 21 e Maven 3.9 ou superior.

Na raiz do repositório:

```bash
cd backend/core
mvn clean test-compile
mvn -q exec:java -Dexec.mainClass=br.com.agrogestao.core.CoreApplicationTests -Dexec.classpathScope=test
mvn -q exec:java -Dexec.mainClass=br.com.agrogestao.core.domain.propriedades.AcessoPropriedadeTest -Dexec.classpathScope=test
```

Esses testes não acessam o Atlas nem precisam de credenciais.

## Etapa 6 — carregar o ambiente e conectar ao Atlas

Volte à raiz do repositório, carregue o `.env` no terminal e inicie o core:

```bash
cd ../..
set -a
. ./.env
set +a
cd backend/core
mvn -q compile exec:java -Dexec.mainClass=br.com.agrogestao.core.CoreApplication
```

Na partida, o Java executa `ping` no Atlas e cria os índices das coleções. Se a URI, senha ou IP estiver incorreto, o core encerra com erro e não inicia o servidor.

O conector Atlas desta sessão informou que o acesso por clientes de IA está desabilitado nas organizações da conta. Isso não impede a conexão direta do Java com o Atlas, mas impede que o assistente crie ou inspecione recursos por esse conector. Um Organization Owner pode habilitar esse acesso se o grupo desejar; não é obrigatório para rodar o projeto.

Em outro terminal, verifique:

```bash
curl -i http://127.0.0.1:8081/health
curl -i http://127.0.0.1:8081/ready
```

As duas respostas devem retornar HTTP 200 e `{"status":"UP"}`. `health` confirma que o processo Java está ativo; `ready` faz um novo `ping` no MongoDB Atlas.

## Evidência necessária para concluir a issue #100

Sem publicar credenciais, em um banco de testes separado do banco de desenvolvimento comum:

1. Criar dois usuários fictícios (produtores A e B) e uma propriedade para cada um.
2. Verificar que a consulta por `_id` e `proprietarioId` devolve somente a propriedade do próprio produtor; para um ID alheio, deve ter o mesmo resultado de propriedade inexistente.
3. Verificar referências inválidas (usuário ou propriedade inexistente) e que os índices únicos rejeitam duplicatas.
4. Encerrar o processo Java, iniciá-lo novamente e repetir as consultas para comprovar persistência.
5. Registrar no commit/issue apenas comandos, resultados sanitizados e identificação do banco de teste, nunca URI, senha, endereço IP ou dados pessoais reais.

Os testes locais sem Atlas exercitam somente validações e regras em memória; eles **não** substituem essa evidência persistente.

## Configuração dos colegas

Cada colega deve:

1. aceitar o convite para o projeto Atlas;
2. ter um usuário de banco individual;
3. adicionar seu IP à lista autorizada;
4. criar seu próprio `.env` a partir do exemplo;
5. executar os comandos de compilação e validação.

Nunca commitar o `.env`. Nunca publicar a URI completa em issue, pull request, captura de tela ou mensagem.

## Checklist básico de segurança

- O `.gitignore` bloqueia `.env`, variações de ambiente, chaves, certificados e dumps do MongoDB.
- `.env.example` possui somente marcadores e pode ser versionado.
- Cada integrante usa uma credencial própria com `readWrite` apenas em `agrogestao_dev`.
- A senha da conta Atlas nunca é usada pela aplicação Java.
- O IP de cada integrante é autorizado individualmente; não usar `0.0.0.0/0`.
- A URI do Atlas usa TLS pelo esquema `mongodb+srv://`.
- O core escuta em `127.0.0.1` por padrão, não ficando exposto à rede local.
- Mensagens de erro do core não imprimem a URI nem detalhes da credencial.
- O documento de usuário armazena apenas `senhaHash`; a senha original nunca deve ser salva ou registrada em log.
- Para autenticação, usar uma biblioteca consolidada de hash de senha, como Argon2id ou bcrypt, com salt. Não criar algoritmo criptográfico próprio.
- Não usar dados pessoais reais nos testes acadêmicos; preferir dados fictícios.
- Revogar imediatamente o usuário de banco e o acesso Atlas de qualquer integrante que deixe o grupo.

Antes de cada commit, confira:

```bash
git status --short
git diff --check
git check-ignore -v .env
```

O último comando deve indicar uma regra do `.gitignore`. Se `.env` aparecer em `git status`, não faça o commit até corrigir.
