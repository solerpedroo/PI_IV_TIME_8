// Miguel — RA: 25003110
package dados;

import java.util.List;
import org.bson.Document;

/** Membros operacionais vinculados à propriedade ativa (subordinados sem conta própria). */
public interface RepositorioEquipe
{
    List<Document> listar (String propriedadeId) throws Exception;

    Document buscarPorId (String propriedadeId, String id) throws Exception;

    Document criar (String propriedadeId, String proprietarioId, Document dados) throws Exception;

    Document atualizar (String propriedadeId, String id, Document dados) throws Exception;

    boolean excluir (String propriedadeId, String id) throws Exception;
}