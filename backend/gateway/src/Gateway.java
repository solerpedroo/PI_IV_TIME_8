/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import handlers.RotasHandler;
import util.ClienteCore;

/*
    Equivalente ao antigo index.ts + createGateway do server.ts.
    Variaveis (todas com padrao):
    GATEWAY_HOST   127.0.0.1
    GATEWAY_PORT   8080
    JAVA_BASE_URL  http://127.0.0.1:8081
*/
public class Gateway
{
    // igual ao "process.env.X ?? padrao" do index.ts: so usa o padrao quando a variavel nao existe
    // (variavel definida como vazia continua vazia, como no TS)
    private static String variavel (String nome, String padrao)
    {
        String ret = System.getenv (nome);

        if (ret == null)
            ret = padrao;

        return ret;
    }

    public static int lerPorta (String texto) throws Exception
    {
        int ret;

        if (texto == null)
            throw new Exception ("GATEWAY_PORT deve ser uma porta TCP valida");

        try
        {
            // o trim imita o Number(...) do index.ts, que ignora espacos nas pontas
            ret = Integer.parseInt (texto.trim());
        }
        catch (NumberFormatException erro)
        {
            throw new Exception ("GATEWAY_PORT deve ser uma porta TCP valida");
        }

        if (ret < 1 || ret > 65535)
            throw new Exception ("GATEWAY_PORT deve ser uma porta TCP valida");

        return ret;
    }

    // cria e devolve o servidor HTTP do gateway (porta 0 = porta livre qualquer, usado nos testes)
    public static HttpServer criar (String host, int porta, String javaBaseUrl) throws Exception
    {
        if (host == null)
            throw new Exception ("Host do gateway nao fornecido");

        HttpServer servidor = HttpServer.create (new InetSocketAddress (host, porta), 0);

        servidor.createContext ("/", new RotasHandler (new ClienteCore (javaBaseUrl)));
        servidor.setExecutor   (Executors.newFixedThreadPool (10));

        return servidor;
    }

    public static void main (String[] args)
    {
        try
        {
            String host        = Gateway.variavel ("GATEWAY_HOST",  "127.0.0.1");
            int    porta       = Gateway.lerPorta (Gateway.variavel ("GATEWAY_PORT", "8080"));
            String javaBaseUrl = Gateway.variavel ("JAVA_BASE_URL", "http://127.0.0.1:8081");

            HttpServer servidor = Gateway.criar (host, porta, javaBaseUrl);
            servidor.start ();

            // mesmo texto do console.log do index.ts
            System.out.println ("Gateway pronto em http://" + host + ":" + porta);
        }
        catch (Exception erro)
        {
            System.err.println ("Nao foi possivel iniciar o gateway: " + erro.getMessage());
            System.exit (1); // unico System.exit permitido: falha na partida, igual ao throw do index.ts
        }
    }
}
