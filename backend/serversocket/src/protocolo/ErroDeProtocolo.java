package protocolo;

// O parceiro enviou algo que nao faz parte do protocolo (por exemplo, um objeto que nao e Comunicado).
public class ErroDeProtocolo extends Exception
{
    public ErroDeProtocolo (String mensagem)
    {
        super (mensagem);
    }
}
