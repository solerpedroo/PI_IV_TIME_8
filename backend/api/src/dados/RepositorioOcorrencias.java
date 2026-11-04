/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados;

import java.util.List;
import org.bson.Document;

/* O registro e seu evento devem ser gravados juntos, inclusive na exclusão lógica. */
public interface RepositorioOcorrencias
{
    List<Document> listar (String usuarioId, String propriedadeId) throws Exception;
    Document buscar (String usuarioId, String propriedadeId, String id) throws Exception;
    List<Document> timeline (String usuarioId, String propriedadeId, String id) throws Exception;
    void criar (Document ocorrencia, Document evento) throws Exception;
    boolean salvar (Document anterior, Document nova, Document evento) throws Exception;

    /*
        Atualiza o registro com controle de versão sem inserir evento na timeline
        (ex.: anexo de foto #175, que não altera o texto do cadastro).
    */
    boolean substituirComVersao (Document anterior, Document nova) throws Exception;

    boolean temRegistros (String propriedadeId) throws Exception;
}
