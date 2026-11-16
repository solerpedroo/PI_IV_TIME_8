/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados.estoque;

import dados.infra.ConexaoMongo;
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

/*
    Estoque no MongoDB. O saldo mora na coleção insumos e o histórico na
    coleção movimentacoes_insumos. As duas escrituras de criar e de
    movimentar usam a mesma ClientSession e withTransaction: ou o saldo
    e o lançamento confirmam juntos, ou nenhum fica gravado.
    withTransaction repete conflitos transitórios e confirmações de
    resultado incerto. Transação no Mongo exige replica set.

    O isolamento é o filtro propriedadeId em toda leitura e escrita.
    Os índices (propriedadeId, excluido) e (propriedadeId, insumoId, criadoEm)
    apoiam a listagem do catálogo e do histórico de uma fazenda.
    Atividades são só leitura na coleção atividades.
*/
public class RepositorioEstoqueMongo implements RepositorioEstoque
{
    private final ConexaoMongo conexao;
    private final MongoCollection<Document> insumos;
    private final MongoCollection<Document> movimentos;
    private final MongoCollection<Document> atividades;

    /* Abre as três coleções e garante os índices usados pelo filtro de propriedade. */
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

    /* _id e propriedadeId juntos. Id inválido não vira consulta e o método devolve null. */
    private static Bson filtro (String propriedadeId, String id)
    {
        if (id == null || !ObjectId.isValid (id)) return null;
        return Filters.and (Filters.eq ("_id", new ObjectId (id)), Filters.eq ("propriedadeId", propriedadeId));
    }

    /* Catálogo da fazenda, sem excluido true, em ordem estável de _id. */
    public List<Document> listar (String propriedadeId)
    {
        return this.insumos.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId), Filters.ne ("excluido", true)))
            .sort (Sorts.ascending ("_id")).into (new ArrayList<> ());
    }

    /* Um documento desta propriedade, inclusive arquivado. */
    public Document buscarPorId (String propriedadeId, String id)
    {
        Bson filtro = filtro (propriedadeId, id);
        return filtro == null ? null : this.insumos.find (filtro).first ();
    }

    /* Transação: insere o insumo e, se o saldo inicial for positivo, a movimentação de abertura. */
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

    /* $set só no documento ativo desta propriedade. Não altera saldo nem histórico. */
    public boolean atualizar (String propriedadeId, String id, Document campos)
    {
        Bson filtro = filtro (propriedadeId, id);
        return filtro != null && this.insumos.updateOne (Filters.and (filtro, Filters.ne ("excluido", true)),
            new Document ("$set", campos)).getMatchedCount () == 1;
    }

    /* Marca excluido apenas se o saldo gravado for 0.0. O histórico em movimentacoes_insumos permanece. */
    public boolean excluir (String propriedadeId, String id)
    {
        Bson filtro = filtro (propriedadeId, id);
        return filtro != null && this.insumos.updateOne (Filters.and (filtro, Filters.eq ("saldo", 0.0), Filters.ne ("excluido", true)),
            new Document ("$set", new Document ("excluido", true))).getModifiedCount () == 1;
    }

    /*
        Transação: lê o saldo, calcula a baixa e só então atualiza saldo,
        custo médio e data no insumo e insere a movimentação. Saída maior
        que o saldo aborta sem gravar.
    */
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

    /* Movimentações da fazenda, ou de um insumo, da mais recente para a mais antiga. */
    public List<Document> listarMovimentacoes (String propriedadeId, String id)
    {
        Bson filtro = Filters.eq ("propriedadeId", propriedadeId);
        if (id != null) filtro = Filters.and (filtro, Filters.eq ("insumoId", id));
        return this.movimentos.find (filtro).sort (Sorts.descending ("criadoEm", "_id")).into (new ArrayList<> ());
    }

    /* Compatível com a modelagem de atividades da #6: o mesmo id pode estar como ObjectId ou como string. */
    private static Bson referencia (String campo, String id)
    {
        return ObjectId.isValid (id) ? Filters.or (Filters.eq (campo, id), Filters.eq (campo, new ObjectId (id))) : Filters.eq (campo, id);
    }

    /* Atividade desta propriedade e deste usuário (usuarioId ou proprietarioId). */
    private static Bson escopoAtividades (String usuarioId, String propriedadeId)
    {
        return Filters.and (referencia ("propriedadeId", propriedadeId),
            Filters.or (referencia ("usuarioId", usuarioId), referencia ("proprietarioId", usuarioId)));
    }

    /* Leitura da coleção atividades, sem criar nem alterar atividade a partir do estoque. */
    public List<Document> listarAtividades (String usuarioId, String propriedadeId)
    {
        return this.atividades.find (escopoAtividades (usuarioId, propriedadeId)).sort (Sorts.ascending ("_id")).into (new ArrayList<> ());
    }

    /* Uma atividade do escopo, pelo _id. Id que não é ObjectId válido não consulta o banco. */
    public Document buscarAtividade (String usuarioId, String propriedadeId, String id)
    {
        if (!ObjectId.isValid (id)) return null;
        return this.atividades.find (Filters.and (escopoAtividades (usuarioId, propriedadeId), Filters.eq ("_id", new ObjectId (id)))).first ();
    }
}
