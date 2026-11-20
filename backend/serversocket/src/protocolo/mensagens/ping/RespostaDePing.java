package protocolo.mensagens.ping;

import protocolo.Resposta;

/*
    Resposta do PedidoDePing. versao e a do servidor (Protocolo.VERSAO), nao a que o cliente pediu:
    o ping nao manda versao. A API usa isto no /ready e para saber com qual geracao do protocolo esta falando.
*/
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
