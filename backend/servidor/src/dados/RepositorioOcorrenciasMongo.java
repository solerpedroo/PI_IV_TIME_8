/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/* Duas coleções, uma transação: nunca confirmamos o status sem o respectivo histórico.
   MongoDB precisa de replica set (Atlas ou configuração local descrita na documentação). */
public class RepositorioOcorrenciasMongo implements RepositorioOcorrencias
{
    private final ConexaoMongo conexao;
    private final MongoCollection<Document> registros;
    private final MongoCollection<Document> eventos;

    public RepositorioOcorrenciasMongo (ConexaoMongo conexao)
    {
        this.conexao = conexao;
        this.registros = conexao.getBanco ().getCollection ("ocorrencias");
        this.eventos = conexao.getBanco ().getCollection ("ocorrencias_timeline");
        this.registros.createIndex (Indexes.ascending ("usuarioId", "propriedadeId", "talhaoId"));
        this.eventos.createIndex (Indexes.ascending ("usuarioId", "propriedadeId", "ocorrenciaId", "ordem"),
                                 new IndexOptions ().unique (true));
    }

    private static Bson escopo (String usuario, String propriedade)
    {
        return Filters.and (Filters.eq ("usuarioId", usuario), Filters.eq ("propriedadeId", propriedade));
    }

    public List<Document> listar (String usuario, String propriedade)
    {
        return this.registros.find (escopo (usuario, propriedade)).sort (Sorts.descending ("criadaEm", "_id"))
                             .into (new ArrayList<> ());
    }

    public Document buscar (String usuario, String propriedade, String id)
    {
        if (id == null || !ObjectId.isValid (id)) return null;
        return this.registros.find (Filters.and (escopo (usuario, propriedade), Filters.eq ("_id", new ObjectId (id)))).first ();
    }

    public List<Document> timeline (String usuario, String propriedade, String id)
    {
        return this.eventos.find (Filters.and (escopo (usuario, propriedade), Filters.eq ("ocorrenciaId", id)))
                           .sort (Sorts.ascending ("ordem")).into (new ArrayList<> ());
    }

    public void criar (Document ocorrencia, Document evento)
    {
        try (ClientSession sessao = this.conexao.getCliente ().startSession ())
        {
            sessao.withTransaction (() -> {
                this.registros.insertOne (sessao, ocorrencia);
                this.eventos.insertOne (sessao, evento);
                return true;
            });
        }
    }

    public boolean salvar (Document anterior, Document nova, Document evento)
    {
        try (ClientSession sessao = this.conexao.getCliente ().startSession ())
        {
            // Se outro cliente gravar primeiro, o retry transacional encontra versão diferente.
            return sessao.withTransaction (() -> {
                Bson filtro = Filters.and (escopo (anterior.getString ("usuarioId"), anterior.getString ("propriedadeId")),
                    Filters.eq ("_id", anterior.get ("_id")), Filters.eq ("versao", anterior.get ("versao")));
                if (this.registros.replaceOne (sessao, filtro, nova).getModifiedCount () != 1) return false;
                this.eventos.insertOne (sessao, evento);
                return true;
            });
        }
    }

    public boolean temRegistros (String propriedade)
    {
        return this.registros.find (Filters.eq ("propriedadeId", propriedade)).limit (1).first () != null;
    }
}
