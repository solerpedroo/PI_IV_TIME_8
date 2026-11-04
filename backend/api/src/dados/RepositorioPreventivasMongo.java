/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
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
    Manutencoes preventivas programadas dos equipamentos na colecao "preventivas" do MongoDB.
    Indice (propriedadeId, dataPrevista) para a agenda.
*/
public class RepositorioPreventivasMongo implements RepositorioPreventivas
{
    private final MongoCollection<Document> registros;

    public RepositorioPreventivasMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.registros = banco.getCollection ("preventivas");

        this.registros.createIndex (Indexes.ascending ("propriedadeId", "dataPrevista"));
    }

    // filtro por _id que tambem exige a propriedade (um usuario nunca enxerga registro de outro)
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

        return this.registros.find (Filters.eq ("propriedadeId", propriedadeId))
                             .sort (Sorts.ascending ("_id"))
                             .into (new ArrayList<Document> ());
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret    = null;
        Bson     filtro = RepositorioPreventivasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.registros.find (filtro).first ();

        return ret;
    }

    public void criar (Document preventiva) throws Exception
    {
        if (preventiva == null)
            throw new Exception ("Registro ausente");

        preventiva.put ("_id", new ObjectId ());
        this.registros.insertOne (preventiva);
    }

    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        boolean ret    = false;
        Bson    filtro = RepositorioPreventivasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.registros.updateOne (filtro, new Document ("$set", campos)).getMatchedCount () == 1;

        return ret;
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        boolean ret    = false;
        Bson    filtro = RepositorioPreventivasMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.registros.deleteOne (filtro).getDeletedCount () == 1;

        return ret;
    }
}
