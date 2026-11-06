package protocolo;

import java.util.concurrent.atomic.AtomicLong;

// Comunicado que a API envia e que exige uma Resposta. O id e gerado aqui e a Resposta devolve o mesmo.
public abstract class Pedido extends Comunicado
{
    private static final long       serialVersionUID = 1L;
    private static final AtomicLong CONTADOR         = new AtomicLong (0);

    private final long id;

    protected Pedido ()
    {
        this.id = Pedido.CONTADOR.incrementAndGet ();
    }

    public long getId ()
    {
        return this.id;
    }

    public String toString ()
    {
        return this.getClass ().getSimpleName () + "#" + this.id;
    }
}
