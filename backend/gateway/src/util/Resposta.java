package util;

import java.io.OutputStream;
import com.sun.net.httpserver.HttpExchange;

public class Resposta
{
    // Toda resposta do gateway e JSON, igual ao server.ts antigo
    public static void enviar (HttpExchange troca, int status, String json) throws Exception
    {
        if (troca == null)
            throw new Exception ("Troca nao fornecida");

        if (json == null)
            throw new Exception ("JSON nao fornecido");

        byte[] bytes = json.getBytes ("UTF-8");

        troca.getResponseHeaders().set ("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders (status, bytes.length);

        OutputStream saida = troca.getResponseBody ();
        saida.write (bytes);
        saida.close ();
    }
}
