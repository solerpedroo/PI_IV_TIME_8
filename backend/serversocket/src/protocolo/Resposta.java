package protocolo;

// Comunicado que o servidor devolve para um Pedido. idDoPedido = 0 quando nao ha pedido associado.
public abstract class Resposta extends Comunicado
{
    private static final long serialVersionUID = 1L;

    private final long idDoPedido;

    protected Resposta (long idDoPedido)
    {
        this.idDoPedido = idDoPedido;
    }

    public long getIdDoPedido ()
    {
        return this.idDoPedido;
    }

    public String toString ()
    {
        return this.getClass ().getSimpleName () + "#" + this.idDoPedido;
    }
}
