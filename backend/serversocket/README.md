# ServerSocket do AgroGestão (Java)

Servidor de **algoritmos** da API: a API envia dados, ele valida ou calcula e devolve o resultado.
É o quadro "ServerSocket (JAVA)" da arquitetura da disciplina:

```
Front End  <->  API  <->  MongoDB
                 ^
                 |  protocolo próprio sobre TCP
                 v
            ServerSocket (JAVA)   <- esta pasta
```

## O que ele faz e o que NÃO faz

| Faz | Não faz (é da API) |
|---|---|
| Validar campos (cadastro, senha, talhão) | HTTP, rotas, CORS, arquivos estáticos |
| Calcular movimentação de estoque (saldo e custo médio) | Banco de dados (MongoDB), repositórios |
| Consolidar relatório de safra e resumo de talhões | Sessões, autenticação, tokens |
| Responder `ping` | Regras que dependem do banco (e-mail duplicado, nome de talhão único, vínculo com atividade) |

Sem dependência externa: só o JDK 21. Sem `Document`, sem biblioteca de JSON.

## Estrutura

```
src/
  Servidor.java                 main, ciclo de vida, comando "desativar"
  AceitadoraDeConexao.java      accept() + limite de conexões (Semaphore)
  SupervisoraDeConexao.java     uma Thread por conexão: lê Pedido, devolve Resposta
  Atendente.java                Pedido -> algoritmo -> Resposta (sem estado, sem E/S)
  protocolo/                    PACOTE QUE A API COPIA
    Comunicado, Pedido, Resposta, RespostaDeErro, ComunicadoDeBoasVindas,
    ComunicadoDeDesligamento, PedidoParaSair, Protocolo
    Parceiro                    socket + streams (usado pelos dois lados)
    Cliente                     lado da API: conectar e chamar(pedido)
    FiltroDeClasses             lista de permissão do ObjectInputStream
    mensagens/                  os pedidos e respostas de cada algoritmo
  algoritmos/                   funções puras, uma classe por assunto
teste/                          TesteAlgoritmos (sem socket) e TesteProtocolo (socket real)
```

## Protocolo

Protocolo próprio da disciplina: objetos `Comunicado` serializados em `ObjectOutputStream` /
`ObjectInputStream` sobre TCP (porta padrão **3000**). Uma conexão atende vários pedidos, um por vez.

1. A API conecta. Os dois lados criam o `ObjectOutputStream` **antes** do `ObjectInputStream` (com `flush`).
2. O servidor fala primeiro: `ComunicadoDeBoasVindas(versão)`, ou `RespostaDeErro SERVIDOR_LOTADO` e fecha.
3. A API envia um `Pedido` (que tem um `id`) e espera **uma** `Resposta` com o mesmo `idDoPedido`.
4. A API termina com `PedidoParaSair` (sem resposta). Se o servidor for desativado, ele avisa com
   `ComunicadoDeDesligamento` e fecha. Conexão sem pedido por 5 minutos é fechada pelo servidor.

### Mensagens

| Pedido | Resposta | O que acontece |
|---|---|---|
| `PedidoDePing` | `RespostaDePing` | devolve a versão do protocolo |
| `PedidoDeValidacaoDeCadastro` | `RespostaDeValidacao` | valida nome, e-mail, telefone, senha forte, confirmação, termos; devolve valores normalizados |
| `PedidoDeValidacaoDeSenha` | `RespostaDeValidacao` | senha forte + confirmação (redefinir senha) |
| `PedidoDeValidacaoDeTalhao` | `RespostaDeValidacao` | nome, cultura, safra, hectares (0 < h ≤ 1.000.000), status; aplica padrões |
| `PedidoDeResumoDeTalhoes` | `RespostaDeResumoDeTalhoes` | total de hectares, quantidade total e de ativos |
| `PedidoDeMovimentacaoDeEstoque` | `RespostaDeMovimentacaoDeEstoque` | valida a movimentação e calcula novo saldo e custo médio ponderado (`OK`, `INVALIDO` ou `SALDO_INSUFICIENTE`) |
| `PedidoDeRelatorioDeSafra` | `RespostaDeRelatorioDeSafra` | área, registros no período, pendências com motivo, contagens e total em centavos |
| `PedidoDeValidacaoDeEquipamento` | `RespostaDeValidacao` | nome, tipo, marca, modelo, identificação, ano (1950 até `anoAtual` + 1), horímetro ≥ 0 e status; aplica o status padrão |
| `PedidoDeValidacaoDePreventiva` | `RespostaDeValidacao` | descrição, data prevista (recusa data impossível) e intervalo de 1 a 3650 dias |
| `PedidoDeValidacaoDeIntervencao` | `RespostaDeValidacao` | tipo, data (não pode passar de `hoje`), descrição, responsável, horímetro e valor em `BigDecimal` com 2 casas (sem valor, sem custo) |
| `PedidoDeAgendaDeManutencao` | `RespostaDeAgendaDeManutencao` | situação de cada preventiva programada (`atrasada`, `proxima` até `diasDeAviso` dias, `futura`), dias até a data e totais |
| `PedidoDeProximaPreventiva` | `RespostaDeProximaPreventiva` | data realizada + intervalo em dias (`null` sem intervalo) |

Falha de **validação de campo** não é erro: volta como `RespostaDeValidacao` com `getErros()`
(chaves = nomes dos campos do front; a confirmação de senha é `password-confirm`).
Falha do **servidor** volta como `RespostaDeErro` com um destes códigos:

| Código | Significa | A API costuma responder |
|---|---|---|
| `PEDIDO_INVALIDO` | faltam dados no pedido | 400 |
| `DADOS_INCONSISTENTES` | os dados se contradizem (ex.: período da safra inválido, custo negativo) | 409 |
| `PEDIDO_DESCONHECIDO` | tipo de pedido que o servidor não conhece | 500 |
| `COMUNICADO_PROIBIDO` | classe fora da lista de permissão | 400 |
| `COMUNICADO_INESPERADO` | enviou algo que não é `Pedido` | 400 |
| `SERVIDOR_LOTADO` | limite de 100 conexões | 503 |
| `ERRO_INTERNO` | falha inesperada (detalhe só no console do servidor) | 500 |

## Como a API usa

Copie `src/protocolo/` para a API **mantendo o nome do pacote** (`protocolo`): a serialização Java
identifica a classe pelo nome, e o filtro do servidor só aceita `protocolo.*`.

```java
Cliente servidor = new Cliente ("127.0.0.1", 3000, 3000);   // host, porta, tempo limite (ms)

Resposta r = servidor.chamar (new PedidoDeValidacaoDeTalhao (nome, cultura, safra, hectares, status));

if (r instanceof RespostaDeValidacao)
{
    RespostaDeValidacao v = (RespostaDeValidacao) r;
    if (!v.isValido ()) { /* 400 com v.getErros () */ }
    else                { /* grava v.getNormalizados () no banco */ }
}
else if (r instanceof RespostaDeErro) { /* veja a tabela de códigos */ }
```

- `chamar` lança exceção só em falha de **comunicação** (rede, tempo limite, servidor desligando).
- Uma conexão faz um pedido por vez. Para atender requisições em paralelo, a API abre várias conexões (um pool simples).
- Estourou o tempo limite? Descarte aquele `Cliente` e abra outro.
- O servidor não grava nada. Quem grava, e de forma atômica, é a API (ex.: estoque: gravar só se o saldo no banco
  ainda for o que foi enviado no pedido).

## Rodar e testar

```bash
sh backend/serversocket/compilar.sh      # ou compilar.bat
sh backend/serversocket/rodar.sh [porta] # ou rodar.bat; digite "desativar" para encerrar
sh backend/serversocket/testar.sh        # ou testar.bat
```

Variáveis de ambiente: `SERVIDOR_HOST` (padrão `127.0.0.1`) e `SERVIDOR_PORT` (padrão `3000`; o argumento tem prioridade).
O servidor escuta só em loopback por padrão. Há uma linha de log por pedido (classe e id, nunca o conteúdo).

## Como incluir um algoritmo novo

1. `algoritmos/NovoAlgoritmo.java`: método estático `Pedido -> Resposta`, puro (sem E/S, sem estado).
2. `protocolo/mensagens/`: o par `PedidoDeX` / `RespostaDeX`.
3. Um `if (pedido instanceof PedidoDeX)` em `Atendente.atender`.
4. Casos em `teste/TesteAlgoritmos.java` (e uma chamada pelo socket em `TesteProtocolo` se o formato for novo).

## Regras que evitam problemas com serialização

- Toda classe serializável declara `serialVersionUID` (a API e o servidor são compilados separados).
- Nas mensagens use `ArrayList`/`LinkedHashMap` e tipos simples (`String`, `double`, `Double`, `long`, `boolean`,
  `BigDecimal`). `List.of`/`Map.of` e qualquer outra classe são **recusados** pelo `FiltroDeClasses`
  (os construtores já copiam as listas para `ArrayList`). Dinheiro vai em `BigDecimal`, nunca em `double`.
- `Parceiro.receba` chama `reset()` depois de cada envio: sem isso o `ObjectOutputStream` retém todo objeto
  enviado e a memória cresce a cada mensagem.
- Campos nulos significam "não informado"; o servidor valida tudo de novo (um objeto desserializado não passa
  pelo construtor).

## Decisões e limites

- **Uma thread por conexão**, com limite de 100 (`Semaphore`); cada thread só usa o `Atendente`, que não tem
  estado: não há trava nos algoritmos.
- **Desativar**: para de aceitar, espera o pedido em andamento de cada conexão, avisa com
  `ComunicadoDeDesligamento` e fecha.
- O filtro limita classes, profundidade (20) e tamanho de arrays/listas (100.000). Cada mensagem serializada
  também é limitada a `Protocolo.MAXIMO_BYTES_POR_MENSAGEM` (1 MiB) via `LimiteDeLeitura` no `Parceiro`.
  O servidor escuta só em loopback por padrão; expor em rede ainda exige TLS.
- A lista de safras válidas (`2024/25`, `2025/26`, `2026/27`) e as culturas/status do talhão estão fixas em
  `ValidadorDeTalhao`, como no cadastro atual.
- A origem de cada regra é o servidor HTTP antigo (`backend/api`): `Validacao`, `TalhoesHandler.lerTalhao`,
  `EstoqueHandler.movimentar` + `CalculosEstoque`, `RelatorioSafra`. Ele foi removido desta branch, mas continua na
  `dev` (ex.: `git show origin/dev:backend/api/src/handlers/AuthHandler.java`). Rotas, autenticação, e-mail e
  acesso ao MongoDB que ele implementava passam a ser responsabilidade da API.
