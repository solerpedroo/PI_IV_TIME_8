# Revisão de áreas de cultivo e plantio

Branch: `fix/frontend-cultivo-plantio`.

A revisão abrange Áreas de Cultivo e a etapa Plantio em Safras. Os registros vêm de `/api/talhoes` e `/api/safras`; os defaults operacionais estão vazios. Os catálogos de culturas e etapas são opções do domínio, não registros mockados. A produtividade de Áreas permanece sem valor calculado e orienta consultar o relatório da safra.

Correções:
- “Em preparo” filtra o status do talhão, em vez de procurar uma cultura inexistente.
- A coluna Safra mostra o valor da API, substituindo “Última atividade” com o texto fixo “Cadastro manual”.
- Removido o texto permanente “Carregando propriedade…”.
- Selects visuais acompanham o reset do formulário, evitando cultura exibida diferente do valor enviado.
- Erros de consulta no histórico de safras não são convertidos em listas vazias e custos zero.

Validação com Chromium/Playwright, API Java real em memória na porta 18080 e serviço de algoritmos na porta 3000. Nenhum registro de teste foi gravado no Atlas e nenhum e-mail foi enviado. Foram verificados estado vazio, filtro de status, safra na tabela, reset do formulário, avanço até Plantio, histórico e indisponibilidade de custos. Desktop 1440×1000 e celular 390×844, sem erros de JavaScript ou overflow da página. Tabelas extensas têm rolagem horizontal própria. As capturas foram inspecionadas e apagadas.

Para repetir, compile a API e o ServerSocket, mantenha o serviço de algoritmos na porta 3000 e deixe 18080 livre. Com Java 21 no PATH e Playwright instalado:

```bash
node tests/cultivo-plantio-audit.cjs
```

`PLAYWRIGHT_MODULE` permite apontar para uma instalação externa. `AUDIT_SCREENSHOTS` opcional aponta para um diretório existente de capturas; o responsável pela execução deve apagá-las após a inspeção.

Esta revisão cobre os cenários acima, não garante ausência de bugs em todos os fluxos de colheita, localização ou relatórios.
