/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package http;

/*
    Contrato de quem sabe responder uma rota HTTP.

    O Roteador guarda um Manipulador por método + caminho. Pode ser uma classe
    inteira (SaudeHandler) ou um método solto (this::listar). Quando o pedido
    chega, o roteador chama tratar() e devolve a Resposta pronta para o socket.

    throws Exception de propósito: a SupervisoraDeConexao captura o erro e
    transforma em HTTP 500, para um handler quebrado não derrubar o servidor.
*/
public interface Manipulador
{
    // Lê a requisição já interpretada e devolve status, cabeçalhos e corpo.
    public Resposta tratar (Requisicao requisicao) throws Exception;
}
