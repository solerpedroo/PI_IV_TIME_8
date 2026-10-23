package seguranca;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/*
    Sessoes por token (UUID) guardadas em memoria.
    ConcurrentHashMap porque varias SupervisoraDeConexao usam ao mesmo tempo.
    Reiniciar o servidor desloga todo mundo.
*/
public class Sessoes
{
    private static final long DURACAO = 12L * 60 * 60 * 1000; // 12 horas
    private static final int  MAXIMO_POR_USUARIO = 5;

    private final ConcurrentHashMap<String, Sessao> sessoes = new ConcurrentHashMap<String, Sessao> ();
    private final AtomicLong sequencia = new AtomicLong ();

    // cria a sessao e devolve o token
    public String criar (String usuarioId, String propriedadeId) throws Exception
    {
        this.removerExpiradas ();

        // Limita tokens simultaneos por conta. A sessao mais antiga e descartada
        // para impedir que logins repetidos deixem o mapa crescer sem limite.
        String maisAntiga = null;
        long   ordemPrimeira = Long.MAX_VALUE;
        int    quantidade = 0;
        for (Map.Entry<String, Sessao> entrada : this.sessoes.entrySet ())
            if (entrada.getValue ().getUsuarioId ().equals (usuarioId))
            {
                quantidade++;
                if (entrada.getValue ().getOrdem () < ordemPrimeira)
                {
                    maisAntiga = entrada.getKey ();
                    ordemPrimeira = entrada.getValue ().getOrdem ();
                }
            }

        if (quantidade >= Sessoes.MAXIMO_POR_USUARIO)
            this.sessoes.remove (maisAntiga);

        String token = UUID.randomUUID ().toString ();

        this.sessoes.put (token, new Sessao (usuarioId, propriedadeId,
                                             System.currentTimeMillis () + Sessoes.DURACAO,
                                             this.sequencia.incrementAndGet ()));

        return token;
    }

    // devolve null se o token nao existir ou tiver expirado
    public Sessao buscar (String token)
    {
        this.removerExpiradas ();

        if (token == null)
            return null;

        Sessao ret = this.sessoes.get (token);
        if (ret == null)
            return null;

        if (ret.expirou ())
        {
            this.sessoes.remove (token);
            return null;
        }

        return ret;
    }

    public void encerrar (String token)
    {
        if (token != null)
            this.sessoes.remove (token);
    }

    // usado quando a senha muda: derruba todos os logins do usuario
    public void encerrarDoUsuario (String usuarioId)
    {
        for (Map.Entry<String, Sessao> sessao : this.sessoes.entrySet ())
            if (sessao.getValue ().getUsuarioId ().equals (usuarioId))
                this.sessoes.remove (sessao.getKey ());
    }

    private void removerExpiradas ()
    {
        for (Map.Entry<String, Sessao> sessao : this.sessoes.entrySet ())
            if (sessao.getValue ().expirou ())
                this.sessoes.remove (sessao.getKey (), sessao.getValue ());
    }
}
