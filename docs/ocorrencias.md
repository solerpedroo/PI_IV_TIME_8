<!-- Autor Principal: Pedro Henrique Contardi Soler - 25005592 -->
# Ocorrências — implementação e validação

Entrega na branch `feat/ocorrencias`, referente às issues #33, #20, #148–#150,
#21 e #151–#153. Responsável atribuído no GitHub: `solerpedroo`.
As referências de escopo são o projeto §8.7, os critérios das issues e o modelo
de dados de ocorrências documentado na frente #6. Esta evidência descreve a
branch de trabalho; não equivale a commit, PR aprovado ou merge em `dev`.

## Arquitetura e regras

O servidor continua em Java 21 puro, com HTTP manual sobre `ServerSocket`.
`OcorrenciasHandler` valida os dados e os casos de uso; `RepositorioOcorrencias`
separa as implementações em memória e MongoDB. A única biblioteca usada é o
driver oficial já existente. HTML, CSS e JavaScript usam o padrão do projeto.

Cada registro pertence ao usuário e à propriedade ativa da sessão. Um talhão é
opcional para ocorrências gerais; quando informado, deve pertencer à propriedade.
São guardados seu ID, nome e safra existente como fotografia do vínculo no momento
do cadastro/edição. A branch atual representa safra pelo campo do talhão, sem um
cadastro independente ou `safraId`; a integração desse cadastro pertence à frente
de safras #14. O cliente não escolhe o dono nem sobrescreve datas do servidor.

O fluxo é `ABERTA → EM_ACOMPANHAMENTO → RESOLVIDA`. Conforme o modelo documentado,
também é permitido resolver uma ocorrência aberta diretamente. Iniciar
acompanhamento, publicar atualização e resolver exigem uma descrição. Publicações
são permitidas em acompanhamento; resolvidas preservam o desfecho e não podem
ser reabertas, editadas ou receber novas publicações.

O cadastro aceita título (até 160 caracteres), tipo (`Praga`, `Doença`, `Clima`,
`Equipamento`, `Outro`), data civil ISO válida, prioridade (`Baixa`, `Média`, `Alta`)
e descrição opcional de até 6000 caracteres. A descrição mantém suas quebras de
linha e caracteres literais. O detalhe usa `textContent`, sem interpretar HTML.
Foto permanece complemento condicionado pelo próprio escopo dos cards: a tela
informa que o anexo está indisponível e a API rejeita seu envio.

Editar um cadastro conserva os dados anteriores na timeline, incluindo a descrição
original. A exclusão é lógica: retira o item da lista ativa e preserva todos os
eventos. A opção **Incluir excluídas (consulta)** permite recuperar a ficha. Uma
propriedade com ocorrências ou histórico preservado não pode ser excluída.

As coleções `ocorrencias` e `ocorrencias_timeline` são gravadas na mesma transação.
O MongoDB precisa de replica set, como já exigido pelo cadastro. Cada escrita usa
a versão esperada; uma tela desatualizada recebe 409 e deve consultar novamente.
Um índice único de usuário/propriedade/ocorrência/ordem evita duplicar eventos.
Se o histórico falhar, a alteração do registro também é desfeita. Sessões e a
coordenação com seleção/exclusão de propriedades continuam locais a um processo.

## Contrato HTTP

Todas as rotas exigem `Authorization: Bearer <token>`:

- `GET /api/ocorrencias`: lista os registros da propriedade ativa.
- `POST /api/ocorrencias`: cria com `titulo`, `tipo`, `talhaoId` opcional, `data`,
  `prioridade` e `descricao`. O status inicial é sempre aberto.
- `GET /api/ocorrencias/{id}`: consulta a ficha, inclusive após exclusão lógica.
- `PUT /api/ocorrencias/{id}`: edita os campos de cadastro, com `versao` esperada.
- `DELETE /api/ocorrencias/{id}`: exclusão lógica, com `{ "versao": 5 }` no corpo.
- `GET /api/ocorrencias/{id}/timeline`: eventos em ordem crescente.
- `POST /api/ocorrencias/{id}/timeline`: recebe `versao`, `descricao` e `acao`
  (`acompanhamento`, `atualizacao` ou `resolucao`).
- `GET /api/ocorrencias/resumo?mes=2026-10`: abertas, em acompanhamento,
  prioridade alta ainda ativa e resolvidas no mês solicitado.

Lista e resumo aceitam os mesmos filtros: `talhaoId`, `q` (título) e
`incluirExcluidas=true`. O mês filtra o indicador de resoluções; a tabela mantém
as ocorrências de todos os meses. O resumo usa **resolvidaEm**, atribuída pelo
servidor, no fuso `America/Sao_Paulo`; não usa a data de abertura, o texto da
tabela ou apenas o status. Excluídas não entram na consulta padrão; passam a
compor lista e indicadores quando a opção de incluí-las está selecionada.

O front envia também `contextoPropriedadeId`, comparado com a sessão para impedir
que uma aba aberta na fazenda anterior grave na nova propriedade por engano.
Consultas de outro usuário/propriedade retornam 404. Dados inválidos retornam 400;
versão antiga, transição inválida ou registro somente consultável retornam 409.

## Interface e mocks

O formulário inclui a data real e carrega talhões por ID da API. Registro, edição,
detalhe, acompanhamento, publicação, resolução e exclusão dependem de confirmação
do servidor. Falhas preservam o texto digitado. Se a escrita foi confirmada e uma
consulta posterior falhar, a mensagem distingue esses dois resultados.

Lista vazia permanece vazia. Falhas de consulta apresentam aviso e nova tentativa,
com indicadores sem valor confirmado, sem restaurar exemplos. O cache legado de
`occurrences` não é importado, sobrescrito ou removido. Os alertas do Dashboard
também consultam a API e levam à ficha pelo ID; uma ocorrência resolvida deixa de
aparecer nos alertas ativos.

## Cobertura dos cards

- #33: descrição literal no cadastro/detalhe, preservada na edição e no histórico.
- #20 e #148: CRUD persistente, validação e isolamento de propriedade/talhão.
- #149: acompanhamento e resolução, rejeição de transições inválidas e reconexão.
- #150: publicações em ordem e recuperação integral da timeline após resolução.
- #21 e #151: formulário/detalhe pela API, com descrição, data e vínculos reais.
- #152: ações integradas, desfecho obrigatório e consulta das atualizações após reload.
- #153: indicador por data de resolução, mês/ano, fuso e contexto selecionado.

## Como repetir a validação

Na raiz do repositório, com JDK 21:

```bash
sh backend/servidor/testar.sh
npm install --no-save playwright@1.49.1
npx playwright install chromium
node tests/ocorrencias-real.cjs
node tests/local-persistence.cjs
node tests/local-screens.cjs
node --test tests/storage-scope.cjs
node tests/ui-polish.cjs
```

Com MongoDB em replica set (por exemplo, `docker compose up -d --wait mongo`):

```bash
export MONGODB_URI='mongodb://127.0.0.1:27017/agro_ocorrencias_teste?replicaSet=rs0&serverSelectionTimeoutMS=5000'
(cd backend/servidor && java -cp 'bin:lib/*' TesteOcorrencias --mongo)
node tests/ocorrencias-real.cjs --mongo
```

O servidor de teste usa a porta 8080, que deve estar livre. A suíte de navegador
desabilita SMTP e usa contas fictícias; em Mongo, exige banco terminado em `_teste`
e remove somente os dados dessas contas. Os testes Java removem somente seus
registros aleatórios. Não executam `dropDatabase` ou `dropCollection`.
`PLAYWRIGHT_MODULE` e `BROWSER_PATH` são opcionais para dependências externas ao repo.

Em 24/10/2026 foram comprovados: 166 testes existentes do servidor, 61 verificações
novas em memória e 64 no MongoDB real; concorrência com dois clientes e rollback
por falha na timeline; navegador com criação, edição, falha controlada de escrita,
acompanhamento, publicação, resolução, recarga, filtro mensal/talhão, isolamento,
exclusão com histórico e falha de leitura. A execução Mongo reinicia o processo
Java, exige novo login e confere os mesmos dados e eventos. Os testes locais dos
demais módulos continuam separados e não substituem essa validação real.
A regressão visual confere 12 telas em sete dimensões desktop, além dos fluxos
de formulários e navegação. Os testes de talhões/propriedades com servidor real
também passaram (`node tests/talhoes-real.cjs`).

O workflow `ocorrencias-testes.yml` reproduz a suíte Java e os fluxos de navegador
em memória e MongoDB nos PRs que alterem esta entrega. A execução remota do CI e
o vínculo desta evidência ao commit ficam disponíveis quando a branch for publicada.
