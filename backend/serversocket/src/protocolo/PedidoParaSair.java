package protocolo;

/*
    A API encerra a conversa de proposito.
    Nao estende Pedido de proposito: nao tem id e nao espera Resposta.
    A SupervisoraDeConexao ve este comunicado, sai do laco e fecha o socket.
*/
public class PedidoParaSair extends Comunicado
{
    private static final long serialVersionUID = 1L;
}
