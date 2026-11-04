package dados;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/*
    Cliente do MongoDB (driver oficial). Um so para o servidor todo:
    o MongoClient ja e thread-safe e tem seu proprio pool de conexoes.
*/
public class ConexaoMongo
{
    private final MongoClient   cliente;
    private final MongoDatabase banco;

    public ConexaoMongo (String uri) throws Exception
    {
        if (uri == null || uri.isBlank ())
            throw new Exception ("MONGODB_URI ausente");

        ConnectionString endereco = new ConnectionString (uri);
        String nomeDoBanco = ConexaoMongo.resolverNomeDoBanco (endereco, System.getenv ("MONGODB_DATABASE"));

        this.cliente = MongoClients.create (endereco);
        this.banco   = this.cliente.getDatabase (nomeDoBanco);
    }

    /*
        Path da URI tem prioridade (Atlas Connect). Sem path, usa MONGODB_DATABASE (alinhado ao core).
        Se ambos existirem e divergirem, falha na subida para evitar gravar no banco errado.
    */
    public static String resolverNomeDoBanco (ConnectionString endereco, String envDatabase) throws Exception
    {
        String naUri = endereco.getDatabase ();
        String env   = envDatabase == null ? null : envDatabase.trim ();

        if (naUri != null && !naUri.isBlank ())
        {
            if (env != null && !env.isEmpty () && !naUri.equals (env))
                throw new Exception ("MONGODB_URI aponta para o banco \"" + naUri
                    + "\", mas MONGODB_DATABASE=\"" + env + "\". Use o mesmo nome nos dois.");

            return naUri;
        }

        if (env != null && !env.isEmpty ())
            return env;

        return "agrogestao";
    }

    public static String resolverNomeDoBanco (String uri, String envDatabase) throws Exception
    {
        if (uri == null || uri.isBlank ())
            throw new Exception ("MONGODB_URI ausente");

        return ConexaoMongo.resolverNomeDoBanco (new ConnectionString (uri), envDatabase);
    }

    public MongoDatabase getBanco ()
    {
        return this.banco;
    }

    public MongoClient getCliente ()
    {
        return this.cliente;
    }

    // ping no banco; usado pelo /ready
    public boolean pronto ()
    {
        try
        {
            this.banco.runCommand (new Document ("ping", 1));
            return true;
        }
        catch (Exception erro)
        {
            return false;
        }
    }

    public void fechar ()
    {
        this.cliente.close ();
    }
}
