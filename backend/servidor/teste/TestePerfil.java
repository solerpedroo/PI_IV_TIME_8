/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
import dados.*;
import http.Roteador;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;
import org.bson.Document;
import seguranca.Senhas;

/* Contratos de perfil por HTTP real. --mongo também encerra/reabre o servidor
   e a conexão para conferir persistência. Não usa JUnit nem apaga coleções. */
public class TestePerfil
{
    private static int total;
    private static Servidor servidor;
    private static RepositorioUsuarios usuarios;
    private static ConexaoMongo conexao;
    private static final HttpClient cliente = HttpClient.newHttpClient ();

    private static void conferir (boolean condicao, String texto) throws Exception
    {
        if (!condicao) throw new Exception ("FALHA: " + texto);
        total++;
        System.out.println ("ok perfil: " + texto);
    }

    private static HttpResponse<String> chamar (String metodo, String caminho, Document corpo, String token, int status) throws Exception
    {
        HttpRequest.Builder req = HttpRequest.newBuilder (URI.create ("http://127.0.0.1:" + servidor.getPorta () + caminho))
            .header ("Content-Type", "application/json");
        if (token != null) req.header ("Authorization", "Bearer " + token);
        HttpResponse<String> resposta = cliente.send (req.method (metodo, corpo == null ? HttpRequest.BodyPublishers.noBody () :
            HttpRequest.BodyPublishers.ofString (corpo.toJson ())).build (), HttpResponse.BodyHandlers.ofString ());
        conferir (resposta.statusCode () == status, metodo + " " + caminho + " → " + status + " (recebido " + resposta.statusCode () + ")");
        return resposta;
    }

    private static Document get (String token) throws Exception
    {
        return Document.parse (chamar ("GET", "/api/perfil", null, token, 200).body ());
    }

    private static String login (String email, String senha, int status) throws Exception
    {
        Document ret = Document.parse (chamar ("POST", "/api/auth/login", new Document ("email", email).append ("password", senha), null, status).body ());
        return ret.getString ("token");
    }

    private static void iniciar (boolean mongo) throws Exception
    {
        if (mongo)
        {
            conexao = new ConexaoMongo (uriTeste ());
            usuarios = new RepositorioUsuariosMongo (conexao);
        }
        else usuarios = new RepositorioUsuariosMemoria ();
        Roteador rotas = Servidor.montarRoteador (usuarios, mongo ? new RepositorioTalhoesMongo (conexao.getBanco ()) :
            new RepositorioTalhoesMemoria (), conexao, Path.of ("../.."));
        servidor = new Servidor ("127.0.0.1", 0, rotas, conexao, 100);
    }

    public static String uriTeste () throws Exception
    {
        String uri = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null : new com.mongodb.ConnectionString (uri).getDatabase ();
        if (banco == null || !banco.endsWith ("_teste")) throw new Exception ("Use um banco dedicado terminado em _teste.");
        return uri;
    }

    public static void limpar (ConexaoMongo banco, String uid) throws Exception
    {
        for (String colecao : List.of ("propriedades"))
            banco.getBanco ().getCollection (colecao).deleteMany (new Document ("usuarioId", uid));
        banco.getBanco ().getCollection ("usuarios").deleteOne (new Document ("_id", new org.bson.types.ObjectId (uid)));
    }

    public static void main (String[] args) throws Exception
    {
        if (args.length > 0 && args[0].equals ("--limpar-e2e"))
        {
            ConexaoMongo banco = new ConexaoMongo (uriTeste ());
            try
            {
                for (int i = 1; i < args.length; i++)
                {
                    Document u = banco.getBanco ().getCollection ("usuarios").find (new Document ("_id", new org.bson.types.ObjectId (args[i]))).first ();
                    if (u != null && u.getString ("email").matches ("perfil-e2e-[A-Za-z0-9-]+@agro\\.test")) limpar (banco, args[i]);
                }
            }
            finally { banco.fechar (); }
            return;
        }
        boolean mongo = List.of (args).contains ("--mongo");
        for (String variavel : List.of ("SMTP_HOST", "SMTP_USERNAME", "SMTP_PASSWORD", "SMTP_FROM", "SMTP_PORT"))
            if (System.getenv (variavel) != null && !System.getenv (variavel).isBlank ())
                throw new Exception ("Testes de perfil exigem SMTP em modo local, sem credenciais.");
        ArrayList<String> ids = new ArrayList<> ();
        iniciar (mongo);
        try
        {
            String email = "perfil-java-" + UUID.randomUUID () + "@agro.test";
            String novoEmail = "novo-" + email;
            Document u = usuarios.criar (new Document ("name", "Usuário de teste").append ("email", email)
                .append ("phone", "(19) 99999-1234").append ("emailConfirmado", true).append ("senhaHash", Senhas.gerar ("Senha@123")),
                new Document ("farm", "Propriedade de teste").append ("area", "10 ha"));
            String uid = u.getObjectId ("_id").toHexString (); ids.add (uid);
            Document outro = usuarios.criar (new Document ("name", "Outro produtor").append ("email", "outro-" + email)
                .append ("emailConfirmado", true).append ("senhaHash", Senhas.gerar ("Senha@123")), new Document ("farm", "Outra fazenda"));
            ids.add (outro.getObjectId ("_id").toHexString ());
            String token = login (email, "Senha@123", 200);
            String outraSessao = login (email, "Senha@123", 200);
            chamar ("GET", "/api/perfil", null, null, 401);
            Document perfil = get (token);
            conferir (perfil.get ("usuario", Document.class).getString ("email").equals (email), "dados do usuário real");
            conferir (!perfil.toJson ().contains ("senhaHash") && !perfil.toJson ().contains ("tokenTrocaEmail"), "API não expõe segredos");
            Document campos = new Document ("name", "Nome editado").append ("email", email).append ("phone", "(11) 98888-1234");
            chamar ("PUT", "/api/perfil", campos, token, 200);
            conferir (get (token).get ("usuario", Document.class).getString ("name").equals ("Nome editado"), "nome e telefone persistidos");
            chamar ("PUT", "/api/perfil", new Document (campos).append ("name", ""), token, 400);
            chamar ("PUT", "/api/perfil", new Document (campos).append ("phone", "inválido"), token, 400);
            chamar ("PUT", "/api/perfil", new Document (campos).append ("email", "inválido"), token, 400);
            Document prefs = new Document ("stock", false).append ("weather", false).append ("activities", true).append ("occurrences", false).append ("reports", true);
            chamar ("PUT", "/api/perfil/preferencias", prefs, token, 200);
            conferir (get (token).get ("preferencias", Document.class).equals (prefs), "preferências recuperadas exatamente");
            chamar ("PUT", "/api/perfil/preferencias", new Document (prefs).append ("stock", "false"), token, 400);
            Document segunda = usuarios.criarPropriedade (uid, new Document ("nome", "Segunda fazenda"));
            String pid = segunda.getObjectId ("_id").toHexString ();
            chamar ("PUT", "/api/perfil/propriedade-padrao", new Document ("propriedadeId", outro.getString ("propriedadeId")), token, 404);
            chamar ("PUT", "/api/perfil/propriedade-padrao", new Document ("propriedadeId", pid), token, 200);
            Document me = Document.parse (chamar ("GET", "/api/me", null, token, 200).body ());
            conferir (me.get ("usuario", Document.class).getString ("propriedadeId").equals (u.getString ("propriedadeId")), "padrão não altera sessão já aberta");
            String novoLogin = login (email, "Senha@123", 200);
            conferir (Document.parse (chamar ("GET", "/api/me", null, novoLogin, 200).body ()).get ("usuario", Document.class).getString ("propriedadeId").equals (pid), "novo login inicia na propriedade padrão");
            chamar ("PUT", "/api/perfil", new Document (campos).append ("email", outro.getString ("email")).append ("senhaAtual", "Senha@123"), token, 400);
            chamar ("PUT", "/api/perfil", new Document (campos).append ("email", novoEmail), token, 400);
            chamar ("PUT", "/api/perfil", new Document (campos).append ("email", novoEmail).append ("senhaAtual", "Senha@123"), token, 200);
            Document pendente = get (token).get ("usuario", Document.class);
            conferir (pendente.getString ("email").equals (email) && pendente.getString ("emailPendente").equals (novoEmail), "e-mail antigo preservado até confirmação");
            chamar ("POST", "/api/perfil/email/reenviar", new Document (), token, 429);
            chamar ("POST", "/api/perfil/email/confirmar", new Document ("codigo", "inválido"), token, 400);
            String codigo = usuarios.buscarPorId (uid).getString ("tokenTrocaEmail");
            usuarios.atualizar (uid, new Document ("trocaEmailExpiraEm", 0L));
            chamar ("POST", "/api/perfil/email/confirmar", new Document ("codigo", codigo), token, 400);
            usuarios.atualizar (uid, new Document ("ultimoEnvioTrocaEmail", 0L));
            chamar ("POST", "/api/perfil/email/reenviar", new Document (), token, 200);
            chamar ("POST", "/api/perfil/email/confirmar", new Document ("codigo", codigo), token, 400);
            codigo = usuarios.buscarPorId (uid).getString ("tokenTrocaEmail");
            chamar ("POST", "/api/perfil/email/confirmar", new Document ("codigo", codigo), token, 200);
            chamar ("GET", "/api/me", null, outraSessao, 401);
            login (email, "Senha@123", 401);
            token = login (novoEmail, "Senha@123", 200);
            chamar ("POST", "/api/perfil/email/confirmar", new Document ("codigo", codigo), token, 400);
            Document senha = new Document ("current", "errada").append ("new", "Nova@1234").append ("confirm", "Nova@1234");
            chamar ("PUT", "/api/perfil/senha", senha, token, 400);
            chamar ("PUT", "/api/perfil/senha", new Document (senha).append ("current", "Senha@123").append ("new", "fraca"), token, 400);
            chamar ("PUT", "/api/perfil/senha", new Document (senha).append ("current", "Senha@123").append ("confirm", "diferente"), token, 400);
            usuarios.atualizar (uid, new Document ("tokenRedefinicao", "token-antigo-perfil").append ("redefinicaoExpiraEm", System.currentTimeMillis () + 60000));
            chamar ("PUT", "/api/perfil/senha", new Document (senha).append ("current", "Senha@123"), token, 200);
            chamar ("GET", "/api/me", null, token, 401);
            login (novoEmail, "Senha@123", 401);
            token = login (novoEmail, "Nova@1234", 200);
            chamar ("POST", "/api/auth/reset-password", new Document ("token", "token-antigo-perfil").append ("password", "Velha@1234").append ("confirm", "Velha@1234"), null, 400);
            conferir (!usuarios.atualizarCondicional (uid, new Document ("senhaHash", "hash-antigo"), new Document ("name", "Não deve salvar")), "comparação impede gravação com credencial obsoleta");
            if (mongo)
            {
                servidor.desativar (); iniciar (true);
                token = login (novoEmail, "Nova@1234", 200);
                perfil = get (token);
                conferir (perfil.get ("preferencias", Document.class).equals (prefs) && perfil.get ("usuario", Document.class).getString ("propriedadePadraoId").equals (pid), "preferências e padrão sobrevivem a reinício real");
            }
            chamar ("POST", "/api/auth/logout", null, token, 204);
            chamar ("GET", "/api/perfil", null, token, 401);
            System.out.println ("TestePerfil: " + total + " verificações OK" + (mongo ? " com MongoDB e reinício" : " em memória"));
        }
        finally
        {
            if (servidor != null) servidor.desativar ();
            if (mongo)
            {
                ConexaoMongo limpeza = new ConexaoMongo (uriTeste ());
                try { for (String id : ids) limpar (limpeza, id); }
                finally { limpeza.fechar (); }
            }
        }
    }
}
