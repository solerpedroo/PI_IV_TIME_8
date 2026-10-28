/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/* Saldo e histórico separados, gravados na mesma transação (Mongo replica set).
   withTransaction repete conflitos transitórios e confirmações de resultado incerto. */
public class RepositorioEstoqueMongo implements RepositorioEstoque
{
    private final ConexaoMongo conexao;
    private final MongoCollection<Document> insumos;
    private final MongoCollection<Document> movimentos;
    private final MongoCollection<Document> atividades;

    public RepositorioEstoqueMongo (ConexaoMongo conexao) throws Exception
    {
        if (conexao == null) throw new Exception ("Conexão ausente");
        this.conexao = conexao;
        this.insumos = conexao.getBanco ().getCollection ("insumos");
        this.movimentos = conexao.getBanco ().getCollection ("movimentacoes_insumos");
        this.atividades = conexao.getBanco ().getCollection ("atividades");
        this.insumos.createIndex (Indexes.ascending ("propriedadeId", "excluido"));
        this.movimentos.createIndex (Indexes.ascending ("propriedadeId", "insumoId", "criadoEm"));
    }

    private static Bson filtro (String propriedadeId, String id)
    {
        if (id == null || !ObjectId.isValid (id)) return null;
        return Filters.and (Filters.eq ("_id", new ObjectId (id)), Filters.eq ("propriedadeId", propriedadeId));
    }

    public List<Document> listar (String propriedadeId)
    {
        return this.insumos.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId), Filters.ne ("excluido", true)))
            .sort (Sorts.ascending ("_id")).into (new ArrayList<> ());
    }

    public Document buscarPorId (String propriedadeId, String id)
    {
        Bson filtro = filtro (propriedadeId, id);
        return filtro == null ? null : this.insumos.find (filtro).first ();
    }

    public boolean criar (Document insumo)
    {
        insumo.put ("_id", new ObjectId ());
        try (ClientSession sessao = this.conexao.getCliente ().startSession ())
        {
            return sessao.withTransaction (() -> {
                this.insumos.insertOne (sessao, new Document (insumo));
                if (CalculosEstoque.numero (insumo, "saldo") > 0)
                    this.movimentos.insertOne (sessao, CalculosEstoque.saldoInicial (insumo));
                return true;
            });
        }
    }

    public boolean atualizar (String propriedadeId, String id, Document campos)
    {
        Bson filtro = filtro (propriedadeId, id);
        return filtro != null && this.insumos.updateOne (Filters.and (filtro, Filters.ne ("excluido", true)),
            new Document ("$set", campos)).getMatchedCount () == 1;
    }

    public boolean excluir (String propriedadeId, String id)
    {
        Bson filtro = filtro (propriedadeId, id);
        return filtro != null && this.insumos.updateOne (Filters.and (filtro, Filters.eq ("saldo", 0.0), Filters.ne ("excluido", true)),
            new Document ("$set", new Document ("excluido", true))).getModifiedCount () == 1;
    }

    public Document movimentar (String propriedadeId, String id, Document movimento)
    {
        Bson filtro = filtro (propriedadeId, id);
        if (filtro == null) return null;
        try (ClientSession sessao = this.conexao.getCliente ().startSession ())
        {
            return sessao.withTransaction (() -> {
                Document item = this.insumos.find (sessao, Filters.and (filtro, Filters.ne ("excluido", true))).first ();
                if (item == null) return null;
                Document atualizado = CalculosEstoque.movimentar (item, movimento);
                if (atualizado == null) return null;
                movimento.append ("_id", new ObjectId ()).append ("propriedadeId", propriedadeId).append ("insumoId", id);
                this.insumos.updateOne (sessao, filtro, new Document ("$set", new Document ("saldo", atualizado.get ("saldo"))
                    .append ("custoUnitario", atualizado.get ("custoUnitario")).append ("atualizadoEm", atualizado.get ("atualizadoEm"))));
                this.movimentos.insertOne (sessao, new Document (movimento));
                return atualizado;
            });
        }
    }

    public List<Document> listarMovimentacoes (String propriedadeId, String id)
    {
        Bson filtro = Filters.eq ("propriedadeId", propriedadeId);
        if (id != null) filtro = Filters.and (filtro, Filters.eq ("insumoId", id));
        return this.movimentos.find (filtro).sort (Sorts.descending ("criadoEm", "_id")).into (new ArrayList<> ());
    }

    // Compatível com a modelagem de atividades da #6: IDs BSON ou strings.
    private static Bson referencia (String campo, String id)
    {
        return ObjectId.isValid (id) ? Filters.or (Filters.eq (campo, id), Filters.eq (campo, new ObjectId (id))) : Filters.eq (campo, id);
    }

    private static Bson escopoAtividades (String usuarioId, String propriedadeId)
    {
        return Filters.and (referencia ("propriedadeId", propriedadeId),
            Filters.or (referencia ("usuarioId", usuarioId), referencia ("proprietarioId", usuarioId)));
    }

    public List<Document> listarAtividades (String usuarioId, String propriedadeId)
    {
        return this.atividades.find (escopoAtividades (usuarioId, propriedadeId)).sort (Sorts.ascending ("_id")).into (new ArrayList<> ());
    }

    public Document buscarAtividade (String usuarioId, String propriedadeId, String id)
    {
        if (!ObjectId.isValid (id)) return null;
        return this.atividades.find (Filters.and (escopoAtividades (usuarioId, propriedadeId), Filters.eq ("_id", new ObjectId (id)))).first ();
    }
}
