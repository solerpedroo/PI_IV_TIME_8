# Revisão de todas as telas

Escopo: 14 páginas de `frontend/` (o partial da sidebar é validado nas páginas internas), em desktop 1440×1000 e celular 390×844. Capturas temporárias foram inspecionadas e removidas ao concluir.

As páginas de dashboard, áreas, safras, atividades, insumos, manutenção, ocorrências, custos e configurações foram carregadas com sessão criada pela API Java real em memória. Login, criação de conta, confirmação de e-mail, recuperação e redefinição de senha tiveram suas telas verificadas. Nenhum registro fictício foi gravado no Atlas; não houve envio de e-mail nesta revisão.

Correções:
- Indicadores de Atividades, Insumos e Ocorrências agora usam duas colunas no celular, sem alargar a página.
- O menu móvel ocupa uma linha própria e seus ícones permanecem acessíveis; links recebem nome acessível e tooltip.
- O painel não fica exibindo “Carregando propriedade…” permanentemente.
- Um valor de data inválido não lança RangeError no calendário; o rótulo informa o problema e o calendário abre no mês atual.
- A suíte antiga de interações foi atualizada para preencher datas ISO e selecionar a safra pelo select atual.

`tests/todas-telas-audit.cjs` verifica as 28 combinações de página e viewport, erros HTTP/JavaScript, overflow e placeholders permanentes. Verifica também o valor inválido de data. Exige API compilada, Java 21, Playwright e serviço de algoritmos na porta 3000, com 18080 livre, conforme a revisão de cultivo. `AUDIT_SCREENSHOTS` é opcional; não versionar capturas.

Não foram encontrados registros operacionais mockados nas telas de produção: os defaults estão vazios e os módulos consultam a API. Dados fictícios permanecem nas fixtures de testes. A inspeção visual inclui estados vazios, não todos os estados possíveis de cada módulo; não equivale a garantia de ausência de bugs ou a teste completo de todos os fluxos de autenticação e persistência no Atlas.

O seletor de safra em Configurações também deixou de marcar 2025/26 fixamente como “em acompanhamento”; agora identifica a safra que está selecionada na propriedade.

Validação complementar: `tests/local-screens.cjs` passou nos sete cenários de interação (áreas; atividades com criação/edição/lista/arraste/conclusão/duplicação; custos; configurações; criação/conclusão de atividade pelo painel; painel com cache inválido; notificações persistidas). Essa suíte usa API simulada de teste, separada da varredura de páginas com servidor Java real.
