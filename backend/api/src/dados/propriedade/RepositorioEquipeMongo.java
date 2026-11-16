// Miguel — RA: 25003110
package dados.propriedade;

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
import util.propriedade.ConviteEquipe;
import util.propriedade.EquipeMembro;
import util.propriedade.TelasAcesso;

public class RepositorioEquipeMongo implements RepositorioEquipe
{
    private final MongoCollection<Document> membros;

    public RepositorioEquipeMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.membros = banco.getCollection ("equipe_membros");
        this.membros.createIndex (Indexes.ascending ("propriedadeId", "nomeNormalizado"),
                                  new IndexOptions ().unique (true)
                                                     .partialFilterExpression (new Document ("status", EquipeMembro.STATUS_LOCAL))
                                                     .name ("equipe_nome_local_por_propriedade"));
        this.membros.createIndex (Indexes.ascending ("propriedadeId", "emailConvidado"),
                                  new IndexOptions ().unique (true)
                                                     .partialFilterExpression (new Document ("emailConvidado",
                                                         new Document ("$type", "string")))
                                                     .name ("equipe_email_por_propriedade"));
        this.membros.createIndex (Indexes.ascending ("conviteToken"),
                                  new IndexOptions ().unique (true).sparse (true).name ("equipe_convite_token"));
        this.membros.createIndex (Indexes.ascending ("usuarioId", "propriedadeId"));
    }

    private static void aplicarCargo (Document doc, String cargo)
    {
        if (cargo == null || cargo.isBlank ())
            doc.remove ("cargo");
        else
            doc.put ("cargo", cargo.trim ());
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document doc : this.membros.find (Filters.eq ("propriedadeId", propriedadeId))
                                        .sort (Sorts.ascending ("nome")))
            ret.add (EquipeMembro.publico (doc, true));

        return ret;
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return null;

        Document doc = this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                       Filters.eq ("_id", new ObjectId (id))))
                                   .first ();
        return doc == null ? null : EquipeMembro.publico (doc, true);
    }

    public Document criar (String propriedadeId, String proprietarioId, Document dados) throws Exception
    {
        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        if (this.existeNomeLocal (propriedadeId, nome, null))
            return null;

        String agora = Instant.now ().toString ();
        Document doc = new Document ("_id", new ObjectId ())
                           .append ("propriedadeId",  propriedadeId)
                           .append ("proprietarioId", proprietarioId)
                           .append ("status",         EquipeMembro.STATUS_LOCAL)
                           .append ("nome",           nome.trim ())
                           .append ("nomeNormalizado", EquipeMembro.nomeNormalizado (nome))
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

        return EquipeMembro.publico (doc, false);
    }

    public Document criarConvite (String propriedadeId, String proprietarioId, Document dados) throws Exception
    {
        String email = EquipeMembro.emailNormalizado (dados.getString ("emailConvidado"));
        if (email.isEmpty () || !email.matches ("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new Exception ("E-mail inválido");

        if (this.existeEmail (propriedadeId, email, null))
            return null;

        String nome = dados.getString ("nome");
        if (nome == null || nome.isBlank ())
            nome = email;

        String token = ConviteEquipe.gerarToken ();
        String agora = Instant.now ().toString ();
        Document doc = new Document ("_id", new ObjectId ())
                           .append ("propriedadeId",   propriedadeId)
                           .append ("proprietarioId",  proprietarioId)
                           .append ("status",          EquipeMembro.STATUS_CONVITE_PENDENTE)
                           .append ("nome",            nome.trim ())
                           .append ("emailConvidado",  email)
                           .append ("conviteToken",    token)
                           .append ("conviteExpiraEm", ConviteEquipe.expiraEm ())
                           .append ("telas",           TelasAcesso.normalizar (dados.get ("telas")))
                           .append ("criadoEm",        agora)
                           .append ("atualizadoEm",    agora);
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

        return EquipeMembro.publico (doc, true);
    }

    public Document atualizar (String propriedadeId, String id, Document dados) throws Exception
    {
        Document atual = this.docInterno (propriedadeId, id);
        if (atual == null)
            return null;

        if (EquipeMembro.STATUS_CONVITE_PENDENTE.equals (atual.getString ("status")))
        {
            atual.append ("telas", TelasAcesso.normalizar (dados.get ("telas")))
                 .append ("atualizadoEm", Instant.now ().toString ());
            RepositorioEquipeMongo.aplicarCargo (atual, dados.getString ("cargo"));
            this.membros.replaceOne (Filters.eq ("_id", atual.getObjectId ("_id")), atual);
            return EquipeMembro.publico (atual, true);
        }

        if (!EquipeMembro.STATUS_LOCAL.equals (atual.getString ("status"))
            && !EquipeMembro.STATUS_ATIVO.equals (atual.getString ("status")))
            return null;

        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        if (EquipeMembro.STATUS_LOCAL.equals (atual.getString ("status"))
            && this.existeNomeLocal (propriedadeId, nome, atual.getObjectId ("_id")))
            return null;

        atual.append ("nome", nome.trim ())
             .append ("telas", TelasAcesso.normalizar (dados.get ("telas")))
             .append ("atualizadoEm", Instant.now ().toString ());

        if (EquipeMembro.STATUS_LOCAL.equals (atual.getString ("status")))
            atual.append ("nomeNormalizado", EquipeMembro.nomeNormalizado (nome));

        RepositorioEquipeMongo.aplicarCargo (atual, dados.getString ("cargo"));

        try
        {
            this.membros.replaceOne (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                  Filters.eq ("_id", atual.getObjectId ("_id"))),
                                     atual);
        }
        catch (MongoWriteException erro)
        {
            if (erro.getError ().getCategory () == ErrorCategory.DUPLICATE_KEY)
                return null;
            throw erro;
        }

        return EquipeMembro.publico (atual, EquipeMembro.STATUS_CONVITE_PENDENTE.equals (atual.getString ("status")));
    }

    public Document regenerarConvite (String propriedadeId, String id) throws Exception
    {
        Document atual = this.docInterno (propriedadeId, id);
        if (atual == null || !EquipeMembro.STATUS_CONVITE_PENDENTE.equals (atual.getString ("status")))
            return null;

        atual.append ("conviteToken",    ConviteEquipe.gerarToken ())
             .append ("conviteExpiraEm", ConviteEquipe.expiraEm ())
             .append ("atualizadoEm",    Instant.now ().toString ());

        this.membros.replaceOne (Filters.eq ("_id", atual.getObjectId ("_id")), atual);
        return EquipeMembro.publico (atual, true);
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return false;

        return this.membros.deleteOne (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                    Filters.eq ("_id", new ObjectId (id))))
                           .getDeletedCount () > 0;
    }

    public Document buscarPorConviteToken (String token) throws Exception
    {
        if (token == null || token.isBlank ())
            return null;

        return this.membros.find (Filters.eq ("conviteToken", token.trim ())).first ();
    }

    public Document aceitarConvite (String token, String usuarioId, String nomeUsuario, String emailUsuario) throws Exception
    {
        Document convite = this.buscarPorConviteToken (token);
        if (convite == null)
            return null;

        if (!EquipeMembro.STATUS_CONVITE_PENDENTE.equals (convite.getString ("status")))
            return null;

        if (ConviteEquipe.expirado (convite.get ("conviteExpiraEm")))
            return null;

        String esperado = EquipeMembro.emailNormalizado (convite.getString ("emailConvidado"));
        String recebido = EquipeMembro.emailNormalizado (emailUsuario);
        if (!esperado.equals (recebido))
            return null;

        if (usuarioId.equals (convite.getString ("proprietarioId")))
            return null;

        convite.append ("status", EquipeMembro.STATUS_ATIVO)
               .append ("usuarioId", usuarioId)
               .append ("nome", nomeUsuario == null || nomeUsuario.isBlank () ? convite.getString ("nome") : nomeUsuario.trim ())
               .append ("conviteToken", null)
               .append ("conviteExpiraEm", null)
               .append ("atualizadoEm", Instant.now ().toString ());
        convite.remove ("nomeNormalizado");

        this.membros.replaceOne (Filters.eq ("_id", convite.getObjectId ("_id")), convite);
        return EquipeMembro.publico (convite, false);
    }

    public Document membroAtivoDoUsuario (String propriedadeId, String usuarioId) throws Exception
    {
        if (propriedadeId == null || usuarioId == null)
            return null;

        Document doc = this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                       Filters.eq ("usuarioId", usuarioId),
                                                       Filters.eq ("status", EquipeMembro.STATUS_ATIVO)))
                                   .first ();
        return doc == null ? null : EquipeMembro.publico (doc, false);
    }

    public List<String> listarPropriedadesColaborador (String usuarioId) throws Exception
    {
        if (usuarioId == null)
            return List.of ();

        ArrayList<String> ret = new ArrayList<String> ();
        for (Document doc : this.membros.find (Filters.and (Filters.eq ("usuarioId", usuarioId),
                                                            Filters.eq ("status", EquipeMembro.STATUS_ATIVO))))
            ret.add (doc.getString ("propriedadeId"));

        return ret;
    }

    private Document docInterno (String propriedadeId, String id)
    {
        if (propriedadeId == null || id == null || !ObjectId.isValid (id))
            return null;

        return this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                               Filters.eq ("_id", new ObjectId (id))))
                           .first ();
    }

    private boolean existeNomeLocal (String propriedadeId, String nome, ObjectId ignorar)
    {
        Document achado = this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                         Filters.eq ("status", EquipeMembro.STATUS_LOCAL),
                                                         Filters.eq ("nomeNormalizado", EquipeMembro.nomeNormalizado (nome))))
                                     .first ();
        if (achado == null)
            return false;
        return ignorar == null || !ignorar.equals (achado.getObjectId ("_id"));
    }

    private boolean existeEmail (String propriedadeId, String email, ObjectId ignorar)
    {
        Document achado = this.membros.find (Filters.and (Filters.eq ("propriedadeId", propriedadeId),
                                                         Filters.eq ("emailConvidado", email)))
                                     .first ();
        if (achado == null)
            return false;
        return ignorar == null || !ignorar.equals (achado.getObjectId ("_id"));
    }
}