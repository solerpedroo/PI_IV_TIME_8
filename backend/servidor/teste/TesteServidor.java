/* Miguel — RA: 25003110 */
/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */

import dados.RepositorioCustosMemoria;
import dados.RepositorioSafrasMemoria;
import dados.RepositorioTalhoesMemoria;
import dados.RepositorioEstoqueMemoria;
import dados.RepositorioUsuariosMemoria;
import http.Roteador;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
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
import handlers.CalendarioHandler;
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
    private static RepositorioEstoqueMemoria estoque;

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
        conferir ("saude compativel com gateway Java -> 200", 200, get ("/actuator/health", null));

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

        conferir ("login sem e-mail confirmado -> 403", 403,
                  post ("/api/auth/login", "{\"email\":\" Maria@Agro.com \",\"password\":\"Senha@123\"}", null));
    }

    private static void testarConfirmacaoERedefinicao () throws Exception
    {
        System.out.println ("\n# confirmacao de e-mail e redefinicao de senha");

        String tokenConfirmacao = usuarios.buscarPor ("email", "maria@agro.com").getString ("tokenConfirmacao");
        conferir ("token errado -> 400",    400, post ("/api/auth/confirm-email", "{\"token\":\"errado\"}", null));
        conferir ("confirmar e-mail -> 200", 200, post ("/api/auth/confirm-email", new Document ("token", tokenConfirmacao).toJson (), null));
        conferir ("token de confirmacao nao reutiliza", 400, post ("/api/auth/confirm-email", new Document ("token", tokenConfirmacao).toJson (), null));

        String token = login ("maria@agro.com", "Senha@123");
        conferir ("login liberado depois da confirmacao", token != null);
        conferir ("/api/me mostra e-mail confirmado",
                  Boolean.TRUE.equals (get ("/api/me", token).json ().get ("usuario", Document.class).getBoolean ("emailConfirmado")));

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

        String tokenJoao = usuarios.buscarPor ("email", "joao@agro.com").getString ("tokenConfirmacao");
        conferir ("confirmar e-mail reenviado -> 200", 200,
                  post ("/api/auth/confirm-email", new Document ("token", tokenJoao).toJson (), null));

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
                  post ("/api/talhoes/" + id1 + "/historico", "{\"tipo\":\"atividade\",\"descricao\":\"Plantio confirmado\"}", a));
        conferir ("historico contem operacao", get ("/api/talhoes/" + id1 + "/historico", a).corpo.contains ("Plantio confirmado"));
        conferir ("outro usuario nao acessa historico", 404, get ("/api/talhoes/" + id1 + "/historico", b));

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

    /* A API única de status preserva os mesmos eventos para Lista, arraste e menu. */
    private static void testarAtividadesEHistorico () throws Exception
    {
        System.out.println ("\n# atividades e historico");
        String token = login ("joao@agro.com", "Senha@123");

        conferir ("listar atividades sem token -> 401", 401, get ("/api/atividades", null));
        conferir ("talhao inexistente e rejeitado", 400, post ("/api/atividades",
                  "{\"nome\":\"Teste\",\"tipo\":\"Aplicação\",\"talhao\":\"Ausente\",\"data\":\"08 ago\"}", token));
        conferir ("tipo fora do catálogo e rejeitado", 400, post ("/api/atividades",
                  "{\"nome\":\"Teste\",\"tipo\":\"Inválido\",\"talhao\":\"T-01 Norte\",\"data\":\"08 ago\"}", token));

        String talhaoId = idDoTalhao (token, "T-01 Norte");
        Resp safra = post ("/api/safras", safra ("Safra da atividade", "Trigo", "2026-01-01", "2026-12-31", talhaoId), token);
        conferir ("criar safra vinculável -> 201", 201, safra);
        String safraId = safra.status == 201 ? safra.json ().getString ("id") : "";

        Resp criada = post ("/api/atividades", "{\"nome\":\"Aplicação teste\",\"tipo\":\"Aplicação\",\"talhao\":\"T-01 Norte\",\"data\":\"08 ago\",\"horario\":\"07:30\",\"responsavel\":\"Equipe Norte\",\"prioridade\":\"Alta\",\"safraId\":\"" + safraId + "\"}", token);
        conferir ("criar atividade -> 201", 201, criada);
        String id = criada.status == 201 ? criada.json ().getString ("id") : "";
        conferir ("criar preserva os campos de planejamento", criada.status == 201 &&
                  "07:30".equals (criada.json ().getString ("horario")) &&
                  "Equipe Norte".equals (criada.json ().getString ("responsavel")) &&
                  "Alta".equals (criada.json ().getString ("prioridade")) &&
                  safraId.equals (criada.json ().getString ("safraId")) &&
                  "Trigo".equals (criada.json ().getString ("cultura")));
        conferir ("safra inexistente rejeitada", 400, post ("/api/atividades",
                  "{\"nome\":\"Teste\",\"tipo\":\"Aplicação\",\"talhao\":\"T-01 Norte\",\"data\":\"08 ago\",\"safraId\":\"inexistente\"}", token));
        post ("/api/talhoes", talhao ("Talhão sem safra", "Trigo", 1, null), token);
        conferir ("edicao nao preserva safra incompatível com novo talhao", 400,
                  put ("/api/atividades/" + id, "{\"nome\":\"Teste\",\"tipo\":\"Aplicação\",\"talhao\":\"Talhão sem safra\",\"data\":\"08 ago\"}", token));
        conferir ("vinculo anterior permanece após edição inválida", safraId.equals (get ("/api/atividades/" + id, token).json ().getString ("safraId")));
        conferir ("talhao com atividade nao pode ser renomeado", 409,
                  put ("/api/talhoes/" + talhaoId, talhao ("Talhão renomeado", "Trigo", 40, null), token));
        conferir ("safra com atividade nao pode ser excluida", 409,
                  requisitar ("DELETE", "/api/safras/" + safraId, null, token));
        conferir ("safra com atividade nao pode mudar cultura", 409,
                  put ("/api/safras/" + safraId, safra ("Safra da atividade", "Milho", "2026-01-01", "2026-12-31", talhaoId), token));
        Resp editada = put ("/api/atividades/" + id,
                            "{\"nome\":\"Aplicação revisada\",\"tipo\":\"Aplicação\",\"talhao\":\"T-01 Norte\",\"data\":\"08 ago\",\"horario\":\"08:15\",\"responsavel\":\"Equipe Sul\",\"prioridade\":\"Média\"}", token);
        conferir ("editar preserva os campos de planejamento", editada.status == 200 &&
                  "Aplicação revisada".equals (editada.json ().getString ("nome")) &&
                  "08:15".equals (editada.json ().getString ("horario")) &&
                  "Equipe Sul".equals (editada.json ().getString ("responsavel")) &&
                  "Média".equals (editada.json ().getString ("prioridade")));
        conferir ("horario invalido e rejeitado", 400, post ("/api/atividades",
                  "{\"nome\":\"Teste\",\"tipo\":\"Aplicação\",\"talhao\":\"T-01 Norte\",\"data\":\"08 ago\",\"horario\":\"25:61\"}", token));
        conferir ("data livre sem calendario e rejeitada", 400, post ("/api/atividades",
                  "{\"nome\":\"Teste\",\"tipo\":\"Aplicação\",\"talhao\":\"T-01 Norte\",\"data\":\"quando der\"}", token));
        conferir ("data ISO invalida e rejeitada", 400, post ("/api/atividades",
                  "{\"nome\":\"Teste\",\"tipo\":\"Aplicação\",\"talhao\":\"T-01 Norte\",\"data\":\"2026-13-40\"}", token));
        conferir ("nao conclui atividade pendente", 409, post ("/api/atividades/" + id + "/concluir", "{}", token));
        conferir ("status ausente aponta erro", 400, post ("/api/atividades/" + id + "/status", "{}", token));
        conferir ("criacao com status final e rejeitada", 400, post ("/api/atividades", "{\"nome\":\"Teste\",\"tipo\":\"Aplicação\",\"talhao\":\"T-01 Norte\",\"data\":\"08 ago\",\"status\":\"Concluída\"}", token));
        conferir ("lista altera status pela rota unica", 200,
                  post ("/api/atividades/" + id + "/status", "{\"status\":\"Agendada\"}", token));
        conferir ("nao pula de agendada para concluida", 409, post ("/api/atividades/" + id + "/status", "{\"status\":\"Concluída\"}", token));
        conferir ("arraste altera status pela rota unica", 200,
                  post ("/api/atividades/" + id + "/status", "{\"status\":\"Em andamento\"}", token));
        conferir ("conclusao rejeita JSON malformado", 400, post ("/api/atividades/" + id + "/concluir", "{", token));
        conferir ("cancelamento rejeita motivo numerico", 400, post ("/api/atividades/" + id + "/cancelar", "{\"motivo\":42}", token));
        conferir ("falhas nao alteram estado", "Em andamento".equals (get ("/api/atividades/" + id, token).json ().getString ("status")));
        conferir ("nao retrocede em andamento", 409, post ("/api/atividades/" + id + "/status", "{\"status\":\"Agendada\"}", token));
        conferir ("menu conclui pela rota unica", 200,
                  post ("/api/atividades/" + id + "/status", "{\"status\":\"Concluída\"}", token));
        conferir ("estado terminal rejeita nova transicao", 409,
                  post ("/api/atividades/" + id + "/status", "{\"status\":\"Pendente\"}", token));
        conferir ("historico registra conclusao", get ("/api/atividades/" + id + "/historico", token).corpo.contains ("conclusao"));

        Resp cancelada = post ("/api/atividades", "{\"nome\":\"Irrigação teste\",\"tipo\":\"Irrigação\",\"talhao\":\"T-01 Norte\",\"data\":\"09 ago\"}", token);
        String idCancelada = cancelada.status == 201 ? cancelada.json ().getString ("id") : "";
        conferir ("cancelamento com motivo pela rota unica", 200,
                  post ("/api/atividades/" + idCancelada + "/status", "{\"status\":\"Cancelada\",\"motivo\":\"Chuva forte\"}", token));
        conferir ("historico preserva motivo", get ("/api/atividades/" + idCancelada + "/historico", token).corpo.contains ("Chuva forte"));
    }

    /* Exercita o contrato do estoque, inclusive a saída concorrente que não pode negativar saldo. */
    private static void testarEstoque () throws Exception
    {
        System.out.println ("\n# estoque persistente");
        String a = login ("maria@agro.com", "Nova@1234");
        String b = login ("joao@agro.com", "Senha@123");
        Document produto = new Document ("nome", "Ureia teste").append ("categoria", "Fertilizante")
            .append ("unidade", "kg").append ("saldo", 10).append ("minimo", 4);
        Resp criado = post ("/api/insumos", produto.toJson (), a);
        conferir ("criar insumo -> 201", 201, criado);
        String id = criado.status == 201 ? criado.json ().getString ("id") : "";
        conferir ("estoque isolado por propriedade", !get ("/api/insumos", b).corpo.contains ("Ureia teste"));
        conferir ("entrada atualiza saldo", 201, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"entrada\",\"quantidade\":5}", a));
        conferir ("saída acima do saldo -> 409", 409, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"saida\",\"quantidade\":16}", a));
        conferir ("ajuste registra saldo contado", 201, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"ajuste\",\"quantidade\":7,\"observacao\":\"contagem\"}", a));
        conferir ("histórico preserva saldo inicial e operações confirmadas", Document.parse ("{\"l\":" + get ("/api/insumos/" + id + "/movimentacoes", a).corpo + "}").getList ("l", Document.class).size () == 3);
        Document lista = Document.parse ("{\"l\":" + get ("/api/insumos", a).corpo + "}");
        conferir ("saldo após ajuste permanece 7", lista.getList ("l", Document.class).get (0).get ("saldo", Number.class).doubleValue () == 7);
        // Duas saídas de 5 para saldo 7 chegam juntas: somente uma pode vencer.
        ExecutorService grupo = Executors.newFixedThreadPool (2);
        Future<Resp> primeira = grupo.submit (() -> post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"saida\",\"quantidade\":5}", a));
        Future<Resp> segunda  = grupo.submit (() -> post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"saida\",\"quantidade\":5}", a));
        Resp uma = primeira.get (); Resp duas = segunda.get (); grupo.shutdown ();
        conferir ("consumos simultâneos não negativam saldo", (uma.status == 201 && duas.status == 409) || (uma.status == 409 && duas.status == 201));
        conferir ("outro usuário não acessa produto", 404, get ("/api/insumos/" + id + "/movimentacoes", b));
        conferir ("catálogo sem token exige autenticação", 401, get ("/api/insumos", null));
        conferir ("quantidade como texto é inválida", 400, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"entrada\",\"quantidade\":\"2\"}", a));
        conferir ("quantidade negativa é inválida", 400, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"saida\",\"quantidade\":-1}", a));
        conferir ("ajuste exige motivo", 400, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"ajuste\",\"quantidade\":0}", a));
        conferir ("outro produtor não edita", 404, put ("/api/insumos/" + id, produto.toJson (), b));
        conferir ("outro produtor não exclui", 404, requisitar ("DELETE", "/api/insumos/" + id, null, b));
        conferir ("não exclui produto com saldo", 409, requisitar ("DELETE", "/api/insumos/" + id, null, a));

        Document usuario = usuarios.buscarPor ("email", "maria@agro.com");
        String uid = usuario.getObjectId ("_id").toHexString (), pid = usuario.getString ("propriedadeId");
        ObjectId atividadeId = new ObjectId ();
        estoque.adicionarAtividadeParaTeste (new Document ("_id", atividadeId).append ("usuarioId", uid)
            .append ("propriedadeId", pid).append ("titulo", "Aplicação de teste"));
        conferir ("lista somente atividade persistida do titular", get ("/api/insumos/atividades", a).corpo.contains (atividadeId.toHexString ()) && !get ("/api/insumos/atividades", b).corpo.contains (atividadeId.toHexString ()));
        String consumo = new Document ("tipo", "saida").append ("quantidade", 1).append ("atividadeId", atividadeId.toHexString ()).toJson ();
        conferir ("saída vinculada à atividade real", 201, post ("/api/insumos/" + id + "/movimentacoes", consumo, a));
        conferir ("histórico retém ID e título da atividade", get ("/api/insumos/" + id + "/movimentacoes", a).corpo.contains ("Aplicação de teste"));
        conferir ("referência inexistente rejeitada", 400, post ("/api/insumos/" + id + "/movimentacoes", consumo.replace (atividadeId.toHexString (), new ObjectId ().toHexString ()), a));
        conferir ("ajuste zero permitido com motivo", 201, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"ajuste\",\"quantidade\":0,\"observacao\":\"contagem física\"}", a));
        conferir ("exclusão lógica com saldo zero", 204, requisitar ("DELETE", "/api/insumos/" + id, null, a));
        conferir ("exclusão preserva histórico", get ("/api/insumos/" + id + "/movimentacoes", a).corpo.contains ("Aplicação de teste"));
        conferir ("produto excluído não recebe entrada", 404, post ("/api/insumos/" + id + "/movimentacoes", "{\"tipo\":\"entrada\",\"quantidade\":1}", a));

        // Conjunto conhecido: 10 a R$2 + 10 a R$4 = 20 a R$3 = R$60.
        produto.put ("nome", "Conjunto conhecido"); produto.put ("custoUnitario", 2);
        produto.put ("minimo", 25); produto.put ("limiarCritico", 5);
        String conhecido = post ("/api/insumos", produto.toJson (), a).json ().getString ("id");
        post ("/api/insumos/" + conhecido + "/movimentacoes", "{\"tipo\":\"entrada\",\"quantidade\":10,\"custoUnitario\":4,\"fornecedor\":\"Fornecedor teste\"}", a);
        Document resumo = get ("/api/insumos/resumo", a).json ();
        conferir ("indicador conhecido R$60 e item abaixo do mínimo", resumo.get ("valorEmEstoque", Number.class).doubleValue () == 60 && resumo.getInteger ("abaixoMinimo") == 1 && resumo.getInteger ("criticos") == 0);
        int entradas = resumo.getInteger ("entradasNoMes");
        estoque.movimentar (pid, conhecido, new Document ("tipo", "entrada").append ("quantidade", 1.0)
            .append ("criadoEm", java.time.ZonedDateTime.now (java.time.ZoneId.of ("America/Sao_Paulo")).minusYears (1).toInstant ().toString ()));
        conferir ("mesmo mês de outro ano não conta", get ("/api/insumos/resumo", a).json ().getInteger ("entradasNoMes") == entradas);
        conferir ("catálogo vazio aponta campos inválidos", 400, post ("/api/insumos", "{}", a));
        conferir ("estoque mínimo negativo rejeitado", 400, post ("/api/insumos", new Document (produto).append ("minimo", -1).toJson (), a));
        produto.put ("categoria", "Inválida");
        conferir ("categoria fora do catálogo rejeitada", 400, post ("/api/insumos", produto.toJson (), a));
        produto.put ("categoria", "Fertilizante"); produto.put ("limiarCritico", 30);
        conferir ("limiar acima do mínimo rejeitado", 400, post ("/api/insumos", produto.toJson (), a));
        produto.put ("limiarCritico", 5); produto.put ("saldo", 0);
        String zerado = post ("/api/insumos", produto.toJson (), a).json ().getString ("id");
        produto.put ("nome", "Catálogo editado"); produto.put ("unidade", "L"); produto.put ("categoria", "Herbicida");
        conferir ("edita unidade antes de haver saldo ou histórico", 200, put ("/api/insumos/" + zerado, produto.toJson (), a));
        Document editado = get ("/api/insumos/" + zerado, a).json ();
        conferir ("consulta confirma nome categoria unidade e mínimo", "Catálogo editado".equals (editado.getString ("nome")) &&
            "L".equals (editado.getString ("unidade")) && "Herbicida".equals (editado.getString ("categoria")) && editado.getDouble ("minimo") == 25);
        conferir ("unidade histórica não pode ser reinterpretada", 400, put ("/api/insumos/" + conhecido, produto.toJson (), a));
    }

    private static void testarPropriedades () throws Exception
    {
        System.out.println ("\n# propriedades, contexto e autorizacao");
        String a = login ("maria@agro.com", "Nova@1234");
        String b = login ("joao@agro.com", "Senha@123");
        String corpo = new Document ("nome", "Fazenda Horizonte").append ("localizacao", "Rodovia SP-101, km 12")
            .append ("municipio", "Campinas, SP").append ("safraAtual", "2026/27").append ("areaHectares", 350).toJson ();
        conferir ("propriedade invalida aponta campos", camposComErro (post ("/api/propriedades", "{}", a))
            .keySet ().containsAll (List.of ("nome", "localizacao", "municipio", "safraAtual", "areaHectares")));
        conferir ("safra invalida -> 400", 400, post ("/api/propriedades", corpo.replace ("2026/27", "invalida"), a));
        Resp criada = post ("/api/propriedades", corpo, a);
        conferir ("criar segunda propriedade -> 201", 201, criada);
        String id = criada.json ().getString ("id");
        conferir ("listar inclui propriedade propria", get ("/api/propriedades", a).corpo.contains (id));
        conferir ("outro usuario nao consulta -> 404", 404, get ("/api/propriedades/" + id, b));
        conferir ("outro usuario nao altera -> 404", 404, put ("/api/propriedades/" + id, corpo, b));
        conferir ("outro usuario nao seleciona -> 404", 404, post ("/api/propriedades/" + id + "/selecionar", "{}", b));
        conferir ("outro usuario nao exclui -> 404", 404, requisitar ("DELETE", "/api/propriedades/" + id, null, b));
        conferir ("atualiza propriedade propria", 200, put ("/api/propriedades/" + id, corpo.replace ("Horizonte", "Atualizada"), a));
        conferir ("selecionar propriedade propria -> 200", 200, post ("/api/propriedades/" + id + "/selecionar", "{}", a));
        conferir ("me informa propriedade ativa", id.equals (get ("/api/me", a).json ().get ("usuario", Document.class).getString ("propriedadeId")));
        conferir ("talhoes usam contexto selecionado", get ("/api/talhoes", a).corpo.equals ("[]"));
        conferir ("nao exclui propriedade ativa", 409, requisitar ("DELETE", "/api/propriedades/" + id, null, a));
        String principal = usuarios.buscarPor ("email", "maria@agro.com").getString ("propriedadeId");
        String outraSessao = login ("maria@agro.com", "Nova@1234");
        post ("/api/propriedades/" + id + "/selecionar", "{}", outraSessao);
        post ("/api/propriedades/" + principal + "/selecionar", "{}", a);
        conferir ("nao exclui contexto usado em outra sessao", 409, requisitar ("DELETE", "/api/propriedades/" + id, null, a));
        post ("/api/auth/logout", null, outraSessao);
        conferir ("exclui propriedade sem vinculos -> 204", 204, requisitar ("DELETE", "/api/propriedades/" + id, null, a));
        conferir ("propriedade excluida -> 404", 404, get ("/api/propriedades/" + id, a));
        conferir ("nao exclui ultima propriedade", 409, requisitar ("DELETE", "/api/propriedades/" + principal, null, a));

        RepositorioUsuariosMemoria repo = new RepositorioUsuariosMemoria ();
        Document u = repo.criar (new Document ("email", "referencia@teste.com"), new Document ("farm", "Inicial"));
        String uid = u.getObjectId ("_id").toHexString ();
        Document p = repo.criarPropriedade (uid, new Document ("nome", "Alternativa"));
        conferir ("exclusao da padrao atualiza referencia", repo.excluirPropriedade (uid, u.getString ("propriedadeId"))
            && repo.buscarPorId (uid).getString ("propriedadeId").equals (p.getObjectId ("_id").toHexString ()));
        conferir ("repositorio tambem preserva ultima propriedade", !repo.excluirPropriedade (uid, p.getObjectId ("_id").toHexString ()));
    }

    private static List<Document> lista (Resp resp)
    {
        return Document.parse ("{\"l\":" + resp.corpo + "}").getList ("l", Document.class);
    }

    private static String safra (String nome, String cultura, String inicio, String fim, String... talhoes)
    {
        return new Document ("nome", nome).append ("cultura", cultura).append ("dataInicio", inicio)
                   .append ("dataFim", fim).append ("talhoes", List.of (talhoes)).toJson ();
    }

    // id do talhao com esse nome na propriedade ativa de quem tem o token
    private static String idDoTalhao (String token, String nome) throws Exception
    {
        for (Document talhao : lista (get ("/api/talhoes", token)))
            if (talhao.getString ("nome").equalsIgnoreCase (nome))
                return talhao.getString ("id");
        return null;
    }

    private static String atividade (String nome, String tipo, String talhao, String data, String horario)
    {
        Document ret = new Document ("nome", nome).append ("tipo", tipo).append ("talhao", talhao).append ("data", data);

        if (horario != null)
            ret.append ("horario", horario);

        return ret.toJson ();
    }

    // dia do calendario com essa data
    private static Document dia (Document calendario, String data)
    {
        for (Document dia : calendario.getList ("dias", Document.class))
            if (dia.getString ("data").equals (data))
                return dia;
        return new Document ("total", -1);
    }

    private static int totalDoCalendario (String consulta, String token) throws Exception
    {
        Resp resp = get ("/api/calendario?" + consulta, token);

        return resp.status == 200 ? resp.json ().getInteger ("total") : -1;
    }

    private static void testarCalendario () throws Exception
    {
        System.out.println ("\n# calendario de atividades por semana e mes");

        String a = login ("maria@agro.com", "Nova@1234");
        String b = login ("joao@agro.com",  "Senha@123");

        String aplicacao  = "Aplica\u00e7\u00e3o";
        String irrigacao  = "Irriga\u00e7\u00e3o";
        String manutencao = "Manuten\u00e7\u00e3o";
        String semana     = "de=2031-03-03&ate=2031-03-09";

        conferir ("calendario sem token -> 401", 401, get ("/api/calendario?" + semana, null));
        Document semPeriodo = camposComErro (get ("/api/calendario", a));
        conferir ("calendario sem periodo -> 400 com de e ate",
                  semPeriodo.containsKey ("de") && semPeriodo.containsKey ("ate"));
        conferir ("data impossivel -> 400",
                  camposComErro (get ("/api/calendario?de=2031-02-30&ate=2031-03-09", a)).containsKey ("de"));
        conferir ("periodo invertido -> 400",
                  camposComErro (get ("/api/calendario?de=2031-03-09&ate=2031-03-03", a)).containsKey ("ate"));
        conferir ("periodo maior que 62 dias -> 400",
                  camposComErro (get ("/api/calendario?de=2031-01-01&ate=2031-03-04", a)).containsKey ("ate"));
        conferir ("periodo de 62 dias e aceito", 200, get ("/api/calendario?de=2031-01-01&ate=2031-03-03", a));

        // duas semanas e dois meses diferentes
        String plantio = post ("/api/atividades", atividade ("Plantio", aplicacao, "T-01 Norte", "2031-03-03", "08:00"), a).json ().getString ("id");
        post ("/api/atividades", atividade ("Irrigar", irrigacao, "t-02 sul",   "2031-03-03", "06:00"), a);
        post ("/api/atividades", atividade ("Colher",  "Colheita", "T-01 Norte", "2031-03-09", null),   a);
        post ("/api/atividades", atividade ("Revisar", manutencao, "T-01 Norte", "2031-03-10", null),   a);
        post ("/api/atividades", atividade ("Abril",   aplicacao,  "T-01 Norte", "2031-04-01", null),   a);

        Resp     respSemana = get ("/api/calendario?" + semana, a);
        Document doc        = respSemana.status == 200 ? respSemana.json () : new Document ();
        conferir ("semana -> 200", 200, respSemana);
        conferir ("semana traz so as 3 atividades do periodo", Integer.valueOf (3).equals (doc.getInteger ("total")));
        conferir ("semana tem os 7 dias, inclusive os vazios",
                  doc.containsKey ("dias") && doc.getList ("dias", Document.class).size () == 7 &&
                  dia (doc, "2031-03-05").getInteger ("total") == 0);
        conferir ("contagem por dia", dia (doc, "2031-03-03").getInteger ("total") == 2 &&
                                      dia (doc, "2031-03-09").getInteger ("total") == 1);
        conferir ("contagem por status no dia",
                  dia (doc, "2031-03-03").get ("porStatus", Document.class).getInteger ("Pendente") == 2);
        conferir ("contagem por tipo no periodo",
                  doc.containsKey ("porTipo") &&
                  doc.get ("porTipo", Document.class).getInteger (aplicacao) == 1 &&
                  doc.get ("porTipo", Document.class).getInteger (irrigacao) == 1 &&
                  doc.get ("porTipo", Document.class).getInteger ("Colheita") == 1 &&
                  doc.get ("porTipo", Document.class).getInteger (manutencao) == 0);

        ArrayList<String> nomes = new ArrayList<String> ();
        if (doc.containsKey ("atividades"))
            for (Document item : doc.getList ("atividades", Document.class))
                nomes.add (item.getString ("nome") + "@" + item.getString ("dataIso"));
        conferir ("atividades em ordem de data e horario",
                  nomes.equals (List.of ("Irrigar@2031-03-03", "Plantio@2031-03-03", "Colher@2031-03-09")));
        conferir ("resposta nao expoe campos internos", !respSemana.corpo.contains ("propriedadeId"));

        conferir ("semana seguinte so com a manutencao", totalDoCalendario ("de=2031-03-10&ate=2031-03-16", a) == 1);
        conferir ("mes de marco com 4 atividades", totalDoCalendario ("de=2031-03-01&ate=2031-03-31", a) == 4);
        conferir ("mes de abril com 1 atividade",  totalDoCalendario ("de=2031-04-01&ate=2031-04-30", a) == 1);
        Resp marco = get ("/api/calendario?de=2031-03-01&ate=2031-03-31", a);
        conferir ("mes tem todos os 31 dias",
                  marco.status == 200 && marco.json ().getList ("dias", Document.class).size () == 31);

        // os filtros sao os mesmos da lista de atividades
        conferir ("filtro por tipo",   totalDoCalendario (semana + "&tipo=" + URLEncoder.encode (irrigacao, StandardCharsets.UTF_8), a) == 1);
        conferir ("filtro por talhao", totalDoCalendario (semana + "&talhao=T-01%20Norte", a) == 2);
        conferir ("filtro por busca",  totalDoCalendario (semana + "&q=COLH", a) == 1);
        conferir ("filtro todos e ignorado", totalDoCalendario (semana + "&tipo=todos", a) == 3);
        post ("/api/atividades/" + plantio + "/status", "{\"status\":\"Agendada\"}", a);
        conferir ("filtro por status acompanha a mudanca", totalDoCalendario (semana + "&status=Agendada", a) == 1);
        Resp agendada = get ("/api/calendario?" + semana, a);
        conferir ("contagem por status acompanha a mudanca",
                  agendada.status == 200 && agendada.json ().get ("porStatus", Document.class).getInteger ("Agendada") == 1 &&
                  dia (agendada.json (), "2031-03-03").get ("porStatus", Document.class).getInteger ("Agendada") == 1);

        conferir ("calendario de outro usuario nao mostra as atividades", totalDoCalendario ("de=2031-03-01&ate=2031-03-31", b) == 0);

        // formato do prototipo: o ano e o da ocorrencia mais proxima da criacao
        LocalDate hoje     = LocalDate.now (ZoneId.of ("America/Sao_Paulo"));
        LocalDate daqui    = hoje.plusDays (3);
        String    meses    = "jan fev mar abr mai jun jul ago set out nov dez";
        String    curta    = daqui.getDayOfMonth () + " " + meses.split (" ")[daqui.getMonthValue () - 1];
        conferir ("atividade no formato do prototipo -> 201", 201,
                  post ("/api/atividades", atividade ("Prototipo", aplicacao, "T-01 Norte", curta, null), b));
        Resp proxima = get ("/api/calendario?de=" + hoje + "&ate=" + hoje.plusDays (6), b);
        boolean   achou    = false;
        if (proxima.status == 200)
            for (Document item : proxima.json ().getList ("atividades", Document.class))
                if ("Prototipo".equals (item.getString ("nome")) && daqui.toString ().equals (item.getString ("dataIso")))
                    achou = true;
        conferir ("data sem ano cai na ocorrencia mais proxima", achou);

        conferir ("resolver: 10 jan criada em dezembro vai para o ano seguinte",
                  LocalDate.of (2027, 1, 10).equals (CalendarioHandler.resolver ("10 jan", LocalDate.of (2026, 12, 10))));
        conferir ("resolver: 20 mai criada em outubro fica no mesmo ano",
                  LocalDate.of (2026, 5, 20).equals (CalendarioHandler.resolver ("20 mai", LocalDate.of (2026, 10, 28))));
        conferir ("resolver: 29 fev usa o ano bissexto mais proximo",
                  LocalDate.of (2028, 2, 29).equals (CalendarioHandler.resolver ("29 fev", LocalDate.of (2027, 6, 1))));
        conferir ("resolver: 31 fev nao existe", CalendarioHandler.resolver ("31 fev", LocalDate.of (2026, 10, 28)) == null);
        conferir ("resolver: ISO e mantido",
                  LocalDate.of (2031, 3, 3).equals (CalendarioHandler.resolver ("2031-03-03", null)));
    }

    private static void testarSafras () throws Exception
    {
        System.out.println ("\n# safras, ciclo produtivo e isolamento");

        String a = login ("maria@agro.com", "Nova@1234");
        String b = login ("joao@agro.com",  "Senha@123");

        String t1 = idDoTalhao (a, "T-01 Norte");
        String t2 = idDoTalhao (a, "t-02 sul");
        String t3 = idDoTalhao (a, "T-03 V\u00e1rzea");
        String tb = idDoTalhao (b, "T-01 Norte");

        LocalDate hoje   = LocalDate.now (ZoneId.of ("America/Sao_Paulo"));
        String    inicio = hoje.minusDays (30).toString ();
        String    fim    = hoje.plusDays (150).toString ();

        conferir ("listar safras sem token -> 401", 401, get ("/api/safras", null));
        conferir ("criar safra sem token -> 401",   401, post ("/api/safras", safra ("S", "Soja", inicio, fim, t1), null));

        Document vazio = camposComErro (post ("/api/safras", "{}", a));
        conferir ("safra vazia -> 400 com nome, cultura, datas e talhoes",
                  vazio.keySet ().containsAll (List.of ("nome", "cultura", "dataInicio", "dataFim", "talhoes")));
        conferir ("cultura fora da lista -> 400",
                  camposComErro (post ("/api/safras", safra ("S", "Arroz", inicio, fim, t1), a)).containsKey ("cultura"));
        conferir ("data inexistente (30/02) -> 400",
                  camposComErro (post ("/api/safras", safra ("S", "Soja", "2026-02-30", fim, t1), a)).containsKey ("dataInicio"));
        conferir ("data em outro formato -> 400",
                  camposComErro (post ("/api/safras", safra ("S", "Soja", "01/09/2026", fim, t1), a)).containsKey ("dataInicio"));
        conferir ("termino antes do inicio -> 400",
                  camposComErro (post ("/api/safras", safra ("S", "Soja", fim, inicio, t1), a)).containsKey ("dataFim"));
        conferir ("talhao de outro usuario -> 400",
                  camposComErro (post ("/api/safras", safra ("S", "Soja", inicio, fim, tb), a)).containsKey ("talhoes"));
        conferir ("talhao inexistente -> 400",
                  camposComErro (post ("/api/safras", safra ("S", "Soja", inicio, fim, "abc"), a)).containsKey ("talhoes"));
        conferir ("talhao repetido -> 400",
                  camposComErro (post ("/api/safras", safra ("S", "Soja", inicio, fim, t1, t1), a)).containsKey ("talhoes"));
        conferir ("talhoes como texto -> 400",
                  camposComErro (post ("/api/safras", "{\"nome\":\"S\",\"cultura\":\"Soja\",\"dataInicio\":\"" + inicio +
                                       "\",\"dataFim\":\"" + fim + "\",\"talhoes\":\"" + t1 + "\"}", a)).containsKey ("talhoes"));

        Resp s1 = post ("/api/safras", safra ("Soja Ver\u00e3o", "Soja", inicio, fim, t1, t2), a);
        conferir ("criar safra -> 201", 201, s1);
        Document d1 = s1.status == 201 ? s1.json () : new Document ();
        String   id1 = d1.getString ("id");
        conferir ("safra comeca em Planejamento com uma etapa no historico",
                  "Planejamento".equals (d1.getString ("etapa")) && d1.getList ("historicoEtapas", Document.class).size () == 1);
        conferir ("area da safra = soma dos talhoes (124 + 100)",
                  d1.get ("areaHectares", Number.class).doubleValue () == 224);
        conferir ("safra esconde campos internos",
                  !s1.corpo.contains ("propriedadeId") && !s1.corpo.contains ("nomeNormalizado"));

        conferir ("nome de safra repetido -> 409", 409,
                  post ("/api/safras", safra ("SOJA VER\u00c3O", "Soja", hoje.plusYears (2).toString (), hoje.plusYears (3).toString (), t3), a));
        Resp cruzada = post ("/api/safras", safra ("Milho Safrinha", "Milho", hoje.toString (), hoje.plusDays (300).toString (), t2), a);
        conferir ("talhao em duas safras no mesmo periodo -> 409", 409, cruzada);
        conferir ("conflito aponta o campo talhoes", cruzada.status == 409 && cruzada.json ().get ("fields", Document.class).containsKey ("talhoes"));

        Resp s2 = post ("/api/safras", safra ("Caf\u00e9 2026", "Caf\u00e9", inicio, fim, t3), a);
        conferir ("outro talhao no mesmo periodo -> 201", 201, s2);
        String id2 = s2.status == 201 ? s2.json ().getString ("id") : "x";
        Resp s3 = post ("/api/safras", safra ("Milho Safrinha", "Milho", hoje.plusDays (151).toString (), hoje.plusDays (300).toString (), t1), a);
        conferir ("mesmo talhao em periodo seguinte -> 201", 201, s3);
        String id3 = s3.status == 201 ? s3.json ().getString ("id") : "x";

        conferir ("usuario A lista 3 safras", lista (get ("/api/safras", a)).size () == 3);
        conferir ("usuario B nao ve safras de A", !get ("/api/safras", b).corpo.contains ("Soja Verão"));
        conferir ("filtro cultura=soja",    lista (get ("/api/safras?cultura=soja", a)).size () == 1);
        conferir ("filtro etapa=planejamento", lista (get ("/api/safras?etapa=planejamento", a)).size () == 3);
        conferir ("busca q=safrinha",       lista (get ("/api/safras?q=safrinha", a)).size () == 1);
        conferir ("consultar safra -> 200", 200, get ("/api/safras/" + id1, a));
        conferir ("B consulta safra de A -> 404", 404, get ("/api/safras/" + id1, b));
        conferir ("B altera safra de A -> 404",   404, put ("/api/safras/" + id1, safra ("X", "Soja", inicio, fim, tb), b));
        conferir ("B avanca safra de A -> 404",   404, post ("/api/safras/" + id1 + "/avancar", null, b));
        conferir ("B exclui safra de A -> 404",   404, requisitar ("DELETE", "/api/safras/" + id1, null, b));
        conferir ("B ve historico de A -> 404",   404, get ("/api/safras/" + id1 + "/historico", b));
        conferir ("id invalido -> 404",           404, get ("/api/safras/xyz", a));

        Resp editada = put ("/api/safras/" + id1, safra ("Soja Ver\u00e3o 26/27", "Soja", inicio, fim, t1), a);
        conferir ("editar safra -> 200", 200, editada);
        conferir ("edicao recalcula a area (so T-01: 124 ha)",
                  editada.status == 200 && editada.json ().get ("areaHectares", Number.class).doubleValue () == 124);
        conferir ("edicao nao mexe na etapa", editada.status == 200 && "Planejamento".equals (editada.json ().getString ("etapa")));
        conferir ("editar trazendo talhao ocupado -> 409", 409,
                  put ("/api/safras/" + id1, safra ("Soja Ver\u00e3o 26/27", "Soja", inicio, fim, t1, t3), a));
        conferir ("talhao liberado pela edicao entra em outra safra", 201,
                  post ("/api/safras", safra ("Trigo Inverno", "Trigo", hoje.toString (), hoje.plusDays (60).toString (), t2), a));

        Resp avancou = post ("/api/safras/" + id1 + "/avancar", null, a);
        conferir ("avancar -> 200 em Preparacao", avancou.status == 200 && "Prepara\u00e7\u00e3o".equals (avancou.json ().getString ("etapa")));
        conferir ("exclusao fora do planejamento -> 409", 409, requisitar ("DELETE", "/api/safras/" + id1, null, a));
        conferir ("excluir safra em planejamento -> 204", 204, requisitar ("DELETE", "/api/safras/" + id3, null, a));
        conferir ("safra excluida -> 404", 404, get ("/api/safras/" + id3, a));

        post ("/api/talhoes/" + t1 + "/historico", "{\"tipo\":\"atividade\",\"descricao\":\"Plantio da soja\"}", a);
        Resp historico = get ("/api/safras/" + id1 + "/historico", a);
        conferir ("historico da safra traz eventos dos seus talhoes no periodo",
                  historico.status == 200 && historico.corpo.contains ("Plantio da soja") && historico.corpo.contains ("T-01 Norte"));
        conferir ("historico de outra safra nao traz o evento",
                  !get ("/api/safras/" + id2 + "/historico", a).corpo.contains ("Plantio da soja"));

        for (int i = 0; i < 4; i++)
            post ("/api/safras/" + id1 + "/avancar", null, a);
        Document finalizada = get ("/api/safras/" + id1, a).json ();
        conferir ("ciclo completo chega em Finalizacao com 6 etapas no historico",
                  "Finaliza\u00e7\u00e3o".equals (finalizada.getString ("etapa")) && finalizada.getString ("finalizadaEm") != null &&
                  finalizada.getList ("historicoEtapas", Document.class).size () == 6);
        conferir ("avancar safra finalizada -> 409", 409, post ("/api/safras/" + id1 + "/avancar", null, a));
        conferir ("editar safra finalizada -> 409",  409, put ("/api/safras/" + id1, safra ("Outra", "Soja", inicio, fim, t1), a));
        conferir ("excluir safra finalizada -> 409", 409, requisitar ("DELETE", "/api/safras/" + id1, null, a));
        conferir ("historico da finalizada segue consultavel",
                  get ("/api/safras/" + id1 + "/historico", a).corpo.contains ("Plantio da soja"));
        conferir ("PATCH nao existe -> 405", 405, requisitar ("PATCH", "/api/safras/" + id1, "{}", a));
    }

    private static String custo (String descricao, String categoria, Object valor, String data, String talhaoId, String safraId)
    {
        return new Document ("description", descricao).append ("category", categoria).append ("value", valor)
                   .append ("date", data).append ("talhaoId", talhaoId).append ("safraId", safraId).toJson ();
    }

    private static String idDaSafra (String token, String nome) throws Exception
    {
        for (Document safra : lista (get ("/api/safras", token)))
            if (safra.getString ("nome").equals (nome))
                return safra.getString ("id");
        return null;
    }

    private static double numero (Document documento, String campo)
    {
        return documento.get (campo, Number.class).doubleValue ();
    }

    // total do item de uma lista do resumo (porCategoria, porCultura...) cuja chave tem esse valor
    private static double totalDe (Document resumo, String lista, String chave, String valor)
    {
        for (Document item : resumo.getList (lista, Document.class))
            if (String.valueOf (item.get (chave)).equals (valor))
                return numero (item, "total");
        return -1;
    }

    private static void testarCustos () throws Exception
    {
        System.out.println ("\n# custos, indicadores e vinculos");

        String a = login ("maria@agro.com", "Nova@1234");
        String b = login ("joao@agro.com",  "Senha@123");

        String t1   = idDoTalhao (a, "T-01 Norte");
        String t2   = idDoTalhao (a, "t-02 sul");
        String t3   = idDoTalhao (a, "T-03 V\u00e1rzea");
        String tb   = idDoTalhao (b, "T-01 Norte");
        String cafe = idDaSafra  (a, "Caf\u00e9 2026");
        String trig = idDaSafra  (a, "Trigo Inverno");
        String soja = idDaSafra  (a, "Soja Ver\u00e3o 26/27"); // finalizada no teste de safras

        conferir ("listar custos sem token -> 401", 401, get ("/api/custos", null));
        conferir ("resumo sem token -> 401",        401, get ("/api/custos/resumo", null));

        Document vazio = camposComErro (post ("/api/custos", "{}", a));
        conferir ("custo vazio -> 400 com descricao, categoria, valor e data",
                  vazio.keySet ().containsAll (List.of ("description", "category", "value", "date")));
        conferir ("categoria fora da lista -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Impostos", 10, "2026-08-01", null, null), a)).containsKey ("category"));
        conferir ("valor zero -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Outros", 0, "2026-08-01", null, null), a)).containsKey ("value"));
        conferir ("valor negativo -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Outros", -3, "2026-08-01", null, null), a)).containsKey ("value"));
        conferir ("valor como texto -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Outros", "10", "2026-08-01", null, null), a)).containsKey ("value"));
        conferir ("data invalida -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Outros", 10, "2026-13-01", null, null), a)).containsKey ("date"));
        conferir ("talhao de outro usuario -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Outros", 10, "2026-08-01", tb, null), a)).containsKey ("talhaoId"));
        conferir ("safra inexistente -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Outros", 10, "2026-08-01", null, "abc"), a)).containsKey ("safraId"));
        conferir ("talhao fora da safra escolhida -> 400",
                  camposComErro (post ("/api/custos", custo ("X", "Outros", 10, "2026-08-01", t1, cafe), a)).containsKey ("talhaoId"));
        conferir ("custo em safra finalizada -> 409", 409,
                  post ("/api/custos", custo ("X", "Outros", 10, "2026-08-01", t1, soja), a));

        Resp c1 = post ("/api/custos", custo ("Glifosato 20L", "Insumos", 2840, "2026-08-05", t3, cafe), a);
        conferir ("criar custo -> 201", 201, c1);
        Document d1 = c1.status == 201 ? c1.json () : new Document ();
        conferir ("custo traz cultura, talhao e safra resolvidos",
                  "Caf\u00e9".equals (d1.getString ("cultura")) && "T-03 V\u00e1rzea".equals (d1.getString ("talhaoNome")) &&
                  "Caf\u00e9 2026".equals (d1.getString ("safraNome")));
        conferir ("custo esconde a propriedade", !c1.corpo.contains ("propriedadeId"));

        String id2 = post ("/api/custos", custo ("Sal\u00e1rio operador", "M\u00e3o de Obra", 4200, "2026-08-01", null, null), a).json ().getString ("id");
        Resp   c3  = post ("/api/custos", custo ("Manuten\u00e7\u00e3o pulverizador", "Maquin\u00e1rio", 1650.555, "2026-07-28", t2, null), a);
        String id4 = post ("/api/custos", custo ("Diesel 500 L", "Combust\u00edvel", 3100, "2026-07-25", "", ""), a).json ().getString ("id");
        String id5 = post ("/api/custos", custo ("Sementes de trigo", "Insumos", 8400, "2026-07-20", t2, trig), a).json ().getString ("id");
        conferir ("valor arredondado para centavos", c3.status == 201 && numero (c3.json (), "value") == 1650.56);
        conferir ("custo sem talhao fica na propriedade e cultura vem do talhao",
                  get ("/api/custos/" + id4, a).json ().getString ("talhaoId") == null &&
                  "Milho".equals (c3.json ().getString ("cultura")));

        List<Document> todos = lista (get ("/api/custos", a));
        conferir ("lista os 5 custos, mais recente primeiro",
                  todos.size () == 5 && "2026-08-05".equals (todos.get (0).getString ("date")) &&
                  "2026-07-20".equals (todos.get (4).getString ("date")));
        conferir ("usuario B nao ve custos de A", get ("/api/custos", b).corpo.equals ("[]"));
        conferir ("B consulta custo de A -> 404", 404, get ("/api/custos/" + id2, b));
        conferir ("B altera custo de A -> 404",   404, put ("/api/custos/" + id2, custo ("X", "Outros", 1, "2026-08-01", null, null), b));
        conferir ("B exclui custo de A -> 404",   404, requisitar ("DELETE", "/api/custos/" + id2, null, b));
        conferir ("id invalido -> 404",           404, get ("/api/custos/xyz", a));
        conferir ("PUT em /resumo -> 404",        404, put ("/api/custos/resumo", custo ("X", "Outros", 1, "2026-08-01", null, null), a));

        conferir ("filtro category=insumos",     lista (get ("/api/custos?category=insumos", a)).size () == 2);
        conferir ("filtro talhaoId=propriedade", lista (get ("/api/custos?talhaoId=propriedade", a)).size () == 2);
        conferir ("filtro por talhao",           lista (get ("/api/custos?talhaoId=" + t2, a)).size () == 2);
        conferir ("filtro por safra",            lista (get ("/api/custos?safraId=" + cafe, a)).size () == 1);
        conferir ("filtro safraId=nenhuma",      lista (get ("/api/custos?safraId=nenhuma", a)).size () == 3);
        conferir ("filtro de periodo (de)",      lista (get ("/api/custos?de=2026-08-01", a)).size () == 2);
        conferir ("filtro de periodo (de e ate)", lista (get ("/api/custos?de=2026-07-20&ate=2026-07-28", a)).size () == 3);
        conferir ("busca q=diesel",              lista (get ("/api/custos?q=diesel", a)).size () == 1);
        conferir ("periodo com data invalida -> 400", camposComErro (get ("/api/custos?de=ontem", a)).containsKey ("de"));
        conferir ("periodo invertido -> 400",         camposComErro (get ("/api/custos/resumo?de=2026-08-01&ate=2026-07-01", a)).containsKey ("ate"));

        // 2840 + 4200 + 1650,56 + 3100 + 8400 = 20190,56 sobre 296,5 ha de talhoes
        Document resumo = get ("/api/custos/resumo", a).json ();
        conferir ("resumo: total 20190,56 em 5 lancamentos",
                  numero (resumo, "total") == 20190.56 && resumo.getInteger ("quantidade") == 5);
        conferir ("resumo: custo por hectare sobre a area dos talhoes (296,5 ha -> 68,10)",
                  numero (resumo, "areaHectares") == 296.5 && numero (resumo, "custoPorHectare") == 68.10);
        conferir ("resumo por categoria (todas, inclusive zeradas)",
                  totalDe (resumo, "porCategoria", "categoria", "Insumos") == 11240 &&
                  totalDe (resumo, "porCategoria", "categoria", "Maquin\u00e1rio") == 1650.56 &&
                  totalDe (resumo, "porCategoria", "categoria", "Outros") == 0 &&
                  resumo.getList ("porCategoria", Document.class).size () == 5);
        conferir ("resumo por cultura",
                  totalDe (resumo, "porCultura", "cultura", "Caf\u00e9") == 2840 &&
                  totalDe (resumo, "porCultura", "cultura", "Trigo") == 8400 &&
                  totalDe (resumo, "porCultura", "cultura", "null") == 7300);
        conferir ("resumo por talhao e por safra",
                  totalDe (resumo, "porTalhao", "nome", "Propriedade") == 7300 &&
                  totalDe (resumo, "porTalhao", "talhaoId", t2) == 10050.56 &&
                  totalDe (resumo, "porSafra", "safraId", trig) == 8400 &&
                  totalDe (resumo, "porSafra", "nome", "Sem safra") == 8950.56);
        conferir ("evolucao mensal em ordem",
                  resumo.getList ("evolucao", Document.class).size () == 2 &&
                  "2026-07".equals (resumo.getList ("evolucao", Document.class).get (0).getString ("mes")) &&
                  totalDe (resumo, "evolucao", "mes", "2026-07") == 13150.56 &&
                  totalDe (resumo, "evolucao", "mes", "2026-08") == 7040);

        Document daSafra = get ("/api/custos/resumo?safraId=" + cafe, a).json ();
        conferir ("resumo da safra usa a area da safra (72,5 ha -> 39,17)",
                  numero (daSafra, "total") == 2840 && numero (daSafra, "areaHectares") == 72.5 &&
                  numero (daSafra, "custoPorHectare") == 39.17);
        Document doTalhao = get ("/api/custos/resumo?talhaoId=" + t2, a).json ();
        conferir ("resumo do talhao usa a area do talhao (100 ha -> 100,51)",
                  numero (doTalhao, "total") == 10050.56 && numero (doTalhao, "custoPorHectare") == 100.51);
        Document doPeriodo = get ("/api/custos/resumo?de=2026-08-01&ate=2026-08-31", a).json ();
        conferir ("resumo respeita o periodo", numero (doPeriodo, "total") == 7040 && doPeriodo.getInteger ("quantidade") == 2);

        String id3 = c3.json ().getString ("id");
        Resp editado = put ("/api/custos/" + id3, custo ("Manuten\u00e7\u00e3o pulverizador", "Maquin\u00e1rio", 2000, "2026-07-28", t2, null), a);
        conferir ("editar custo -> 200", editado.status == 200 && numero (editado.json (), "value") == 2000);
        conferir ("editar com talhao fora da safra -> 400", 400,
                  put ("/api/custos/" + id3, custo ("X", "Maquin\u00e1rio", 2000, "2026-07-28", t1, cafe), a));
        conferir ("excluir custo -> 204", 204, requisitar ("DELETE", "/api/custos/" + id4, null, a));
        conferir ("custo excluido -> 404", 404, get ("/api/custos/" + id4, a));
        conferir ("total acompanha edicao e exclusao (20190,56 + 349,44 - 3100)",
                  numero (get ("/api/custos/resumo", a).json (), "total") == 17440);

        // vinculos com a safra
        conferir ("safra com custos nao e excluida", 409, requisitar ("DELETE", "/api/safras/" + cafe, null, a));
        String t4 = post ("/api/talhoes", talhao ("T-04 Leste", "Soja", 10, null), a).json ().getString ("id");
        Document safraCafe = get ("/api/safras/" + cafe, a).json ();
        Resp tirou = put ("/api/safras/" + cafe, safra ("Caf\u00e9 2026", "Caf\u00e9", safraCafe.getString ("dataInicio"),
                                                       safraCafe.getString ("dataFim"), t4), a);
        conferir ("safra nao perde talhao com custo lancado nela",
                  tirou.status == 409 && "Plot has costs in this crop season".equals (tirou.json ().getString ("error")));

        for (int i = 0; i < 5; i++)
            post ("/api/safras/" + trig + "/avancar", null, a);
        conferir ("custo de safra que foi finalizada nao e alterado", 409,
                  put ("/api/custos/" + id5, custo ("Sementes de trigo", "Insumos", 1, "2026-07-20", t2, trig), a));
        conferir ("custo de safra finalizada nao e excluido", 409, requisitar ("DELETE", "/api/custos/" + id5, null, a));
        conferir ("custo de safra finalizada segue consultavel", 200, get ("/api/custos/" + id5, a));

        // propriedade com custos nao e excluida
        String outra = post ("/api/propriedades", new Document ("nome", "Sitio Custos").append ("localizacao", "Estrada 1")
            .append ("municipio", "Campinas, SP").append ("safraAtual", "2026/27").append ("areaHectares", 10).toJson (), a)
            .json ().getString ("id");
        String principal = usuarios.buscarPor ("email", "maria@agro.com").getString ("propriedadeId");
        post ("/api/propriedades/" + outra + "/selecionar", "{}", a);
        String geral = post ("/api/custos", custo ("Cerca", "Outros", 500, "2026-08-10", null, null), a).json ().getString ("id");
        conferir ("custos seguem a propriedade selecionada", lista (get ("/api/custos", a)).size () == 1);
        post ("/api/propriedades/" + principal + "/selecionar", "{}", a);
        conferir ("propriedade com custos nao e excluida", 409, requisitar ("DELETE", "/api/propriedades/" + outra, null, a));
        post ("/api/propriedades/" + outra + "/selecionar", "{}", a);
        requisitar ("DELETE", "/api/custos/" + geral, null, a);
        post ("/api/propriedades/" + principal + "/selecionar", "{}", a);
        conferir ("sem custos a propriedade pode ser excluida", 204, requisitar ("DELETE", "/api/propriedades/" + outra, null, a));
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
                  get ("/api/talhoes/resumo", token).json ().getInteger ("quantidadeTotal") == 27);

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

        Roteador roteador = Servidor.montarRoteador (new RepositorioUsuariosMemoria (), new RepositorioTalhoesMemoria (), new RepositorioEstoqueMemoria (),
                                                     new RepositorioSafrasMemoria (),
                                                     new RepositorioCustosMemoria (), null, Path.of ("../../frontend"));
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
        TesteServidor.estoque = new RepositorioEstoqueMemoria ();

        Roteador roteador = Servidor.montarRoteador (TesteServidor.usuarios, new RepositorioTalhoesMemoria (), TesteServidor.estoque,
                                                     new RepositorioSafrasMemoria (),
                                                     new RepositorioCustosMemoria (), null, Path.of ("../../frontend"));

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
            TesteServidor.testarAtividadesEHistorico ();
            TesteServidor.testarEstoque ();
            TesteServidor.testarPropriedades ();
            TesteServidor.testarSafras ();
            TesteServidor.testarCustos ();
            TesteServidor.testarCalendario ();
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
