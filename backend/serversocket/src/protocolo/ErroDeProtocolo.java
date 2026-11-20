package protocolo;

/*
    O parceiro enviou algo que nao faz parte do protocolo.
    Parceiro.envie lanca isto quando o objeto lido nao e um Comunicado.
    A Supervisora trata igual a classe recusada pelo filtro: avisa com COMUNICADO_PROIBIDO e fecha.
    Nao e RespostaDeErro porque o problema apareceu na leitura, antes de haver um Pedido valido.
*/
public class ErroDeProtocolo extends Exception
{
    public ErroDeProtocolo (String mensagem)
    {
        super (mensagem);
    }
}
