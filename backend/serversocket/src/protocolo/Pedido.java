package protocolo;

import java.util.concurrent.atomic.AtomicLong;

/*
    Comunicado que a API envia e que exige exatamente uma Resposta.
    O id nasce aqui, no construtor, com um contador atomico: varias threads da API
    podem criar pedidos ao mesmo tempo sem repetir o numero. A Resposta devolve o mesmo id
    para o Cliente descartar uma resposta atrasada de um pedido que estourou o tempo.
    Nao ha campos neste nivel: cada subclasse em protocolo.mensagens carrega os dados do algoritmo.
*/
public abstract class Pedido extends Comunicado
{
    private static final long       serialVersionUID = 1L;
    private static final AtomicLong CONTADOR         = new AtomicLong (0);

    private final long id;

    // incrementAndGet comeca em 1; 0 fica reservado para Resposta sem pedido (erro de conexao)
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
