/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import dados.RepositorioTalhoesMemoria;
import dados.RepositorioUsuariosMemoria;
import http.Roteador;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.LimitadorDeTaxa;
import seguranca.Sessoes;
import handlers.EstaticosHandler;
import http.Requisicao;

/*
    Testes sem JUnit. Sobe o servidor de verdade (porta livre, repositorios em memoria)
    e faz requisicoes HTTP reais por socket, inclusive requisicoes mal formadas.
*/
public class TesteServidor
{
    private static int falhas = 0;
    private static int total  = 0;
    private static int porta;

    private static RepositorioUsuariosMemoria usuarios;

    // ------------------------------------------------------------------ auxiliares

    private static void conferir (String nome, boolean condicao)
    {
        TesteServidor.total++;

        if (condicao)
            System.out.println ("ok    " + nome);
        else
        {
            TesteServidor.falhas++;
            System.out.println ("FALHA " + nome);
        }
    }

    private static void conferir (String nome, int statusEsperado, Resp resp)
    {
        TesteServidor.total++;

        if (resp.status == statusEsperado)
            System.out.println ("ok    " + nome);
        else
        {
            TesteServidor.falhas++;
            System.out.println ("FALHA " + nome + ": esperava " + statusEsperado + " e veio " +
                                resp.status + " " + resp.corpo);
        }
    }

    // resposta crua: status, cabecalhos (texto) e corpo
    private static class Resp
    {
        int    status;
        String cabecalhos;
        String corpo;

        Document json ()
        {
            return Document.parse (this.corpo);
        }

        String cabecalho (String nome)
        {
            for (String linha : this.cabecalhos.split ("\r\n"))
                if (linha.toLowerCase ().startsWith (nome.toLowerCase () + ":"))
                    return linha.substring (nome.length () + 1).trim ();
            return null;
        }
    }

    // manda bytes crus e le tudo ate o servidor fechar
    private static Resp bruto (int porta, byte[] envio) throws Exception
    {
        try (Socket conexao = new Socket ("127.0.0.1", porta))
        {
            conexao.setSoTimeout (15000);

            OutputStream saida = conexao.getOutputStream ();
            saida.write (envio);
            saida.flush ();

            InputStream           entrada = conexao.getInputStream ();
            ByteArrayOutputStream lido    = new ByteArrayOutputStream ();
            byte[]                pedaco  = new byte[4096];
            int                   n;
            try
            {
                while ((n = entrada.read (pedaco)) != -1)
                    lido.write (pedaco, 0, n);
            }
            catch (java.net.SocketException erro)
            {} // servidor pode resetar depois de responder (ex.: 413 sem ler o corpo)

            String texto = lido.toString (StandardCharsets.UTF_8);
            Resp   ret   = new Resp ();
            int    fim   = texto.indexOf ("\r\n\r\n");

            if (fim < 0)
            {
                ret.status     = -1;
                ret.cabecalhos = texto;
                ret.corpo      = "";
                return ret;
            }

            ret.cabecalhos = texto.substring (0, fim);
            ret.corpo      = texto.substring (fim + 4);
            ret.status     = Integer.parseInt (ret.cabecalhos.split (" ")[1]);
            return ret;
        }
    }

    private static Resp bruto (String envio) throws Exception
    {
        return TesteServidor.bruto (TesteServidor.porta, envio.getBytes (StandardCharsets.UTF_8));
    }

    private static Resp requisitar (String metodo, String caminho, String json, String token) throws Exception
    {
        byte[] corpo = json == null ? new byte[0] : json.getBytes (StandardCharsets.UTF_8);

        String cabeca = metodo + " " + caminho + " HTTP/1.1\r\n" +
                        "Host: 127.0.0.1\r\n" +
                        (token == null ? "" : "Authorization: Bearer " + token + "\r\n") +
                        (json  == null ? "" : "Content-Type: application/json\r\n") +
                        "Content-Length: " + corpo.length + "\r\n" +
                        "\r\n";

        ByteArrayOutputStream envio = new ByteArrayOutputStream ();
        envio.write (cabeca.getBytes (StandardCharsets.UTF_8));
        envio.write (corpo);

        return TesteServidor.bruto (TesteServidor.porta, envio.toByteArray ());
    }

    private static Resp get (String caminho, String token) throws Exception
    {
        return TesteServidor.requisitar ("GET", caminho, null, token);
    }

    private static Resp post (String caminho, String json, String token) throws Exception
    {
        return TesteServidor.requisitar ("POST", caminho, json, token);
    }

    private static Resp put (String caminho, String json, String token) throws Exception
    {
        return TesteServidor.requisitar ("PUT", caminho, json, token);
    }

    private static String cadastro (String email)
    {
        return new Document ("name", "Maria Silva").append ("email", email).append ("phone", "(11) 98765-4321")
                   .append ("password", "Senha@123").append ("password-confirm", "Senha@123")
                   .append ("farm", "Fazenda Boa Vista").append ("area", "150 hectares").append ("terms", true)
                   .toJson ();
    }

    private static String login (String email, String senha) throws Exception
    {
        Resp resp = TesteServidor.post ("/api/auth/login",
                                        new Document ("email", email).append ("password", senha).toJson (), null);
        return resp.status == 200 ? resp.json ().getString ("token") : null;
    }

    private static Document camposComErro (Resp resp)
    {
        if (resp.status != 400)
            return new Document ();
        return resp.json ().get ("fields", new Document ());
    }

    private static String idDoUsuario (String email) throws Exception
    {
        return ((ObjectId) TesteServidor.usuarios.buscarPor ("email", email).get ("_id")).toHexString ();
    }

    // ------------------------------------------------------------------ grupos de teste

    private static void testarHttp () throws Exception
    {
        System.out.println ("\n# parse HTTP");

        conferir ("lixo na linha de requisicao -> 400",       400, bruto ("GARBAGE\r\n\r\n"));
        conferir ("HTTP/1.1 sem Host -> 400",                 400, bruto ("GET /health HTTP/1.1\r\n\r\n"));
        conferir ("HTTP/1.0 sem Host e aceito",               200, bruto ("GET /health HTTP/1.0\r\n\r\n"));
        conferir ("versao desconhecida -> 505",               505, bruto ("GET /health HTTP/2.0\r\nHost: x\r\n\r\n"));
        conferir ("alvo sem barra -> 400",                    400, bruto ("GET health HTTP/1.1\r\nHost: x\r\n\r\n"));
        conferir ("cabecalho sem dois-pontos -> 400",         400, bruto ("GET /health HTTP/1.1\r\nHost: x\r\nquebrado\r\n\r\n"));
        conferir ("Content-Length nao numerico -> 400",       400, bruto ("POST /api/auth/login HTTP/1.1\r\nHost: x\r\nContent-Length: abc\r\n\r\n"));
        conferir ("Content-Length duplicado -> 400",          400, bruto ("POST /api/auth/login HTTP/1.1\r\nHost: x\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\nab"));
        conferir ("corpo maior que 1 MB -> 413",              413, bruto ("POST /api/auth/login HTTP/1.1\r\nHost: x\r\nContent-Length: 2000000\r\n\r\n"));
        conferir ("cabecalhos maiores que 16 KB -> 431",      431, bruto ("GET /health HTTP/1.1\r\nHost: x\r\nX-Grande: " + "a".repeat (17000) + "\r\n\r\n"));
        conferir ("Transfer-Encoding chunked -> 501",         501, bruto ("POST /api/auth/login HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"));
        conferir ("percent-encoding invalido -> 400",         400, bruto ("GET /%zz HTTP/1.1\r\nHost: x\r\n\r\n"));
        conferir ("JSON invalido -> 400",                     400, post ("/api/auth/login", "{nao e json", null));
        conferir ("JSON que nao e objeto -> 400",             400, post ("/api/auth/login", "[1,2]", null));

        // corpo menor que o Content-Length: o cliente fecha a escrita no meio
        Resp incompleto;
        try (Socket conexao = new Socket ("127.0.0.1", TesteServidor.porta))
        {
            conexao.setSoTimeout (15000);
            conexao.getOutputStream ().write ("POST /api/auth/login HTTP/1.1\r\nHost: x\r\nContent-Length: 50\r\n\r\n{}".getBytes ());
            conexao.shutdownOutput ();
            String texto = new String (conexao.getInputStream ().readAllBytes (), StandardCharsets.UTF_8);
            incompleto        = new Resp ();
            incompleto.status = Integer.parseInt (texto.split (" ")[1]);
            incompleto.corpo  = texto;
        }
        conferir ("corpo incompleto -> 400", 400, incompleto);

        conferir ("arquivo inexistente -> 404",               404, get ("/nada.html", null));
        Resp metodo = requisitar ("POST", "/health", null, null);
        conferir ("POST em rota so GET -> 405",               405, metodo);
        conferir ("405 traz cabecalho Allow",                 "GET, OPTIONS".equals (metodo.cabecalho ("Allow")));
        conferir ("DELETE em estatico -> 405",                405, requisitar ("DELETE", "/login.html", null, null));
        conferir ("toda resposta tem Connection: close",      "close".equals (metodo.cabecalho ("Connection")));

        Resp head = requisitar ("HEAD", "/login.html", null, null);
        conferir ("HEAD -> 200 sem corpo e com Content-Length",
                  head.status == 200 && head.corpo.isEmpty () && Integer.parseInt (head.cabecalho ("Content-Length")) > 0);

        Resp cors = bruto ("OPTIONS /api/talhoes HTTP/1.1\r\nHost: x\r\nOrigin: http://localhost:5500\r\n\r\n");
        conferir ("OPTIONS -> 204",                           204, cors);
        conferir ("CORS libera localhost",                    "http://localhost:5500".equals (cors.cabecalho ("Access-Control-Allow-Origin")));
        Resp corsRuim = bruto ("GET /health HTTP/1.1\r\nHost: x\r\nOrigin: http://malicioso.com\r\n\r\n");
        conferir ("CORS nao libera outra origem",             corsRuim.cabecalho ("Access-Control-Allow-Origin") == null);
    }

    private static void testarSaudeEEstaticos () throws Exception
    {
        System.out.println ("\n# saude e estaticos");

        Resp saude = get ("/health", null);
        conferir ("GET /health -> 200 UP", saude.status == 200 && saude.corpo.equals ("{\"status\":\"UP\"}"));
        conferir ("GET /ready (memoria) -> 200", 200, get ("/ready", null));

        Resp raiz = get ("/", null);
        conferir ("/ redireciona para login.html", raiz.status == 302 && "/login.html".equals (raiz.cabecalho ("Location")));

        Resp html = get ("/login.html", null);
        conferir ("login.html -> 200 text/html",
                  html.status == 200 && html.cabecalho ("Content-Type").startsWith ("text/html") && html.corpo.contains ("<form"));
        conferir ("css -> text/css",         get ("/styles/auth.css",  null).cabecalho ("Content-Type").startsWith ("text/css"));
        conferir ("js -> text/javascript",   get ("/scripts/auth.js",  null).cabecalho ("Content-Type").startsWith ("text/javascript"));
        conferir ("jpg -> image/jpeg",       "image/jpeg".equals (get ("/Assets/Logo.jpg", null).cabecalho ("Content-Type")));

        conferir ("bloqueia ../",                 404, get ("/../.env.example", null));
        conferir ("bloqueia ../ codificado",      404, get ("/%2e%2e/%2e%2e/Windows/win.ini", null));
        conferir ("bloqueia ..\\",                404, get ("/scripts\\..\\..\\.env.example", null));
        conferir ("bloqueia .env",                404, get ("/.env", null));
        conferir ("bloqueia .env.example",        404, get ("/.env.example", null));
        conferir ("bloqueia .git",                404, get ("/.git/config", null));
        conferir ("bloqueia .gitignore",          404, get ("/.gitignore", null));
        conferir ("bloqueia backend/",            404, get ("/backend/servidor/teste/TesteServidor.java", null));
        conferir ("bloqueia Backend/ (Windows)",  404, get ("/Backend/gateway/rodar.sh", null));
        conferir ("bloqueia nome curto BACKEN~1", 404, get ("/BACKEN~1/x.js", null));
        conferir ("bloqueia extensao fora da lista (.md)",   404, get ("/docs/README.md", null));
        conferir ("bloqueia extensao fora da lista (.yaml)", 404, get ("/compose.yaml", null));
        conferir ("bloqueia ponto no fim (Windows)",         404, get ("/login.html.", null));
    }

    private static void testarCadastro () throws Exception
    {
        System.out.println ("\n# cadastro");

        Document vazio = camposComErro (post ("/api/auth/register", "{}", null));
        conferir ("corpo vazio -> 400 apontando todos os campos",
                  vazio.keySet ().containsAll (List.of ("name", "email", "phone", "password", "confirm", "farm", "area", "terms")));

        Document base = Document.parse (cadastro ("x@agro.com"));

        conferir ("e-mail invalido",     camposComErro (post ("/api/auth/register", new Document (base).append ("email", "maria@").toJson (), null)).containsKey ("email"));
        conferir ("senha sem simbolo",   camposComErro (post ("/api/auth/register", new Document (base).append ("password", "Senha1234").append ("password-confirm", "Senha1234").toJson (), null)).containsKey ("password"));
        conferir ("senha sem maiuscula", camposComErro (post ("/api/auth/register", new Document (base).append ("password", "senha@123").append ("password-confirm", "senha@123").toJson (), null)).containsKey ("password"));
        conferir ("senha curta",         camposComErro (post ("/api/auth/register", new Document (base).append ("password", "Se@1").append ("password-confirm", "Se@1").toJson (), null)).containsKey ("password"));
        conferir ("confirmacao diferente", camposComErro (post ("/api/auth/register", new Document (base).append ("password-confirm", "Outra@123").toJson (), null)).containsKey ("password-confirm"));
        conferir ("telefone fixo recusado", camposComErro (post ("/api/auth/register", new Document (base).append ("phone", "(11) 3456-7890").toJson (), null)).containsKey ("phone"));
        conferir ("telefone sem mascara recusado", camposComErro (post ("/api/auth/register", new Document (base).append ("phone", "11987654321").toJson (), null)).containsKey ("phone"));
        conferir ("termos falsos",       camposComErro (post ("/api/auth/register", new Document (base).append ("terms", false).toJson (), null)).containsKey ("terms"));
        conferir ("nome so com espacos", camposComErro (post ("/api/auth/register", new Document (base).append ("name", "   ").toJson (), null)).containsKey ("name"));
        conferir ("nome que nao e texto", camposComErro (post ("/api/auth/register", new Document (base).append ("name", 42).toJson (), null)).containsKey ("name"));

        Resp criado = post ("/api/auth/register", cadastro ("maria@agro.com"), null);
        conferir ("cadastro valido -> 201", 201, criado);
        Document usuario = criado.status == 201 ? criado.json ().get ("usuario", Document.class) : new Document ();
        conferir ("resposta nao expoe hash nem token",
                  !criado.corpo.contains ("senhaHash") && !criado.corpo.contains ("token") && usuario.getString ("id") != null);
        conferir ("usuario nasce com emailConfirmado=false", Boolean.FALSE.equals (usuario.getBoolean ("emailConfirmado")));
        conferir ("senha guardada como PBKDF2",
                  usuarios.buscarPor ("email", "maria@agro.com").getString ("senhaHash").startsWith ("pbkdf2$"));

        Resp repetido = post ("/api/auth/register", cadastro ("MARIA@agro.com"), null);
        conferir ("e-mail repetido (maiusculas) -> 409", 409, repetido);

        Document comConfirm = Document.parse (cadastro ("joao@agro.com"));
        comConfirm.remove ("password-confirm");
        comConfirm.put ("confirm", "Senha@123");
        conferir ("aceita o campo confirm no lugar de password-confirm", 201, post ("/api/auth/register", comConfirm.toJson (), null));
    }

    private static void testarLogin () throws Exception
    {
        System.out.println ("\n# login, logout e token");

        Resp errada     = post ("/api/auth/login", "{\"email\":\"maria@agro.com\",\"password\":\"Errada@123\"}", null);
        Resp inexistente = post ("/api/auth/login", "{\"email\":\"ninguem@agro.com\",\"password\":\"Errada@123\"}", null);
        conferir ("senha errada -> 401",        401, errada);
        conferir ("e-mail inexistente -> 401",  401, inexistente);
        conferir ("401 generico (mesmo corpo)", errada.corpo.equals (inexistente.corpo));
        conferir ("login sem senha -> 400",     400, post ("/api/auth/login", "{\"email\":\"maria@agro.com\"}", null));

        Resp ok = post ("/api/auth/login", "{\"email\":\" Maria@Agro.com \",\"password\":\"Senha@123\"}", null);
        conferir ("login valido -> 200", 200, ok);
        String token = ok.status == 200 ? ok.json ().getString ("token") : null;
        conferir ("login devolve token e usuario",
                  token != null && "maria@agro.com".equals (ok.json ().get ("usuario", Document.class).getString ("email")));

        Resp eu = get ("/api/me", token);
        conferir ("GET /api/me com token -> 200", 200, eu);
        conferir ("/api/me traz a propriedade",
                  eu.status == 200 && "Fazenda Boa Vista".equals (eu.json ().get ("propriedade", Document.class).getString ("farm")));
        conferir ("GET /api/me sem token -> 401",      401, get ("/api/me", null));
        conferir ("GET /api/me com token falso -> 401", 401, get ("/api/me", "nao-existe"));
        conferir ("/api inexistente sem token -> 401",  401, get ("/api/nada", null));
        conferir ("/api inexistente com token -> 404",  404, get ("/api/nada", token));

        conferir ("logout -> 204", 204, post ("/api/auth/logout", null, token));
        conferir ("token nao vale depois do logout", 401, get ("/api/me", token));
    }

    private static void testarConfirmacaoERedefinicao () throws Exception
    {
        System.out.println ("\n# confirmacao de e-mail e redefinicao de senha");

        String tokenConfirmacao = usuarios.buscarPor ("email", "maria@agro.com").getString ("tokenConfirmacao");
        conferir ("token errado -> 400",    400, post ("/api/auth/confirm-email", "{\"token\":\"errado\"}", null));
        conferir ("confirmar e-mail -> 200", 200, post ("/api/auth/confirm-email", new Document ("token", tokenConfirmacao).toJson (), null));
        conferir ("token de confirmacao nao reutiliza", 400, post ("/api/auth/confirm-email", new Document ("token", tokenConfirmacao).toJson (), null));

        String token = login ("maria@agro.com", "Senha@123");
        conferir ("/api/me mostra e-mail confirmado",
                  Boolean.TRUE.equals (get ("/api/me", token).json ().get ("usuario", Document.class).getBoolean ("emailConfirmado")));

        // joao acabou de se cadastrar: reenvio antes de 30 s e recusado
        Resp cedo = post ("/api/auth/resend-confirmation", "{\"email\":\"joao@agro.com\"}", null);
        conferir ("reenvio antes de 30 s -> 429", 429, cedo);
        conferir ("429 traz Retry-After", cedo.cabecalho ("Retry-After") != null);

        String idJoao     = idDoUsuario ("joao@agro.com");
        String tokenAntes = usuarios.buscarPor ("email", "joao@agro.com").getString ("tokenConfirmacao");
        usuarios.atualizar (idJoao, new Document ("ultimoEnvioConfirmacao", System.currentTimeMillis () - 31000));
        conferir ("reenvio depois de 30 s -> 200", 200, post ("/api/auth/resend-confirmation", "{\"email\":\"joao@agro.com\"}", null));
        conferir ("reenvio gera token novo", !tokenAntes.equals (usuarios.buscarPor ("email", "joao@agro.com").getString ("tokenConfirmacao")));
        conferir ("reenvio de novo logo depois -> 429", 429, post ("/api/auth/resend-confirmation", "{\"email\":\"joao@agro.com\"}", null));

        conferir ("esqueci a senha (e-mail inexistente) -> 200", 200, post ("/api/auth/forgot-password", "{\"email\":\"ninguem@agro.com\"}", null));
        conferir ("esqueci a senha (e-mail invalido) -> 400",    400, post ("/api/auth/forgot-password", "{\"email\":\"invalido\"}", null));
        conferir ("esqueci a senha -> 200",                      200, post ("/api/auth/forgot-password", "{\"email\":\"maria@agro.com\"}", null));

        String tokenRedefinicao = usuarios.buscarPor ("email", "maria@agro.com").getString ("tokenRedefinicao");
        conferir ("token de redefinicao foi gerado", tokenRedefinicao != null);

        conferir ("redefinir com senha fraca -> 400", 400,
                  post ("/api/auth/reset-password", new Document ("token", tokenRedefinicao).append ("password", "fraca").append ("password-confirm", "fraca").toJson (), null));
        conferir ("redefinir com confirmacao diferente -> 400", 400,
                  post ("/api/auth/reset-password", new Document ("token", tokenRedefinicao).append ("password", "Nova@1234").append ("password-confirm", "Nova@9999").toJson (), null));
        conferir ("redefinir com token errado -> 400", 400,
                  post ("/api/auth/reset-password", new Document ("token", "errado").append ("password", "Nova@1234").append ("password-confirm", "Nova@1234").toJson (), null));
        conferir ("redefinir senha -> 200", 200,
                  post ("/api/auth/reset-password", new Document ("token", tokenRedefinicao).append ("password", "Nova@1234").append ("password-confirm", "Nova@1234").toJson (), null));

        conferir ("sessao antiga cai depois da redefinicao", 401, get ("/api/me", token));
        conferir ("senha antiga nao entra mais", login ("maria@agro.com", "Senha@123") == null);
        conferir ("senha nova entra",            login ("maria@agro.com", "Nova@1234") != null);
        conferir ("token de redefinicao nao reutiliza", 400,
                  post ("/api/auth/reset-password", new Document ("token", tokenRedefinicao).append ("password", "Outra@1234").append ("password-confirm", "Outra@1234").toJson (), null));

        // token vencido
        post ("/api/auth/forgot-password", "{\"email\":\"joao@agro.com\"}", null);
        String vencido = usuarios.buscarPor ("email", "joao@agro.com").getString ("tokenRedefinicao");
        usuarios.atualizar (idJoao, new Document ("redefinicaoExpiraEm", System.currentTimeMillis () - 1));
        conferir ("token de redefinicao vencido -> 400", 400,
                  post ("/api/auth/reset-password", new Document ("token", vencido).append ("password", "Nova@1234").append ("password-confirm", "Nova@1234").toJson (), null));
    }

    private static String talhao (String nome, String cultura, Object hectares, String status)
    {
        Document ret = new Document ("nome", nome).append ("cultura", cultura).append ("hectares", hectares);
        if (status != null)
            ret.append ("status", status);
        return ret.toJson ();
    }

    private static void testarTalhoes () throws Exception
    {
        System.out.println ("\n# talhoes e isolamento entre usuarios");

        String a = login ("maria@agro.com", "Nova@1234");
        String b = login ("joao@agro.com",  "Senha@123");

        conferir ("listar sem token -> 401", 401, get ("/api/talhoes", null));
        conferir ("criar sem token -> 401",  401, post ("/api/talhoes", talhao ("T-01", "Soja", 10, null), null));

        Document vazio = camposComErro (post ("/api/talhoes", "{}", a));
        conferir ("corpo vazio -> 400 com nome, cultura e hectares", vazio.keySet ().containsAll (List.of ("nome", "cultura", "hectares")));
        conferir ("cultura fora da lista -> 400", camposComErro (post ("/api/talhoes", talhao ("T-01", "Arroz", 10, null), a)).containsKey ("cultura"));
        conferir ("hectares zero -> 400",         camposComErro (post ("/api/talhoes", talhao ("T-01", "Soja", 0, null), a)).containsKey ("hectares"));
        conferir ("hectares negativo -> 400",     camposComErro (post ("/api/talhoes", talhao ("T-01", "Soja", -5, null), a)).containsKey ("hectares"));
        conferir ("hectares como texto -> 400",   camposComErro (post ("/api/talhoes", talhao ("T-01", "Soja", "10", null), a)).containsKey ("hectares"));
        conferir ("status fora da lista -> 400",  camposComErro (post ("/api/talhoes", talhao ("T-01", "Soja", 10, "Colhido"), a)).containsKey ("status"));

        Resp t1 = post ("/api/talhoes", talhao ("T-01 Norte", "Soja", 124, null), a);
        conferir ("criar talhao -> 201", 201, t1);
        Document doc1 = t1.status == 201 ? t1.json () : new Document ();
        conferir ("status padrao Ativo", "Ativo".equals (doc1.getString ("status")));
        conferir ("devolve id e esconde campos internos",
                  doc1.getString ("id") != null && !t1.corpo.contains ("propriedadeId") && !t1.corpo.contains ("nomeNormalizado"));

        String id1 = doc1.getString ("id");
        conferir ("historico vazio do talhao -> 200", 200, get ("/api/talhoes/" + id1 + "/historico", a));
        conferir ("registrar atividade no historico -> 201", 201,
                  post ("/api/talhoes/" + id1 + "/historico", "{\"tipo\":\"atividade\",\"descricao\":\"Plantio concluido\"}", a));
        conferir ("historico isolado do outro produtor -> 404", 404,
                  get ("/api/talhoes/" + id1 + "/historico", b));

        Resp t2 = post ("/api/talhoes", talhao ("T-02 Sul", "Milho", 98, "Irrigação pendente"), a);
        Resp t3 = post ("/api/talhoes", talhao ("T-03 Várzea", "Café", 72.5, "Em preparo"), a);
        conferir ("criar com acentos (UTF-8) -> 201", t2.status == 201 && t3.status == 201 && "T-03 Várzea".equals (t3.json ().getString ("nome")));

        conferir ("nome repetido na mesma propriedade -> 409", 409, post ("/api/talhoes", talhao ("t-01 norte", "Milho", 5, null), a));
        conferir ("mesmo nome em outra propriedade -> 201",    201, post ("/api/talhoes", talhao ("T-01 Norte", "Trigo", 40, null), b));

        Resp listaA = get ("/api/talhoes", a);
        conferir ("usuario A ve so os 3 dele", listaA.status == 200 && Document.parse ("{\"l\":" + listaA.corpo + "}").getList ("l", Document.class).size () == 3);
        Resp listaB = get ("/api/talhoes", b);
        conferir ("usuario B ve so o 1 dele",  listaB.status == 200 && Document.parse ("{\"l\":" + listaB.corpo + "}").getList ("l", Document.class).size () == 1);

        conferir ("filtro cultura=soja", Document.parse ("{\"l\":" + get ("/api/talhoes?cultura=soja", a).corpo + "}").getList ("l", Document.class).size () == 1);
        conferir ("filtro cultura=Caf%C3%A9", Document.parse ("{\"l\":" + get ("/api/talhoes?cultura=Caf%C3%A9", a).corpo + "}").getList ("l", Document.class).size () == 1);
        conferir ("busca q=sul",  Document.parse ("{\"l\":" + get ("/api/talhoes?q=sul", a).corpo + "}").getList ("l", Document.class).size () == 1);
        conferir ("busca q=T-0 com cultura=todos", Document.parse ("{\"l\":" + get ("/api/talhoes?q=T-0&cultura=todos", a).corpo + "}").getList ("l", Document.class).size () == 3);

        Document resumo = get ("/api/talhoes/resumo", a).json ();
        conferir ("resumo: 294,5 ha, 3 talhoes, 1 ativo",
                  resumo.get ("totalHectares", Number.class).doubleValue () == 294.5 &&
                  resumo.getInteger ("quantidadeTotal") == 3 && resumo.getInteger ("quantidadeAtivos") == 1);

        String id2 = t2.json ().getString ("id");
        Resp editado = put ("/api/talhoes/" + id2, talhao ("T-02 Sul", "Milho", 100, "Ativo"), a);
        conferir ("editar talhao -> 200", 200, editado);
        conferir ("edicao aplicada", editado.status == 200 && editado.json ().get ("hectares", Number.class).doubleValue () == 100);

        resumo = get ("/api/talhoes/resumo", a).json ();
        conferir ("resumo depois da edicao: 296,5 ha e 2 ativos",
                  resumo.get ("totalHectares", Number.class).doubleValue () == 296.5 && resumo.getInteger ("quantidadeAtivos") == 2);

        conferir ("renomear para nome existente -> 409",   409, put ("/api/talhoes/" + id2, talhao ("T-03 VÁRZEA", "Milho", 100, null), a));
        conferir ("manter o proprio nome e permitido",     200, put ("/api/talhoes/" + id2, talhao ("t-02 sul", "Milho", 100, null), a));
        conferir ("editar com corpo invalido -> 400",      400, put ("/api/talhoes/" + id2, talhao ("T-02", "Milho", -1, null), a));
        conferir ("usuario B editando talhao de A -> 404", 404, put ("/api/talhoes/" + id2, talhao ("Roubado", "Soja", 1, null), b));
        conferir ("id invalido -> 404",                    404, put ("/api/talhoes/xyz", talhao ("T-09", "Soja", 1, null), a));
        conferir ("DELETE nao existe -> 405",              405, requisitar ("DELETE", "/api/talhoes/" + id2, null, a));
        conferir ("talhao de A continua intacto",
                  get ("/api/talhoes?q=t-02", a).corpo.contains ("t-02 sul") && !get ("/api/talhoes", a).corpo.contains ("Roubado"));
    }

    private static void testarConcorrencia () throws Exception
    {
        System.out.println ("\n# concorrencia e erros");

        String token = login ("joao@agro.com", "Senha@123");

        // 50 requisicoes ao mesmo tempo: 25 cadastros de talhao + 25 leituras
        ExecutorService      threads  = Executors.newFixedThreadPool (50);
        CountDownLatch       largada  = new CountDownLatch (1);
        List<Future<Integer>> futuros = new ArrayList<Future<Integer>> ();

        for (int i = 0; i < 50; i++)
        {
            final int numero = i;
            Callable<Integer> tarefa = () ->
            {
                largada.await ();
                if (numero % 2 == 0)
                    return post ("/api/talhoes", talhao ("Paralelo " + numero, "Soja", 1, null), token).status;
                return get ("/api/talhoes/resumo", token).status;
            };
            futuros.add (threads.submit (tarefa));
        }
        largada.countDown ();

        int certos = 0;
        for (int i = 0; i < futuros.size (); i++)
            if (futuros.get (i).get () == (i % 2 == 0 ? 201 : 200))
                certos++;
        conferir ("50 requisicoes paralelas respondidas corretamente", certos == 50);
        conferir ("os 25 talhoes paralelos foram gravados",
                  get ("/api/talhoes/resumo", token).json ().getInteger ("quantidadeTotal") == 26);

        // 20 cadastros simultaneos com o mesmo e-mail: so um pode passar
        CountDownLatch        largada2 = new CountDownLatch (1);
        List<Future<Integer>> futuros2 = new ArrayList<Future<Integer>> ();
        for (int i = 0; i < 20; i++)
            futuros2.add (threads.submit (() -> { largada2.await (); return post ("/api/auth/register", cadastro ("corrida@agro.com"), null).status; }));
        largada2.countDown ();

        int criados = 0, conflitos = 0;
        for (Future<Integer> futuro : futuros2)
        {
            int status = futuro.get ();
            if (status == 201) criados++;
            if (status == 409) conflitos++;
        }
        conferir ("cadastros simultaneos: 1 criado e 19 conflitos", criados == 1 && conflitos == 19);

        // mesmo nome de talhao em paralelo: so um pode passar
        CountDownLatch        largada3 = new CountDownLatch (1);
        List<Future<Integer>> futuros3 = new ArrayList<Future<Integer>> ();
        for (int i = 0; i < 20; i++)
            futuros3.add (threads.submit (() -> { largada3.await (); return post ("/api/talhoes", talhao ("Disputado", "Milho", 3, null), token).status; }));
        largada3.countDown ();

        criados = 0;
        conflitos = 0;
        for (Future<Integer> futuro : futuros3)
        {
            int status = futuro.get ();
            if (status == 201) criados++;
            if (status == 409) conflitos++;
        }
        conferir ("talhoes com mesmo nome em paralelo: 1 criado e 19 conflitos", criados == 1 && conflitos == 19);

        threads.shutdown ();

        // erro dentro de um handler vira 500 e o servidor continua de pe
        Resp explodiu = get ("/explodir", null);
        conferir ("excecao no handler -> 500 generico",
                  explodiu.status == 500 && explodiu.corpo.equals ("{\"error\": \"Internal server error\"}"));

        // cliente que manda metade da requisicao e some
        try (Socket conexao = new Socket ("127.0.0.1", TesteServidor.porta))
        {
            conexao.getOutputStream ().write ("GET /health HTTP/1.1\r\nHo".getBytes ());
        }
        // cliente que conecta e nao manda nada
        new Socket ("127.0.0.1", TesteServidor.porta).close ();
        conferir ("servidor segue respondendo depois de clientes quebrados", 200, get ("/health", null));
    }

    private static void testarLimiteDeConexoes () throws Exception
    {
        System.out.println ("\n# limite de conexoes simultaneas (503)");

        Roteador roteador = Servidor.montarRoteador (new RepositorioUsuariosMemoria (), new RepositorioTalhoesMemoria (),
                                                     null, Path.of ("../.."));
        Servidor pequeno  = new Servidor ("127.0.0.1", 0, roteador, null, 2);

        // duas conexoes paradas ocupam as duas vagas
        Socket parada1 = new Socket ("127.0.0.1", pequeno.getPorta ());
        Socket parada2 = new Socket ("127.0.0.1", pequeno.getPorta ());
        Thread.sleep (300);

        Resp lotado = bruto (pequeno.getPorta (), "GET /health HTTP/1.1\r\nHost: x\r\n\r\n".getBytes ());
        conferir ("terceira conexao -> 503", 503, lotado);

        parada1.close ();
        parada2.close ();
        Thread.sleep (300);

        conferir ("vagas liberadas -> 200", 200, bruto (pequeno.getPorta (), "GET /health HTTP/1.1\r\nHost: x\r\n\r\n".getBytes ()));

        pequeno.desativar ();

        boolean recusou;
        try (Socket depois = new Socket ("127.0.0.1", pequeno.getPorta ()))
        {
            recusou = false;
        }
        catch (Exception erro)
        {
            recusou = true;
        }
        conferir ("desativar fecha a porta", recusou);
    }

    private static void testarProtecoesDeAbuso () throws Exception
    {
        System.out.println ("\n# protecoes de abuso");

        LimitadorDeTaxa limitador = new LimitadorDeTaxa ();
        conferir ("limitador aceita tentativas dentro da janela",
                  limitador.permitir ("login:127.0.0.1", 2, 60000) &&
                  limitador.permitir ("login:127.0.0.1", 2, 60000));
        conferir ("limitador recusa tentativa acima da janela",
                  !limitador.permitir ("login:127.0.0.1", 2, 60000));

        Sessoes sessoes = new Sessoes ();
        String primeira = sessoes.criar ("usuario", "propriedade");
        for (int i = 0; i < 5; i++)
            sessoes.criar ("usuario", "propriedade");
        conferir ("sexta sessao derruba a mais antiga", sessoes.buscar (primeira) == null);

        Path raiz = Files.createTempDirectory ("agrogestao-estaticos-");
        Path grande = raiz.resolve ("grande.js");
        try
        {
            Files.write (grande, new byte[2 * 1024 * 1024 + 1]);
            Requisicao requisicao = new Requisicao ("GET", "/grande.js", new HashMap<String, String> (),
                                                    new HashMap<String, String> (), new byte[0], "127.0.0.1");
            conferir ("estatico acima do limite -> 413",
                      new EstaticosHandler (raiz).tratar (requisicao).getStatus () == 413);
        }
        finally
        {
            Files.deleteIfExists (grande);
            Files.deleteIfExists (raiz);
        }
    }

    public static void main (String[] args) throws Exception
    {
        TesteServidor.usuarios = new RepositorioUsuariosMemoria ();

        Roteador roteador = Servidor.montarRoteador (TesteServidor.usuarios, new RepositorioTalhoesMemoria (),
                                                     null, Path.of ("../.."));

        // rota de teste que sempre lanca excecao
        roteador.adicionarPublica ("GET", "/explodir", requisicao -> { throw new RuntimeException ("de proposito"); });

        Servidor servidor = new Servidor ("127.0.0.1", 0, roteador, null, Servidor.MAXIMO_DE_CONEXOES);
        TesteServidor.porta = servidor.getPorta ();

        try
        {
            TesteServidor.testarHttp ();
            TesteServidor.testarSaudeEEstaticos ();
            TesteServidor.testarCadastro ();
            TesteServidor.testarLogin ();
            TesteServidor.testarConfirmacaoERedefinicao ();
            TesteServidor.testarTalhoes ();
            TesteServidor.testarConcorrencia ();
            TesteServidor.testarLimiteDeConexoes ();
            TesteServidor.testarProtecoesDeAbuso ();
        }
        catch (Exception erro)
        {
            TesteServidor.falhas++;
            System.out.println ("FALHA inesperada: " + erro);
            erro.printStackTrace ();
        }
        finally
        {
            servidor.desativar ();
        }

        System.out.println ("\n" + (TesteServidor.total - TesteServidor.falhas) + "/" + TesteServidor.total + " testes ok");
        System.exit (TesteServidor.falhas == 0 ? 0 : 1);
    }
}
