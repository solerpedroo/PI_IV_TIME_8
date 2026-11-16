/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
import com.mongodb.ConnectionString;
import dados.conta.RepositorioUsuariosMongo;
import dados.cultivo.RepositorioSafrasMongo;
import dados.cultivo.RepositorioTalhoesMongo;
import dados.estoque.RepositorioEstoqueMongo;
import dados.financeiro.RepositorioCustosMongo;
import dados.infra.ConexaoMongo;
import java.nio.file.Path;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Senhas;

/* Servidor de apoio ao teste de navegador do estoque. Usa APENAS banco terminado
   em _teste. Sobe dois produtores isolados, cada um com uma atividade fixture.
   As atividades não são uma nova API de criação. */
public class CenarioEstoqueTeste
{
    /* Falha cedo se a URI não apontar para um banco cujo nome termina em _teste. */
    public static String uriSegura () throws Exception
    {
        String uri = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null
            : ConexaoMongo.resolverNomeDoBanco (uri, System.getenv ("MONGODB_DATABASE"));
        if (banco == null || !banco.endsWith ("_teste"))
            throw new Exception ("Informe MONGODB_URI com banco terminado em _teste.");
        return uri;
    }

    public static void main (String[] args) throws Exception
    {
        String uri = uriSegura ();
        ConexaoMongo conexao = new ConexaoMongo (uri);
        if (List.of (args).contains ("--limpar"))
        {
            /* Remove somente os produtores fictícios deste cenário e o estoque, as movimentações e as atividades deles.
               Nunca usa drop, nem limpa registros de usuários reais do banco de teste. */
            for (String email : List.of ("estoque-e2e@agro.test", "outro-estoque@agro.test"))
            {
                Document usuario = conexao.getBanco ().getCollection ("usuarios").find (new Document ("email", email)).first ();
                if (usuario == null) continue;
                String uid = usuario.getObjectId ("_id").toHexString ();
                for (Document propriedade : conexao.getBanco ().getCollection ("propriedades").find (new Document ("usuarioId", uid)))
                {
                    String pid = propriedade.getObjectId ("_id").toHexString ();
                    // Apaga só documentos cuja propriedadeId é desta fazenda fictícia.
                    for (String colecao : List.of ("insumos", "movimentacoes_insumos", "atividades"))
                        conexao.getBanco ().getCollection (colecao).deleteMany (new Document ("propriedadeId", pid));
                    conexao.getBanco ().getCollection ("propriedades").deleteOne (new Document ("_id", propriedade.getObjectId ("_id")));
                }
                conexao.getBanco ().getCollection ("usuarios").deleteOne (new Document ("_id", usuario.getObjectId ("_id")));
            }
            conexao.fechar ();
            System.out.println ("Fixtures de estoque removidas; demais dados preservados.");
            return;
        }
        if (List.of (args).contains ("--iniciar-replica-local"))
        {
            /* Inicialização opcional do replica set, e só no Mongo local 127.0.0.1. Fora desse host a opção falha. */
            ConnectionString config = new ConnectionString (uri);
            if (config.getHosts ().size () != 1 || !config.getHosts ().get (0).startsWith ("127.0.0.1:"))
                throw new Exception ("Inicialização permitida somente em 127.0.0.1.");
            try
            {
                conexao.getCliente ().getDatabase ("admin").runCommand (new Document ("replSetInitiate",
                    new Document ("_id", "rs0").append ("members", List.of (new Document ("_id", 0).append ("host", config.getHosts ().get (0))))));
            }
            catch (com.mongodb.MongoCommandException erro)
            {
                if (erro.getErrorCode () != 23) throw erro; // AlreadyInitialized
            }
            boolean primaria = false;
            for (int i = 0; i < 100 && !primaria; i++)
            {
                primaria = Boolean.TRUE.equals (conexao.getBanco ().runCommand (new Document ("hello", 1)).getBoolean ("isWritablePrimary"));
                if (!primaria) Thread.sleep (100);
            }
            conexao.fechar ();
            if (!primaria) throw new Exception ("Replica set não ficou pronto.");
            System.out.println ("Replica set local de teste pronto.");
            return;
        }

        /* Garante os dois produtores do teste e uma atividade já gravada em cada propriedade, para o isolamento do consumo. */
        RepositorioUsuariosMongo usuarios = new RepositorioUsuariosMongo (conexao);
        for (String email : List.of ("estoque-e2e@agro.test", "outro-estoque@agro.test"))
        {
            Document usuario = usuarios.buscarPor ("email", email);
            if (usuario == null)
                usuario = usuarios.criar (new Document ("name", "Produtor de teste").append ("email", email)
                    .append ("senhaHash", Senhas.gerar ("Senha@123")).append ("emailConfirmado", true),
                    new Document ("farm", "Propriedade " + email).append ("area", "50 ha"));
            String pid = usuario.getString ("propriedadeId");
            if (conexao.getBanco ().getCollection ("atividades").countDocuments (new Document ("propriedadeId", pid)) == 0)
                conexao.getBanco ().getCollection ("atividades").insertOne (new Document ("_id", new ObjectId ())
                    .append ("propriedadeId", pid).append ("usuarioId", usuario.getObjectId ("_id").toHexString ())
                    .append ("titulo", "estoque-e2e@agro.test".equals (email) ? "Aplicação persistida" : "Atividade de outro produtor"));
        }
        /* Sobe a API na 8080 e fica de pé até a linha "desativar", para o navegador exercitar saldo e histórico. */
        Servidor servidor = new Servidor ("127.0.0.1", 8080,
            Servidor.montarRoteador (usuarios, new RepositorioTalhoesMongo (conexao.getBanco ()),
                new RepositorioEstoqueMongo (conexao), new RepositorioSafrasMongo (conexao.getBanco ()),
                new RepositorioCustosMongo (conexao.getBanco ()), conexao, Path.of ("../../frontend")), conexao, 100);
        System.out.println ("CENARIO_ESTOQUE_PRONTO");
        try (java.util.Scanner entrada = new java.util.Scanner (System.in))
        {
            while (entrada.hasNextLine () && !entrada.nextLine ().equals ("desativar")) { }
        }
        servidor.desativar ();
    }
}
