# Revisão de áreas de cultivo e plantio

Branch: `fix/frontend-cultivo-plantio`.

A revisão abrange Áreas de Cultivo e a etapa Plantio em Safras. Os registros vêm de `/api/talhoes` e `/api/safras`; os defaults operacionais estão vazios. Os catálogos de culturas e etapas são opções do domínio, não registros mockados. A produtividade de Áreas permanece sem valor calculado e orienta consultar o relatório da safra.

Correções:
- “Em preparo” filtra o status do talhão, em vez de procurar uma cultura inexistente.
- A coluna Safra mostra o valor da API, substituindo “Última atividade” com o texto fixo “Cadastro manual”.
- Removido o texto permanente “Carregando propriedade…”.
- Selects visuais acompanham o reset do formulário, evitando cultura exibida diferente do valor enviado.
- Falha em ocorrências/custos abre o histórico em modo parcial, com “indisponível” no total (sem inventar R$ 0,00); falha na safra/histórico do ciclo bloqueia a abertura.
- O cache local de talhões inclui `safra`, alinhado à coluna da tabela.
- O chip de status usa `data-filter` (cultura ou status), sem reutilizar o atributo só de cultura.

Validação com Chromium/Playwright, API Java real em memória na porta 18080 e serviço de algoritmos na porta 3000. A auditoria falha cedo se a porta 3000 estiver fechada (`tests/audit-prereqs.cjs`). Nenhum registro de teste foi gravado no Atlas e nenhum e-mail foi enviado. Foram verificados estado vazio, filtro de status, safra na tabela, reset do formulário, avanço até Plantio, histórico parcial e indisponibilidade de custos. Desktop 1440×1000 e celular 390×844, sem erros de JavaScript ou overflow da página. Tabelas extensas têm rolagem horizontal própria. As capturas foram inspecionadas e apagadas.

Para repetir, compile a API e o ServerSocket, mantenha o serviço de algoritmos na porta 3000 e deixe 18080 livre. Com Java 21 no PATH e Playwright instalado:

```bash
node tests/cultivo-plantio-audit.cjs
```

`PLAYWRIGHT_MODULE` permite apontar para uma instalação externa. `AUDIT_SCREENSHOTS` opcional aponta para um diretório existente de capturas; o responsável pela execução deve apagá-las após a inspeção.

Esta revisão cobre os cenários acima, não garante ausência de bugs em todos os fluxos de colheita, localização ou relatórios.
