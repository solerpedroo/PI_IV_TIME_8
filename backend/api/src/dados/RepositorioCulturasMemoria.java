package dados;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;

public class RepositorioCulturasMemoria implements RepositorioCulturas
{
    private final Map<String, Map<String, Document>> porPropriedade = new LinkedHashMap<String, Map<String, Document>> ();

    private static String chaveNome (String nome)
    {
        return nome.trim ().toLowerCase (Locale.ROOT);
    }

    private static Document publico (Document doc)
    {
        return new Document ("id", doc.getObjectId ("_id").toHexString ())
                   .append ("nome", doc.getString ("nome"))
                   .append ("criadoEm", doc.get ("criadoEm"));
    }

    private Map<String, Document> mapa (String propriedadeId)
    {
        return this.porPropriedade.computeIfAbsent (propriedadeId, chave -> new LinkedHashMap<String, Document> ());
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document doc : this.mapa (propriedadeId).values ())
            ret.add (RepositorioCulturasMemoria.publico (doc));

        ret.sort ((a, b) -> a.getString ("nome").compareToIgnoreCase (b.getString ("nome")));
        return ret;
    }

    public void garantirCatalogoInicial (String propriedadeId, String proprietarioId) throws Exception
    {
    }

    public boolean existeNome (String propriedadeId, String nome) throws Exception
    {
        return this.mapa (propriedadeId).containsKey (RepositorioCulturasMemoria.chaveNome (nome));
    }

    public Document criar (String propriedadeId, String proprietarioId, String nome) throws Exception
    {
        String limpo = nome == null ? "" : nome.trim ();
        if (limpo.length () < 2)
            throw new Exception ("Nome curto");

        String chave = RepositorioCulturasMemoria.chaveNome (limpo);
        if (this.mapa (propriedadeId).containsKey (chave))
            return null;

        Document doc = new Document ("_id", new ObjectId ())
                           .append ("propriedadeId",    propriedadeId)
                           .append ("proprietarioId",   proprietarioId)
                           .append ("nome",             limpo)
                           .append ("nomeNormalizado",  chave)
                           .append ("criadoEm",         Instant.now ().toString ());

        this.mapa (propriedadeId).put (chave, doc);
        return RepositorioCulturasMemoria.publico (doc);
    }

    public Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (id == null || !ObjectId.isValid (id))
            return null;

        for (Document doc : this.mapa (propriedadeId).values ())
            if (doc.getObjectId ("_id").toHexString ().equals (id))
                return RepositorioCulturasMemoria.publico (doc);

        return null;
    }

    public boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (id == null || !ObjectId.isValid (id))
            return false;

        Map<String, Document> mapa = this.mapa (propriedadeId);
        String                  remover = null;

        for (Map.Entry<String, Document> entrada : mapa.entrySet ())
        {
            if (entrada.getValue ().getObjectId ("_id").toHexString ().equals (id))
            {
                remover = entrada.getKey ();
                break;
            }
        }

        if (remover == null)
            return false;

        mapa.remove (remover);
        return true;
    }
}
