// Miguel — RA: 25003110
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
import util.TelasAcesso;

public class RepositorioEquipeMongo implements RepositorioEquipe
{
    private final MongoCollection<Document> membros;

    public RepositorioEquipeMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.membros = banco.getCollection ("equipe_membros");
        this.membros.createIndex (Indexes.ascending ("propriedadeId", "nomeNormalizado"),
                                  new IndexOptions ().unique (true).name ("equipe_nome_por_propriedade"));
    }

    private static String normalizado (String nome)
    {
        return nome.trim ().toLowerCase (Locale.ROOT);
    }

    // Campo ausente: a função ficou em branco. null quebraria o schema (bsonType string).
    private static void aplicarCargo (Document doc, String cargo)
    {
        if (cargo == null || cargo.isBlank ())
            doc.remove ("cargo");
        else
            doc.put ("cargo", cargo.trim ());
    }

    private static Document publico (Document doc)
    {
        Document telas = doc.get ("telas", Document.class);
        Document ret = new Document ("id", doc.getObjectId ("_id").toHexString ())
                           .append ("nome", doc.getString ("nome"))
                           .append ("telas", telas == null ? new Document () : new Document (telas))
                           .append ("criadoEm", doc.get ("criadoEm"))
                           .append ("atualizadoEm", doc.get ("atualizadoEm"));
        String cargo = doc.getString ("cargo");
        if (cargo != null && !cargo.isBlank ())
            ret.append ("cargo", cargo);
        return ret;
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document doc : this.membros.find (Filters.eq ("propriedadeId", propriedadeId))
                                        .sort (Sorts.ascending ("nome")))
            ret.add (RepositorioEquipeMongo.publico (doc));

        return ret;
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return null;

        Document doc = this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                       Filters.eq ("_id", new ObjectId (id))))
                                   .first ();
        return doc == null ? null : RepositorioEquipeMongo.publico (doc);
    }

    public Document criar (String propriedadeId, String proprietarioId, Document dados) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        if (proprietarioId == null)
            throw new Exception ("Titular ausente");

        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        if (this.existeNome (propriedadeId, nome, null))
            return null;

        String agora = Instant.now ().toString ();
        Document doc = new Document ("_id", new ObjectId ())
                           .append ("propriedadeId",  propriedadeId)
                           .append ("proprietarioId", proprietarioId)
                           .append ("nome",           nome.trim ())
                           .append ("nomeNormalizado", RepositorioEquipeMongo.normalizado (nome))
                           .append ("telas",          TelasAcesso.normalizar (dados.get ("telas")))
                           .append ("criadoEm",       agora)
                           .append ("atualizadoEm",   agora);
        RepositorioEquipeMongo.aplicarCargo (doc, dados.getString ("cargo"));

        try
        {
            this.membros.insertOne (doc);
        }
        catch (MongoWriteException erro)
        {
            if (erro.getError ().getCategory () == ErrorCategory.DUPLICATE_KEY)
                return null;
            throw erro;
        }

        return RepositorioEquipeMongo.publico (doc);
    }

    public Document atualizar (String propriedadeId, String id, Document dados) throws Exception
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return null;

        Document atual = this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                         Filters.eq ("_id", new ObjectId (id))))
                                     .first ();
        if (atual == null)
            return null;

        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        String chaveNova = RepositorioEquipeMongo.normalizado (nome);
        if (this.existeNome (propriedadeId, nome, atual.getObjectId ("_id")))
            return null;

        atual.append ("nome", nome.trim ())
             .append ("nomeNormalizado", chaveNova)
             .append ("telas", TelasAcesso.normalizar (dados.get ("telas")))
             .append ("atualizadoEm", Instant.now ().toString ());
        RepositorioEquipeMongo.aplicarCargo (atual, dados.getString ("cargo"));

        try
        {
            // O filtro inclui a propriedade para não gravar o _id em outro dono.
            this.membros.replaceOne (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                  Filters.eq ("_id", atual.getObjectId ("_id"))),
                                     atual);
        }
        catch (MongoWriteException erro)
        {
            // Corrida entre duas renomeações: o índice único responde 11000.
            if (erro.getError ().getCategory () == ErrorCategory.DUPLICATE_KEY)
                return null;
            throw erro;
        }

        return RepositorioEquipeMongo.publico (atual);
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return false;

        return this.membros.deleteOne (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                    Filters.eq ("_id", new ObjectId (id))))
                           .getDeletedCount () > 0;
    }

    // ignorar evita tratar o próprio integrante como duplicata na atualização.
    private boolean existeNome (String propriedadeId, String nome, ObjectId ignorar) throws Exception
    {
        if (propriedadeId == null || nome == null || nome.isBlank ())
            return false;

        Document achado = this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                         Filters.eq ("nomeNormalizado", RepositorioEquipeMongo.normalizado (nome))))
                                     .first ();
        if (achado == null)
            return false;

        return ignorar == null || !ignorar.equals (achado.getObjectId ("_id"));
    }
}