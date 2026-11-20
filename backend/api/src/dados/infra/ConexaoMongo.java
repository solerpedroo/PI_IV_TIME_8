package dados.infra;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/*
    Cliente do MongoDB (driver oficial). Existe um so para o servidor inteiro.

    MongoClient ja e thread-safe e mantem o proprio pool de conexoes. Criar
    um cliente por requisicao abriria sockets sem necessidade e estouraria
    o limite do banco. Esta classe guarda o cliente e o database ja escolhido;
    os repositorios pedem getBanco() e nao leem a URI de novo.
*/
public class ConexaoMongo
{
    private final MongoClient   cliente;
    private final MongoDatabase banco;

    /*
        Abre o pool a partir da URI e fixa o database usado por todo o processo.

        URI em branco e erro de configuracao na subida, nao na primeira query.
        O nome do banco passa por resolverNomeDoBanco para a API e o core nao
        gravarem em databases diferentes sem ninguem perceber.
    */
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
        Decide o nome do database sem abrir conexao.

        O path da URI tem prioridade (e o que o Atlas Connect coloca na string).
        Sem path, usa MONGODB_DATABASE, o mesmo criterio do servico core.
        Se os dois existem e divergem, a subida falha: escolher em silencio
        um deles faria usuario cair num banco e ocorrencia em outro.
        Sem path e sem variavel, o padrao do projeto e "agrogestao".
    */
    public static String resolverNomeDoBanco (ConnectionString endereco, String envDatabase) throws Exception
    {
        String naUri = endereco.getDatabase ();
        String env   = envDatabase == null ? null : envDatabase.trim ();

        /*
            URI com database. A variavel de ambiente, se preenchida, precisa
            repetir o mesmo nome; caso contrario recusamos os dois.
        */
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

    /*
        Atalho para quem tem a URI em texto (teste, checagem na subida) e
        ainda nao quer criar o MongoClient. A URI vazia falha igual ao
        construtor, com a mesma mensagem.
    */
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

    /*
        Responde se o banco aceita comando agora. A rota /ready usa isto
        no health check: processo no ar nao significa Mongo alcancavel.

        ping e o comando barato do proprio servidor. Qualquer excecao
        (rede, autenticacao, timeout) vira false para o probe falhar
        e o orquestrador nao mandar trafego para esta instancia.
    */
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

    /*
        Devolve os sockets do pool quando o processo encerra.

        Sem este close o driver segura conexoes ate o sistema operacional
        mata-las, o que atrapalha reinicio rapido e teste automatizado.
    */
    public void fechar ()
    {
        this.cliente.close ();
    }
}
