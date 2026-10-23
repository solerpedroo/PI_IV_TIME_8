package dados;

/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.util.List;
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

    // Todas as propriedades pertencentes ao usuario. O vinculo e verificado no banco,
    // nunca a partir de um id enviado pelo navegador.
    public List<Document> listarPropriedades (String usuarioId) throws Exception;

    public Document criarPropriedade (String usuarioId, Document propriedade) throws Exception;

    public boolean atualizarPropriedade (String usuarioId, String propriedadeId, Document campos) throws Exception;

    public boolean excluirPropriedade (String usuarioId, String propriedadeId) throws Exception;
}
