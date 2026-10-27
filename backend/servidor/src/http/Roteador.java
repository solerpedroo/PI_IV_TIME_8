package http;

/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import seguranca.Sessao;
import seguranca.Sessoes;

/*
    Tabela de rotas: metodo + padrao de caminho (ex.: /api/talhoes/{id}) -> handler.
    Tambem faz o filtro de autenticacao, o CORS e o OPTIONS.
    O que nao e /api/** cai no handler de estaticos.
*/
public class Roteador
{
    private static class Rota
    {
        String      metodo;
        String[]    segmentos;
        boolean     publica;
        Manipulador manipulador;
    }

    private final ArrayList<Rota> rotas = new ArrayList<Rota> ();
    private final Sessoes         sessoes;
    private Manipulador           estaticos = null;

    public Roteador (Sessoes sessoes) throws Exception
    {
        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        this.sessoes = sessoes;
    }

    // rota que exige "Authorization: Bearer <token>"
    public void adicionar (String metodo, String padrao, Manipulador manipulador) throws Exception
    {
        this.incluir (metodo, padrao, manipulador, false);
    }

    // rota liberada sem token
    public void adicionarPublica (String metodo, String padrao, Manipulador manipulador) throws Exception
    {
        this.incluir (metodo, padrao, manipulador, true);
    }

    public void setEstaticos (Manipulador estaticos)
    {
        this.estaticos = estaticos;
    }

    private void incluir (String metodo, String padrao, Manipulador manipulador, boolean publica) throws Exception
    {
        if (metodo == null)
            throw new Exception ("Metodo ausente");

        if (padrao == null || !padrao.startsWith ("/"))
            throw new Exception ("Padrao ausente");

        if (manipulador == null)
            throw new Exception ("Manipulador ausente");

        Rota rota = new Rota ();
        rota.metodo      = metodo;
        rota.segmentos   = padrao.substring (1).split ("/", -1);
        rota.publica     = publica;
        rota.manipulador = manipulador;

        this.rotas.add (rota);
    }

    // devolve os parametros {x} se o caminho casar com a rota, senao null
    private static Map<String, String> casar (Rota rota, String[] partes)
    {
        if (rota.segmentos.length != partes.length)
            return null;

        Map<String, String> ret = new HashMap<String, String> ();
        for (int i = 0; i < partes.length; i++)
        {
            String segmento = rota.segmentos[i];

            if (segmento.startsWith ("{") && segmento.endsWith ("}"))
            {
                if (partes[i].isEmpty ())
                    return null;
                ret.put (segmento.substring (1, segmento.length () - 1), partes[i]);
            }
            else if (!segmento.equals (partes[i]))
                return null;
        }

        return ret;
    }

    // nunca lanca excecao: qualquer erro vira uma resposta
    public Resposta tratar (Requisicao requisicao)
    {
        Resposta ret;

        try
        {
            ret = this.despachar (requisicao);
        }
        catch (ErroHttp erro)
        {
            ret = Resposta.erro (erro.getStatus (), erro.getMessage ());
        }
        catch (Exception erro)
        {
            System.err.println ("Erro em " + requisicao.getMetodo () + " " + requisicao.getCaminho () + ": " + erro);
            ret = Resposta.erro (500, "Internal server error");
        }

        Roteador.adicionarCors (requisicao, ret);
        ret.setCabecalho ("X-Content-Type-Options", "nosniff");

        if (requisicao.getMetodo ().equals ("HEAD"))
            ret.removerCorpo ();

        return ret;
    }

    private Resposta despachar (Requisicao requisicao) throws Exception
    {
        String metodo  = requisicao.getMetodo ();
        String caminho = requisicao.getCaminho ();

        // preflight do CORS
        if (metodo.equals ("OPTIONS"))
            return Resposta.semConteudo ();

        // HEAD responde como GET, sem corpo (o corpo e tirado em tratar)
        String procurado = metodo.equals ("HEAD") ? "GET" : metodo;

        String[]    partes      = caminho.substring (1).split ("/", -1);
        Set<String> permitidos  = new LinkedHashSet<String> ();

        for (Rota rota : this.rotas)
        {
            Map<String, String> parametros = Roteador.casar (rota, partes);
            if (parametros == null)
                continue;

            permitidos.add (rota.metodo);
            if (!rota.metodo.equals (procurado))
                continue;

            if (!rota.publica)
            {
                Sessao sessao = this.sessoes.buscar (requisicao.getToken ());
                if (sessao == null)
                    return Resposta.erro (401, "Unauthorized");
                requisicao.setSessao (sessao);
            }

            requisicao.setParametros (parametros);
            return rota.manipulador.tratar (requisicao);
        }

        // o caminho existe, mas nao com esse metodo
        if (!permitidos.isEmpty ())
        {
            Resposta ret = Resposta.erro (405, "Method not allowed");
            ret.setCabecalho ("Allow", String.join (", ", permitidos) + ", OPTIONS");
            return ret;
        }

        boolean ehApi = caminho.equals ("/api") || caminho.startsWith ("/api/");

        if (ehApi)
        {
            // fora de /api/auth/* nao revela quais rotas existem para quem nao tem token
            if (!caminho.startsWith ("/api/auth/") && this.sessoes.buscar (requisicao.getToken ()) == null)
                return Resposta.erro (401, "Unauthorized");
            return Resposta.erro (404, "Not found");
        }

        if (this.estaticos == null)
            return Resposta.erro (404, "Not found");

        if (!procurado.equals ("GET"))
        {
            Resposta ret = Resposta.erro (405, "Method not allowed");
            ret.setCabecalho ("Allow", "GET, HEAD, OPTIONS");
            return ret;
        }

        return this.estaticos.tratar (requisicao);
    }

    // libera o front rodando em outra porta de localhost
    private static void adicionarCors (Requisicao requisicao, Resposta resposta)
    {
        String origem = requisicao.getCabecalho ("Origin");

        if (origem == null || !origem.matches ("https?://(localhost|127\\.0\\.0\\.1)(:\\d{1,5})?"))
            return;

        resposta.setCabecalho ("Access-Control-Allow-Origin",  origem);
        resposta.setCabecalho ("Vary",                         "Origin");
        resposta.setCabecalho ("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resposta.setCabecalho ("Access-Control-Allow-Headers", "Content-Type, Authorization");
        resposta.setCabecalho ("Access-Control-Max-Age",       "600");
    }
}
