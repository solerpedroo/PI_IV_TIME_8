package seguranca;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.util.concurrent.ConcurrentHashMap;

/*
    Trava simples contra muitas tentativas seguidas (login, cadastro, esqueci a senha).

    Cada chave (em geral "rota + IP") tem uma janela de tempo. Dentro dela
    contamos tentativas. Passou do máximo, permitir() devolve false e o handler
    responde 429. Quando a janela vence, a contagem recomeça do zero.

    Não é um limite por usuário logado: serve nas rotas públicas, antes de existir sessão.
    O mapa é ConcurrentHashMap porque várias conexões chamam permitir() ao mesmo tempo.
    Se passar de 10 mil chaves, apagamos as janelas já vencidas para o mapa não crescer para sempre.
*/
public class LimitadorDeTaxa
{
    // Uma janela = instante em que começou + quantas tentativas já entraram nela.
    private static class Janela
    {
        long inicio;
        int  tentativas;

        Janela (long inicio)
        {
            this.inicio = inicio;
        }
    }

    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<String, Janela> ();

    // true = pode seguir. false = estourou o limite, responda 429.
    // duracao é em milissegundos. compute() garante que duas threads não contem a mesma chave errado.
    public boolean permitir (String chave, int maximo, long duracao) throws Exception
    {
        if (chave == null || chave.isBlank () || maximo < 1 || duracao < 1)
            throw new Exception ("Limite invalido");

        long agora = System.currentTimeMillis ();
        /*
            compute é atômico para esta chave: duas threads com o mesmo IP
            não leem "3 tentativas" ao mesmo tempo e as duas gravam "4".

            O mapa guarda:
              chave  -> Janela { inicio: milissegundos, tentativas: inteiro }

            Exemplo de chave que o AuthHandler monta: "register|127.0.0.1".
            Se a janela não existe, ou já passou "duracao" ms desde inicio,
            nasce uma janela nova com tentativas 0 e em seguida soma 1.
            Se ainda está dentro do prazo, só soma 1 em cima do valor antigo.
        */
        Janela janela = this.janelas.compute (chave, (ignorada, atual) ->
        {
            if (atual == null || agora - atual.inicio >= duracao)
                atual = new Janela (agora);
            atual.tentativas++;
            return atual;
        });

        // 10 mil IPs diferentes já é lixo acumulado de janelas velhas, não tráfego útil.
        if (this.janelas.size () > 10000)
            this.removerInativas (agora, duracao);

        // A tentativa que estourou também foi contada. 6 com máximo 5 devolve false (429).
        return janela.tentativas <= maximo;
    }

    /*
        Percorre o mapa e apaga janelas cujo inicio já passou da duração.
        remove(chave, valor) só apaga se o valor ainda for o mesmo objeto:
        se outra thread acabou de recriar a janela, ela não é removida por engano.
    */
    private void removerInativas (long agora, long duracao)
    {
        for (var entrada : this.janelas.entrySet ())
            if (agora - entrada.getValue ().inicio >= duracao)
                this.janelas.remove (entrada.getKey (), entrada.getValue ());
    }
}
