/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;

/* Modo de demonstração/teste. As cópias impedem alterações fora do repositório. */
public class RepositorioOcorrenciasMemoria implements RepositorioOcorrencias
{
    private final Map<String, Document> registros = new LinkedHashMap<> ();
    private final List<Document> eventos = new ArrayList<> ();

    private static Document copiar (Document documento)
    {
        return documento == null ? null : Document.parse (documento.toJson ());
    }

    private static boolean pertence (Document documento, String usuario, String propriedade)
    {
        return documento != null && usuario.equals (documento.getString ("usuarioId")) &&
               propriedade.equals (documento.getString ("propriedadeId"));
    }

    public synchronized List<Document> listar (String usuario, String propriedade)
    {
        List<Document> ret = new ArrayList<> ();
        for (Document registro : this.registros.values ())
            if (pertence (registro, usuario, propriedade)) ret.add (copiar (registro));
        return ret;
    }

    public synchronized Document buscar (String usuario, String propriedade, String id)
    {
        Document registro = this.registros.get (id);
        return pertence (registro, usuario, propriedade) ? copiar (registro) : null;
    }

    public synchronized List<Document> timeline (String usuario, String propriedade, String id)
    {
        List<Document> ret = new ArrayList<> ();
        for (Document evento : this.eventos)
            if (pertence (evento, usuario, propriedade) && id.equals (evento.getString ("ocorrenciaId")))
                ret.add (copiar (evento));
        return ret;
    }

    public synchronized void criar (Document ocorrencia, Document evento)
    {
        String id = ocorrencia.getObjectId ("_id").toHexString ();
        if (this.registros.containsKey (id)) throw new IllegalStateException ("Ocorrência já existe");
        this.registros.put (id, copiar (ocorrencia));
        this.eventos.add (copiar (evento));
    }

    // A versão impede duas telas de sobrescreverem uma à outra e perderem eventos.
    public synchronized boolean salvar (Document anterior, Document nova, Document evento)
    {
        String id = anterior.getObjectId ("_id").toHexString ();
        Document atual = this.registros.get (id);
        if (!pertence (atual, anterior.getString ("usuarioId"), anterior.getString ("propriedadeId")) ||
            !atual.get ("versao").equals (anterior.get ("versao"))) return false;
        this.registros.put (id, copiar (nova));
        this.eventos.add (copiar (evento));
        return true;
    }

    public synchronized boolean temRegistros (String propriedade)
    {
        return this.registros.values ().stream ().anyMatch (r -> propriedade.equals (r.getString ("propriedadeId")));
    }
}
