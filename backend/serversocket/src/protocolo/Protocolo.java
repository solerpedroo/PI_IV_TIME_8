package protocolo;

/*
    Constantes do protocolo proprio entre a API e o ServerSocket.

    Conversa (uma conexao TCP, varios pedidos):
      1. a API conecta; os dois lados trocam o cabecalho dos streams (transmissor antes do receptor);
      2. o servidor fala primeiro: ComunicadoDeBoasVindas (ou RespostaDeErro SERVIDOR_LOTADO e fecha);
      3. a API envia um Pedido e espera exatamente uma Resposta com o mesmo id (um pedido por vez);
      4. a API termina com PedidoParaSair; se o servidor for desligado, ele avisa com ComunicadoDeDesligamento.
*/
public class Protocolo
{
    public static final String VERSAO       = "1.0";
    public static final int    PORTA_PADRAO = 3000;
}
