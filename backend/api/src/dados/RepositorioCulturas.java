package dados;

import java.util.List;
import org.bson.Document;

/** Catálogo de culturas por propriedade (coleção culturas). */
public interface RepositorioCulturas
{
    List<Document> listar (String propriedadeId) throws Exception;

    void garantirCatalogoInicial (String propriedadeId, String proprietarioId) throws Exception;

    boolean existeNome (String propriedadeId, String nome) throws Exception;

    Document criar (String propriedadeId, String proprietarioId, String nome) throws Exception;

    boolean excluir (String propriedadeId, String id) throws Exception;

    Document buscarPorId (String propriedadeId, String id) throws Exception;
}
