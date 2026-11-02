/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.List;
import org.bson.Document;

/*
    Orcamento planejado de cada safra, sempre filtrado pela propriedade (isolamento entre usuarios).
    No maximo um orcamento por safra: (propriedadeId, safraId) e a chave.
    Implementacoes: RepositorioOrcamentosMongo e RepositorioOrcamentosMemoria (testes).
*/
public interface RepositorioOrcamentos
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    // null se a safra ainda nao tem orcamento
    public Document buscarPorSafra (String propriedadeId, String safraId) throws Exception;

    // cria ou substitui os campos do orcamento da safra; true se criou, false se alterou
    public boolean salvar (Document orcamento) throws Exception;

    // false se a safra nao tinha orcamento
    public boolean excluir (String propriedadeId, String safraId) throws Exception;
}
