package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import util.ClienteCore;
import util.Resposta;

/*
    Mesmas rotas do antigo server.ts:
    GET /health  ->  200 {"status":"UP"}    (nao consulta o core)
    GET /ready   ->  200 {"status":"UP"}    se o core responder 2xx em ate 2 s
                     503 {"status":"DOWN"}  caso contrario
    qualquer outra coisa -> 404 {"error":"Not found"}

    O server.ts comparava request.url inteiro (request.url === '/health'), ou seja,
    caminho + query string, sem decodificar nada. Por isso aqui a comparacao e com o
    alvo da requisicao como veio: "/health?x=1" e "/health/" dao 404, como no TS.
*/
public class RotasHandler implements HttpHandler
{
    private static final String UP        = "{\"status\":\"UP\"}";
    private static final String DOWN      = "{\"status\":\"DOWN\"}";
    private static final String NOT_FOUND = "{\"error\":\"Not found\"}";
    private static final String ERRO      = "{\"error\":\"Internal server error\"}";

    private ClienteCore core;

    public RotasHandler (ClienteCore core) throws Exception
    {
        if (core == null)
            throw new Exception ("Cliente do core nao fornecido");

        this.core = core;
    }

    @Override
    public void handle (HttpExchange troca)
    {
        try
        {
            String metodo = troca.getRequestMethod ();
            String alvo   = troca.getRequestURI().toString ();

            if (metodo.equals("GET") && alvo.equals("/health"))
                Resposta.enviar (troca, 200, RotasHandler.UP);
            else if (metodo.equals("GET") && alvo.equals("/ready"))
            {
                if (this.core.coreSaudavel ())
                    Resposta.enviar (troca, 200, RotasHandler.UP);
                else
                    Resposta.enviar (troca, 503, RotasHandler.DOWN);
            }
            else
                Resposta.enviar (troca, 404, RotasHandler.NOT_FOUND);
        }
        catch (Exception erro)
        {
            erro.printStackTrace ();
            try
            {
                Resposta.enviar (troca, 500, RotasHandler.ERRO);
            }
            catch (Exception outroErro)
            {} // a conexao ja caiu, nao ha o que fazer
        }
    }
}
