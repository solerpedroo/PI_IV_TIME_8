# Qualidade do backend (#36, #183, #184)

## Objetivo

Garantir que **resumos e listas** usados pelo painel e pelos módulos dizem a mesma coisa, que leituras paralelas não distorcem indicadores, e que os tempos de consulta ficam **medidos e registrados** para demonstração.

## Execução automatizada

```bash
cd backend/api && bash compilar.sh && java -cp "bin:lib/*" TesteQualidade
```

Com Mongo dedicado (`*_teste`), incluindo checagem de índices:

```bash
export MONGODB_URI='mongodb://.../agrogestao_teste?...'
java -cp "bin:lib/*" TesteQualidade --mongo
```

Ou via script:

```bash
bash backend/api/medir-qualidade.sh          # memória
bash backend/api/medir-qualidade.sh --mongo  # Atlas/_teste
```

A suíte também roda ao final de `bash backend/api/testar.sh`.

## O que a suíte cobre

| Frente | Issue | Conteúdo |
|--------|-------|----------|
| Consistência | #183 | `custos/resumo` vs lista, `ocorrencias/resumo` vs lista, `insumos/resumo`, `relatorios/propriedade`, isolamento entre contas |
| Concorrência | #183 | 12 leituras paralelas de `custos/resumo` com o mesmo total |
| Medição | #184 | Mediana (15 amostras) em rotas do painel; linhas `medicao <rota> medianaMs=...` |
| Índices | #183 | `historicos_talhoes (propriedadeId, talhaoId, criadoEm)` no modo `--mongo` |

## Critério de demonstração (equipe)

- **Referência local (memória):** mediana ≤ **750 ms** por rota na suíte `TesteQualidade` (limite em código; ajuste aqui se o time acordar outro valor).
- **Atlas / dados reais:** registrar na issue #36 a tabela copiada do log (`medicao ...`) com volume de registros e ambiente (commit, URI de teste, hardware).
- **Gargalos:** se alguma rota ultrapassar o limite ou crescer após mudanças, abrir correção (índice, filtro, cache) e **repetir** a medição antes de fechar #184.

## Evidência para o Project

1. Colar no corpo da #36 ou em comentário o trecho de saída entre `# qualidade — consistencia` e `# qualidade — medicao`.
2. Indicar commit e comando usado (`TesteQualidade` ou `medir-qualidade.sh`).
3. Marcar #183 e #184 quando os critérios acima forem atendidos no escopo combinado.