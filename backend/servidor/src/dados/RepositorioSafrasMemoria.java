/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Safras em memoria, para os testes e o modo --memoria.
    Metodos synchronized, sempre devolve copias (inclusive das listas internas).
*/
public class RepositorioSafrasMemoria implements RepositorioSafras
{
    private final LinkedHashMap<String, Document> safras = new LinkedHashMap<String, Document> ();

    // copia profunda: a safra guarda listas (talhoes, historicoEtapas) que nao podem ser compartilhadas
    private static Document copiar (Document safra)
    {
        return Document.parse (safra.toJson ());
    }

    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document safra : this.safras.values ())
            if (propriedadeId.equals (safra.getString ("propriedadeId")))
                ret.add (RepositorioSafrasMemoria.copiar (safra));

        return ret;
    }

    public synchronized Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret   = null;
        Document safra = this.safras.get (id);

        if (safra != null && propriedadeId.equals (safra.getString ("propriedadeId")))
            ret = RepositorioSafrasMemoria.copiar (safra);

        return ret;
    }

    // existe outra safra (diferente de idIgnorado) com esse nome na propriedade?
    private boolean nomeEmUso (String propriedadeId, String nomeNormalizado, String idIgnorado)
    {
        boolean ret = false;

        for (Map.Entry<String, Document> entrada : this.safras.entrySet ())
            if (!entrada.getKey ().equals (idIgnorado) &&
                propriedadeId.equals   (entrada.getValue ().getString ("propriedadeId")) &&
                nomeNormalizado.equals (entrada.getValue ().getString ("nomeNormalizado")))
                ret = true;

        return ret;
    }

    public synchronized boolean criar (Document safra) throws Exception
    {
        if (safra == null)
            throw new Exception ("Safra ausente");

        if (this.nomeEmUso (safra.getString ("propriedadeId"), safra.getString ("nomeNormalizado"), null))
            return false;

        ObjectId id = new ObjectId ();
        safra.put ("_id", id);
        this.safras.put (id.toHexString (), RepositorioSafrasMemoria.copiar (safra));

        return true;
    }

    public synchronized boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        Document safra = this.safras.get (id);
        if (safra == null || !safra.getString ("propriedadeId").equals (propriedadeId))
            return true; // nada para atualizar

        String nome = campos.getString ("nomeNormalizado");
        if (nome != null && this.nomeEmUso (propriedadeId, nome, id))
            return false;

        safra.putAll (RepositorioSafrasMemoria.copiar (campos));
        return true;
    }

    public synchronized boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document safra = this.safras.get (id);
        if (safra == null || !propriedadeId.equals (safra.getString ("propriedadeId")))
            return false;

        this.safras.remove (id);
        return true;
    }
}
