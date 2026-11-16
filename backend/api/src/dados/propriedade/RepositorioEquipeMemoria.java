// Miguel — RA: 25003110
package dados.propriedade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import util.propriedade.ConviteEquipe;
import util.propriedade.EquipeMembro;
import util.propriedade.TelasAcesso;

public class RepositorioEquipeMemoria implements RepositorioEquipe
{
    private final Map<String, Map<String, Document>> porPropriedade = new LinkedHashMap<String, Map<String, Document>> ();
    private final Map<String, String>                  porToken      = new HashMap<String, String> ();

    private Map<String, Document> mapa (String propriedadeId)
    {
        return this.porPropriedade.computeIfAbsent (propriedadeId, chave -> new LinkedHashMap<String, Document> ());
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
        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document doc : this.mapa (propriedadeId).values ())
            ret.add (EquipeMembro.publico (doc, true));

        ret.sort ((a, b) -> a.getString ("nome").compareToIgnoreCase (b.getString ("nome")));
        return ret;
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        Document doc = this.mapa (propriedadeId).get (id);
        return doc == null ? null : EquipeMembro.publico (doc, true);
    }

    public Document criar (String propriedadeId, String proprietarioId, Document dados) throws Exception
    {
        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        for (Document existente : this.mapa (propriedadeId).values ())
            if (EquipeMembro.STATUS_LOCAL.equals (existente.getString ("status"))
                && EquipeMembro.nomeNormalizado (nome).equals (existente.getString ("nomeNormalizado")))
                return null;

        String id = new ObjectId ().toHexString ();
        String agora = Instant.now ().toString ();
        Document doc = new Document ("_id", new ObjectId (id))
                           .append ("propriedadeId",  propriedadeId)
                           .append ("proprietarioId", proprietarioId)
                           .append ("status",         EquipeMembro.STATUS_LOCAL)
                           .append ("nome",           nome.trim ())
                           .append ("nomeNormalizado", EquipeMembro.nomeNormalizado (nome))
                           .append ("telas",          TelasAcesso.normalizar (dados.get ("telas")))
                           .append ("criadoEm",       agora)
                           .append ("atualizadoEm",   agora);
        RepositorioEquipeMemoria.aplicarCargo (doc, dados.getString ("cargo"));
        this.mapa (propriedadeId).put (id, doc);
        return EquipeMembro.publico (doc, false);
    }

    public Document criarConvite (String propriedadeId, String proprietarioId, Document dados) throws Exception
    {
        String email = EquipeMembro.emailNormalizado (dados.getString ("emailConvidado"));
        if (email.isEmpty ())
            throw new Exception ("E-mail ausente");

        for (Document existente : this.mapa (propriedadeId).values ())
            if (email.equals (existente.getString ("emailConvidado")))
                return null;

        String nome = dados.getString ("nome");
        if (nome == null || nome.isBlank ())
            nome = email;

        String token = ConviteEquipe.gerarToken ();
        String id = new ObjectId ().toHexString ();
        String agora = Instant.now ().toString ();
        Document doc = new Document ("_id", new ObjectId (id))
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
        RepositorioEquipeMemoria.aplicarCargo (doc, dados.getString ("cargo"));
        this.mapa (propriedadeId).put (id, doc);
        this.porToken.put (token, id + ":" + propriedadeId);
        return EquipeMembro.publico (doc, true);
    }

    public Document atualizar (String propriedadeId, String id, Document dados) throws Exception
    {
        Document atual = this.mapa (propriedadeId).get (id);
        if (atual == null)
            return null;

        if (EquipeMembro.STATUS_CONVITE_PENDENTE.equals (atual.getString ("status")))
        {
            atual.append ("telas", TelasAcesso.normalizar (dados.get ("telas")))
                 .append ("atualizadoEm", Instant.now ().toString ());
            RepositorioEquipeMemoria.aplicarCargo (atual, dados.getString ("cargo"));
            return EquipeMembro.publico (atual, true);
        }

        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        if (EquipeMembro.STATUS_LOCAL.equals (atual.getString ("status")))
        {
            String chave = EquipeMembro.nomeNormalizado (nome);
            for (Map.Entry<String, Document> par : this.mapa (propriedadeId).entrySet ())
                if (!par.getKey ().equals (id)
                    && EquipeMembro.STATUS_LOCAL.equals (par.getValue ().getString ("status"))
                    && chave.equals (par.getValue ().getString ("nomeNormalizado")))
                    return null;
            atual.append ("nomeNormalizado", chave);
        }

        atual.append ("nome", nome.trim ())
             .append ("telas", TelasAcesso.normalizar (dados.get ("telas")))
             .append ("atualizadoEm", Instant.now ().toString ());
        RepositorioEquipeMemoria.aplicarCargo (atual, dados.getString ("cargo"));
        return EquipeMembro.publico (atual, EquipeMembro.STATUS_CONVITE_PENDENTE.equals (atual.getString ("status")));
    }

    public Document regenerarConvite (String propriedadeId, String id) throws Exception
    {
        Document atual = this.mapa (propriedadeId).get (id);
        if (atual == null || !EquipeMembro.STATUS_CONVITE_PENDENTE.equals (atual.getString ("status")))
            return null;

        String antigo = atual.getString ("conviteToken");
        if (antigo != null)
            this.porToken.remove (antigo);

        String token = ConviteEquipe.gerarToken ();
        atual.append ("conviteToken", token)
             .append ("conviteExpiraEm", ConviteEquipe.expiraEm ())
             .append ("atualizadoEm", Instant.now ().toString ());
        this.porToken.put (token, id + ":" + propriedadeId);
        return EquipeMembro.publico (atual, true);
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        Document removido = this.mapa (propriedadeId).remove (id);
        if (removido == null)
            return false;
        String token = removido.getString ("conviteToken");
        if (token != null)
            this.porToken.remove (token);
        return true;
    }

    public Document buscarPorConviteToken (String token) throws Exception
    {
        if (token == null)
            return null;

        String ref = this.porToken.get (token.trim ());
        if (ref == null)
            return null;

        String[] partes = ref.split (":", 2);
        if (partes.length != 2)
            return null;

        Document doc = this.mapa (partes[1]).get (partes[0]);
        return doc == null || !token.equals (doc.getString ("conviteToken")) ? null : doc;
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

        if (!EquipeMembro.emailNormalizado (convite.getString ("emailConvidado"))
                         .equals (EquipeMembro.emailNormalizado (emailUsuario)))
            return null;

        if (usuarioId.equals (convite.getString ("proprietarioId")))
            return null;

        String antigo = convite.getString ("conviteToken");
        convite.append ("status", EquipeMembro.STATUS_ATIVO)
               .append ("usuarioId", usuarioId)
               .append ("nome", nomeUsuario == null || nomeUsuario.isBlank () ? convite.getString ("nome") : nomeUsuario.trim ())
               .append ("conviteToken", null)
               .append ("conviteExpiraEm", null)
               .append ("atualizadoEm", Instant.now ().toString ());
        convite.remove ("nomeNormalizado");
        if (antigo != null)
            this.porToken.remove (antigo);

        return EquipeMembro.publico (convite, false);
    }

    public Document membroAtivoDoUsuario (String propriedadeId, String usuarioId) throws Exception
    {
        for (Document doc : this.mapa (propriedadeId).values ())
            if (EquipeMembro.STATUS_ATIVO.equals (doc.getString ("status")) && usuarioId.equals (doc.getString ("usuarioId")))
                return EquipeMembro.publico (doc, false);
        return null;
    }

    public List<String> listarPropriedadesColaborador (String usuarioId) throws Exception
    {
        ArrayList<String> ret = new ArrayList<String> ();
        for (Map.Entry<String, Map<String, Document>> propriedade : this.porPropriedade.entrySet ())
            for (Document doc : propriedade.getValue ().values ())
                if (EquipeMembro.STATUS_ATIVO.equals (doc.getString ("status")) && usuarioId.equals (doc.getString ("usuarioId")))
                    ret.add (propriedade.getKey ());
        return ret;
    }
}