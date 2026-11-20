# Validação dos ciclos oficiais (#35, #180–#182)

Roteiro e evidência para as issues **[Validação]** do projeto I31.

## O que foi automatizado

| Issue | Escopo | Evidência no repositório |
|-------|--------|---------------------------|
| **#180** | Ciclo produtivo (talhão → safra → atividade → estoque/custo → histórico → painel) | `TesteServidor.testarValidacaoCiclosOficiais()` — espelho automático em `historicos_talhoes` via `EspelhoHistoricoTalhao` |
| **#181** | Ciclo de ocorrências (registro → acompanhamento → resolução → histórico) | Mesmo bloco de teste + `tests/ocorrencias-real.cjs` |
| **#182** | Falhas e regressões (consumo inválido, edição bloqueada pós-resolução) | Asserções 409 no bloco de validação + E2E de ocorrências |

Integração **#162**: conclusão de atividade, consumo com custo e marcos de ocorrência passam a gerar eventos em `GET /api/talhoes/{id}/historico` e alimentam `GET /api/safras/{id}/historico` no período da safra — sem `POST` manual.

## Como executar

### API (memória, CI local)

```bash
cd backend/api && bash compilar.sh && bash testar.sh
```

Procure a seção `# validacao — ciclos oficiais (#35 / #180-#182)` na saída de `TesteServidor`.

### Navegador + servidor real

```bash
cd backend/api && bash compilar.sh
node tests/ocorrencias-real.cjs          # ciclo de ocorrências (#181)
node tests/atividades-real.cjs           # atividades e persistência após reload
```

Com Atlas dedicado (`*_teste`):

```bash
export MONGODB_URI='...'
node tests/ocorrencias-real.cjs --mongo  # inclui reinício do processo Java
```

### Ambiente integrado (DoD #35)

1. `bash emular.sh --build` (ou Atlas com `MONGODB_URI` no `.env`).
2. Executar os dois fluxos com **dados próprios** (não fixtures de demo).
3. **Reload** da página e **reinício** do `Servidor` (`desativar` + subir de novo).
4. Consultar MongoDB ou APIs finais (`/api/talhoes/{id}/historico`, `/api/safras/{id}/historico`, `/api/relatorios/propriedade`).
5. Registrar na issue: commit, ambiente, prints ou trechos de log, falhas encontradas.

## Critérios de aceite (#35)

- Dados inseridos pelo produtor no fluxo real.
- Reload/reinício preservam registros e histórico espelhado.
- Evidência vinculada ao commit (não basta toast ou arquivo existir).