/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

package http;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import util.comum.Json;

/*
    Resposta HTTP que os handlers montam e o ParceiroHttp escreve no socket.

    Guarda três coisas: o status (200, 201, 404...), os cabeçalhos e o corpo
    em bytes. Os atalhos estáticos evitam repetir charset e JSON em cada rota:
      json()          tela e API
      erro()          {"error":"..."}
      semConteudo()   DELETE que deu certo (204, sem corpo)
      binario()       foto da ocorrência ou da atividade
      redirecionar()  Location para o navegador seguir
*/
public class Resposta
{
    private final int                 status;
    private final Map<String, String> cabecalhos = new LinkedHashMap<String, String> ();
    private byte[]                    corpo;

    // tipo null significa "não enviar Content-Type" (204 e 302).
    public Resposta (int status, String tipo, byte[] corpo)
    {
        this.status = status;
        this.corpo  = corpo == null ? new byte[0] : corpo;

        if (tipo != null)
            this.cabecalhos.put ("Content-Type", tipo);
    }

    // JSON já pronto em texto. O charset utf-8 entra no cabeçalho para o navegador.
    public static Resposta json (int status, String json)
    {
        return new Resposta (status, "application/json; charset=utf-8",
                             json.getBytes (StandardCharsets.UTF_8));
    }

    // Atalho quando o handler já tem um Document do Mongo/BSON.
    public static Resposta json (int status, Document documento)
    {
        return Resposta.json (status, Json.escrever (documento));
    }

    // Formato único de erro da API: {"error":"mensagem"}. O front lê esse campo.
    public static Resposta erro (int status, String mensagem)
    {
        return Resposta.json (status, new Document ("error", mensagem));
    }

    // 204: a operação deu certo e não há JSON para devolver (exclusão, por exemplo).
    public static Resposta semConteudo ()
    {
        return new Resposta (204, null, null);
    }

    // Foto: o corpo são os bytes da imagem, não JSON. no-store evita cache do navegador
    // misturar a foto de um usuário com a de outro no mesmo computador.
    public static Resposta binario (int status, String tipoMime, byte[] bytes)
    {
        Resposta ret = new Resposta (status, tipoMime, bytes);
        ret.setCabecalho ("Cache-Control", "private, no-store");
        return ret;
    }

    // 302 pede ao navegador para abrir outra URL. Usado pouco; a API em geral responde JSON.
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

    // HEAD pede "me diga o tamanho, mas não mande o arquivo". Guardamos o tamanho
    // no cabeçalho e zeramos os bytes que seriam enviados.
    public void removerCorpo ()
    {
        this.cabecalhos.put ("Content-Length", "" + this.corpo.length);
        this.corpo = new byte[0];
    }

    // Texto que aparece depois do número na primeira linha ("200 OK", "404 Não encontrado").
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
