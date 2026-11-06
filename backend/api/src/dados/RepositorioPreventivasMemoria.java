/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package dados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Manutencoes preventivas programadas dos equipamentos em memoria, para os testes e o modo --memoria.
    Metodos synchronized, sempre devolve copias.
*/
public class RepositorioPreventivasMemoria implements RepositorioPreventivas
{
    private final LinkedHashMap<String, Document> registros = new LinkedHashMap<String, Document> ();

    // registro de outra propriedade e tratado como inexistente
    private Document daPropriedade (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        Document ret      = null;
        Document registro = id == null ? null : this.registros.get (id);

        if (registro != null && propriedadeId.equals (registro.getString ("propriedadeId")))
            ret = registro;

        return ret;
    }

    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document registro : this.registros.values ())
            if (propriedadeId.equals (registro.getString ("propriedadeId")))
                ret.add (new Document (registro));

        return ret;
    }

    public synchronized Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        Document ret      = null;
        Document registro = this.daPropriedade (propriedadeId, id);

        if (registro != null)
            ret = new Document (registro);

        return ret;
    }

    public synchronized void criar (Document preventiva) throws Exception
    {
        if (preventiva == null)
            throw new Exception ("Registro ausente");

        ObjectId id = new ObjectId ();
        preventiva.put ("_id", id);
        this.registros.put (id.toHexString (), new Document (preventiva));
    }

    public synchronized boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        boolean  ret      = false;
        Document registro = this.daPropriedade (propriedadeId, id);

        if (registro != null)
        {
            registro.putAll (campos);
            ret = true;
        }

        return ret;
    }

    public synchronized boolean marcarRealizadaSeProgramada (String propriedadeId, String id, String intervencaoId,
                                                             String realizadaEm, String atualizadoEm) throws Exception
    {
        if (intervencaoId == null || realizadaEm == null || atualizadoEm == null)
            throw new Exception ("Parametros ausentes");

        boolean  ret      = false;
        Document registro = this.daPropriedade (propriedadeId, id);

        if (registro != null && "programada".equals (registro.getString ("status")))
        {
            registro.append ("status",        "realizada")
                    .append  ("intervencaoId", intervencaoId)
                    .append  ("realizadaEm",   realizadaEm)
                    .append  ("atualizadoEm",  atualizadoEm);
            ret = true;
        }

        return ret;
    }

    public synchronized boolean excluir (String propriedadeId, String id) throws Exception
    {
        boolean ret = false;

        if (this.daPropriedade (propriedadeId, id) != null)
        {
            this.registros.remove (id);
            ret = true;
        }

        return ret;
    }
}
