/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package dados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Equipamentos em memoria, para os testes e o modo --memoria.
    Metodos synchronized, sempre devolve copias.
*/
public class RepositorioEquipamentosMemoria implements RepositorioEquipamentos
{
    private final LinkedHashMap<String, Document> equipamentos = new LinkedHashMap<String, Document> ();

    // equipamento de outra propriedade e tratado como inexistente
    private Document daPropriedade (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret         = null;
        Document equipamento = id == null ? null : this.equipamentos.get (id);

        if (equipamento != null && propriedadeId.equals (equipamento.getString ("propriedadeId")))
            ret = equipamento;

        return ret;
    }

    // existe outro equipamento (diferente de idIgnorado) com esse nome na propriedade?
    private boolean nomeEmUso (String propriedadeId, String nomeNormalizado, String idIgnorado)
    {
        boolean ret = false;

        for (Map.Entry<String, Document> entrada : this.equipamentos.entrySet ())
            if (!entrada.getKey ().equals (idIgnorado) &&
                propriedadeId.equals   (entrada.getValue ().getString ("propriedadeId")) &&
                nomeNormalizado.equals (entrada.getValue ().getString ("nomeNormalizado")))
                ret = true;

        return ret;
    }

    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document equipamento : this.equipamentos.values ())
            if (propriedadeId.equals (equipamento.getString ("propriedadeId")))
                ret.add (new Document (equipamento));

        return ret;
    }

    public synchronized Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        Document ret         = null;
        Document equipamento = this.daPropriedade (propriedadeId, id);

        if (equipamento != null)
            ret = new Document (equipamento);

        return ret;
    }

    public synchronized boolean criar (Document equipamento) throws Exception
    {
        if (equipamento == null)
            throw new Exception ("Equipamento ausente");

        boolean ret = false;

        if (!this.nomeEmUso (equipamento.getString ("propriedadeId"), equipamento.getString ("nomeNormalizado"), null))
        {
            ObjectId id = new ObjectId ();
            equipamento.put ("_id", id);
            this.equipamentos.put (id.toHexString (), new Document (equipamento));
            ret = true;
        }

        return ret;
    }

    public synchronized boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        boolean  ret         = true;
        Document equipamento = this.daPropriedade (propriedadeId, id);
        String   nome        = campos.getString ("nomeNormalizado");

        if (equipamento != null)
        {
            if (nome != null && this.nomeEmUso (propriedadeId, nome, id))
                ret = false;
            else
                equipamento.putAll (campos);
        }

        return ret;
    }
}
