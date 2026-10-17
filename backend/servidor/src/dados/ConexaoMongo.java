package dados;

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

        String nomeDoBanco = endereco.getDatabase ();
        if (nomeDoBanco == null)
            nomeDoBanco = "agrogestao";

        this.cliente = MongoClients.create (endereco);
        this.banco   = this.cliente.getDatabase (nomeDoBanco);
    }

    public MongoDatabase getBanco ()
    {
        return this.banco;
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
