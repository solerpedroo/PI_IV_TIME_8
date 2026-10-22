package seguranca;

/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.util.concurrent.ConcurrentHashMap;

/*
    Janela fixa pequena para rotas publicas sensiveis. A chave normalmente e
    rota + IP; entradas inativas sao removidas para nao virar outro cache sem limite.
*/
public class LimitadorDeTaxa
{
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

    // true se a tentativa cabe na janela; false se deve responder 429
    public boolean permitir (String chave, int maximo, long duracao) throws Exception
    {
        if (chave == null || chave.isBlank () || maximo < 1 || duracao < 1)
            throw new Exception ("Limite invalido");

        long agora = System.currentTimeMillis ();
        Janela janela = this.janelas.compute (chave, (ignorada, atual) ->
        {
            if (atual == null || agora - atual.inicio >= duracao)
                atual = new Janela (agora);
            atual.tentativas++;
            return atual;
        });

        if (this.janelas.size () > 10000)
            this.removerInativas (agora, duracao);

        return janela.tentativas <= maximo;
    }

    private void removerInativas (long agora, long duracao)
    {
        for (var entrada : this.janelas.entrySet ())
            if (agora - entrada.getValue ().inicio >= duracao)
                this.janelas.remove (entrada.getKey (), entrada.getValue ());
    }
}
