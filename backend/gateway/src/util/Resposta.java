/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
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

        Resposta.enviar (troca, status, json.getBytes ("UTF-8"), "application/json; charset=utf-8");
    }

    // Sobrecarga usada pelo proxy para nao alterar corpo, status ou Content-Type do core.
    public static void enviar (HttpExchange troca, int status, byte[] bytes, String tipo) throws Exception
    {
        if (troca == null) throw new Exception ("Troca nao fornecida");
        if (bytes == null) throw new Exception ("Corpo nao fornecido");
        if (tipo == null || tipo.isBlank ()) tipo = "application/json; charset=utf-8";

        troca.getResponseHeaders().set ("Content-Type", tipo);
        troca.sendResponseHeaders (status, bytes.length);

        OutputStream saida = troca.getResponseBody ();
        saida.write (bytes);
        saida.close ();
    }
}
