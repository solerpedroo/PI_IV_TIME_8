package seguranca;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

/*
    Quem esta logado em um token: o usuario e a propriedade dele.
    Imutavel, entao pode ser lida por varias threads sem trava.
*/
public class Sessao
{
    private final String usuarioId;
    private final String propriedadeId;
    private final long   expiraEm;
    private final long   ordem;

    public Sessao (String usuarioId, String propriedadeId, long expiraEm, long ordem) throws Exception
    {
        if (usuarioId == null)
            throw new Exception ("Usuario ausente");

        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        this.usuarioId     = usuarioId;
        this.propriedadeId = propriedadeId;
        this.expiraEm      = expiraEm;
        this.ordem         = ordem;
    }

    public String getUsuarioId ()
    {
        return this.usuarioId;
    }

    public String getPropriedadeId ()
    {
        return this.propriedadeId;
    }

    public boolean expirou ()
    {
        return System.currentTimeMillis () > this.expiraEm;
    }

    public long getExpiraEm ()
    {
        return this.expiraEm;
    }

    public long getOrdem ()
    {
        return this.ordem;
    }
}
