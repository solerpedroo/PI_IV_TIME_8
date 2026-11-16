# Módulos da API HTTP

O código em `src/` usa pacotes Java alinhados às pastas: `handlers.cultivo.TalhoesHandler`, `dados.estoque.RepositorioEstoqueMongo`, etc.

## Onde achar cada funcionalidade

### Conta e acesso (`conta`)

- Autenticação, confirmação de e-mail, logout: `handlers/conta/AuthHandler.java`
- Perfil e senha: `handlers/conta/PerfilHandler.java`
- Health/ready e arquivos estáticos: `handlers/conta/SaudeHandler.java`, `EstaticosHandler.java`
- Persistência de usuários: `dados/conta/RepositorioUsuarios*.java`

### Propriedade e equipe (`propriedade`)

- CRUD de propriedade e contexto da sessão: `handlers/propriedade/PropriedadesHandler.java`
- Convites e membros: `handlers/propriedade/EquipeHandler.java`, `dados/propriedade/RepositorioEquipe*.java`

### Cultivo (`cultivo`)

- Talhões, safras, culturas, colheitas, georreferenciamento/mapa
- Repositórios homônimos em `dados/cultivo/`

### Operação (`operacao`)

- Atividades (Kanban), calendário, ocorrências
- `dados/operacao/RepositorioAtividades*.java`, `RepositorioOcorrencias*.java`

### Financeiro (`financeiro`)

- Custos e orçamentos por safra/talhão
- Serviços `CustosDeConsumo`, `CustosDeManutencao` em `servicos/financeiro/`

### Estoque (`estoque`)

- Catálogo, movimentações, relatório de estoque
- `dados/estoque/`, `handlers/estoque/`

### Manutenção (`manutencao`)

- Equipamentos, preventivas, intervenções
- Integração com validações do socket: `servicos/integracao/AlgoritmosDeManutencao.java`

### Relatórios (`relatorios`)

- Handlers de exportação/consulta agregada
- `servicos/relatorios/RelatorioSafra.java` (composição + socket)

### Infraestrutura

| Pasta | Uso |
| --- | --- |
| `dados/infra/` | Conexão MongoDB, exceções de conflito |
| `servicos/integracao/` | `ClienteDeAlgoritmos` e adaptadores geo/manutenção |
| `util/comum/` | JSON, validações genéricas, fotos em atividades |
| `http/`, `seguranca/` | Pipeline HTTP e sessão Bearer |

## Montagem das rotas

`Servidor.java` (raiz de `src/`) instancia repositórios e registra handlers no `http.Roteador`. Ao incluir uma nova área:

1. Criar handler em `handlers/<modulo>/`
2. Criar interface + implementações memória/Mongo em `dados/<modulo>/`
3. Registrar no `montarRoteador` de `Servidor.java`

## Compilar e testar

```bash
bash compilar.sh
bash testar.sh
```

O `compilar.sh` inclui as classes de `protocolo/` do `serversocket` no classpath de compilação.