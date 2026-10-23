/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

package util;

import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/* Cliente unico do core para readiness e para rotas de dominio /api/**. */
public class ClienteCore
{
    public static final int TIMEOUT_MS = 2000;
    public static final int MAXIMO_CORPO = 1024 * 1024;
    private static final String CAMINHO_SAUDE = "/actuator/health";

    public static final class CorpoGrande extends java.io.IOException {}

    private final String javaBaseUrl;
    private final HttpClient cliente;

    // Status, corpo e Content-Type que o gateway deve repassar sem reinterpretar.
    public static final class RespostaDoCore
    {
        private final int status;
        private final byte[] corpo;
        private final String tipo;

        public RespostaDoCore (int status, byte[] corpo, String tipo)
        {
            this.status = status;
            this.corpo = corpo;
            this.tipo = tipo;
        }

        public int getStatus () { return this.status; }
        public byte[] getCorpo () { return this.corpo; }
        public String getTipo () { return this.tipo; }
    }

    public ClienteCore (String javaBaseUrl) throws Exception
    {
        if (javaBaseUrl == null)
            throw new Exception ("JAVA_BASE_URL nao fornecida");

        this.javaBaseUrl = javaBaseUrl;
        this.cliente = HttpClient.newBuilder ().connectTimeout (Duration.ofMillis (ClienteCore.TIMEOUT_MS)).build ();
    }

    public String getJavaBaseUrl () { return this.javaBaseUrl; }

    // Descarta o caminho da base e preserva os escapes do caminho/query recebidos.
    private URI destino (String caminho, String query) throws Exception
    {
        URI base = URI.create (this.javaBaseUrl);
        URI origem = new URI (base.getScheme (), null, base.getHost (), base.getPort (), "/", null, null);
        return URI.create (origem.toASCIIString () + caminho.substring (1) + (query == null ? "" : "?" + query));
    }

    // readiness consulta somente a saude do core e nao uma rota de produto.
    public boolean coreSaudavel ()
    {
        HttpURLConnection conexao = null;
        try
        {
            conexao = (HttpURLConnection) this.destino (ClienteCore.CAMINHO_SAUDE, null).toURL ().openConnection ();
            conexao.setRequestMethod ("GET");
            conexao.setConnectTimeout (ClienteCore.TIMEOUT_MS);
            conexao.setReadTimeout (ClienteCore.TIMEOUT_MS);
            int status = conexao.getResponseCode ();
            InputStream corpo = status >= 200 && status <= 299 ? conexao.getInputStream () : conexao.getErrorStream ();
            if (corpo != null) corpo.close ();
            return status >= 200 && status <= 299;
        }
        catch (Exception erro)
        {
            return false;
        }
        finally
        {
            if (conexao != null) conexao.disconnect ();
        }
    }

    // Preserva metodo, corpo e os tres cabecalhos que fazem parte do contrato da API.
    public RespostaDoCore encaminhar (HttpExchange troca) throws Exception
    {
        URI destino = this.destino (troca.getRequestURI ().getRawPath (), troca.getRequestURI ().getRawQuery ());
        byte[] corpo = troca.getRequestBody ().readNBytes (ClienteCore.MAXIMO_CORPO + 1);
        if (corpo.length > ClienteCore.MAXIMO_CORPO) throw new CorpoGrande ();
        HttpRequest.Builder pedido = HttpRequest.newBuilder (destino)
            .timeout (Duration.ofMillis (ClienteCore.TIMEOUT_MS))
            .method (troca.getRequestMethod (), HttpRequest.BodyPublishers.ofByteArray (corpo));

        ClienteCore.copiarCabecalho (troca, pedido, "Authorization");
        ClienteCore.copiarCabecalho (troca, pedido, "Content-Type");
        ClienteCore.copiarCabecalho (troca, pedido, "Accept");

        HttpResponse<byte[]> resposta = this.cliente.send (pedido.build (), HttpResponse.BodyHandlers.ofByteArray ());
        String tipo = resposta.headers ().firstValue ("Content-Type").orElse ("application/json; charset=utf-8");
        return new RespostaDoCore (resposta.statusCode (), resposta.body (), tipo);
    }

    private static void copiarCabecalho (HttpExchange troca, HttpRequest.Builder pedido, String nome)
    {
        String valor = troca.getRequestHeaders ().getFirst (nome);
        if (valor != null && !valor.isBlank ()) pedido.header (nome, valor);
    }

    @Override
    public String toString () { return "ClienteCore (" + this.javaBaseUrl + ")"; }
}
