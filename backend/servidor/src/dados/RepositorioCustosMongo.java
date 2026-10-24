/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/*
    Lancamentos de custo na colecao "custos" do MongoDB.
    Indice (propriedadeId, date) para as consultas por periodo da propriedade.
*/
public class RepositorioCustosMongo implements RepositorioCustos
{
    private final MongoCollection<Document> custos;

    public RepositorioCustosMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.custos = banco.getCollection ("custos");

        this.custos.createIndex (Indexes.ascending ("propriedadeId", "date"));
    }

    // filtro por _id que tambem exige a propriedade (um usuario nunca enxerga custo de outro)
    private static Bson filtro (String propriedadeId, String id)
    {
        Bson ret = null;

        if (id != null && ObjectId.isValid (id))
            ret = Filters.and (Filters.eq ("_id", new ObjectId (id)), Filters.eq ("propriedadeId", propriedadeId));

        return ret;
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        return this.custos.find (Filters.eq ("propriedadeId", propriedadeId))
                          .sort (Sorts.ascending ("_id"))
                          .into (new ArrayList<Document> ());
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret    = null;
        Bson     filtro = RepositorioCustosMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.custos.find (filtro).first ();

        return ret;
    }

    public void criar (Document custo) throws Exception
    {
        if (custo == null)
            throw new Exception ("Custo ausente");

        custo.put ("_id", new ObjectId ());
        this.custos.insertOne (custo);
    }

    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        boolean ret    = false;
        Bson    filtro = RepositorioCustosMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.custos.updateOne (filtro, new Document ("$set", campos)).getMatchedCount () == 1;

        return ret;
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        boolean ret    = false;
        Bson    filtro = RepositorioCustosMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.custos.deleteOne (filtro).getDeletedCount () == 1;

        return ret;
    }
}
