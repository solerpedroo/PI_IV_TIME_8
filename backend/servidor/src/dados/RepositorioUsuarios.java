/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package dados;

import org.bson.Document;

/*
    Usuarios e a propriedade (fazenda) de cada um.
    Implementacoes: RepositorioUsuariosMongo e RepositorioUsuariosMemoria (testes).
*/
public interface RepositorioUsuarios
{
    // grava o usuario e a propriedade dele; devolve o usuario com _id e propriedadeId,
    // ou null se o e-mail ja estiver cadastrado
    public Document criar (Document usuario, Document propriedade) throws Exception;

    public Document buscarPorId (String id) throws Exception;

    // primeiro usuario com campo == valor, ou null
    public Document buscarPor (String campo, Object valor) throws Exception;

    // $set dos campos informados
    public void atualizar (String id, Document campos) throws Exception;

    public Document buscarPropriedade (String id) throws Exception;
}
