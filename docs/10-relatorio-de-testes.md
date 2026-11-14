# Relatório de testes do AgroGestão

Registro da execução das validações automatizadas sobre a branch `dev`. O
relatório separa o que foi **executado e observado** do que continua **pendente**.
Nenhum resultado foi marcado como aprovado sem execução comprovada, e as falhas
são descritas como foram encontradas, sem correção neste documento.

Documentos relacionados: [casos de uso](03-casos-de-uso.md),
[contrato da API](06-contrato-api.md) e [manual do usuário](15-manual-do-usuario.md).
Evidência bruta em [`docs/evidencias/testes/`](evidencias/testes/).

## 1. Objetivo e período

Registrar a execução das suítes do repositório (servidor de algoritmos, API Java
e navegador) e deixar o relatório reaproveitável para a entrega final.

- **Execução principal:** 12/11/2026, das 20:58 às 21:02 (-03:00).
- **Segunda execução** (variante diagnóstica, descrita na seção 3): logo em seguida, no mesmo dia.
- **Estado do relatório:** parcial. Os testes que dependem de MongoDB real não puderam ser executados (seção 7).

## 2. Ambiente e versão testada

| Item | Valor |
|---|---|
| Código testado | branch `dev`, commit `60d7dda` (`fix(shell): repassa usuário no evento de autenticação`), igual a `origin/dev` no momento da execução |
| Sistema | Linux 6.18.58.1-microsoft-standard-WSL2 (Ubuntu 24.04 no WSL2) |
| Java | OpenJDK 21.0.13 (instalado nesta máquina para a execução; os scripts compilam com `--release 21`) |
| Node.js | v22.22.0 |
| Playwright | 1.62.1 com Chromium. O CI do repositório fixa 1.49.1, então a versão difere |
| Banco | MongoDB e Docker **indisponíveis**. Tudo rodou com repositórios em memória |
| Servidor de algoritmos | socket na porta 30100 para a API (`testar.sh`) e na 3000 para os testes de navegador |

## 3. Execuções realizadas

Comandos, na ordem em que foram rodados, a partir da raiz do repositório:

```bash
# 1. Servidor de algoritmos (JUnit 5)
sh backend/serversocket/testar.sh

# 2. API Java (compila, sobe o socket em 30100 e roda a cadeia de suítes)
sh backend/api/testar.sh

# 3. Suítes que o testar.sh não alcançou por causa da cadeia com &&
#    (socket de teste em 30100, uma por vez)
cd backend/api
java -cp "bin:lib/*" TesteOcorrencias
java -cp "bin:lib/*" TesteColheitas
java -cp "bin:lib/*" TesteManutencao
java -cp "bin:lib/*" TesteRelatorioSafra

# 4. Testes de navegador (socket na 3000 ligado antes)
sh backend/serversocket/compilar.sh
(cd backend/serversocket && java -cp bin Servidor 3000 &)
export PLAYWRIGHT_MODULE=/caminho/para/node_modules/playwright
for t in tests/*.cjs; do node $t; done   # exceto browser-launch, mock-api e audit-prereqs (módulos de apoio)
```

**Variante diagnóstica.** Quase todas as suítes de navegador falharam logo no
cadastro de teste (seção 4). Para saber se o produto funciona e só o teste está
defasado, rodei uma **cópia temporária** de `tests/` em que o regex que lê o
código de confirmação no log do servidor aceita `codigo:` além de `token:`. A
cópia não foi versionada e nenhum arquivo de `tests/` foi alterado. Os
resultados dessa variante aparecem separados, na seção 4.4.

## 4. Resultados por suíte

### 4.1 Servidor de algoritmos (`backend/serversocket`)

| Suíte | Resultado |
|---|---|
| JUnit 5 (`testar.sh`) | 280 encontrados, 276 passaram, **4 falharam**, 0 ignorados |

Falhas: `ProtocoloTest.conversa`, `AtendenteAlgoritmosTest.talhoes`,
`ValidadorDeTalhaoTest.recusaTodosOsCamposInvalidosSemNormalizacaoParcial` e
`ValidadorDeTalhaoTest.recusaPeriodoInvalido` (caso `1999/00`). Os quatro usam o
período de safra `1999/00` e esperam que ele seja recusado. Causa verificada no
código: `PeriodoSafra.valida` aceita qualquer ano entre 1900 e 2100 cujo sufixo
seja o ano seguinte, então `1999/00` é considerado válido. A regra foi
espelhada da API em `6579e20` (11/11/2026).

### 4.2 API Java (`backend/api`, memória)

| Suíte | Resultado |
|---|---|
| `TesteServidor` | **420/420** ok |
| `TesteOrcamentos` | **41/41** ok |
| `TestePerfil` | **56** verificações ok |
| `TesteGeorreferenciamento` | **38/40**, 2 falhas |
| `TesteOcorrencias` (rodada à parte) | **69** verificações ok |
| `TesteColheitas` (rodada à parte) | **41/44**, 3 falhas |
| `TesteManutencao` (rodada à parte) | **61/61** ok |
| `TesteRelatorioSafra` (rodada à parte) | **24** verificações ok |

O `testar.sh` encadeia as suítes com `&&`: a falha da Georreferenciamento
interrompeu a cadeia, e por isso Ocorrências, Colheitas, Manutenção e Relatório
de Safra só rodaram na execução à parte. O código de saída do `testar.sh` foi 1.

Falhas observadas, sem causa investigada:

- `TesteGeorreferenciamento`: "PUT /api/talhoes/{id} (edicao normal) preserva a localizacao" e "mapa filtrado por cultura (Norte virou Milho)". Ambas envolvem edição de talhão com cultura `Milho`.
- `TesteColheitas`: "safra nao perde talhao que ja tem colheita (409 no campo talhoes)", "safra nao comeca depois de uma colheita existente (409 em dataInicio)" e "sem a colheita do Sul, a safra pode sair do talhao".

### 4.3 Navegador (`tests/*.cjs`, primeira execução)

**Passaram**

| Suíte | Resultado |
|---|---|
| `empty-state-origin` | ok |
| `local-persistence` | 9 verificações ok |
| `storage-scope` | 1 teste, 0 falhas |

**Falharam ou não rodaram**

| Suíte | Primeira falha observada |
|---|---|
| `login-real`, `atividades-real`, `calendario-real`, `colheita-real`, `custos-real`, `notificacoes-real`, `ocorrencias-real`, `orcamento-real`, `perfil-real`, `talhoes-real` | O teste espera `token: <valor>` no log do servidor e não encontra. O servidor em modo simulado imprime `codigo: <6 dígitos>` (observado ao subir `Servidor --memoria` e cadastrar um usuário). |
| `cultivo-plantio-audit`, `georreferenciamento-real`, `manutencao-real`, `todas-telas-audit` | `Cannot read properties of null (reading '1')`: o mesmo regex de `token:` não casa com o log. |
| `local-screens` | 5 de 6 cenários ok. Falhou "áreas: criação, edição, filtro e indisponibilidade offline" (timeout de 30 s). Repetida uma vez, com o mesmo resultado. |
| `ui-polish` | Passa as 12 telas na verificação de sete dimensões e depois falha em "console ou recurso com erro": o `tests/mock-api.cjs` não responde `/api/culturas` e `/api/propriedades/periodos-safra` (404). O CI da `dev` também registra falha deste workflow no commit `60d7dda` (push de 12/11/2026, 00:20 UTC), assim como nos quatro commits anteriores. |
| `atividades-reinicio` | Não executável aqui: precisa de `docker` (`spawnSync docker ENOENT`). |
| `estoque-real` | Não executável aqui: exige `MONGODB_URI` de teste. |
| `relatorio-safra-real` | Não executável aqui: depende do módulo `mongodb`, que não está instalado. |

`token-confirmacao.cjs` é um módulo de apoio, não uma suíte, e `browser-launch.cjs`, `mock-api.cjs` e `audit-prereqs.cjs` também.

### 4.4 Navegador, variante diagnóstica (cópia temporária dos testes)

| Resultado | Suítes |
|---|---|
| Passou | `perfil-real` |
| Falhou em outro ponto | `login-real`, `talhoes-real`, `atividades-real`, `calendario-real`, `colheita-real`, `custos-real`, `notificacoes-real`, `ocorrencias-real`, `orcamento-real`, `cultivo-plantio-audit`, `georreferenciamento-real`, `manutencao-real`, `todas-telas-audit` |
| Repetição de `local-screens` | mesmo resultado da primeira execução |

Nas variantes que falharam, o teste passa do cadastro e do login e quebra mais
adiante. Em `ocorrencias-real` e `cultivo-plantio-audit` a resposta traz a
causa: *"Selecione uma cultura cadastrada ou inclua uma nova em Culturas."* Hoje
o talhão só aceita culturas já cadastradas em `/api/culturas`, regra que esses
testes não cumprem. `georreferenciamento-real` e `manutencao-real` falham na
asserção "talhão criado", o que é compatível com a mesma causa, mas a resposta
não foi capturada. `login-real` e `notificacoes-real` recebem 400 onde esperavam
201, sem identificar a rota. Nas demais suítes dessa lista a causa não foi
investigada.

## 5. Bugs e correções

Nenhuma correção foi feita nesta entrega: o objetivo é registrar o estado real.
Achados, em ordem de impacto:

| # | Achado | Onde | Situação |
|---|---|---|---|
| 1 | Os testes de navegador leem `token:` e o servidor imprime `codigo:` no e-mail simulado. Bloqueia 14 suítes logo no cadastro. | `tests/*.cjs`, `tests/token-confirmacao.cjs` | Aberto |
| 2 | Testes de navegador criam talhão com cultura fora do catálogo; a API recusa com 400. | `tests/*.cjs` | Aberto (confirmado em `ocorrencias-real` e `cultivo-plantio-audit`) |
| 3 | `tests/mock-api.cjs` sem as rotas `/api/culturas` e `/api/propriedades/periodos-safra`. Quebra o `ui-polish` e o workflow "Polish de interface" no CI da `dev`. | `tests/mock-api.cjs` | Aberto |
| 4 | `PeriodoSafra.valida` aceita `1999/00`, e 4 testes JUnit do socket esperam recusa. Código e testes divergem. | `backend/serversocket` | Aberto |
| 5 | `TesteGeorreferenciamento` (2 falhas) e `TesteColheitas` (3 falhas) em memória. | `backend/api/teste` | Aberto, causa não investigada |
| 6 | `local-screens`, cenário "áreas", com timeout. | `tests/local-screens.cjs` | Aberto, causa não investigada |
| 7 | O `testar.sh` da API para na primeira falha (`&&`) e esconde as suítes seguintes. | `backend/api/testar.sh` | Observação |
| 8 | O README da API descreve cinco culturas fixas, mas o código exige o catálogo `/api/culturas`. | `backend/api/README.md` | Documentação defasada (registrado também no [contrato da API](06-contrato-api.md)) |

## 6. Evidências

- [`evidencias/testes/execucao-2026-11-12-resumo.txt`](evidencias/testes/execucao-2026-11-12-resumo.txt): tabela suíte, código de saída e duração das três rodadas.
- [`evidencias/testes/execucao-2026-11-12-falhas.txt`](evidencias/testes/execucao-2026-11-12-falhas.txt): trechos de saída das falhas.
- Saída resumida das passagens:
  - `TesteServidor`: `420/420 testes ok`
  - `TesteOrcamentos`: `41/41 verificações OK (memória)`
  - `TestePerfil`: `56 verificações OK em memória`
  - `TesteManutencao`: `61/61 verificações OK (memória)`
  - `TesteOcorrencias`: `69 verificações OK (memória)`
  - `TesteRelatorioSafra`: `24 verificações aprovadas`
- CI da `dev` (GitHub Actions): "Polish de interface" com `failure` em `60d7dda`.

## 7. Limitações e pendências

**Não executado (pendente, sem resultado):**

- `sh backend/api/testar-mongo.sh`, `testar-estoque-mongo.sh` e as suítes `Teste* --mongo`, que exigem MongoDB em replica set (`rs0`) com banco terminado em `_teste`.
- `atividades-reinicio`, `estoque-real` e `relatorio-safra-real`, que dependem de Docker ou do módulo `mongodb`.
- Persistência após reinício do servidor com MongoDB real.
- `testar-smtp.sh` (envio real de e-mail).
- `backend/core` (Maven) e `backend/gateway`.
- Execução dos workflows do CI nesta máquina. O que consta aqui sobre o CI vem do histórico do GitHub.

**Limitações da execução:**

- Playwright 1.62.1 local contra 1.49.1 do CI.
- Os testes de navegador rodaram com o servidor em memória e socket local.
- A segunda execução usou uma cópia modificada dos testes e serve só como diagnóstico; seus resultados não substituem os da primeira.
- O OpenJDK 21 foi instalado nesta máquina para a execução.

## 8. Conclusão

No estado `60d7dda` da `dev`, **o conjunto de testes não está verde**:

- Aprovado e comprovado: `TesteServidor` (420/420), `TesteOrcamentos`, `TestePerfil`, `TesteOcorrencias`, `TesteManutencao`, `TesteRelatorioSafra`, 276 dos 280 testes JUnit do socket e três suítes de navegador (`empty-state-origin`, `local-persistence`, `storage-scope`).
- Reprovado: 4 testes JUnit, 2 verificações de Georreferenciamento, 3 de Colheitas, 1 de 6 cenários do `local-screens` e o `ui-polish`.
- Bloqueado por testes defasados: 14 suítes de navegador falham antes de exercitar o produto, por um regex de log e por criar talhão sem cultura cadastrada. O resultado delas **não** permite afirmar que o produto está quebrado nem que está correto.
- Sem resultado: tudo o que depende de MongoDB real, Docker e SMTP.

Para fechar o relatório da entrega final, falta corrigir os itens 1 a 4 da seção 5, rodar a cadeia completa em uma máquina com Docker e MongoDB, e atualizar as tabelas acima com a nova execução.
