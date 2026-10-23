/*
    Alterado por: Vinícius Panutti Salgado — R.A. 25007329
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
import java.io.OutputStream;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

// Finge ser o core Java puro: responde GET /ready com o status escolhido
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
            byte[] bytes   = "{\"status\":\"UP\"}".getBytes ("UTF-8");
            int    retorno = this.status;

            if (!troca.getRequestURI().getPath().equals("/ready"))
                retorno = 404;

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
