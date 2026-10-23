/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import util.ClienteCore;
import util.Resposta;

/*
    /health e /ready pertencem ao gateway. Somente /api/** segue ao core;
    assim o servidor nao vira um proxy aberto para caminhos arbitrarios.
*/
public class RotasHandler implements HttpHandler
{
    private static final String UP        = "{\"status\":\"UP\"}";
    private static final String DOWN      = "{\"status\":\"DOWN\"}";
    private static final String NOT_FOUND = "{\"error\":\"Not found\"}";
    private static final String CORE_DOWN = "{\"error\":\"Core unavailable\"}";
    private static final String ERRO      = "{\"error\":\"Internal server error\"}";

    private final ClienteCore core;

    public RotasHandler (ClienteCore core) throws Exception
    {
        if (core == null) throw new Exception ("Cliente do core nao fornecido");
        this.core = core;
    }

    @Override
    public void handle (HttpExchange troca)
    {
        try
        {
            String metodo = troca.getRequestMethod ();
            String caminho = troca.getRequestURI ().getPath ();
            String alvo = troca.getRequestURI ().toString ();

            // Health e ready preservam o contrato estrito original: sem query.
            if (metodo.equals ("GET") && alvo.equals ("/health"))
                Resposta.enviar (troca, 200, RotasHandler.UP);
            else if (metodo.equals ("GET") && alvo.equals ("/ready"))
                this.responderReadiness (troca);
            else if (caminho.startsWith ("/api/"))
                this.encaminharDominio (troca);
            else
                Resposta.enviar (troca, 404, RotasHandler.NOT_FOUND);
        }
        catch (Exception erro)
        {
            // Nao devolve stack trace ou detalhes internos para o cliente.
            try { Resposta.enviar (troca, 500, RotasHandler.ERRO); }
            catch (Exception ignorado) {}
        }
    }

    private void responderReadiness (HttpExchange troca) throws Exception
    {
        boolean saudavel = this.core.coreSaudavel ();
        Resposta.enviar (troca, saudavel ? 200 : 503, saudavel ? RotasHandler.UP : RotasHandler.DOWN);
    }

    private void encaminharDominio (HttpExchange troca) throws Exception
    {
        try
        {
            ClienteCore.RespostaDoCore resposta = this.core.encaminhar (troca);
            Resposta.enviar (troca, resposta.getStatus (), resposta.getCorpo (), resposta.getTipo ());
        }
        catch (HttpTimeoutException erro)
        {
            Resposta.enviar (troca, 504, RotasHandler.CORE_DOWN);
        }
        catch (IOException erro)
        {
            Resposta.enviar (troca, 502, RotasHandler.CORE_DOWN);
        }
        catch (InterruptedException erro)
        {
            Thread.currentThread ().interrupt ();
            Resposta.enviar (troca, 504, RotasHandler.CORE_DOWN);
        }
    }
}
