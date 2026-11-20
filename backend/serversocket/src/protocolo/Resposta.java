package protocolo;

/*
    Comunicado que o servidor devolve para um Pedido.
    idDoPedido repete o id do Pedido que originou a resposta.
    Zero significa que nao ha pedido associado: recusa de conexao, comunicado proibido
    ou servidor lotado, antes de existir um id para devolver.
*/
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
