/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

package http;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import util.Json;

/*
    Resposta HTTP montada pelos handlers e escrita pelo ParceiroHttp.
*/
public class Resposta
{
    private final int                 status;
    private final Map<String, String> cabecalhos = new LinkedHashMap<String, String> ();
    private byte[]                    corpo;

    public Resposta (int status, String tipo, byte[] corpo)
    {
        this.status = status;
        this.corpo  = corpo == null ? new byte[0] : corpo;

        if (tipo != null)
            this.cabecalhos.put ("Content-Type", tipo);
    }

    public static Resposta json (int status, String json)
    {
        return new Resposta (status, "application/json; charset=utf-8",
                             json.getBytes (StandardCharsets.UTF_8));
    }

    public static Resposta json (int status, Document documento)
    {
        return Resposta.json (status, Json.escrever (documento));
    }

    // {"error":"mensagem"}
    public static Resposta erro (int status, String mensagem)
    {
        return Resposta.json (status, new Document ("error", mensagem));
    }

    public static Resposta semConteudo ()
    {
        return new Resposta (204, null, null);
    }

    /** Resposta binária para GET .../foto (#176) com Content-Type da evidência. */
    public static Resposta binario (int status, String tipoMime, byte[] bytes)
    {
        Resposta ret = new Resposta (status, tipoMime, bytes);
        ret.setCabecalho ("Cache-Control", "private, no-store");
        return ret;
    }

    public static Resposta redirecionar (String destino)
    {
        Resposta ret = new Resposta (302, null, null);
        ret.setCabecalho ("Location", destino);
        return ret;
    }

    public int getStatus ()
    {
        return this.status;
    }

    public Map<String, String> getCabecalhos ()
    {
        return this.cabecalhos;
    }

    public void setCabecalho (String nome, String valor)
    {
        this.cabecalhos.put (nome, valor);
    }

    public byte[] getCorpo ()
    {
        return this.corpo;
    }

    // usado no HEAD: mantem o Content-Length do corpo, mas nao envia o corpo
    public void removerCorpo ()
    {
        this.cabecalhos.put ("Content-Length", "" + this.corpo.length);
        this.corpo = new byte[0];
    }

    public static String textoDoStatus (int status)
    {
        switch (status)
        {
            case 200: return "OK";
            case 201: return "Criado";
            case 204: return "Sem conteúdo";
            case 302: return "Encontrado";
            case 400: return "Requisição inválida";
            case 401: return "Não autorizado";
            case 403: return "Proibido";
            case 404: return "Não encontrado";
            case 405: return "Método não permitido";
            case 408: return "Tempo esgotado";
            case 409: return "Conflito";
            case 413: return "Carga grande demais";
            case 429: return "Muitas requisições";
            case 431: return "Cabeçalhos grandes demais";
            case 500: return "Erro interno do servidor";
            case 501: return "Não implementado";
            case 503: return "Serviço indisponível";
            case 505: return "Versão HTTP não suportada";
            default:  return "Desconhecido";
        }
    }
}
