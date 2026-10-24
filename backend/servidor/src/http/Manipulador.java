/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package http;

/*
    Tudo que sabe responder a uma rota (usado como lambda ou referencia de metodo).
*/
public interface Manipulador
{
    public Resposta tratar (Requisicao requisicao) throws Exception;
}
