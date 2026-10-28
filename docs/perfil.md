<!-- Autor Principal: Pedro Henrique Contardi Soler - 25005592 -->
# Perfil — #30, #177, #178 e #179

Implementação da branch `feat/perfil`, com Java 21 puro, HTTP manual e o driver
MongoDB já usado pelo projeto. A tela existente de Configurações consome a API.
Não usa Spring, framework de interface ou ORM.

## Dados pessoais (#177)

`GET /api/perfil` devolve `usuario` e `preferencias` da conta autenticada.
`PUT /api/perfil` recebe `name`, `email` e `phone` (telefone pode ficar vazio).
Nome e e-mail são obrigatórios; telefone informado segue `(DD) 9XXXX-XXXX`.
O usuário vem da sessão, nunca de um ID escolhido pelo cliente. Campos como
hash, códigos, permissões e confirmação de e-mail não podem ser escritos pelo
formulário nem são expostos pela projeção pública.

Trocar e-mail exige `senhaAtual`. O novo endereço fica em `emailPendente` e o
endereço atual continua válido até a confirmação. O código expira em 30 minutos.
`POST /api/perfil/email/confirmar`, com `codigo`, confirma o novo endereço e
encerra todas as sessões, exigindo novo login. O índice único de e-mail e a
comparação do token rejeitam concorrência, código reutilizado e endereço já
ocupado. `POST /api/perfil/email/reenviar` renova o código após 30 segundos.
Falha SMTP mantém o endereço antigo e informa que o pedido foi salvo, mas o
envio não foi confirmado. O envio utiliza o serviço de e-mail já existente:
SMTP configurado ou código no console em ambiente local sem credenciais.

Foto está condicionada à aprovação no escopo da issue. Não foi criado upload
sem essa decisão; o avatar usa iniciais da conta real. Equipe, convites,
permissões, cobrança e plano permanecem informativos, sem sucesso simulado,
datas de renovação ou plano ativo inventados.

## Preferências e propriedade padrão (#178)

`PUT /api/perfil/preferencias` recebe os cinco booleanos: `stock`, `weather`,
`activities`, `occurrences`, `reports`. Persistem no documento do usuário.
Contas antigas sem preferências leem os valores iniciais já usados na interface:
os quatro primeiros habilitados e relatórios desabilitados. Gravar a escolha
não implementa por si só os serviços de clima, relatórios ou notificações.

`PUT /api/perfil/propriedade-padrao` recebe `propriedadeId`. O repositório valida
a titularidade e grava a referência usada pelo próximo login. No MongoDB,
essa escrita é transacional e coordenada com a exclusão de propriedades.
A seleção temporária em `/api/propriedades/{id}/selecionar` continua separada:
trocar a padrão não muda uma sessão já aberta. `/api/me` expõe o contexto ativo
em `propriedadeId` e a preferência persistida em `propriedadePadraoId`.

## Segurança (#179)

`PUT /api/perfil/senha` recebe `current`, `new`, `confirm`. Exige a senha atual,
uma nova senha diferente com 8+ caracteres, maiúscula, número e símbolo, e
confirmação igual. Usa PBKDF2 já existente. Gravação condicional impede que uma
credencial lida antes de outra alteração sobrescreva o estado novo. Após salvar,
encerra todas as sessões e invalida tokens de recuperação e troca de e-mail.
O fluxo antigo de redefinição também compara o token/hash antes de atualizar.

Logout continua em `POST /api/auth/logout`. A interface verifica a resposta:
em falha de rede/HTTP, remove o token local e informa que o encerramento no
servidor não foi confirmado. Nunca afirma revogação remota sem resposta válida.
Senhas e códigos não são guardados em localStorage nem na projeção de sessão.

As rotas de perfil são autenticadas e as operações possuem limite por conta.
Os formulários só anunciam gravação após resposta válida. Em falha de consulta,
limpam os dados apresentados e oferecem nova tentativa; não restauram exemplos
ou o antigo `settings` local. Registros legados ficam intactos, sem importação
automática para uma conta no servidor.

## Testes reproduzíveis

Na raiz, com JDK 21:

```bash
sh backend/servidor/testar.sh
```

O script compila as fontes e executa `TesteServidor` e `TestePerfil`. No Windows,
use `backend/servidor/testar.bat`. Para persistência real, use um replica set local
ou Atlas e um banco dedicado terminado em `_teste`, com SMTP em modo local:

```bash
export MONGODB_URI='mongodb://127.0.0.1:27017/agro_perfil_teste?replicaSet=rs0&serverSelectionTimeoutMS=5000'
java -cp 'backend/servidor/bin:backend/servidor/lib/*' TestePerfil --mongo
PROFILE_MONGO=1 node tests/perfil-real.cjs
```

O teste de navegador requer Playwright/Chromium instalados e porta 8080 livre.
`PLAYWRIGHT_MODULE` e `BROWSER_PATH` permitem usar instalações existentes.
Sem `PROFILE_MONGO=1`, `node tests/perfil-real.cjs` usa servidor em memória.
As suítes abrem seu próprio servidor Java e o encerram. Com Mongo, reiniciam
o servidor/conexão e consultam novamente nome, e-mail, preferências e padrão.
Removem apenas contas/propriedades fictícias geradas por elas, nunca usam drop.

Evidências locais de 24/10/2026: `TesteServidor` 166/166; `TestePerfil` 49
verificações em memória e 52 com MongoDB/reinício. O teste no navegador validou
nome/telefone após reload, preferências, padrão no login, e-mail com código,
senha, invalidação de sessão, falhas da API e logout. As suítes locais existentes
de persistência e telas também passaram. O Mongo isolado usado foi 8.0.12;
o workflow mantém a imagem 8.0.32 já definida pelo projeto.

A automação está em `.github/workflows/perfil-testes.yml`. Evidência local não
substitui revisão e execução do CI no PR. Foto/equipe/plano continuam limitados
às condições explicitadas no escopo das issues.
