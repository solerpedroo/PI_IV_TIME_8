/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados.operacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;

/*
    Ocorrências em memória, para demonstração e teste. Não há coleção Mongo.
    O synchronized faz o papel da transação: criar e salvar gravam o registro
    e o evento antes de liberar o bloqueio, então a timeline não fica sem
    o status correspondente.

    O isolamento é pertence: usuarioId e propriedadeId precisam bater.
    A versão impede duas telas de sobrescreverem uma à outra. As cópias
    (JSON de ida e volta) impedem o chamador de alterar o mapa interno.
*/
public class RepositorioOcorrenciasMemoria implements RepositorioOcorrencias
{
    private final Map<String, Document> registros = new LinkedHashMap<> ();
    private final List<Document> eventos = new ArrayList<> ();

    /* Cópia profunda via JSON. Assim um put no documento devolvido não muda o que está guardado. */
    private static Document copiar (Document documento)
    {
        return documento == null ? null : Document.parse (documento.toJson ());
    }

    /* Isolamento: o documento só é visível para o dono na propriedade pedida. */
    private static boolean pertence (Document documento, String usuario, String propriedade)
    {
        return documento != null && usuario.equals (documento.getString ("usuarioId")) &&
               propriedade.equals (documento.getString ("propriedadeId"));
    }

    /* Varre o mapa e devolve cópias só do par usuário + propriedade. */
    public synchronized List<Document> listar (String usuario, String propriedade)
    {
        List<Document> ret = new ArrayList<> ();
        for (Document registro : this.registros.values ())
            if (pertence (registro, usuario, propriedade)) ret.add (copiar (registro));
        return ret;
    }

    /* Busca pela chave e ainda confere dono e fazenda. Id de outro contexto devolve null. */
    public synchronized Document buscar (String usuario, String propriedade, String id)
    {
        Document registro = this.registros.get (id);
        return pertence (registro, usuario, propriedade) ? copiar (registro) : null;
    }

    /* Eventos desta ocorrência, na ordem em que foram acrescentados (a ordem do campo ordem). */
    public synchronized List<Document> timeline (String usuario, String propriedade, String id)
    {
        List<Document> ret = new ArrayList<> ();
        for (Document evento : this.eventos)
            if (pertence (evento, usuario, propriedade) && id.equals (evento.getString ("ocorrenciaId")))
                ret.add (copiar (evento));
        return ret;
    }

    /* Grava cadastro e primeiro evento juntos. Id repetido é erro, não sobrescrita. */
    public synchronized void criar (Document ocorrencia, Document evento)
    {
        String id = ocorrencia.getObjectId ("_id").toHexString ();
        if (this.registros.containsKey (id)) throw new IllegalStateException ("Ocorrência já existe");
        this.registros.put (id, copiar (ocorrencia));
        this.eventos.add (copiar (evento));
    }

    /*
        A versão impede duas telas de sobrescreverem uma à outra e perderem eventos.
        Se a versão guardada já mudou, devolve false e não acrescenta evento.
    */
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

    /* Mesma checagem de versão de salvar, sem evento. Usada pela foto de evidência. */
    public synchronized boolean substituirComVersao (Document anterior, Document nova)
    {
        String id = anterior.getObjectId ("_id").toHexString ();
        Document atual = this.registros.get (id);
        if (!pertence (atual, anterior.getString ("usuarioId"), anterior.getString ("propriedadeId")) ||
            !atual.get ("versao").equals (anterior.get ("versao"))) return false;
        this.registros.put (id, copiar (nova));
        return true;
    }

    /* true se qualquer usuário já deixou ocorrência nesta propriedade. */
    public synchronized boolean temRegistros (String propriedade)
    {
        return this.registros.values ().stream ().anyMatch (r -> propriedade.equals (r.getString ("propriedadeId")));
    }
}
