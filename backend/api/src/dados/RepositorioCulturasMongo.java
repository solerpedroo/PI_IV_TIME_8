package dados;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.ObjectId;

public class RepositorioCulturasMongo implements RepositorioCulturas
{
    private final MongoCollection<Document> culturas;

    public RepositorioCulturasMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.culturas = banco.getCollection ("culturas");
        this.culturas.createIndex (Indexes.ascending ("propriedadeId", "nomeNormalizado"),
                                   new IndexOptions ().unique (true).name ("cultura_nome_por_propriedade"));
    }

    private static String normalizado (String nome)
    {
        return nome.trim ().toLowerCase (Locale.ROOT);
    }

    private static Document publico (Document doc)
    {
        return new Document ("id", doc.getObjectId ("_id").toHexString ())
                   .append ("nome", doc.getString ("nome"))
                   .append ("criadoEm", doc.get ("criadoEm"));
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document doc : this.culturas.find (Filters.eq ("propriedadeId", propriedadeId))
                                          .sort (Sorts.ascending ("nome")))
            ret.add (RepositorioCulturasMongo.publico (doc));

        return ret;
    }

    public void garantirCatalogoInicial (String propriedadeId, String proprietarioId) throws Exception
    {
        // Catálogo vazio até o usuário cadastrar em Culturas.
    }

    public boolean existeNome (String propriedadeId, String nome) throws Exception
    {
        if (propriedadeId == null || nome == null || nome.isBlank ())
            return false;

        return this.culturas.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                Filters.eq ("nomeNormalizado", RepositorioCulturasMongo.normalizado (nome))))
                          .first () != null;
    }

    public Document criar (String propriedadeId, String proprietarioId, String nome) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        if (proprietarioId == null)
            throw new Exception ("Titular ausente");

        String limpo = nome == null ? "" : nome.trim ();
        if (limpo.length () < 2)
            throw new Exception ("Nome curto");

        if (this.existeNome (propriedadeId, limpo))
            return null;

        Document doc = new Document ("_id", new ObjectId ())
                           .append ("propriedadeId",    propriedadeId)
                           .append ("proprietarioId",   proprietarioId)
                           .append ("nome",             limpo)
                           .append ("nomeNormalizado", RepositorioCulturasMongo.normalizado (limpo))
                           .append ("criadoEm",         Instant.now ().toString ());

        try
        {
            this.culturas.insertOne (doc);
        }
        catch (MongoWriteException erro)
        {
            if (erro.getError ().getCategory () == ErrorCategory.DUPLICATE_KEY)
                return null;

            throw erro;
        }

        return RepositorioCulturasMongo.publico (doc);
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return null;

        Document doc = this.culturas.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                        Filters.eq ("_id", new ObjectId (id))))
                                  .first ();

        return doc == null ? null : RepositorioCulturasMongo.publico (doc);
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return false;

        return this.culturas.deleteOne (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                      Filters.eq ("_id", new ObjectId (id))))
                            .getDeletedCount () > 0;
    }
}
