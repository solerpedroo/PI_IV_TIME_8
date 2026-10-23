/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import com.sun.net.httpserver.HttpServer;
import handlers.RotasHandler;

/*
    Substitui o antigo server.test.ts (node:test), sem JUnit.
    Sobe um core falso e o gateway em portas livres e confere as respostas.
*/
public class TesteGateway
{
    private static final String UP        = "{\"status\":\"UP\"}";
    private static final String DOWN      = "{\"status\":\"DOWN\"}";
    private static final String NOT_FOUND = "{\"error\":\"Not found\"}";
    private static final String ERRO      = "{\"error\":\"Internal server error\"}";
    private static final String CORE_DOWN = "{\"error\":\"Core unavailable\"}";

    private static int falhas = 0;
    private static int total  = 0;

    private static void conferir (String nome, int statusEsperado, String corpoEsperado,
                                  int status, String corpo)
    {
        TesteGateway.total++;

        if (status == statusEsperado && corpo.equals(corpoEsperado))
            System.out.println ("ok    " + nome);
        else
        {
            TesteGateway.falhas++;
            System.out.println ("FALHA " + nome + ": esperava " + statusEsperado + " " +
                                corpoEsperado + " e veio " + status + " " + corpo);
        }
    }

    private static void conferir (String nome, boolean condicao)
    {
        TesteGateway.total++;

        if (condicao)
            System.out.println ("ok    " + nome);
        else
        {
            TesteGateway.falhas++;
            System.out.println ("FALHA " + nome);
        }
    }

    // devolve { status, corpo, content-type }
    private static String[] requisitar (String metodo, String url) throws Exception
    {
        return TesteGateway.requisitar (metodo, url, null);
    }

    // Permite conferir que o proxy conserva metodo e corpo enviados pelo front.
    private static String[] requisitar (String metodo, String url, String corpoRequisicao) throws Exception
    {
        HttpURLConnection conexao = (HttpURLConnection) URI.create (url).toURL().openConnection ();
        conexao.setRequestMethod  (metodo);
        conexao.setConnectTimeout (5000);
        conexao.setReadTimeout    (5000);

        if (corpoRequisicao != null)
        {
            conexao.setDoOutput (true);
            conexao.setRequestProperty ("Content-Type", "application/json");
            conexao.getOutputStream ().write (corpoRequisicao.getBytes ("UTF-8"));
        }

        int         status = conexao.getResponseCode ();
        InputStream corpo  = status < 400 ? conexao.getInputStream () : conexao.getErrorStream ();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream ();
        if (corpo != null)
        {
            byte[] pedaco = new byte[1024];
            int    lidos;
            while ((lidos = corpo.read (pedaco)) != -1)
                buffer.write (pedaco, 0, lidos);
            corpo.close ();
        }

        String tipo = conexao.getHeaderField ("Content-Type");

        conexao.disconnect ();

        String[] ret = { "" + status, buffer.toString ("UTF-8"), "" + tipo };
        return ret;
    }

    private static int portaLivre () throws Exception
    {
        ServerSocket socket = new ServerSocket (0);
        int ret = socket.getLocalPort ();
        socket.close ();
        return ret;
    }

    private static HttpServer coreFalso (int status) throws Exception
    {
        HttpServer ret = HttpServer.create (new InetSocketAddress ("127.0.0.1", 0), 0);
        ret.createContext ("/", new CoreFalsoHandler (status));
        ret.start ();
        return ret;
    }

    private static HttpServer gateway (String javaBaseUrl) throws Exception
    {
        HttpServer ret = Gateway.criar ("127.0.0.1", 0, javaBaseUrl);
        ret.start ();
        return ret;
    }

    private static String endereco (HttpServer servidor)
    {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public static void main (String[] args)
    {
        try
        {
            String coreDesligado = "http://127.0.0.1:" + TesteGateway.portaLivre ();

            // 1) /health responde sem consultar o core
            HttpServer gw = TesteGateway.gateway (coreDesligado);
            String[] r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/health");
            TesteGateway.conferir ("health sem core", 200, TesteGateway.UP,
                                   Integer.parseInt (r[0]), r[1]);
            TesteGateway.conferir ("content-type e json utf-8",
                                   r[2].equals("application/json; charset=utf-8"));

            // 2) /ready com core fora do ar -> 503
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/ready");
            TesteGateway.conferir ("ready com core fora", 503, TesteGateway.DOWN,
                                   Integer.parseInt (r[0]), r[1]);

            // Indisponibilidade de produto nao vira 503 de readiness: e uma falha de proxy 502.
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/api/parcelas");
            TesteGateway.conferir ("dominio com core fora", 502, TesteGateway.CORE_DOWN,
                                   Integer.parseInt (r[0]), r[1]);

            // 3) rota desconhecida -> 404
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/qualquer");
            TesteGateway.conferir ("rota desconhecida", 404, TesteGateway.NOT_FOUND,
                                   Integer.parseInt (r[0]), r[1]);

            // comportamentos conferidos no server.ts (request.method === 'GET' && request.url === '/health')
            r = TesteGateway.requisitar ("POST", TesteGateway.endereco (gw) + "/health");
            TesteGateway.conferir ("POST /health nao e aceito", 404, TesteGateway.NOT_FOUND,
                                   Integer.parseInt (r[0]), r[1]);

            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/health?x=1");
            TesteGateway.conferir ("health com query string", 404, TesteGateway.NOT_FOUND,
                                   Integer.parseInt (r[0]), r[1]);

            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/health/");
            TesteGateway.conferir ("health com barra no final", 404, TesteGateway.NOT_FOUND,
                                   Integer.parseInt (r[0]), r[1]);
            gw.stop (0);

            // 4) /ready com core saudavel -> 200
            HttpServer core = TesteGateway.coreFalso (200);
            gw = TesteGateway.gateway (TesteGateway.endereco (core));
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/ready");
            TesteGateway.conferir ("ready com core saudavel", 200, TesteGateway.UP,
                                   Integer.parseInt (r[0]), r[1]);

            // Rota de dominio: metodo, corpo, query, status e JSON do core passam pelo gateway.
            r = TesteGateway.requisitar ("POST", TesteGateway.endereco (gw) + "/api/parcelas?ativo=true", "{\"nome\":\"Sul\"}");
            TesteGateway.conferir ("POST de dominio encaminhado", 201,
                                   "{\"method\":\"POST\",\"body\":{\"nome\":\"Sul\"}}",
                                   Integer.parseInt (r[0]), r[1]);
            TesteGateway.conferir ("resposta de dominio continua JSON", r[2].startsWith ("application/json"));

            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/api/erro");
            TesteGateway.conferir ("erro de negocio preservado", 422, "{\"error\":\"Business rule\"}",
                                   Integer.parseInt (r[0]), r[1]);
            gw.stop (0);

            // JAVA_BASE_URL com barra no final nao pode gerar "//actuator"
            gw = TesteGateway.gateway (TesteGateway.endereco (core) + "/");
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/ready");
            TesteGateway.conferir ("ready com barra no final da base", 200, TesteGateway.UP,
                                   Integer.parseInt (r[0]), r[1]);
            gw.stop (0);

            // JAVA_BASE_URL com caminho: o caminho e descartado, como no new URL do server.ts
            gw = TesteGateway.gateway (TesteGateway.endereco (core) + "/prefixo/api");
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/ready");
            TesteGateway.conferir ("ready com caminho na base", 200, TesteGateway.UP,
                                   Integer.parseInt (r[0]), r[1]);
            gw.stop (0);
            core.stop (0);

            // 5) /ready com core respondendo 503 (Mongo fora) -> 503
            core = TesteGateway.coreFalso (503);
            gw = TesteGateway.gateway (TesteGateway.endereco (core) + "/");
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/ready");
            TesteGateway.conferir ("ready com core 503", 503, TesteGateway.DOWN,
                                   Integer.parseInt (r[0]), r[1]);
            gw.stop (0);
            core.stop (0);

            // JAVA_BASE_URL vazia ou invalida: o gateway sobe e o /ready responde 503, como no TS
            gw = TesteGateway.gateway ("");
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/ready");
            TesteGateway.conferir ("ready com base vazia", 503, TesteGateway.DOWN,
                                   Integer.parseInt (r[0]), r[1]);
            gw.stop (0);

            // erro inesperado no handler: responde 500 em vez de derrubar o gateway
            // (o stack trace que aparece na saida deste teste e esperado)
            gw = HttpServer.create (new InetSocketAddress ("127.0.0.1", 0), 0);
            gw.createContext ("/", new RotasHandler (new ClienteCoreQuebrado ()));
            gw.start ();
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/ready");
            TesteGateway.conferir ("erro inesperado no handler", 500, TesteGateway.ERRO,
                                   Integer.parseInt (r[0]), r[1]);
            r = TesteGateway.requisitar ("GET", TesteGateway.endereco (gw) + "/health");
            TesteGateway.conferir ("gateway continua vivo apos o erro", 200, TesteGateway.UP,
                                   Integer.parseInt (r[0]), r[1]);
            gw.stop (0);

            // 6) validacao da porta
            boolean recusouTodas = true;
            String[] invalidas = { "0", "65536", "-1", "abc", "80.5", "" };
            for (int i = 0; i < invalidas.length; i++)
            {
                try
                {
                    Gateway.lerPorta (invalidas[i]);
                    recusouTodas = false;
                }
                catch (Exception erro)
                {} // esperado
            }
            TesteGateway.conferir ("porta invalida e recusada", recusouTodas);

            // 7) porta valida
            TesteGateway.conferir ("porta 8080 e aceita", Gateway.lerPorta ("8080") == 8080);
        }
        catch (Exception erro)
        {
            TesteGateway.falhas++;
            System.out.println ("FALHA inesperada: " + erro);
        }

        System.out.println ((TesteGateway.total - TesteGateway.falhas) + " de " +
                            TesteGateway.total + " testes passaram");

        // aqui o System.exit e necessario: o pool de threads do gateway mantem a JVM viva,
        // e o codigo de saida diferente de zero sinaliza falha para quem rodou o script
        System.exit (TesteGateway.falhas == 0 ? 0 : 1);
    }
}
