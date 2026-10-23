/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

// Finge health e rotas de dominio para validar o contrato do proxy sem Spring.
public class CoreFalsoHandler implements HttpHandler
{
    private int status;

    public CoreFalsoHandler (int status)
    {
        this.status = status;
    }

    @Override
    public void handle (HttpExchange troca)
    {
        try
        {
            String caminho = troca.getRequestURI ().getPath ();
            String corpo = new String (troca.getRequestBody ().readAllBytes (), StandardCharsets.UTF_8);
            String resposta = "{\"status\":\"UP\"}";
            int retorno = this.status;

            if (caminho.equals ("/api/parcelas"))
            {
                retorno = 201;
                resposta = "{\"method\":\"" + troca.getRequestMethod () + "\",\"body\":" + corpo + "}";
            }
            else if (caminho.equals ("/api/erro"))
            {
                retorno = 422;
                resposta = "{\"error\":\"Business rule\"}";
            }
            else if (caminho.equals ("/api/query"))
            {
                retorno = 200;
                resposta = "{\"query\":\"" + troca.getRequestURI ().getRawQuery () + "\"}";
            }
            else if (!caminho.equals ("/actuator/health"))
                retorno = 404;

            byte[] bytes = resposta.getBytes (StandardCharsets.UTF_8);

            troca.getResponseHeaders().set ("Content-Type", "application/json");
            troca.sendResponseHeaders (retorno, bytes.length);

            OutputStream saida = troca.getResponseBody ();
            saida.write (bytes);
            saida.close ();
        }
        catch (Exception erro)
        {}
    }
}
