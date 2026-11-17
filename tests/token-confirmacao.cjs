// Autor: Vinícius Panutti Salgado — R.A. 25007329

// Espera o token de confirmação aparecer no log; o padrão permite até 150 consultas antes de falhar.
async function aguardarTokenDeConfirmacao (lerLog, tentativas = 150)
{
  // Repete a consulta com um limite finito para não deixar a suíte esperando indefinidamente.
  for (let tentativa = 0; tentativa < tentativas; tentativa++)
  {
    // Lê o log atualizado e captura somente o token alfanumérico com hífens depois do prefixo token:.
    const token = lerLog ().match (/token: ([a-zA-Z0-9-]+)/);
    // Entrega o grupo capturado assim que o envio local de confirmação aparece no log.
    if (token) return token[1];
    // Aguarda cem milissegundos sem bloquear o event loop antes de consultar o log novamente.
    await new Promise ((resolve) => setTimeout (resolve, 100));
  }
  // Faz o teste falhar com diagnóstico explícito se todas as tentativas terminarem sem token.
  throw new Error ("Token de confirmação não chegou ao log do servidor.");
}

// Exporta a espera compartilhada para que os testes E2E usem a mesma sincronização.
module.exports = { aguardarTokenDeConfirmacao };
