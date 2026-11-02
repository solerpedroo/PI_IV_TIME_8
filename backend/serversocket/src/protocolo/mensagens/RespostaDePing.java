package protocolo.mensagens;

import protocolo.Resposta;

public class RespostaDePing extends Resposta
{
    private static final long serialVersionUID = 1L;

    private final String versao;

    public RespostaDePing (long idDoPedido, String versao)
    {
        super (idDoPedido);

        this.versao = versao;
    }

    public String getVersao ()
    {
        return this.versao;
    }
}
