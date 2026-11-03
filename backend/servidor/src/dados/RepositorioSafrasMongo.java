/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/*
    Safras na colecao "safras" do MongoDB.
    Indice unico (propriedadeId, nomeNormalizado) = nome unico por propriedade.
*/
public class RepositorioSafrasMongo implements RepositorioSafras
{
    private final MongoCollection<Document> safras;

    public RepositorioSafrasMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.safras = banco.getCollection ("safras");

        this.safras.createIndex (Indexes.ascending ("propriedadeId", "nomeNormalizado"),
                                 new IndexOptions ().unique (true));
    }

    // filtro por _id que tambem exige a propriedade (um usuario nunca enxerga safra de outro)
    private static Bson filtro (String propriedadeId, String id)
    {
        Bson ret = null;

        if (id != null && ObjectId.isValid (id))
            ret = Filters.and (Filters.eq ("_id", new ObjectId (id)), Filters.eq ("propriedadeId", propriedadeId));

        return ret;
    }

    private static boolean nomeRepetido (MongoWriteException erro)
    {
        return erro.getError ().getCategory () == ErrorCategory.DUPLICATE_KEY;
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        return this.safras.find (Filters.eq ("propriedadeId", propriedadeId))
                          .sort (Sorts.ascending ("_id"))
                          .into (new ArrayList<Document> ());
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret    = null;
        Bson     filtro = RepositorioSafrasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.safras.find (filtro).first ();

        return ret;
    }

    public boolean criar (Document safra) throws Exception
    {
        if (safra == null)
            throw new Exception ("Safra ausente");

        safra.put ("_id", new ObjectId ());

        try
        {
            this.safras.insertOne (safra);
            return true;
        }
        catch (MongoWriteException erro)
        {
            if (RepositorioSafrasMongo.nomeRepetido (erro))
                return false;
            throw erro;
        }
    }

    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        Bson filtro = RepositorioSafrasMongo.filtro (propriedadeId, id);
        if (filtro == null)
            return true;

        try
        {
            this.safras.updateOne (filtro, new Document ("$set", campos));
            return true;
        }
        catch (MongoWriteException erro)
        {
            if (RepositorioSafrasMongo.nomeRepetido (erro))
                return false;
            throw erro;
        }
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        boolean ret    = false;
        Bson    filtro = RepositorioSafrasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.safras.deleteOne (filtro).getDeletedCount () == 1;

        return ret;
    }
}
