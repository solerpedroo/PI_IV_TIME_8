/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package util.comum;

import java.util.List;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;

/*
    Converte documentos do Mongo (org.bson.Document) em JSON de resposta,
    sem puxar outra biblioteca alem do driver.

    A saida usa o modo RELAXED: datas viram texto ISO e identificadores
    viram texto legivel, em vez da sintaxe de shell do Mongo
    ({"$oid": "..."}, {"$date": "..."}), que o front nao consome.

    O banco guarda a chave "_id". A API publica "id" em texto, para o
    cliente nao depender do nome interno do Mongo. Campos que nao podem
    sair (hash de senha, codigos) sao retirados em publico(...).
*/
public class Json
{
    /*
        Uma configuracao so, reaproveitada em toda escrita. O builder do
        driver e caro de repetir e o modo nao muda entre requisicoes.
    */
    private static final JsonWriterSettings CONFIGURACAO =
        JsonWriterSettings.builder ().outputMode (JsonMode.RELAXED).build ();

    /*
        Serializa um documento ja preparado para o cliente.

        Quem chama deve ter passado por publico(...) se o documento veio
        direto do banco; este metodo nao esconde campo nenhum, so escreve
        o que recebeu.
    */
    public static String escrever (Document documento)
    {
        return documento.toJson (Json.CONFIGURACAO);
    }

    /*
        Monta um array JSON [ {...}, {...} ] a partir de varios documentos.

        Document.toJson escreve um objeto, nao uma lista solta. Por isso o
        colchete e as virgulas sao montados aqui, e cada item reaproveita
        escrever(Document) para herdar o modo RELAXED.
    */
    public static String escrever (List<Document> documentos)
    {
        StringBuilder ret = new StringBuilder ("[");

        for (int i = 0; i < documentos.size(); i++)
        {
            /*
                Virgula so entre elementos. Colocar antes do primeiro
                geraria um JSON invalido: [, {...}].
            */
            if (i > 0)
                ret.append (',');
            /*
                Este append é de StringBuilder, não de Document.
                Ele cola texto no JSON final da resposta, por exemplo:
                [{"id":"abc","nome":"Ureia"},{"id":"def","nome":"Glifosato"}]
                O colchete de abertura já está no StringBuilder; o de fechamento
                entra no return logo abaixo. Nada disso vai para o Mongo.
            */
            ret.append (Json.escrever (documentos.get (i)));
        }

        return ret.append (']').toString ();
    }

    /*
        Devolve uma copia propria para a API: "id" no lugar de "_id" e sem
        os campos internos pedidos em ocultos.

        A copia e rasa (os valores aninhados continuam os mesmos objetos).
        Isso basta porque so precisamos omitir chaves do primeiro nivel
        antes de serializar. ObjectId vira os 24 caracteres hexadecimais
        que o front usa nas URLs; qualquer outro tipo de id cai em toString.
    */
    public static Document publico (Document documento, String... ocultos)
    {
        Document ret = new Document ();

        Object id = documento.get ("_id");
        if (id != null)
            ret.put ("id", id instanceof ObjectId ? ((ObjectId) id).toHexString () : id.toString ());

        for (String chave : documento.keySet ())
        {
            /*
                _id ja foi reescrito como "id". Os nomes em ocultos (senha,
                codigo de verificacao, etc.) nao podem aparecer no JSON.
            */
            if (chave.equals ("_id") || List.of (ocultos).contains (chave))
                continue;
            ret.put (chave, documento.get (chave));
        }

        return ret;
    }
}
