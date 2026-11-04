/* Autor: Vinicius Panutti Salgado - RA: 25007329 */

async function aguardarTokenDeConfirmacao (lerLog, tentativas = 50)
{
  for (let tentativa = 0; tentativa < tentativas; tentativa++)
  {
    const token = lerLog ().match (/token: ([a-zA-Z0-9-]+)/);
    if (token) return token[1];
    await new Promise ((resolve) => setTimeout (resolve, 100));
  }
  throw new Error ("Token de confirmação não chegou ao log do servidor.");
}

module.exports = { aguardarTokenDeConfirmacao };
