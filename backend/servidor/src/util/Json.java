/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package util;

import java.util.List;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;

/*
    JSON com o org.bson.Document do driver (sem outra biblioteca).
    Saida em modo RELAXED e com o _id trocado por "id" em texto.
*/
public class Json
{
    private static final JsonWriterSettings CONFIGURACAO =
        JsonWriterSettings.builder ().outputMode (JsonMode.RELAXED).build ();

    public static String escrever (Document documento)
    {
        return documento.toJson (Json.CONFIGURACAO);
    }

    // [ {...}, {...} ] (o Document nao escreve lista solta)
    public static String escrever (List<Document> documentos)
    {
        StringBuilder ret = new StringBuilder ("[");

        for (int i = 0; i < documentos.size(); i++)
        {
            if (i > 0)
                ret.append (',');
            ret.append (Json.escrever (documentos.get (i)));
        }

        return ret.append (']').toString ();
    }

    // copia com "id" (texto) no lugar de "_id" e sem os campos internos pedidos
    public static Document publico (Document documento, String... ocultos)
    {
        Document ret = new Document ();

        Object id = documento.get ("_id");
        if (id != null)
            ret.put ("id", id instanceof ObjectId ? ((ObjectId) id).toHexString () : id.toString ());

        for (String chave : documento.keySet ())
        {
            if (chave.equals ("_id") || List.of (ocultos).contains (chave))
                continue;
            ret.put (chave, documento.get (chave));
        }

        return ret;
    }
}
