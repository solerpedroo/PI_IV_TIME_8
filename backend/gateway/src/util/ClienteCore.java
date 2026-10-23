/*
    Alterado por: Vinícius Panutti Salgado — R.A. 25007329
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

public class ClienteCore
{
    public static final int TIMEOUT_MS = 2000;

    private static final String CAMINHO_SAUDE = "/ready";

    private String javaBaseUrl;

    // Nao valida o formato da URL aqui, igual ao createGateway do server.ts:
    // URL vazia ou invalida so aparece no /ready, que responde 503.
    public ClienteCore (String javaBaseUrl) throws Exception
    {
        if (javaBaseUrl == null)
            throw new Exception ("JAVA_BASE_URL nao fornecida");

        this.javaBaseUrl = javaBaseUrl;
    }

    public String getJavaBaseUrl ()
    {
        return this.javaBaseUrl;
    }

    // true se GET /ready do core responder 2xx em ate 2 s
    public boolean coreSaudavel ()
    {
        boolean           ret     = false;
        HttpURLConnection conexao = null;

        try
        {
            // resolve um caminho absoluto para o health do core Java puro:
            // mantem esquema, host e porta e TROCA o caminho inteiro. Assim barra no
            // final nao gera "//actuator" e um caminho na base e descartado, como no TS.
            URI destino = URI.create (this.javaBaseUrl).resolve (ClienteCore.CAMINHO_SAUDE);

            conexao = (HttpURLConnection) destino.toURL().openConnection ();
            conexao.setRequestMethod  ("GET");
            conexao.setConnectTimeout (ClienteCore.TIMEOUT_MS);
            conexao.setReadTimeout    (ClienteCore.TIMEOUT_MS);

            int status = conexao.getResponseCode ();
            ret = status >= 200 && status <= 299;

            // esvazia o corpo para a conexao poder ser reaproveitada
            InputStream corpo = ret ? conexao.getInputStream () : conexao.getErrorStream ();
            if (corpo != null)
            {
                while (corpo.read () != -1)
                {}
                corpo.close ();
            }
        }
        catch (Exception erro)
        {
            ret = false; // URL invalida, core fora do ar, conexao recusada ou tempo estourado
        }
        finally
        {
            if (conexao != null)
                conexao.disconnect ();
        }

        return ret;
    }

    @Override
    public String toString ()
    {
        return "ClienteCore (" + this.javaBaseUrl + ")";
    }

    @Override
    public boolean equals (Object obj)
    {
        if (this == obj) return true;
        if (obj == null) return false;
        if (this.getClass() != obj.getClass()) return false;

        ClienteCore c = (ClienteCore) obj;

        if (!this.javaBaseUrl.equals(c.javaBaseUrl)) return false;

        return true;
    }

    @Override
    public int hashCode ()
    {
        int ret = 13;

        ret = 7 * ret + this.javaBaseUrl.hashCode();

        if (ret < 0) ret = -ret;

        return ret;
    }
}
