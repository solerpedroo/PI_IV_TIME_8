/* Miguel — RA: 25003110 */
package dados;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * Persistência real de atividades e histórico de eventos no MongoDB.
 *
 * As coleções 'atividades' e 'atividades_historico' são isoladas por propriedade.
 * Índices garantem velocidade nas consultas por propriedade e unicidade na ordem
 * cronológica dos eventos de cada atividade.
 */
public class RepositorioAtividadesMongo implements RepositorioAtividades
{
    private final MongoCollection<Document> atividades;
    private final MongoCollection<Document> historicos;

    public RepositorioAtividadesMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.atividades = banco.getCollection ("atividades");
        this.historicos = banco.getCollection ("atividades_historico");

        this.atividades.createIndex (Indexes.ascending ("propriedadeId", "data"));
        this.historicos.createIndex (Indexes.ascending ("propriedadeId", "atividadeId", "ordem"),
                                     new IndexOptions ().unique (true));
    }

    private static Bson filtro (String propriedadeId, String id)
    {
        if (id == null || !ObjectId.isValid (id))
            return null;

        return Filters.and (Filters.eq ("_id", new ObjectId (id)), Filters.eq ("propriedadeId", propriedadeId));
    }

    @Override
    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document d : this.atividades.find (Filters.eq ("propriedadeId", propriedadeId)).sort (Sorts.descending ("_id")))
        {
            d.put ("id", d.getObjectId ("_id").toHexString ());
            ret.add (d);
        }

        return ret;
    }

    @Override
    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        Bson f = filtro (propriedadeId, id);
        if (f == null)
            return null;

        Document d = this.atividades.find (f).first ();
        if (d != null)
            d.put ("id", d.getObjectId ("_id").toHexString ());

        return d;
    }

    @Override
    public boolean criar (Document atividade) throws Exception
    {
        if (atividade == null)
            throw new Exception ("Atividade ausente");

        String propriedadeId = atividade.getString ("propriedadeId");
        if (propriedadeId == null || propriedadeId.isBlank ())
            throw new Exception ("Propriedade ausente na atividade");

        String idStr = atividade.getString ("id");
        ObjectId id = (idStr != null && ObjectId.isValid (idStr)) ? new ObjectId (idStr) : new ObjectId ();
        idStr = id.toHexString ();
        atividade.put ("_id", id);
        atividade.put ("id", idStr);

        if (atividade.getString ("status") == null || atividade.getString ("status").isBlank ())
            atividade.put ("status", "Pendente");

        Instant agora = Instant.now ();
        atividade.put ("criadaEm", agora.toString ());

        this.atividades.insertOne (atividade);

        Document eventoZero = new Document ();
        eventoZero.put ("atividadeId", idStr);
        eventoZero.put ("propriedadeId", propriedadeId);
        eventoZero.put ("ordem", 0);
        eventoZero.put ("registradoEm", agora.toString ());
        eventoZero.put ("status", atividade.getString ("status"));
        eventoZero.put ("acao", "criacao");
        eventoZero.put ("descricao", "Registro inicial da atividade");

        this.historicos.insertOne (eventoZero);
        return true;
    }

    @Override
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        Document atual = this.buscarPorId (propriedadeId, id);
        if (atual == null)
            return false;

        String statusAtual = atual.getString ("status");
        if ("Concluída".equalsIgnoreCase (statusAtual) || "Cancelada".equalsIgnoreCase (statusAtual))
            throw new IllegalStateException ("Atividades concluídas ou canceladas não podem ser alteradas.");

        ArrayList<Bson> updates = new ArrayList<Bson> ();
        if (campos.containsKey ("nome"))
            updates.add (Updates.set ("nome", campos.getString ("nome")));
        if (campos.containsKey ("tipo"))
            updates.add (Updates.set ("tipo", campos.getString ("tipo")));
        if (campos.containsKey ("talhao"))
            updates.add (Updates.set ("talhao", campos.getString ("talhao")));
        if (campos.containsKey ("data"))
            updates.add (Updates.set ("data", campos.getString ("data")));
        if (campos.containsKey ("horario"))
            updates.add (Updates.set ("horario", campos.getString ("horario")));
        if (campos.containsKey ("responsavel"))
            updates.add (Updates.set ("responsavel", campos.getString ("responsavel")));
        if (campos.containsKey ("prioridade"))
            updates.add (Updates.set ("prioridade", campos.getString ("prioridade")));

        if (updates.isEmpty ())
            return true;

        this.atividades.updateOne (filtro (propriedadeId, id), Updates.combine (updates));
        return true;
    }

    @Override
    public boolean mudarStatus (String propriedadeId, String id, String novoStatus, String acao, String descricao) throws Exception
    {
        Document atual = this.buscarPorId (propriedadeId, id);
        if (atual == null)
            return false;

        String statusAtual = atual.getString ("status");
        if ("Concluída".equalsIgnoreCase (statusAtual) || "Cancelada".equalsIgnoreCase (statusAtual))
            throw new IllegalStateException ("Atividade finalizada (" + statusAtual + ") não permite alteração de status.");

        if (statusAtual.equalsIgnoreCase (novoStatus))
            return true;

        this.atividades.updateOne (filtro (propriedadeId, id), Updates.set ("status", novoStatus));

        long contagem = this.historicos.countDocuments (Filters.and (Filters.eq ("propriedadeId", propriedadeId), Filters.eq ("atividadeId", id)));
        Instant agora = Instant.now ();

        Document evento = new Document ();
        evento.put ("atividadeId", id);
        evento.put ("propriedadeId", propriedadeId);
        evento.put ("ordem", (int) contagem);
        evento.put ("registradoEm", agora.toString ());
        evento.put ("status", novoStatus);
        evento.put ("acao", acao != null && !acao.isBlank () ? acao : "mudanca_status");
        evento.put ("descricao", descricao != null && !descricao.isBlank () ? descricao : "Status alterado para " + novoStatus);
        if ("cancelamento".equalsIgnoreCase (acao) || "Cancelada".equalsIgnoreCase (novoStatus))
            evento.put ("motivo", descricao != null ? descricao : "");

        this.historicos.insertOne (evento);
        return true;
    }

    @Override
    public List<Document> listarHistorico (String propriedadeId, String atividadeId) throws Exception
    {
        Document atual = this.buscarPorId (propriedadeId, atividadeId);
        if (atual == null)
            return null;

        ArrayList<Document> ret = new ArrayList<Document> ();
        Bson f = Filters.and (Filters.eq ("propriedadeId", propriedadeId), Filters.eq ("atividadeId", atividadeId));
        for (Document d : this.historicos.find (f).sort (Sorts.ascending ("ordem")))
        {
            d.remove ("_id");
            ret.add (d);
        }

        return ret;
    }
}
