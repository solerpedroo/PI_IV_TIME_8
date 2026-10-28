<!-- Autor secundário: Pedro Henrique Contardi Soler - 25005592 -->
# Notificações — I22/#26

O sino do Painel gera alertas operacionais a partir dos registros reais da
propriedade autenticada. Clique abre o registro relacionado, fecha o painel e
grava a leitura. Preferências de Configurações filtram quais famílias aparecem.

## Tipos gerados (#166)

| Tipo | Fonte | Preferência |
| --- | --- | --- |
| Estoque baixo/crítico | `GET /api/insumos` (`status` Baixo/Crítico ou saldo &lt; mínimo) | `stock` |
| Atividade atrasada | `GET /api/atividades` ativas com data de planejamento anterior a hoje | `activities` |
| Atividades do dia | mesmas atividades ativas com data igual a hoje | `activities` |
| Ocorrência aberta/em acompanhamento | `GET /api/ocorrencias` com status ≠ `RESOLVIDA` | `occurrences` |

`weather` e `reports` permanecem preferências de conta; esta entrega não inventa
alertas climáticos nem e-mails sem serviço correspondente.

## Navegação (#167)

| Alerta | Destino |
| --- | --- |
| Estoque | `insumos.html?insumoId={id}` → modal de detalhe/histórico |
| Atraso | `atividades.html?atividadeId={id}` → detalhes da atividade |
| Hoje | `atividades.html?hoje=1` → filtra atividades do dia |
| Ocorrência | `ocorrencias.html?ocorrenciaId={id}` → ficha da ocorrência |

## Leitura e preferências (#168)

- IDs estáveis (`stock:…`, `atraso:…`, `hoje:YYYY-MM-DD`, `ocorrencia:…`) em
  `AgroLocal` chave `notifications`, escopados por usuário/propriedade.
- Após reload, a lista é regenerada da API; itens com ID já lido voltam sem
  `is-unread`. Leituras órfãs (alerta sumiu) são podadas na sincronização.
- Preferências vêm de `GET/PUT /api/perfil/preferencias` (mesmas chaves da tela
  Configurações). Preferência desligada remove a família do painel.
- Datas do protótipo sem ano (`12 jan`) usam o ano corrente; se caírem no
  futuro, o parse assume o ano anterior para não perder atrasos de virada.
- O painel limita a exibição a 40 alertas no cliente (MVP sem endpoint de resumo).

## Evidência

```bash
bash backend/servidor/testar.sh
npm install --no-save playwright@1.49.1
npx playwright install chromium --with-deps
PLAYWRIGHT_MODULE=playwright node tests/notificacoes-real.cjs
```

O teste cria os quatro tipos, clica nos destinos, marca lidas, desliga a
preferência de estoque e confere a conservação após reload.
