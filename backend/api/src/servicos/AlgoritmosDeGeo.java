/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package servicos;

import http.ErroHttp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.GeometriaParaMapa;
import protocolo.mensagens.PedidoDeGeometriaDoMapa;
import protocolo.mensagens.PedidoDePontoNoTalhao;
import protocolo.mensagens.PedidoDeValidacaoDeLocalizacao;
import protocolo.mensagens.RespostaDeGeometriaDoMapa;
import protocolo.mensagens.RespostaDeValidacao;

/*
    Ponte do georreferenciamento com o servidor de algoritmos (GeometriaDeTalhao no serversocket).
    Monta os pedidos a partir dos Document e traduz as respostas:
      RespostaDeValidacao invalida -> 400 {"error":"Dados inválidos.","fields":{...}}
      RespostaDeErro               -> 503 {"error":"Servidor de algoritmos indisponivel","codigo":...}
                                      (DADOS_INCONSISTENTES -> 409)
      falha de comunicacao         -> 503
    Contrato gravado no talhao: localizacao = {tipo: "ponto"|"poligono", pontos: [{lat, lng}, ...]}.
    Na ocorrencia: localizacao = {lat, lng}.
*/
public class AlgoritmosDeGeo
{
    private static final String INDISPONIVEL = "Servidor de algoritmos indispon\u00edvel";

    public static final class ResultadoLocalizacao
    {
        public final http.Resposta respostaErro;
        public final Document      localizacao;   // pronto para gravar
        public final Double        areaHectares;  // so no poligono
        public final Document      centro;        // {lat, lng}

        private ResultadoLocalizacao (http.Resposta respostaErro, Document localizacao, Double areaHectares, Document centro)
        {
            this.respostaErro = respostaErro;
            this.localizacao  = localizacao;
            this.areaHectares = areaHectares;
            this.centro       = centro;
        }
    }

    public static final class ResultadoPonto
    {
        public final String   erro;   // mensagem do campo "localizacao" (ponto invalido ou fora do poligono)
        public final Document ponto;  // {lat, lng} arredondados

        private ResultadoPonto (String erro, Document ponto)
        {
            this.erro  = erro;
            this.ponto = ponto;
        }
    }

    public static final class ResultadoMapa
    {
        public final http.Resposta             respostaErro;
        public final RespostaDeGeometriaDoMapa geometria;

        private ResultadoMapa (http.Resposta respostaErro, RespostaDeGeometriaDoMapa geometria)
        {
            this.respostaErro = respostaErro;
            this.geometria    = geometria;
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private static Resposta chamar (protocolo.Pedido pedido) throws ErroHttp
    {
        Resposta ret;

        try
        {
            ret = ClienteDeAlgoritmos.chamar (pedido);
        }
        catch (Exception erro)
        {
            System.err.println ("Servidor de algoritmos inacessivel: " + erro);
            throw new ErroHttp (503, AlgoritmosDeGeo.INDISPONIVEL);
        }

        return ret;
    }

    // resposta HTTP para uma RespostaDeErro (ou para um tipo inesperado)
    private static http.Resposta falha (Resposta resposta)
    {
        http.Resposta ret;

        if (resposta instanceof RespostaDeErro)
        {
            RespostaDeErro erro   = (RespostaDeErro) resposta;
            int            status = RespostaDeErro.DADOS_INCONSISTENTES.equals (erro.getCodigo ()) ? 409 : 503;

            ret = http.Resposta.json (status, new Document ("error", AlgoritmosDeGeo.INDISPONIVEL)
                                                  .append ("codigo", erro.getCodigo ()));
        }
        else
            ret = http.Resposta.json (503, new Document ("error", "Resposta inesperada do servidor de algoritmos"));

        return ret;
    }

    private static http.Resposta campos (Map<String, String> erros)
    {
        Document fields = new Document ();

        for (Map.Entry<String, String> par : erros.entrySet ())
            fields.append (par.getKey (), par.getValue ());

        return http.Resposta.json (400, new Document ("error", "Dados inválidos.").append ("fields", fields));
    }

    private static http.Resposta campo (String nome, String mensagem)
    {
        return http.Resposta.json (400, new Document ("error", "Dados inválidos.")
                                            .append ("fields", new Document (nome, mensagem)));
    }

    private static Double numero (Object valor)
    {
        Double ret = null;

        if (valor instanceof Number)
            ret = Double.valueOf (((Number) valor).doubleValue ());

        return ret;
    }

    private static double[] centro (RespostaDeValidacao validacao)
    {
        double[] ret = new double[2];

        ret[0] = ((Double) validacao.getNormalizados ().get ("centroLat")).doubleValue ();
        ret[1] = ((Double) validacao.getNormalizados ().get ("centroLng")).doubleValue ();

        return ret;
    }

    // vertices gravados no talhao: [0] latitudes, [1] longitudes (listas vazias se o formato nao for o esperado)
    @SuppressWarnings ("unchecked")
    public static ArrayList<Double>[] vertices (Document localizacao)
    {
        ArrayList<Double>[] ret  = new ArrayList[2];
        Object              lista = localizacao == null ? null : localizacao.get ("pontos");

        ret[0] = new ArrayList<Double> ();
        ret[1] = new ArrayList<Double> ();

        if (lista instanceof List)
            for (Object item : (List<Object>) lista)
                if (item instanceof Document)
                {
                    ret[0].add (AlgoritmosDeGeo.numero (((Document) item).get ("lat")));
                    ret[1].add (AlgoritmosDeGeo.numero (((Document) item).get ("lng")));
                }

        return ret;
    }

    public static boolean ehPoligono (Document localizacao)
    {
        return localizacao != null && "poligono".equals (localizacao.get ("tipo"));
    }

    // ------------------------------------------------------------------ pedidos

    // corpo de PUT /api/talhoes/{id}/localizacao: {tipo, pontos: [{lat, lng}]}
    @SuppressWarnings ("unchecked")
    public static ResultadoLocalizacao validarLocalizacao (Document corpo) throws ErroHttp
    {
        Object tipo   = corpo.get ("tipo");
        Object pontos = corpo.get ("pontos");

        // tipos errados nao chegam ao servidor de algoritmos
        if (tipo != null && !(tipo instanceof String))
            return new ResultadoLocalizacao (AlgoritmosDeGeo.campo ("tipo", "Use ponto ou pol\u00edgono."), null, null, null);

        if (pontos != null && !(pontos instanceof List))
            return new ResultadoLocalizacao (AlgoritmosDeGeo.campo ("pontos", "Informe os pontos como uma lista de {lat, lng}."), null, null, null);

        ArrayList<Double> lats = null;
        ArrayList<Double> lngs = null;

        if (pontos != null)
        {
            List<Object> lista = (List<Object>) pontos;

            if (lista.size () > 200)
                return new ResultadoLocalizacao (AlgoritmosDeGeo.campo ("pontos", "Informe de 3 a 50 v\u00e9rtices."), null, null, null);

            lats = new ArrayList<Double> ();
            lngs = new ArrayList<Double> ();

            for (int i = 0; i < lista.size (); i++)
            {
                Object item = lista.get (i);
                Double lat  = item instanceof Document ? AlgoritmosDeGeo.numero (((Document) item).get ("lat")) : null;
                Double lng  = item instanceof Document ? AlgoritmosDeGeo.numero (((Document) item).get ("lng")) : null;

                if (lat == null || lng == null)
                    return new ResultadoLocalizacao (AlgoritmosDeGeo.campo ("pontos",
                        "Ponto " + (i + 1) + ": latitude e longitude precisam ser n\u00fameros."), null, null, null);

                lats.add (lat);
                lngs.add (lng);
            }
        }

        Resposta resposta = AlgoritmosDeGeo.chamar (new PedidoDeValidacaoDeLocalizacao ((String) tipo, lats, lngs));

        if (!(resposta instanceof RespostaDeValidacao))
            return new ResultadoLocalizacao (AlgoritmosDeGeo.falha (resposta), null, null, null);

        RespostaDeValidacao validacao = (RespostaDeValidacao) resposta;

        if (!validacao.isValido ())
            return new ResultadoLocalizacao (AlgoritmosDeGeo.campos (validacao.getErros ()), null, null, null);

        List<Double>        latitudes  = (List<Double>) validacao.getNormalizados ().get ("latitudes");
        List<Double>        longitudes = (List<Double>) validacao.getNormalizados ().get ("longitudes");
        ArrayList<Document> vertices   = new ArrayList<Document> ();
        double[]            centro     = AlgoritmosDeGeo.centro (validacao);

        for (int i = 0; i < latitudes.size (); i++)
            vertices.add (new Document ("lat", latitudes.get (i)).append ("lng", longitudes.get (i)));

        Document localizacao = new Document ("tipo", validacao.getNormalizados ().get ("tipo")).append ("pontos", vertices);

        return new ResultadoLocalizacao (null, localizacao, (Double) validacao.getNormalizados ().get ("areaHectares"),
                                         new Document ("lat", centro[0]).append ("lng", centro[1]));
    }

    /*
        O ponto {lat, lng} cai dentro do poligono do talhao? "ponto" e o valor cru do corpo (pode ter tipo errado).
        Falha do servidor de algoritmos lanca ErroHttp (503, ou 409 em DADOS_INCONSISTENTES).
    */
    public static ResultadoPonto pontoNoTalhao (Object ponto, Document localizacaoDoTalhao) throws ErroHttp
    {
        Double lat = ponto instanceof Document ? AlgoritmosDeGeo.numero (((Document) ponto).get ("lat")) : null;
        Double lng = ponto instanceof Document ? AlgoritmosDeGeo.numero (((Document) ponto).get ("lng")) : null;

        if (lat == null || lng == null)
            return new ResultadoPonto ("Informe a posi\u00e7\u00e3o com latitude e longitude num\u00e9ricas.", null);

        ArrayList<Double>[] vertices = AlgoritmosDeGeo.vertices (localizacaoDoTalhao);
        Resposta            resposta = AlgoritmosDeGeo.chamar (new PedidoDePontoNoTalhao (lat, lng, vertices[0], vertices[1]));

        if (resposta instanceof RespostaDeErro)
        {
            RespostaDeErro erro = (RespostaDeErro) resposta;
            throw new ErroHttp (RespostaDeErro.DADOS_INCONSISTENTES.equals (erro.getCodigo ()) ? 409 : 503,
                                AlgoritmosDeGeo.INDISPONIVEL + " (" + erro.getCodigo () + ")");
        }

        if (!(resposta instanceof RespostaDeValidacao))
            throw new ErroHttp (503, "Resposta inesperada do servidor de algoritmos");

        RespostaDeValidacao validacao = (RespostaDeValidacao) resposta;
        ResultadoPonto      ret;

        if (!validacao.isValido ())
            ret = new ResultadoPonto (validacao.getErros ().get ("localizacao"), null);
        else
            ret = new ResultadoPonto (null, new Document ("lat", validacao.getNormalizados ().get ("lat"))
                                                .append ("lng", validacao.getNormalizados ().get ("lng")));

        return ret;
    }

    // centro, area e limites dos talhoes (cada um com "localizacao" gravada)
    public static ResultadoMapa geometriaDoMapa (List<Document> talhoesComLocalizacao, List<String> ids) throws ErroHttp
    {
        ArrayList<GeometriaParaMapa> lista = new ArrayList<GeometriaParaMapa> ();

        for (int i = 0; i < talhoesComLocalizacao.size (); i++)
        {
            Document            localizacao = talhoesComLocalizacao.get (i).get ("localizacao", Document.class);
            ArrayList<Double>[] vertices    = AlgoritmosDeGeo.vertices (localizacao);

            lista.add (new GeometriaParaMapa (ids.get (i), localizacao.getString ("tipo"), vertices[0], vertices[1]));
        }

        Resposta      resposta = AlgoritmosDeGeo.chamar (new PedidoDeGeometriaDoMapa (lista));
        ResultadoMapa ret;

        if (resposta instanceof RespostaDeGeometriaDoMapa)
            ret = new ResultadoMapa (null, (RespostaDeGeometriaDoMapa) resposta);
        else
            ret = new ResultadoMapa (AlgoritmosDeGeo.falha (resposta), null);

        return ret;
    }
}
