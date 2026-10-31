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
    Colheitas na colecao "colheitas" do MongoDB.
    Indices (propriedadeId, safraId) para o comparativo de ciclos e (propriedadeId, data) para o periodo.
*/
public class RepositorioColheitasMongo implements RepositorioColheitas
{
    private final MongoCollection<Document> colheitas;

    public RepositorioColheitasMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.colheitas = banco.getCollection ("colheitas");

        this.colheitas.createIndex (Indexes.ascending ("propriedadeId", "safraId"));
        this.colheitas.createIndex (Indexes.ascending ("propriedadeId", "data"));
    }

    // filtro por _id que tambem exige a propriedade (um usuario nunca enxerga colheita de outro)
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

        return this.colheitas.find (Filters.eq ("propriedadeId", propriedadeId))
                             .sort (Sorts.ascending ("_id"))
                             .into (new ArrayList<Document> ());
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret    = null;
        Bson     filtro = RepositorioColheitasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.colheitas.find (filtro).first ();

        return ret;
    }

    public void criar (Document colheita) throws Exception
    {
        if (colheita == null)
            throw new Exception ("Colheita ausente");

        colheita.put ("_id", new ObjectId ());
        this.colheitas.insertOne (colheita);
    }

    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        boolean ret    = false;
        Bson    filtro = RepositorioColheitasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.colheitas.updateOne (filtro, new Document ("$set", campos)).getMatchedCount () == 1;

        return ret;
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        boolean ret    = false;
        Bson    filtro = RepositorioColheitasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.colheitas.deleteOne (filtro).getDeletedCount () == 1;

        return ret;
    }
}
