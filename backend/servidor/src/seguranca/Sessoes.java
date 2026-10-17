package seguranca;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
    Sessoes por token (UUID) guardadas em memoria.
    ConcurrentHashMap porque varias SupervisoraDeConexao usam ao mesmo tempo.
    Reiniciar o servidor desloga todo mundo.
*/
public class Sessoes
{
    private static final long DURACAO = 12L * 60 * 60 * 1000; // 12 horas

    private final ConcurrentHashMap<String, Sessao> sessoes = new ConcurrentHashMap<String, Sessao> ();

    // cria a sessao e devolve o token
    public String criar (String usuarioId, String propriedadeId) throws Exception
    {
        String token = UUID.randomUUID ().toString ();

        this.sessoes.put (token, new Sessao (usuarioId, propriedadeId,
                                             System.currentTimeMillis () + Sessoes.DURACAO));

        return token;
    }

    // devolve null se o token nao existir ou tiver expirado
    public Sessao buscar (String token)
    {
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
}
