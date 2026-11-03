/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.List;
import org.bson.Document;

/*
    Registros de colheita (quantidade colhida por safra e talhao), sempre filtrados
    pela propriedade (isolamento entre usuarios).
    Implementacoes: RepositorioColheitasMongo e RepositorioColheitasMemoria (testes).
*/
public interface RepositorioColheitas
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    // grava e preenche o _id
    public void criar (Document colheita) throws Exception;

    // $set dos campos; false se a colheita nao existir na propriedade
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    // false se a colheita nao existir na propriedade
    public boolean excluir (String propriedadeId, String id) throws Exception;
}
