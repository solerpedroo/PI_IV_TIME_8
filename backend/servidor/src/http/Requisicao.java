package http;

/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import seguranca.Sessao;

/*
    Uma requisicao HTTP ja lida e separada em partes.
    Os nomes dos cabecalhos ficam sempre em minusculas.
*/
public class Requisicao
{
    private final String              metodo;
    private final String              caminho;
    private final Map<String, String> consulta;
    private final Map<String, String> cabecalhos;
    private final byte[]              corpo;
    private final String              enderecoRemoto;

    // preenchidos pelo roteador
    private Map<String, String> parametros = new HashMap<String, String> ();
    private Sessao              sessao     = null;

    public Requisicao (String metodo, String caminho, Map<String, String> consulta,
                       Map<String, String> cabecalhos, byte[] corpo, String enderecoRemoto) throws Exception
    {
        if (metodo == null)
            throw new Exception ("Metodo ausente");

        if (caminho == null)
            throw new Exception ("Caminho ausente");

        if (consulta == null)
            throw new Exception ("Consulta ausente");

        if (cabecalhos == null)
            throw new Exception ("Cabecalhos ausentes");

        if (corpo == null)
            throw new Exception ("Corpo ausente");

        if (enderecoRemoto == null || enderecoRemoto.isBlank ())
            throw new Exception ("Endereco remoto ausente");

        this.metodo     = metodo;
        this.caminho    = caminho;
        this.consulta   = consulta;
        this.cabecalhos = cabecalhos;
        this.corpo      = corpo;
        this.enderecoRemoto = enderecoRemoto;
    }

    public String getMetodo ()
    {
        return this.metodo;
    }

    public String getCaminho ()
    {
        return this.caminho;
    }

    // valor de ?nome=valor, ou null
    public String getConsulta (String nome)
    {
        return this.consulta.get (nome);
    }

    public String getCabecalho (String nome)
    {
        return this.cabecalhos.get (nome.toLowerCase ());
    }

    public byte[] getCorpo ()
    {
        return this.corpo;
    }

    public String getEnderecoRemoto ()
    {
        return this.enderecoRemoto;
    }

    // corpo interpretado como um objeto JSON; 400 se nao for
    public Document getCorpoJson () throws ErroHttp
    {
        String texto = new String (this.corpo, StandardCharsets.UTF_8).trim ();

        if (!texto.startsWith ("{"))
            throw new ErroHttp (400, "Invalid JSON body");

        try
        {
            return Document.parse (texto);
        }
        catch (Exception erro)
        {
            throw new ErroHttp (400, "Invalid JSON body");
        }
    }

    // valor de {nome} no padrao da rota
    public String getParametro (String nome)
    {
        return this.parametros.get (nome);
    }

    public void setParametros (Map<String, String> parametros)
    {
        this.parametros = parametros;
    }

    public Sessao getSessao ()
    {
        return this.sessao;
    }

    public void setSessao (Sessao sessao)
    {
        this.sessao = sessao;
    }

    // token de "Authorization: Bearer xxx", ou null
    public String getToken ()
    {
        String autorizacao = this.getCabecalho ("Authorization");

        if (autorizacao == null || !autorizacao.regionMatches (true, 0, "Bearer ", 0, 7))
            return null;

        String token = autorizacao.substring (7).trim ();

        if (token.isEmpty ())
            return null;

        return token;
    }
}
