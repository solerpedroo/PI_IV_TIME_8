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
    Colheitas em memoria, para os testes e o modo --memoria.
    Metodos synchronized, sempre devolve copias.
*/
public class RepositorioColheitasMemoria implements RepositorioColheitas
{
    private final LinkedHashMap<String, Document> colheitas = new LinkedHashMap<String, Document> ();

    // a colheita de outra propriedade e tratada como inexistente
    private Document daPropriedade (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret      = null;
        Document colheita = id == null ? null : this.colheitas.get (id);

        if (colheita != null && propriedadeId.equals (colheita.getString ("propriedadeId")))
            ret = colheita;

        return ret;
    }

    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document colheita : this.colheitas.values ())
            if (propriedadeId.equals (colheita.getString ("propriedadeId")))
                ret.add (new Document (colheita));

        return ret;
    }

    public synchronized Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        Document ret      = null;
        Document colheita = this.daPropriedade (propriedadeId, id);

        if (colheita != null)
            ret = new Document (colheita);

        return ret;
    }

    public synchronized void criar (Document colheita) throws Exception
    {
        if (colheita == null)
            throw new Exception ("Colheita ausente");

        ObjectId id = new ObjectId ();
        colheita.put ("_id", id);
        this.colheitas.put (id.toHexString (), new Document (colheita));
    }

    public synchronized boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        Document colheita = this.daPropriedade (propriedadeId, id);
        if (colheita == null)
            return false;

        colheita.putAll (campos);
        return true;
    }

    public synchronized boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (this.daPropriedade (propriedadeId, id) == null)
            return false;

        this.colheitas.remove (id);
        return true;
    }
}
