# Identidade visual AgroGestão

Aplicacao da identidade visual no frontend: simbolo, favicon, nome da marca e
tratamento tipografico. A paleta e o design system Forest Sage ja usavam os
mesmos tokens, entao nenhuma variavel de cor foi alterada.

## Convencao do nome

- **Interface** (HTML, onboarding, meta description, titulos de aba): **AgroGestão** (com acento).
- **Codigo e docs tecnicos** (pastas, commits, slugs): **AgroGestao** sem acento, quando o ASCII evita problemas de encoding.
- **Titulos de aba**: `Pagina - AgroGestão` (hifen ASCII, sem travessao em dash U+2014).

## O que mudou

- **Simbolo** em SVG (`frontend/Assets/brand/`): losango de talhoes com a folha
  e os nos de conexao.
  - `simbolo.svg`: versao colorida, para fundos claros (recuperar e redefinir senha).
  - `simbolo-claro.svg`: versao para fundos escuros (sidebar, painel de login, cadastro e confirmacao de e-mail).
  - `favicon.svg`: simbolo sobre fundo `#f4f7f2` com cantos arredondados, ligado em todas as paginas.
- **Nome**: "Agro" em peso 700 e "Gestão" em peso 400 (Geist), na sidebar e nas telas de autenticacao.
- **Titulos das abas**: terminam em `AgroGestão`, no formato `Pagina - AgroGestão` (sem travessao).
- **Substituido**: o icone generico de folha (Lucide) que ficava em quadrado verde.

## Onde esta aplicado

| Local | Arquivo | Variante |
|---|---|---|
| Sidebar do app | `frontend/partials/app-sidebar.html` | claro |
| Login, criar conta | `login.html`, `criar-conta.html` | claro |
| Confirmar e-mail | `confirmar-email.html` | claro |
| Recuperar e redefinir senha | `recuperar-senha.html`, `redefinir-senha.html` | colorido |
| Estilos | `base.css`, `auth.css`, `shell.css` | `.brand-mark--logo`, `.brand-img`, `.brand-name-light` |
| Sidebar degradada | `scripts/shell.js` (`sidebarFallbackMarkup`) | claro (mesmo lockup do partial) |
| Onboarding | `dashboard.html`, `scripts/onboarding.js` | nome AgroGestão |

## Validacao

`node tests/identidade-visual-real.cjs` sobe o frontend estatico, abre as 15
telas no Chromium e confere favicon, titulo, nome na sidebar, imagens da marca
carregadas (`naturalWidth > 0`), ausencia do icone antigo e a variante certa por
fundo. Workflow: `.github/workflows/identidade-visual-testes.yml`.

## Limites

Os PNGs do pacote de identidade sao referencias visuais, nao vetores. O simbolo
em SVG foi redesenhado a partir da prancha e pode ser refinado por um designer.
O pacote original nao faz parte do repositorio.
