# 2026-08-17 — Kanban arrastável, status pela Lista, fix crítico de popover cortado

[← índice de changelogs](README.md)

## Contexto / pedido original

Usuário mandou um áudio (WhatsApp, transcrito com `whisper` local — ver
abaixo) + uma foto do menu "⋮" de Ocorrências aparecendo cortado no
monitor dele, pedindo três coisas:

1. No Kanban de Atividades, poder mover o card entre os status
   (Pendente/Agendada/Em andamento/Concluída) arrastando.
2. Na visão de Lista, também poder trocar o status.
3. Transição suave entre os seletores Kanban/Lista, e o mesmo tratamento
   nos filtros de Áreas de Cultivo (chips Todos/Soja/Milho/...).

### Transcrição do áudio

> "Mano, estou dando uma olhada aqui, ficou muito bom a implementação, teve
> só uns pontos para eu mandar uma foto, porque eu acho que fica mais fácil
> de entender. Mas por exemplo, aqui na parte de atividades, esse Kanban
> não está dando para definir os estados, pendente, agendado, eu não
> consigo pegar e passar o status dele, eu acho que seria legal, depois
> você pedir para o Claude para dar a opção do Kanban de movimentar e na
> visão de lista dá a possibilidade de mudar também o status, né? E nesses
> seletores de Kanban de lista colocar uma transição suave entre eles, o
> mesmo também na tela de áreas de cultivo nos filtros, na movimentação do
> filtro que tem todo, soja, milho, fazer essas mudanças. E aí, na tela de
> áreas de cultivo, na tela de atividades também, na de insumos, ocorrências
> e só essas, né? Quando você abre os três pontinhos ali, ele pega, aqui
> para mim no meu monitor, pelo menos aparece como cortando essa parte
> dessa sobreposição que aparece desse menuzinho."

A foto mostrava o menu de ações da tabela de Ocorrências aberto, mas só a
coluna de ícones (olho / check / lixeira) visível, sem os rótulos de
texto — cortado na borda direita da tela.

## O que foi investigado e corrigido

### 🔴 Bug crítico: popover "⋮" cortado em todas as 4 telas de módulo

Reproduzido com Playwright antes de mexer em qualquer coisa (screenshot
bateu 1:1 com a foto do usuário). Causa raiz, em duas camadas:

1. **`.app-main` (`styles/motion.css`)** tinha `animation: auth-fade-up
   ... both;`. O keyframe final é `transform: translateY(0)` —
   visualmente idêntico a "sem transform nenhum" — mas `fill-mode: both`
   mantém esse `transform` aplicado **para sempre** depois que a animação
   termina. Um elemento com `transform` ativo vira o *containing block*
   dos seus descendentes `position: fixed` (regra do próprio CSS, não é
   bug de navegador). Como o menu "⋮" é promovido a `position: fixed`
   pelo `shell.js` (fix da sessão anterior, para escapar do clipping de
   tabelas com scroll), ele passou a ser posicionado relativo à borda do
   `.app-main` em vez da viewport — deslocado exatamente pela largura da
   sidebar (260px). Corrigido trocando `both` → `backwards` (só precisa
   seg`urar o estado inicial durante o `animation-delay`, não o final).

2. **Regressão própria desta sessão**: ao implementar o fade-in de itens
   filtrados (`filter-item-in`, ver abaixo), cometi o mesmo erro —
   `animation: filter-item-in ... both;` em `.data-table tbody tr` e
   `.kanban-card`. Isso reintroduziu o bug, só que pior: cada LINHA vira
   containing block do seu próprio menu, com um offset diferente por
   linha (por isso os primeiros testes depois do fix do `.app-main`
   ainda falhavam, com números aparentemente aleatórios). Corrigido
   trocando para `fill-mode: none` (não precisa segurar nada aqui, não há
   `animation-delay`).

**Lição registrada no CLAUDE.md**: qualquer `animation`/`transition` com
`transform` e `fill-mode: forwards`/`both` num ancestral de um elemento
`position: fixed` quebra esse fixed. Isso vai continuar mordendo se uma
próxima sessão adicionar motion a `.card`, `.kanban-column` ou qualquer
outro container de linha/card.

### Kanban: arrastar card para mudar status

`atividades.js` — Drag and Drop nativo (HTML5, sem biblioteca):
- `.kanban-card` ganhou `draggable="true"` (nos 8 cards estáticos do HTML
  e em `buildCard()`, para os criados depois).
- `dragstart`/`dragend`/`dragover`/`dragleave`/`drop` delegados no board
  e em cada `.kanban-column` (só 4 colunas fixas, não precisa de
  delegação para colunas dinâmicas).
- Soltar um card numa coluna diferente chama `moveCardToStatus(card,
  novoStatus)` — direto, sem modal de confirmação (diferente do
  "Concluir" do menu "⋮", que confirma e narra a baixa de insumo
  simulada). Só um toast de sucesso.

### Lista: trocar status também

Célula de Status da Lista virou um `<select>` **nativo** (`.status-select`
em `modules.css`), não o dropdown customizado do design system
(`AgroSelect`). Testado e descartado de propósito: o popup do `AgroSelect`
sofre do mesmo clipping por `overflow: auto` que os menus "⋮" tinham — um
`<select>` nativo renderiza seu popup num layer do próprio navegador,
imune a isso.

`moveCardToStatus()` é agora a única função que move um card de coluna —
usada pelo drag, pelo select da Lista e pelo fluxo "Concluir" (que antes
duplicava essa lógica inline).

### Transições suaves

- `.view-toggle-option` (pills Kanban/Lista) ganhou a mesma
  `transition: background-color/color` que `.filter-chip` e
  `.segmented-option` já tinham — só isso já estava faltando ali.
- Alternar Kanban ⇄ Lista agora esmaece a view atual, troca `hidden`, e a
  nova view entra de opacity 0 → 1 (orquestrado em JS porque `display`
  não anima; ver `initViewToggle()`).
- Linhas de tabela e cards de Kanban que **aparecem** depois de um filtro
  (busca, chip, categoria, dia da semana) ganham um fade-in sutil — regra
  puramente CSS (`filter-item-in`, ver bug acima) que funciona nas 4
  telas de módulo automaticamente, sem precisar de JS extra em cada uma:
  o `hidden` já é alternado por JS em cada tela, a animação "reinicia"
  sozinha sempre que o elemento volta a corresponder a `:not([hidden])`.

### Bug pequeno encontrado no caminho (não relacionado ao pedido)

Duplicar a atividade "Manutenção trator JD" (talhão = "Oficina") falhava
a validação silenciosamente: "Oficina" não existia como `<option>` no
select de talhão do formulário. Adicionado.

## Ferramentas usadas

- `whisper` (openai-whisper, já instalado no ambiente) para transcrever o
  áudio — modelo `small`, português.
- Playwright + Chromium, reaproveitando uma instalação já existente em
  `/palmeiras/node_modules/playwright` (sem precisar baixar de novo — o
  Chromium do Playwright já estava em `~/.cache/ms-playwright`).

## Estado atual

Todos os fixes e features verificados via Playwright: os 4 menus "⋮"
abrem visíveis e dentro da viewport, drag-and-drop move o card e atualiza
métricas, o select da Lista muda o status e reflete no Kanban, o toggle
Kanban/Lista não trava escondido depois de várias trocas, e os fluxos já
existentes (concluir via modal, duplicar, excluir, criar talhão) seguem
funcionando — tudo sem erro de console. Commitado atomicamente
(`fix(motion)`, `feat(modules)`, `feat(atividades)`), ainda não enviado ao
GitHub nesta sessão até a próxima instrução de push.
