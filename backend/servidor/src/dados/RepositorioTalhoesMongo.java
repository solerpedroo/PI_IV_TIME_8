/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

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
    Talhoes na colecao "talhoes" do MongoDB.
    Indice unico (propriedadeId, nomeNormalizado) = nome unico por propriedade.
*/
public class RepositorioTalhoesMongo implements RepositorioTalhoes
{
    private final MongoCollection<Document> talhoes;

    public RepositorioTalhoesMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.talhoes = banco.getCollection ("talhoes");

        this.talhoes.createIndex (Indexes.ascending ("propriedadeId", "nomeNormalizado"),
                                  new IndexOptions ().unique (true));
    }

    // filtro por _id que tambem exige a propriedade (um usuario nunca enxerga talhao de outro)
    private static Bson filtro (String propriedadeId, String id)
    {
        if (id == null || !ObjectId.isValid (id))
            return null;

        return Filters.and (Filters.eq ("_id", new ObjectId (id)), Filters.eq ("propriedadeId", propriedadeId));
    }

    private static boolean nomeRepetido (MongoWriteException erro)
    {
        return erro.getError ().getCategory () == ErrorCategory.DUPLICATE_KEY;
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        return this.talhoes.find (Filters.eq ("propriedadeId", propriedadeId))
                           .sort (Sorts.ascending ("_id"))
                           .into (new ArrayList<Document> ());
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Bson filtro = RepositorioTalhoesMongo.filtro (propriedadeId, id);
        if (filtro == null)
            return null;

        return this.talhoes.find (filtro).first ();
    }

    public boolean criar (Document talhao) throws Exception
    {
        if (talhao == null)
            throw new Exception ("Talhao ausente");

        talhao.put ("_id", new ObjectId ());

        try
        {
            this.talhoes.insertOne (talhao);
            return true;
        }
        catch (MongoWriteException erro)
        {
            if (RepositorioTalhoesMongo.nomeRepetido (erro))
                return false;
            throw erro;
        }
    }

    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        Bson filtro = RepositorioTalhoesMongo.filtro (propriedadeId, id);
        if (filtro == null)
            return true;

        try
        {
            this.talhoes.updateOne (filtro, new Document ("$set", campos));
            return true;
        }
        catch (MongoWriteException erro)
        {
            if (RepositorioTalhoesMongo.nomeRepetido (erro))
                return false;
            throw erro;
        }
    }
}
