/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package http;

/*
    Exceção que já carrega o status HTTP da resposta.

    Use quando a falha é esperada e o cliente precisa de um código específico,
    por exemplo 400 para JSON inválido ou 404 para arquivo estático inexistente.
    A SupervisoraDeConexao captura ErroHttp e chama Resposta.erro(status, mensagem).
    Qualquer outra Exception vira 500 genérico, sem vazar a pilha para o navegador.
*/
public class ErroHttp extends Exception
{
    private final int status;

    public ErroHttp (int status, String mensagem)
    {
        super (mensagem);
        this.status = status;
    }

    // Código que vai na primeira linha da resposta, ex.: "HTTP/1.1 400 ..."
    public int getStatus ()
    {
        return this.status;
    }
}
