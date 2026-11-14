// Miguel — RA: 25003110
package dados;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import util.TelasAcesso;

public class RepositorioEquipeMemoria implements RepositorioEquipe
{
    private final Map<String, Map<String, Document>> porPropriedade = new LinkedHashMap<String, Map<String, Document>> ();

    private static String chaveNome (String nome)
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

    private Map<String, Document> mapa (String propriedadeId)
    {
        return this.porPropriedade.computeIfAbsent (propriedadeId, chave -> new LinkedHashMap<String, Document> ());
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document doc : this.mapa (propriedadeId).values ())
            ret.add (RepositorioEquipeMemoria.publico (doc));

        ret.sort ((a, b) -> a.getString ("nome").compareToIgnoreCase (b.getString ("nome")));
        return ret;
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (id == null || !ObjectId.isValid (id))
            return null;

        for (Document doc : this.mapa (propriedadeId).values ())
            if (doc.getObjectId ("_id").toHexString ().equals (id))
                return RepositorioEquipeMemoria.publico (doc);

        return null;
    }

    public Document criar (String propriedadeId, String proprietarioId, Document dados) throws Exception
    {
        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        String chave = RepositorioEquipeMemoria.chaveNome (nome);
        if (this.mapa (propriedadeId).containsKey (chave))
            return null;

        String agora = Instant.now ().toString ();
        Document doc = new Document ("_id", new ObjectId ())
                           .append ("propriedadeId",  propriedadeId)
                           .append ("proprietarioId", proprietarioId)
                           .append ("nome",           nome.trim ())
                           .append ("nomeNormalizado", chave)
                           .append ("telas",          TelasAcesso.normalizar (dados.get ("telas")))
                           .append ("criadoEm",       agora)
                           .append ("atualizadoEm",   agora);

        RepositorioEquipeMemoria.aplicarCargo (doc, dados.getString ("cargo"));
        this.mapa (propriedadeId).put (chave, doc);
        return RepositorioEquipeMemoria.publico (doc);
    }

    public Document atualizar (String propriedadeId, String id, Document dados) throws Exception
    {
        Document atual = null;
        String chaveAtual = null;

        for (Map.Entry<String, Document> par : this.mapa (propriedadeId).entrySet ())
        {
            if (par.getValue ().getObjectId ("_id").toHexString ().equals (id))
            {
                atual = par.getValue ();
                chaveAtual = par.getKey ();
                break;
            }
        }

        if (atual == null)
            return null;

        String nome = dados.getString ("nome");
        if (nome == null || nome.trim ().length () < 2)
            throw new Exception ("Nome curto");

        String chaveNova = RepositorioEquipeMemoria.chaveNome (nome);
        if (!chaveNova.equals (chaveAtual) && this.mapa (propriedadeId).containsKey (chaveNova))
            return null;

        atual.append ("nome", nome.trim ())
             .append ("nomeNormalizado", chaveNova)
             .append ("telas", TelasAcesso.normalizar (dados.get ("telas")))
             .append ("atualizadoEm", Instant.now ().toString ());
        RepositorioEquipeMemoria.aplicarCargo (atual, dados.getString ("cargo"));

        if (!chaveNova.equals (chaveAtual))
        {
            this.mapa (propriedadeId).remove (chaveAtual);
            this.mapa (propriedadeId).put (chaveNova, atual);
        }

        return RepositorioEquipeMemoria.publico (atual);
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (id == null || !ObjectId.isValid (id))
            return false;

        Map<String, Document> mapa = this.mapa (propriedadeId);
        String remover = null;

        for (Map.Entry<String, Document> par : mapa.entrySet ())
        {
            if (par.getValue ().getObjectId ("_id").toHexString ().equals (id))
            {
                remover = par.getKey ();
                break;
            }
        }

        if (remover == null)
            return false;

        mapa.remove (remover);
        return true;
    }
}