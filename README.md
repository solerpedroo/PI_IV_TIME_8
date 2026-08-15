# Agro — telas de autenticação

Implementação estática das telas de **login**, **criação de conta**, **recuperação de senha**, **redefinição de senha** e **confirmação de e-mail**, alinhada ao design system **Forest Sage** do pen.dev (`designSystemProjetoAgro`).

## Estrutura

```text
frontend_pi_iv/
├── login.html
├── criar-conta.html
├── recuperar-senha.html
├── redefinir-senha.html
├── confirmar-email.html
├── styles/
│   ├── base.css       # tokens Forest Sage + componentes compartilhados
│   ├── auth.css       # layout das telas de autenticação
│   └── motion.css     # animações / transições (com prefers-reduced-motion)
└── scripts/
    └── auth.js        # validação, estados, navegação mock e interações
```

## Fidelidade ao design system

| Item | Implementação |
|------|----------------|
| Tokens | `$token` do pen → `--token` em `styles/base.css` |
| Tipografia | Geist (CDN) |
| Ícones | Lucide inline (`leaf`, `sprout`, `mail`, `arrow-left`) |
| Login / Cadastro | Split Brand Panel + Form (420 / 480px) |
| Recuperar / Redefinir | Card centralizado |
| Confirmar e-mail | Split + card com ícone mail |

## Como executar

Abra `login.html` diretamente no navegador ou sirva a pasta com um servidor local:

```bash
python -m http.server 5500
```

Depois acesse `http://localhost:5500/login.html`.

## Fluxo entre telas

```text
login.html
 ├─ Esqueceu a senha? → recuperar-senha.html → redefinir-senha.html → login.html (flash)
 ├─ Criar conta → criar-conta.html → confirmar-email.html → login.html
 └─ Entrar (mock) → mensagem de sucesso na própria tela
```

## Comportamentos incluídos

- Validação de campos obrigatórios e e-mail.
- Mensagens de erro e sucesso acessíveis (`role="alert|status"`).
- Estado de envio com `Aguarde…` e microanimação.
- Mostrar/ocultar senha.
- Confirmação de senha e medidor de força (obrigatório em redefinir).
- Máscara de telefone no cadastro.
- Persistência do e-mail via `sessionStorage` / query string na confirmação.
- Flash de sucesso no login após redefinir senha.
- Reenvio de confirmação com cooldown de 30 segundos.
- Animações de entrada + `prefers-reduced-motion`.
- Layout responsivo para telas menores.

As rotas de backend, autenticação real e persistência deverão substituir a simulação `simulateRequest` em `scripts/auth.js` quando a API estiver disponível.
