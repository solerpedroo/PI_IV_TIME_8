/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Orcamentos em memoria, para os testes e o modo --memoria.
    Metodos synchronized, sempre devolve copias.
*/
public class RepositorioOrcamentosMemoria implements RepositorioOrcamentos
{
    // chave: propriedadeId + "/" + safraId (um orcamento por safra)
    private final LinkedHashMap<String, Document> orcamentos = new LinkedHashMap<String, Document> ();

    private static String chave (String propriedadeId, String safraId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        if (safraId == null)
            throw new Exception ("Safra ausente");

        return propriedadeId + "/" + safraId;
    }

    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document orcamento : this.orcamentos.values ())
            if (propriedadeId.equals (orcamento.getString ("propriedadeId")))
                ret.add (new Document (orcamento));

        return ret;
    }

    public synchronized Document buscarPorSafra (String propriedadeId, String safraId) throws Exception
    {
        Document ret       = null;
        Document orcamento = this.orcamentos.get (RepositorioOrcamentosMemoria.chave (propriedadeId, safraId));

        if (orcamento != null)
            ret = new Document (orcamento);

        return ret;
    }

    public synchronized boolean salvar (Document orcamento) throws Exception
    {
        if (orcamento == null)
            throw new Exception ("Orcamento ausente");

        String   chave = RepositorioOrcamentosMemoria.chave (orcamento.getString ("propriedadeId"),
                                                              orcamento.getString ("safraId"));
        Document atual = this.orcamentos.get (chave);
        boolean  ret   = atual == null;

        if (ret)
        {
            atual = new Document ("_id", new ObjectId ());
            this.orcamentos.put (chave, atual);
        }
        else
            orcamento.remove ("criadoEm"); // o cadastro original continua valendo

        atual.putAll (orcamento);
        orcamento.put ("_id", atual.get ("_id"));

        return ret;
    }

    public synchronized boolean excluir (String propriedadeId, String safraId) throws Exception
    {
        return this.orcamentos.remove (RepositorioOrcamentosMemoria.chave (propriedadeId, safraId)) != null;
    }
}
