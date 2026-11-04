/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
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
    Equipamentos na colecao "equipamentos" do MongoDB.
    Indice unico (propriedadeId, nomeNormalizado): nome unico por propriedade mesmo entre servidores.
*/
public class RepositorioEquipamentosMongo implements RepositorioEquipamentos
{
    private final MongoCollection<Document> equipamentos;

    public RepositorioEquipamentosMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.equipamentos = banco.getCollection ("equipamentos");

        this.equipamentos.createIndex (Indexes.ascending ("propriedadeId", "nomeNormalizado"),
                                       new IndexOptions ().unique (true));
    }

    // filtro por _id que tambem exige a propriedade (um usuario nunca enxerga equipamento de outro)
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

        return this.equipamentos.find (Filters.eq ("propriedadeId", propriedadeId))
                                .sort (Sorts.ascending ("_id"))
                                .into (new ArrayList<Document> ());
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret    = null;
        Bson     filtro = RepositorioEquipamentosMongo.filtro (propriedadeId, id);

        if (filtro != null)
            ret = this.equipamentos.find (filtro).first ();

        return ret;
    }

    public boolean criar (Document equipamento) throws Exception
    {
        if (equipamento == null)
            throw new Exception ("Equipamento ausente");

        boolean ret = true;

        try
        {
            equipamento.put ("_id", new ObjectId ());
            this.equipamentos.insertOne (equipamento);
        }
        catch (MongoWriteException erro)
        {
            if (!RepositorioEquipamentosMongo.nomeRepetido (erro))
                throw erro;

            equipamento.remove ("_id");
            ret = false;
        }

        return ret;
    }

    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        boolean ret    = true;
        Bson    filtro = RepositorioEquipamentosMongo.filtro (propriedadeId, id);

        if (filtro != null)
        {
            try
            {
                this.equipamentos.updateOne (filtro, new Document ("$set", campos));
            }
            catch (MongoWriteException erro)
            {
                if (!RepositorioEquipamentosMongo.nomeRepetido (erro))
                    throw erro;

                ret = false;
            }
        }

        return ret;
    }
}
