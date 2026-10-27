/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.List;
import org.bson.Document;

/*
    Lancamentos de custo, sempre filtrados pela propriedade (isolamento entre usuarios).
    Implementacoes: RepositorioCustosMongo e RepositorioCustosMemoria (testes).
*/
public interface RepositorioCustos
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    // grava e preenche o _id
    public void criar (Document custo) throws Exception;

    // $set dos campos; false se o lancamento nao existir na propriedade
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    // false se o lancamento nao existir na propriedade
    public boolean excluir (String propriedadeId, String id) throws Exception;
}
