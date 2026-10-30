/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Lancamentos de custo em memoria, para os testes e o modo --memoria.
    Metodos synchronized, sempre devolve copias.
*/
public class RepositorioCustosMemoria implements RepositorioCustos
{
    private final LinkedHashMap<String, Document> custos = new LinkedHashMap<String, Document> ();

    // o lancamento de outra propriedade e tratado como inexistente
    private Document daPropriedade (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret   = null;
        Document custo = this.custos.get (id);

        if (custo != null && propriedadeId.equals (custo.getString ("propriedadeId")))
            ret = custo;

        return ret;
    }

    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document custo : this.custos.values ())
            if (propriedadeId.equals (custo.getString ("propriedadeId")))
                ret.add (new Document (custo));

        return ret;
    }

    public synchronized Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        Document ret   = null;
        Document custo = this.daPropriedade (propriedadeId, id);

        if (custo != null)
            ret = new Document (custo);

        return ret;
    }

    public synchronized Document buscarPorMovimentacao (String propriedadeId, String movimentacaoId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        if (movimentacaoId == null || movimentacaoId.isBlank ())
            return null;

        Document ret = null;
        for (Document custo : this.custos.values ())
        {
            Document origem = custo.get ("origem", Document.class);
            if (ret == null && propriedadeId.equals (custo.getString ("propriedadeId")) && origem != null &&
                movimentacaoId.equals (origem.getString ("movimentacaoId")))
                ret = new Document (custo);
        }

        return ret;
    }

    public synchronized void criar (Document custo) throws Exception
    {
        if (custo == null)
            throw new Exception ("Custo ausente");

        ObjectId id = new ObjectId ();
        custo.put ("_id", id);
        this.custos.put (id.toHexString (), new Document (custo));
    }

    public synchronized boolean criarSeAusente (Document custo) throws Exception
    {
        if (custo == null || custo.get ("origem", Document.class) == null)
            throw new Exception ("Custo sem origem");

        String origem = custo.get ("origem", Document.class).getString ("movimentacaoId");

        if (this.buscarPorMovimentacao (custo.getString ("propriedadeId"), origem) != null)
            return false;

        this.criar (custo);
        return true;
    }

    public synchronized boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        Document custo = this.daPropriedade (propriedadeId, id);
        if (custo == null)
            return false;

        custo.putAll (campos);
        return true;
    }

    public synchronized boolean excluir (String propriedadeId, String id) throws Exception
    {
        if (this.daPropriedade (propriedadeId, id) == null)
            return false;

        this.custos.remove (id);
        return true;
    }
}
