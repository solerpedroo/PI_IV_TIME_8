package dados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Talhoes em memoria, para os testes. Metodos synchronized, sempre devolve copias.
*/
public class RepositorioTalhoesMemoria implements RepositorioTalhoes
{
    private final LinkedHashMap<String, Document> talhoes = new LinkedHashMap<String, Document> ();
    private final LinkedHashMap<String, ArrayList<Document>> historicos = new LinkedHashMap<String, ArrayList<Document>> ();

    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document talhao : this.talhoes.values ())
            if (propriedadeId.equals (talhao.getString ("propriedadeId")))
                ret.add (new Document (talhao));

        return ret;
    }

    public synchronized Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document talhao = this.talhoes.get (id);
        if (talhao == null || !propriedadeId.equals (talhao.getString ("propriedadeId")))
            return null;

        return new Document (talhao);
    }

    // existe outro talhao (diferente de idIgnorado) com esse nome na propriedade?
    private boolean nomeEmUso (String propriedadeId, String nomeNormalizado, String idIgnorado)
    {
        for (Map.Entry<String, Document> entrada : this.talhoes.entrySet ())
            if (!entrada.getKey ().equals (idIgnorado) &&
                propriedadeId.equals   (entrada.getValue ().getString ("propriedadeId")) &&
                nomeNormalizado.equals (entrada.getValue ().getString ("nomeNormalizado")))
                return true;

        return false;
    }

    public synchronized boolean criar (Document talhao) throws Exception
    {
        if (talhao == null)
            throw new Exception ("Talhao ausente");

        if (this.nomeEmUso (talhao.getString ("propriedadeId"), talhao.getString ("nomeNormalizado"), null))
            return false;

        ObjectId id = new ObjectId ();
        talhao.put ("_id", id);
        this.talhoes.put (id.toHexString (), new Document (talhao));

        return true;
    }

    public synchronized boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        Document talhao = this.talhoes.get (id);
        if (talhao == null || !talhao.getString ("propriedadeId").equals (propriedadeId))
            return true; // nada para atualizar

        String nome = campos.getString ("nomeNormalizado");
        if (nome != null && this.nomeEmUso (propriedadeId, nome, id))
            return false;

        talhao.putAll (campos);
        return true;
    }

    public synchronized void criarHistorico (String propriedadeId, String talhaoId, Document evento) throws Exception
    {
        if (this.buscarPorId (propriedadeId, talhaoId) == null) throw new Exception ("Talhao inexistente");
        this.historicos.computeIfAbsent (talhaoId, chave -> new ArrayList<Document> ()).add (new Document (evento));
    }

    public synchronized List<Document> listarHistorico (String propriedadeId, String talhaoId) throws Exception
    {
        if (this.buscarPorId (propriedadeId, talhaoId) == null) return new ArrayList<Document> ();
        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document evento : this.historicos.getOrDefault (talhaoId, new ArrayList<Document> ())) ret.add (new Document (evento));
        return ret;
    }
}
