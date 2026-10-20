# Polish das telas Forest Sage

Miguel — RA: 25003110

## Correções

- O conteúdo principal tem rolagem e não comprime os cards para caber na altura da janela. Em Custos, a tabela deixa de ficar com altura zero e de se sobrepor ao resumo financeiro.
- Os valores monetários, botões, filtros e colunas do Kanban preservam espaço para leitura. Quando necessário, tabelas e quadro têm rolagem própria.
- Os alertas do Painel ocupam a largura disponível, em vez de ficarem presos à coluna de ícone.
- Modais permitem rolar formulários altos. Tab permanece no diálogo ativo, o fechamento devolve o foco e Escape fecha primeiro o select ou calendário sobreposto.
- Selects têm nomes e identificadores associados, refletem os erros do campo original e fecham ao sair com Tab. Menus acompanham o acionador durante a rolagem e fecham quando ele sai da área visível ou a janela muda de tamanho.
- Mensagens de erro mantêm a cor correta. Em Custos, corrigir um campo válido remove seu aviso antigo sem apagar os erros dos outros campos.
- Percentuais e resumo por cultura usam os lançamentos atuais. Gastos sem vínculo com cultura aparecem separados, sem rateio inventado. O orçamento informa o valor excedido e o Painel consulta o mesmo total de custos.

## Validação

O teste `tests/ui-polish.cjs` abre o Microsoft Edge real com Playwright e entrega os arquivos estáticos por uma rota local interceptada. Confere as 12 telas em 1024×768, 1100×700, 1200×768, 1280×720, 1366×768, 1440×900 e 1920×1080: overflow da página, altura útil das tabelas, sobreposição de cards de Custos, identificadores e menus alcançáveis.

Também usa cliques e preenchimento reais para validar erros, selects, calendário sobreposto, foco, criação/edição/exclusão de custos, recarga, estado vazio, orçamento excedido, resumo por cultura, alertas, seletor de safra e formulários de autenticação vazios. A execução falha se encontrar erros no console, exceções JavaScript ou respostas HTTP de erro. Os testes existentes de fluxos operacionais e persistência complementam essa verificação.

Dependências de teste ficam fora do repositório, conforme `docs/persistencia-local.md`. Na raiz do projeto:

```powershell
$env:NODE_PATH = "$env:TEMP/agro-local-validation/node_modules"
node tests/ui-polish.cjs
node tests/local-screens.cjs
node tests/local-persistence.cjs
```

Para repetir apenas as interações, use `node tests/ui-polish.cjs --flows-only`. A fonte é carregada do CDN usado pelo próprio site; `UI_FONT` permite indicar uma cópia temporária local do mesmo arquivo WOFF2.

O teste só grava prints se `UI_SCREENSHOTS` apontar para uma pasta de revisão. Durante esta task, foram capturados e inspecionados prints das telas, menus e estados alterados em pasta temporária externa ao repositório. Os prints foram excluídos após a revisão; nenhum foi versionado.

## Limites

A validação foi feita no Edge desktop. Não certifica outros navegadores ou dispositivos móveis. O teste não inicia backend, não envia e-mails e não comprova autenticação real: as telas estáticas mantêm seus fluxos demonstrativos existentes. Os dados operacionais continuam locais ao navegador.
