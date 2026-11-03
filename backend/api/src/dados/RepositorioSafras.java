/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.List;
import org.bson.Document;

/*
    Safras (ciclos produtivos), sempre filtradas pela propriedade (isolamento entre usuarios).
    O campo "nomeNormalizado" (nome em minusculas) garante nome unico por propriedade.
    Implementacoes: RepositorioSafrasMongo e RepositorioSafrasMemoria (testes).
*/
public interface RepositorioSafras
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    // grava e preenche o _id; false se o nome ja existir na propriedade
    public boolean criar (Document safra) throws Exception;

    // $set dos campos; false se o novo nome ja existir em outra safra da propriedade
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    // false se a safra nao existir na propriedade
    public boolean excluir (String propriedadeId, String id) throws Exception;
}
