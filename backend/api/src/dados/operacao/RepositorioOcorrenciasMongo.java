/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
package dados.operacao;

import dados.infra.ConexaoMongo;
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

/*
    Ocorrências no MongoDB, em duas coleções e uma transação: ocorrencias
    guarda o cadastro, o status (ABERTA, EM_ACOMPANHAMENTO, RESOLVIDA) e a
    versão; ocorrencias_timeline guarda cada evento. criar e salvar usam
    withTransaction para nunca confirmar o status sem o respectivo histórico.
    MongoDB precisa de replica set (Atlas ou a configuração local descrita
    na documentação) para a transação funcionar.

    O isolamento é o filtro usuarioId + propriedadeId. O índice único
    (usuarioId, propriedadeId, ocorrenciaId, ordem) impede dois eventos
    com a mesma ordem na mesma ocorrência.
*/
public class RepositorioOcorrenciasMongo implements RepositorioOcorrencias
{
    private final ConexaoMongo conexao;
    private final MongoCollection<Document> registros;
    private final MongoCollection<Document> eventos;

    /* Abre as duas coleções e cria os índices de consulta e o índice único da ordem da timeline. */
    public RepositorioOcorrenciasMongo (ConexaoMongo conexao)
    {
        this.conexao = conexao;
        this.registros = conexao.getBanco ().getCollection ("ocorrencias");
        this.eventos = conexao.getBanco ().getCollection ("ocorrencias_timeline");
        this.registros.createIndex (Indexes.ascending ("usuarioId", "propriedadeId", "talhaoId"));
        this.eventos.createIndex (Indexes.ascending ("usuarioId", "propriedadeId", "ocorrenciaId", "ordem"),
                                 new IndexOptions ().unique (true));
    }

    /* Filtro de isolamento: só documentos deste usuário nesta propriedade. */
    private static Bson escopo (String usuario, String propriedade)
    {
        return Filters.and (Filters.eq ("usuarioId", usuario), Filters.eq ("propriedadeId", propriedade));
    }

    /* Lista da coleção ocorrencias, da mais recente para a mais antiga. */
    public List<Document> listar (String usuario, String propriedade)
    {
        return this.registros.find (escopo (usuario, propriedade)).sort (Sorts.descending ("criadaEm", "_id"))
                             .into (new ArrayList<> ());
    }

    /* Um documento do escopo. Id que não é ObjectId válido nem chega ao banco. */
    public Document buscar (String usuario, String propriedade, String id)
    {
        if (id == null || !ObjectId.isValid (id)) return null;
        return this.registros.find (Filters.and (escopo (usuario, propriedade), Filters.eq ("_id", new ObjectId (id)))).first ();
    }

    /* Eventos da coleção ocorrencias_timeline, ordenados pelo campo ordem. */
    public List<Document> timeline (String usuario, String propriedade, String id)
    {
        return this.eventos.find (Filters.and (escopo (usuario, propriedade), Filters.eq ("ocorrenciaId", id)))
                           .sort (Sorts.ascending ("ordem")).into (new ArrayList<> ());
    }

    /* Transação: insere o cadastro e o evento inicial. Se um falhar, o outro não permanece. */
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

    /* Transação: substitui o cadastro na versão esperada e insere o evento da timeline. */
    public boolean salvar (Document anterior, Document nova, Document evento)
    {
        try (ClientSession sessao = this.conexao.getCliente ().startSession ())
        {
            /*
                replaceOne só casa se _id, dono, propriedade e versão ainda forem
                os de anterior. Se outro cliente gravar primeiro, o retry
                transacional encontra versão diferente e a transação devolve false
                sem inserir evento.
            */
            return sessao.withTransaction (() -> {
                Bson filtro = Filters.and (escopo (anterior.getString ("usuarioId"), anterior.getString ("propriedadeId")),
                    Filters.eq ("_id", anterior.get ("_id")), Filters.eq ("versao", anterior.get ("versao")));
                if (this.registros.replaceOne (sessao, filtro, nova).getModifiedCount () != 1) return false;
                this.eventos.insertOne (sessao, evento);
                return true;
            });
        }
    }

    /* Transação só no cadastro: mesma versão, sem documento novo em ocorrencias_timeline. */
    public boolean substituirComVersao (Document anterior, Document nova)
    {
        try (ClientSession sessao = this.conexao.getCliente ().startSession ())
        {
            return sessao.withTransaction (() -> {
                Bson filtro = Filters.and (escopo (anterior.getString ("usuarioId"), anterior.getString ("propriedadeId")),
                    Filters.eq ("_id", anterior.get ("_id")), Filters.eq ("versao", anterior.get ("versao")));
                return this.registros.replaceOne (sessao, filtro, nova).getModifiedCount () == 1;
            });
        }
    }

    /* Existe ao menos uma ocorrência desta propriedade, de qualquer usuário. limit 1 evita varrer a coleção. */
    public boolean temRegistros (String propriedade)
    {
        return this.registros.find (Filters.eq ("propriedadeId", propriedade)).limit (1).first () != null;
    }
}
