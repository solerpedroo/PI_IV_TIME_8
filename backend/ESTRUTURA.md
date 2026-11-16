# Estrutura do backend

Mapa das pastas em `backend/` e como elas se relacionam. O código de negócio da API HTTP está organizado **por módulo funcional** dentro de `api/src/` (handlers, persistência, serviços e utilitários).

## Visão geral

| Pasta | Papel |
| --- | --- |
| [`api/`](api/) | Servidor HTTP (8080): rotas `/api/**`, estáticos, sessões, MongoDB |
| [`serversocket/`](serversocket/) | Algoritmos e validações puras via TCP (3000) |
| [`core/`](core/) | Modelo de domínio Maven (#100–#105), `/health` e `/ready` |
| [`gateway/`](gateway/) | Proxy opcional de `/api/**` |
| [`mongo-schema/`](mongo-schema/) | Validadores `$jsonSchema` aplicados com `mongosh` |

Fluxo principal: navegador → **api** → MongoDB; **api** ↔ **serversocket** para cálculos/validações.

## API HTTP — módulos em `api/src/`

Camadas transversais (infraestrutura HTTP):

| Pasta | Conteúdo |
| --- | --- |
| `http/` | Roteador, requisição/resposta, contrato dos handlers |
| `seguranca/` | Sessões, senhas, rate limit |
| Raiz `src/` | `Servidor`, `AceitadoraDeConexao`, `SupervisoraDeConexao` |

Demais código agrupado por **domínio**:

| Módulo | Handlers (`handlers/<modulo>/`) | Dados (`dados/<modulo>/`) | Serviços / util |
| --- | --- | --- | --- |
| **conta** | login, perfil, saúde, estáticos | usuários | e-mail, códigos de verificação; `PerfilUsuario` |
| **propriedade** | propriedades, equipe | convites/membros da equipe | modelos de convite e papéis |
| **cultivo** | talhões, safras, culturas, colheitas, mapa | talhões, safras, culturas, colheitas | `PeriodoSafra` |
| **operacao** | atividades, calendário, ocorrências | atividades, ocorrências | — |
| **financeiro** | custos, orçamentos | custos, orçamentos | lançamento automático de consumo/manutenção |
| **estoque** | insumos, relatório de estoque | catálogo e movimentações | `CalculosEstoque` |
| **manutencao** | equipamentos, preventivas, intervenções | três repositórios de manutenção | validações via socket |
| **relatorios** | safra, propriedade, atividades | — | consolidação de relatório (incl. socket) |
| **infra** | — | `ConexaoMongo`, conflitos de cadastro | — |
| **integracao** | — | — | cliente TCP dos algoritmos, geo e manutenção |
| **comum** | — | — | `Json`, `Validacao`, evidências de foto |

Detalhes e exemplos de caminho: [`api/MODULOS.md`](api/MODULOS.md).

## ServerSocket (`serversocket/src/`)

| Pasta | Conteúdo |
| --- | --- |
| `protocolo/` | Mensagens serializadas e ciclo pedido/resposta |
| `algoritmos/` | Classes puras: talhão, safra, estoque, conta, manutenção, geometria |
| Raiz | `Servidor`, `Atendente`, aceitação de conexão |

Testes JUnit em `src/test/java/` espelham o mesmo recorte (`algoritmos/`, contrato, integração).

## Core Maven (`core/src/main/java/.../core/`)

Já segue DDD leve:

- `domain/` — entidades e regras por área (`usuarios`, `propriedades`, `cultivo`, `atividades`, `insumos`, `custos`, `ocorrencias`, …)
- `infra/mongo/` — repositórios e conexão Atlas

## Scripts úteis

```bash
bash backend/api/compilar.sh
bash backend/api/testar.sh          # memória + socket de teste
./backend/mongo-schema/aplicar-schema.sh   # com MONGODB_* no ambiente
```