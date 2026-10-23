package dados;

/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.ArrayList;
import java.util.List;
import org.bson.conversions.Bson;

/*
    Usuarios nas colecoes "usuarios" e "propriedades" do MongoDB.
    O indice unico em email garante "e-mail unico" mesmo com cadastros simultaneos.
*/
public class RepositorioUsuariosMongo implements RepositorioUsuarios
{
    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> propriedades;
    private final MongoClient cliente;

    public RepositorioUsuariosMongo (ConexaoMongo conexao) throws Exception
    {
        if (conexao == null)
            throw new Exception ("Conexao ausente");

        MongoDatabase banco = conexao.getBanco ();

        this.usuarios     = banco.getCollection ("usuarios");
        this.propriedades = banco.getCollection ("propriedades");
        this.cliente      = conexao.getCliente ();

        this.usuarios.createIndex (Indexes.ascending ("email"), new IndexOptions ().unique (true));
        this.usuarios.createIndex (Indexes.ascending ("tokenConfirmacao"));
        this.usuarios.createIndex (Indexes.ascending ("tokenRedefinicao"));
        this.propriedades.createIndex (Indexes.ascending ("usuarioId"));
    }

    private static ObjectId paraId (String id)
    {
        if (id == null || !ObjectId.isValid (id))
            return null;

        return new ObjectId (id);
    }

    public Document criar (Document usuario, Document propriedade) throws Exception
    {
        if (usuario == null)
            throw new Exception ("Usuario ausente");

        if (propriedade == null)
            throw new Exception ("Propriedade ausente");

        // o usuario entra primeiro: se o e-mail repetir, nenhuma propriedade orfa fica no banco
        ObjectId idPropriedade = new ObjectId ();
        Document novoUsuario   = new Document (usuario).append ("_id", new ObjectId ())
                                                       .append ("propriedadeId", idPropriedade.toHexString ());

        // Usuario e propriedade formam uma unidade: com replica set, a transacao
        // impede referencias para propriedade ausente em falhas parciais.
        try (ClientSession sessao = this.cliente.startSession ())
        {
            sessao.withTransaction (() ->
            {
                this.usuarios.insertOne (sessao, novoUsuario);
                this.propriedades.insertOne (sessao, new Document (propriedade).append ("_id", idPropriedade)
                                                                         .append ("usuarioId", novoUsuario.getObjectId ("_id").toHexString ()));
                return null;
            });
        }
        catch (MongoWriteException erro)
        {
            if (erro.getError ().getCategory () == ErrorCategory.DUPLICATE_KEY)
                return null;
            throw erro;
        }

        return novoUsuario;
    }

    public Document buscarPorId (String id) throws Exception
    {
        ObjectId objectId = RepositorioUsuariosMongo.paraId (id);
        if (objectId == null)
            return null;

        return this.usuarios.find (Filters.eq ("_id", objectId)).first ();
    }

    public Document buscarPor (String campo, Object valor) throws Exception
    {
        if (campo == null)
            throw new Exception ("Campo ausente");

        return this.usuarios.find (Filters.eq (campo, valor)).first ();
    }

    public void atualizar (String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        ObjectId objectId = RepositorioUsuariosMongo.paraId (id);
        if (objectId == null)
            return;

        this.usuarios.updateOne (Filters.eq ("_id", objectId), new Document ("$set", campos));
    }

    public Document buscarPropriedade (String id) throws Exception
    {
        ObjectId objectId = RepositorioUsuariosMongo.paraId (id);
        if (objectId == null)
            return null;

        return this.propriedades.find (Filters.eq ("_id", objectId)).first ();
    }

    public List<Document> listarPropriedades (String usuarioId) throws Exception
    {
        // A #194 gravava somente o vínculo no usuário. Migra apenas esse vínculo
        // comprovado e nunca toma uma propriedade já atribuída a outro dono.
        Document usuario = this.buscarPorId (usuarioId);
        ObjectId principal = usuario == null ? null : RepositorioUsuariosMongo.paraId (usuario.getString ("propriedadeId"));
        if (principal != null)
            this.propriedades.updateOne (Filters.and (Filters.eq ("_id", principal), Filters.exists ("usuarioId", false)),
                                          new Document ("$set", new Document ("usuarioId", usuarioId)));
        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document propriedade : this.propriedades.find (Filters.eq ("usuarioId", usuarioId)))
            ret.add (propriedade);
        return ret;
    }

    public Document criarPropriedade (String usuarioId, Document propriedade) throws Exception
    {
        if (this.buscarPorId (usuarioId) == null || propriedade == null)
            return null;
        Document criada = new Document (propriedade).append ("_id", new ObjectId ()).append ("usuarioId", usuarioId);
        this.propriedades.insertOne (criada);
        return criada;
    }

    private static Bson filtroDaPropriedade (String usuarioId, String propriedadeId)
    {
        ObjectId id = RepositorioUsuariosMongo.paraId (propriedadeId);
        if (id == null)
            return Filters.eq ("_id", new ObjectId ()); // id inexistente, sem risco de atingir outro documento
        return Filters.and (Filters.eq ("_id", id), Filters.eq ("usuarioId", usuarioId));
    }

    public boolean atualizarPropriedade (String usuarioId, String propriedadeId, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");
        return this.propriedades.updateOne (RepositorioUsuariosMongo.filtroDaPropriedade (usuarioId, propriedadeId),
                                             new Document ("$set", campos)).getMatchedCount () == 1;
    }

    public boolean excluirPropriedade (String usuarioId, String propriedadeId) throws Exception
    {
        ObjectId idUsuario = RepositorioUsuariosMongo.paraId (usuarioId);
        if (idUsuario == null) return false;
        try (ClientSession sessao = this.cliente.startSession ())
        {
            return sessao.withTransaction (() -> {
                Document usuario = this.usuarios.find (sessao, Filters.eq ("_id", idUsuario)).first ();
                ArrayList<Document> restantes = this.propriedades.find (sessao, Filters.eq ("usuarioId", usuarioId))
                                                                 .into (new ArrayList<Document> ());
                boolean encontrada = restantes.removeIf (item -> item.getObjectId ("_id").toHexString ().equals (propriedadeId));
                if (usuario == null || !encontrada || restantes.isEmpty ()) return false;
                // Escrever no mesmo usuário serializa exclusões concorrentes e
                // mantém ao menos uma propriedade ao repetir a transação.
                Document alteracoes = new Document ("$inc", new Document ("versaoPropriedades", 1));
                if (propriedadeId.equals (usuario.getString ("propriedadeId")))
                    alteracoes.append ("$set", new Document ("propriedadeId", restantes.get (0).getObjectId ("_id").toHexString ()));
                this.usuarios.updateOne (sessao, Filters.eq ("_id", idUsuario), alteracoes);
                return this.propriedades.deleteOne (sessao, RepositorioUsuariosMongo.filtroDaPropriedade (usuarioId, propriedadeId))
                                       .getDeletedCount () == 1;
            });
        }
    }
}
