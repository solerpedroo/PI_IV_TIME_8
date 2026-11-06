/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.GeometriaParaMapa;
import protocolo.mensagens.PedidoDeGeometriaDoMapa;
import protocolo.mensagens.PedidoDePontoNoTalhao;
import protocolo.mensagens.PedidoDeValidacaoDeLocalizacao;
import protocolo.mensagens.RespostaDeGeometriaDoMapa;
import protocolo.mensagens.RespostaDeValidacao;

/*
    Georreferenciamento (#231, #232): localizacao do talhao, area, centro, ponto no poligono e mapa.
    Chama o Atendente direto, sem socket (mesmo estilo de TesteAlgoritmos).
*/
public class TesteGeometria
{
    private static int total;

    private static void verificar (String nome, boolean ok)
    {
        if (!ok)
            throw new AssertionError (nome);

        TesteGeometria.total++;
        System.out.println ("ok " + nome);
    }

    private static Resposta atender (Pedido pedido)
    {
        Resposta ret = Atendente.atender (pedido);

        if (ret.getIdDoPedido () != pedido.getId ())
            throw new AssertionError ("a resposta de " + pedido + " veio com o id " + ret.getIdDoPedido ());

        return ret;
    }

    private static RespostaDeValidacao validacao (Pedido pedido)
    {
        Resposta resposta = TesteGeometria.atender (pedido);

        if (!(resposta instanceof RespostaDeValidacao))
            throw new AssertionError ("esperava RespostaDeValidacao mas veio " + resposta);

        return (RespostaDeValidacao) resposta;
    }

    private static String codigo (Pedido pedido)
    {
        Resposta resposta = TesteGeometria.atender (pedido);
        String   ret      = null;

        if (resposta instanceof RespostaDeErro)
            ret = ((RespostaDeErro) resposta).getCodigo ();

        return ret;
    }

    // lista de Double a partir de valores soltos (null permitido)
    private static ArrayList<Double> lista (Double... valores)
    {
        ArrayList<Double> ret = new ArrayList<Double> ();

        for (int i = 0; i < valores.length; i++)
            ret.add (valores[i]);

        return ret;
    }

    private static PedidoDeValidacaoDeLocalizacao localizacao (String tipo, List<Double> lats, List<Double> lngs)
    {
        return new PedidoDeValidacaoDeLocalizacao (tipo, lats, lngs);
    }

    private static boolean perto (Object valor, double esperado, double tolerancia)
    {
        return valor instanceof Double && Math.abs (((Double) valor).doubleValue () - esperado) <= tolerancia;
    }

    private static String erroEm (RespostaDeValidacao resposta, String campo)
    {
        return resposta.getErros ().get (campo);
    }

    private static void ponto ()
    {
        RespostaDeValidacao ok = TesteGeometria.validacao (TesteGeometria.localizacao ("ponto",
            TesteGeometria.lista (-22.90561234), TesteGeometria.lista (-47.06084567)));
        List<?> lats = (List<?>) ok.getNormalizados ().get ("latitudes");
        List<?> lngs = (List<?>) ok.getNormalizados ().get ("longitudes");
        TesteGeometria.verificar ("ponto valido arredondado a 6 casas",
                                  ok.isValido () && lats.size () == 1 && Double.valueOf (-22.905612).equals (lats.get (0)) &&
                                  Double.valueOf (-47.060846).equals (lngs.get (0)));
        TesteGeometria.verificar ("ponto: area null e centro no proprio ponto",
                                  ok.getNormalizados ().get ("areaHectares") == null &&
                                  Double.valueOf (-22.905612).equals (ok.getNormalizados ().get ("centroLat")) &&
                                  Double.valueOf (-47.060846).equals (ok.getNormalizados ().get ("centroLng")));
        TesteGeometria.verificar ("ponto: as listas normalizadas sao ArrayList (serializaveis)",
                                  lats instanceof ArrayList && lngs instanceof ArrayList);

        RespostaDeValidacao lat91 = TesteGeometria.validacao (TesteGeometria.localizacao ("ponto", TesteGeometria.lista (91.0), TesteGeometria.lista (-47.0)));
        TesteGeometria.verificar ("latitude 91 recusada no campo pontos",
                                  !lat91.isValido () && TesteGeometria.erroEm (lat91, "pontos").contains ("Latitude"));

        RespostaDeValidacao lng181 = TesteGeometria.validacao (TesteGeometria.localizacao ("ponto", TesteGeometria.lista (-22.0), TesteGeometria.lista (-181.0)));
        TesteGeometria.verificar ("longitude -181 recusada no campo pontos",
                                  !lng181.isValido () && TesteGeometria.erroEm (lng181, "pontos").contains ("Longitude"));

        RespostaDeValidacao nan = TesteGeometria.validacao (TesteGeometria.localizacao ("ponto", TesteGeometria.lista (Double.NaN), TesteGeometria.lista (-47.0)));
        TesteGeometria.verificar ("latitude NaN recusada", !nan.isValido ());

        RespostaDeValidacao nulo = TesteGeometria.validacao (TesteGeometria.localizacao ("ponto", TesteGeometria.lista ((Double) null), TesteGeometria.lista (-47.0)));
        TesteGeometria.verificar ("latitude ausente recusada", !nulo.isValido () && TesteGeometria.erroEm (nulo, "pontos") != null);

        RespostaDeValidacao circulo = TesteGeometria.validacao (TesteGeometria.localizacao ("circulo", TesteGeometria.lista (-22.0), TesteGeometria.lista (-47.0)));
        TesteGeometria.verificar ("tipo circulo recusado no campo tipo", !circulo.isValido () && TesteGeometria.erroEm (circulo, "tipo") != null);

        RespostaDeValidacao semTipo = TesteGeometria.validacao (TesteGeometria.localizacao (null, TesteGeometria.lista (-22.0), TesteGeometria.lista (-47.0)));
        TesteGeometria.verificar ("tipo ausente recusado", TesteGeometria.erroEm (semTipo, "tipo") != null);

        RespostaDeValidacao doisPontos = TesteGeometria.validacao (TesteGeometria.localizacao ("ponto",
            TesteGeometria.lista (-22.0, -22.1), TesteGeometria.lista (-47.0, -47.1)));
        TesteGeometria.verificar ("ponto com dois pares recusado", TesteGeometria.erroEm (doisPontos, "pontos").contains ("exatamente um"));

        RespostaDeValidacao semPontos = TesteGeometria.validacao (TesteGeometria.localizacao ("ponto", null, null));
        TesteGeometria.verificar ("pontos ausentes recusados", TesteGeometria.erroEm (semPontos, "pontos") != null);

        RespostaDeValidacao tamanhos = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (-22.0, -22.1, -22.2), TesteGeometria.lista (-47.0, -47.1)));
        TesteGeometria.verificar ("latitudes e longitudes de tamanhos diferentes recusadas", TesteGeometria.erroEm (tamanhos, "pontos") != null);
    }

    private static void poligono ()
    {
        RespostaDeValidacao dois = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (-22.0, -22.1), TesteGeometria.lista (-47.0, -47.1)));
        TesteGeometria.verificar ("poligono com 2 vertices recusado", TesteGeometria.erroEm (dois, "pontos").contains ("3 a 50"));

        ArrayList<Double> lats51 = new ArrayList<Double> ();
        ArrayList<Double> lngs51 = new ArrayList<Double> ();
        for (int i = 0; i < 51; i++)
        {
            double angulo = 2 * Math.PI * i / 51;
            lats51.add (Double.valueOf (-22.9 + 0.001 * Math.sin (angulo)));
            lngs51.add (Double.valueOf (-47.0 + 0.001 * Math.cos (angulo)));
        }
        RespostaDeValidacao cinquentaEUm = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono", lats51, lngs51));
        TesteGeometria.verificar ("poligono com 51 vertices recusado", TesteGeometria.erroEm (cinquentaEUm, "pontos").contains ("3 a 50"));

        lats51.remove (50);
        lngs51.remove (50);
        TesteGeometria.verificar ("poligono com 50 vertices aceito",
                                  TesteGeometria.validacao (TesteGeometria.localizacao ("poligono", lats51, lngs51)).isValido ());

        RespostaDeValidacao fechado = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (-22.905, -22.905, -22.9065, -22.905), TesteGeometria.lista (-47.061, -47.059, -47.059, -47.061)));
        TesteGeometria.verificar ("ultimo vertice igual ao primeiro e descartado",
                                  fechado.isValido () && ((List<?>) fechado.getNormalizados ().get ("latitudes")).size () == 3);

        RespostaDeValidacao laco = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (0.0, 0.01, 0.01, 0.0), TesteGeometria.lista (0.0, 0.01, 0.0, 0.01)));
        TesteGeometria.verificar ("poligono em laco (lados se cruzando) recusado",
                                  !laco.isValido () && TesteGeometria.erroEm (laco, "pontos").contains ("cruzar"));

        RespostaDeValidacao reta = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (0.0, 0.001, 0.002), TesteGeometria.lista (0.0, 0.001, 0.002)));
        TesteGeometria.verificar ("tres pontos na mesma reta (area zero) recusados",
                                  !reta.isValido () && TesteGeometria.erroEm (reta, "pontos").contains ("maior que zero"));

        RespostaDeValidacao espinho = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (0.0, 0.0, 0.01, 0.01, 0.01), TesteGeometria.lista (0.0, 0.01, 0.01, 0.02, 0.0)));
        TesteGeometria.verificar ("lado que volta sobre o vizinho recusado", !espinho.isValido ());

        RespostaDeValidacao repetido = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (0.0, 0.0, 0.01, 0.01), TesteGeometria.lista (0.0, 0.0, 0.01, 0.0)));
        TesteGeometria.verificar ("dois vertices seguidos iguais recusados", TesteGeometria.erroEm (repetido, "pontos").contains ("seguidos"));

        // quadrado de 0,01 x 0,01 grau no equador: 1113,2 m x 1105,4 m = ~123,05 ha (esperado ~123,6, tolerancia 1%)
        RespostaDeValidacao equador = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (0.0, 0.0, 0.01, 0.01), TesteGeometria.lista (0.0, 0.01, 0.01, 0.0)));
        TesteGeometria.verificar ("quadrado de 0,01 grau no equador ~123,6 ha (+-1%)",
                                  equador.isValido () && TesteGeometria.perto (equador.getNormalizados ().get ("areaHectares"), 123.6, 1.236));
        TesteGeometria.verificar ("centro do quadrado e o meio (0,005; 0,005)",
                                  TesteGeometria.perto (equador.getNormalizados ().get ("centroLat"), 0.005, 0.000001) &&
                                  TesteGeometria.perto (equador.getNormalizados ().get ("centroLng"), 0.005, 0.000001));

        // ~100 m x ~100 m perto de Campinas (-22,9): 100 m = 0,000905 grau de latitude e 0,000975 de longitude
        double dLat = 100 / 110540.0;
        double dLng = 100 / (111320.0 * Math.cos (Math.toRadians (-22.9)));
        RespostaDeValidacao hectare = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (-22.9, -22.9, -22.9 + dLat, -22.9 + dLat), TesteGeometria.lista (-47.06, -47.06 + dLng, -47.06 + dLng, -47.06)));
        TesteGeometria.verificar ("quadrado de ~100 m perto de Campinas ~1 ha (+-2%)",
                                  hectare.isValido () && TesteGeometria.perto (hectare.getNormalizados ().get ("areaHectares"), 1, 0.02));

        RespostaDeValidacao horario = TesteGeometria.validacao (TesteGeometria.localizacao ("poligono",
            TesteGeometria.lista (0.0, 0.01, 0.01, 0.0), TesteGeometria.lista (0.0, 0.0, 0.01, 0.01)));
        TesteGeometria.verificar ("sentido horario ou anti-horario da a mesma area",
                                  horario.getNormalizados ().get ("areaHectares").equals (equador.getNormalizados ().get ("areaHectares")));
    }

    private static void pontoNoTalhao ()
    {
        ArrayList<Double> lats = TesteGeometria.lista (-22.905, -22.905, -22.9065, -22.9065);
        ArrayList<Double> lngs = TesteGeometria.lista (-47.061, -47.059, -47.059, -47.061);

        RespostaDeValidacao dentro = TesteGeometria.validacao (new PedidoDePontoNoTalhao (-22.9057123456, -47.0600987654, lats, lngs));
        TesteGeometria.verificar ("ponto dentro do poligono aceito e arredondado",
                                  dentro.isValido () && Double.valueOf (-22.905712).equals (dentro.getNormalizados ().get ("lat")) &&
                                  Double.valueOf (-47.060099).equals (dentro.getNormalizados ().get ("lng")));

        RespostaDeValidacao fora = TesteGeometria.validacao (new PedidoDePontoNoTalhao (-22.9, -47.06, lats, lngs));
        TesteGeometria.verificar ("ponto fora do poligono recusado no campo localizacao",
                                  !fora.isValido () && TesteGeometria.erroEm (fora, "localizacao").contains ("fora"));

        // decisao documentada em GeometriaDeTalhao: a borda conta como dentro
        RespostaDeValidacao borda = TesteGeometria.validacao (new PedidoDePontoNoTalhao (-22.905, -47.060, lats, lngs));
        TesteGeometria.verificar ("ponto na borda conta como dentro do talhao", borda.isValido ());
        TesteGeometria.verificar ("vertice do poligono conta como dentro",
                                  TesteGeometria.validacao (new PedidoDePontoNoTalhao (-22.9065, -47.061, lats, lngs)).isValido ());

        RespostaDeValidacao invalido = TesteGeometria.validacao (new PedidoDePontoNoTalhao (95.0, -47.06, lats, lngs));
        TesteGeometria.verificar ("ponto com latitude 95 recusado", TesteGeometria.erroEm (invalido, "localizacao") != null);

        RespostaDeValidacao semLat = TesteGeometria.validacao (new PedidoDePontoNoTalhao (null, -47.06, lats, lngs));
        TesteGeometria.verificar ("ponto sem latitude recusado", TesteGeometria.erroEm (semLat, "localizacao") != null);

        // poligono concavo em "L": o canto vazio fica fora
        ArrayList<Double> latsL = TesteGeometria.lista (0.0, 0.0, 0.01, 0.01, 0.02, 0.02);
        ArrayList<Double> lngsL = TesteGeometria.lista (0.0, 0.02, 0.02, 0.01, 0.01, 0.0);
        TesteGeometria.verificar ("poligono concavo: ponto no canto vazio fica fora",
                                  !TesteGeometria.validacao (new PedidoDePontoNoTalhao (0.015, 0.015, latsL, lngsL)).isValido () &&
                                  TesteGeometria.validacao (new PedidoDePontoNoTalhao (0.015, 0.005, latsL, lngsL)).isValido ());

        TesteGeometria.verificar ("poligono do talhao ausente e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (TesteGeometria.codigo (new PedidoDePontoNoTalhao (-22.9, -47.06, null, null))));
    }

    private static void mapa ()
    {
        ArrayList<GeometriaParaMapa> talhoes = new ArrayList<GeometriaParaMapa> ();
        talhoes.add (new GeometriaParaMapa ("norte", "poligono", TesteGeometria.lista (0.0, 0.0, 0.01, 0.01), TesteGeometria.lista (0.0, 0.01, 0.01, 0.0)));
        talhoes.add (new GeometriaParaMapa ("sede", "ponto", TesteGeometria.lista (-0.005), TesteGeometria.lista (0.02)));

        Resposta resposta = TesteGeometria.atender (new PedidoDeGeometriaDoMapa (talhoes));
        TesteGeometria.verificar ("mapa com 2 talhoes devolve RespostaDeGeometriaDoMapa", resposta instanceof RespostaDeGeometriaDoMapa);

        RespostaDeGeometriaDoMapa geo = (RespostaDeGeometriaDoMapa) resposta;
        TesteGeometria.verificar ("mapa: centro do poligono e do ponto",
                                  TesteGeometria.perto (geo.getCentroLat ().get ("norte"), 0.005, 0.000001) &&
                                  TesteGeometria.perto (geo.getCentroLng ().get ("norte"), 0.005, 0.000001) &&
                                  TesteGeometria.perto (geo.getCentroLat ().get ("sede"), -0.005, 0) &&
                                  TesteGeometria.perto (geo.getCentroLng ().get ("sede"), 0.02, 0));
        TesteGeometria.verificar ("mapa: area so do poligono",
                                  TesteGeometria.perto (geo.getAreaHectares ().get ("norte"), 123.05, 0.1) &&
                                  !geo.getAreaHectares ().containsKey ("sede"));
        TesteGeometria.verificar ("mapa: limites cobrem todos os vertices",
                                  TesteGeometria.perto (geo.getLatMin (), -0.005, 0) && TesteGeometria.perto (geo.getLatMax (), 0.01, 0) &&
                                  TesteGeometria.perto (geo.getLngMin (), 0.0, 0) && TesteGeometria.perto (geo.getLngMax (), 0.02, 0));

        RespostaDeGeometriaDoMapa vazio = (RespostaDeGeometriaDoMapa) TesteGeometria.atender (new PedidoDeGeometriaDoMapa (new ArrayList<GeometriaParaMapa> ()));
        TesteGeometria.verificar ("mapa vazio: limites null e nada inventado",
                                  vazio.getLatMin () == null && vazio.getLatMax () == null && vazio.getLngMin () == null &&
                                  vazio.getLngMax () == null && vazio.getCentroLat ().isEmpty ());

        TesteGeometria.verificar ("lista null e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (TesteGeometria.codigo (new PedidoDeGeometriaDoMapa (null))));

        ArrayList<GeometriaParaMapa> quebrado = new ArrayList<GeometriaParaMapa> ();
        quebrado.add (new GeometriaParaMapa ("x", "poligono", TesteGeometria.lista (0.0, 0.0), TesteGeometria.lista (0.0, 0.01)));
        TesteGeometria.verificar ("talhao com poligono incompleto e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (TesteGeometria.codigo (new PedidoDeGeometriaDoMapa (quebrado))));
    }

    public static void main (String[] args) throws Exception
    {
        TesteGeometria.ponto ();
        TesteGeometria.poligono ();
        TesteGeometria.pontoNoTalhao ();
        TesteGeometria.mapa ();

        System.out.println ("Geometria: " + TesteGeometria.total + " verifica\u00e7\u00f5es aprovadas.");
    }
}
