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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
    Georreferenciamento (#231, #232): localizacao do talhao, area, centro, ponto no poligono e mapa.
    Chama o Atendente direto, sem socket (mesmo estilo de AlgoritmosTest).
*/
class GeometriaTest
{

    private static void verificar (String nome, boolean ok)
    {
        assertTrue (ok, nome);
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
        Resposta resposta = GeometriaTest.atender (pedido);

        if (!(resposta instanceof RespostaDeValidacao))
            throw new AssertionError ("esperava RespostaDeValidacao mas veio " + resposta);

        return (RespostaDeValidacao) resposta;
    }

    private static String codigo (Pedido pedido)
    {
        Resposta resposta = GeometriaTest.atender (pedido);
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

    @Test
    void ponto ()
    {
        RespostaDeValidacao ok = GeometriaTest.validacao (GeometriaTest.localizacao ("ponto",
            GeometriaTest.lista (-22.90561234), GeometriaTest.lista (-47.06084567)));
        List<?> lats = (List<?>) ok.getNormalizados ().get ("latitudes");
        List<?> lngs = (List<?>) ok.getNormalizados ().get ("longitudes");
        GeometriaTest.verificar ("ponto valido arredondado a 6 casas",
                                  ok.isValido () && lats.size () == 1 && Double.valueOf (-22.905612).equals (lats.get (0)) &&
                                  Double.valueOf (-47.060846).equals (lngs.get (0)));
        GeometriaTest.verificar ("ponto: area null e centro no proprio ponto",
                                  ok.getNormalizados ().get ("areaHectares") == null &&
                                  Double.valueOf (-22.905612).equals (ok.getNormalizados ().get ("centroLat")) &&
                                  Double.valueOf (-47.060846).equals (ok.getNormalizados ().get ("centroLng")));
        GeometriaTest.verificar ("ponto: as listas normalizadas sao ArrayList (serializaveis)",
                                  lats instanceof ArrayList && lngs instanceof ArrayList);

        RespostaDeValidacao lat91 = GeometriaTest.validacao (GeometriaTest.localizacao ("ponto", GeometriaTest.lista (91.0), GeometriaTest.lista (-47.0)));
        GeometriaTest.verificar ("latitude 91 recusada no campo pontos",
                                  !lat91.isValido () && GeometriaTest.erroEm (lat91, "pontos").contains ("Latitude"));

        RespostaDeValidacao lng181 = GeometriaTest.validacao (GeometriaTest.localizacao ("ponto", GeometriaTest.lista (-22.0), GeometriaTest.lista (-181.0)));
        GeometriaTest.verificar ("longitude -181 recusada no campo pontos",
                                  !lng181.isValido () && GeometriaTest.erroEm (lng181, "pontos").contains ("Longitude"));

        RespostaDeValidacao nan = GeometriaTest.validacao (GeometriaTest.localizacao ("ponto", GeometriaTest.lista (Double.NaN), GeometriaTest.lista (-47.0)));
        GeometriaTest.verificar ("latitude NaN recusada", !nan.isValido ());

        RespostaDeValidacao nulo = GeometriaTest.validacao (GeometriaTest.localizacao ("ponto", GeometriaTest.lista ((Double) null), GeometriaTest.lista (-47.0)));
        GeometriaTest.verificar ("latitude ausente recusada", !nulo.isValido () && GeometriaTest.erroEm (nulo, "pontos") != null);

        RespostaDeValidacao circulo = GeometriaTest.validacao (GeometriaTest.localizacao ("circulo", GeometriaTest.lista (-22.0), GeometriaTest.lista (-47.0)));
        GeometriaTest.verificar ("tipo circulo recusado no campo tipo", !circulo.isValido () && GeometriaTest.erroEm (circulo, "tipo") != null);

        RespostaDeValidacao semTipo = GeometriaTest.validacao (GeometriaTest.localizacao (null, GeometriaTest.lista (-22.0), GeometriaTest.lista (-47.0)));
        GeometriaTest.verificar ("tipo ausente recusado", GeometriaTest.erroEm (semTipo, "tipo") != null);

        RespostaDeValidacao doisPontos = GeometriaTest.validacao (GeometriaTest.localizacao ("ponto",
            GeometriaTest.lista (-22.0, -22.1), GeometriaTest.lista (-47.0, -47.1)));
        GeometriaTest.verificar ("ponto com dois pares recusado", GeometriaTest.erroEm (doisPontos, "pontos").contains ("exatamente um"));

        RespostaDeValidacao semPontos = GeometriaTest.validacao (GeometriaTest.localizacao ("ponto", null, null));
        GeometriaTest.verificar ("pontos ausentes recusados", GeometriaTest.erroEm (semPontos, "pontos") != null);

        RespostaDeValidacao tamanhos = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (-22.0, -22.1, -22.2), GeometriaTest.lista (-47.0, -47.1)));
        GeometriaTest.verificar ("latitudes e longitudes de tamanhos diferentes recusadas", GeometriaTest.erroEm (tamanhos, "pontos") != null);
    }

    @Test
    void poligono ()
    {
        RespostaDeValidacao dois = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (-22.0, -22.1), GeometriaTest.lista (-47.0, -47.1)));
        GeometriaTest.verificar ("poligono com 2 vertices recusado", GeometriaTest.erroEm (dois, "pontos").contains ("3 a 50"));

        ArrayList<Double> lats51 = new ArrayList<Double> ();
        ArrayList<Double> lngs51 = new ArrayList<Double> ();
        for (int i = 0; i < 51; i++)
        {
            double angulo = 2 * Math.PI * i / 51;
            lats51.add (Double.valueOf (-22.9 + 0.001 * Math.sin (angulo)));
            lngs51.add (Double.valueOf (-47.0 + 0.001 * Math.cos (angulo)));
        }
        RespostaDeValidacao cinquentaEUm = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono", lats51, lngs51));
        GeometriaTest.verificar ("poligono com 51 vertices recusado", GeometriaTest.erroEm (cinquentaEUm, "pontos").contains ("3 a 50"));

        lats51.remove (50);
        lngs51.remove (50);
        GeometriaTest.verificar ("poligono com 50 vertices aceito",
                                  GeometriaTest.validacao (GeometriaTest.localizacao ("poligono", lats51, lngs51)).isValido ());

        RespostaDeValidacao fechado = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (-22.905, -22.905, -22.9065, -22.905), GeometriaTest.lista (-47.061, -47.059, -47.059, -47.061)));
        GeometriaTest.verificar ("ultimo vertice igual ao primeiro e descartado",
                                  fechado.isValido () && ((List<?>) fechado.getNormalizados ().get ("latitudes")).size () == 3);

        RespostaDeValidacao laco = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (0.0, 0.01, 0.01, 0.0), GeometriaTest.lista (0.0, 0.01, 0.0, 0.01)));
        GeometriaTest.verificar ("poligono em laco (lados se cruzando) recusado",
                                  !laco.isValido () && GeometriaTest.erroEm (laco, "pontos").contains ("cruzar"));

        RespostaDeValidacao reta = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (0.0, 0.001, 0.002), GeometriaTest.lista (0.0, 0.001, 0.002)));
        GeometriaTest.verificar ("tres pontos na mesma reta (area zero) recusados",
                                  !reta.isValido () && GeometriaTest.erroEm (reta, "pontos").contains ("maior que zero"));

        RespostaDeValidacao espinho = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (0.0, 0.0, 0.01, 0.01, 0.01), GeometriaTest.lista (0.0, 0.01, 0.01, 0.02, 0.0)));
        GeometriaTest.verificar ("lado que volta sobre o vizinho recusado", !espinho.isValido ());

        RespostaDeValidacao repetido = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (0.0, 0.0, 0.01, 0.01), GeometriaTest.lista (0.0, 0.0, 0.01, 0.0)));
        GeometriaTest.verificar ("dois vertices seguidos iguais recusados", GeometriaTest.erroEm (repetido, "pontos").contains ("seguidos"));

        // quadrado de 0,01 x 0,01 grau no equador: 1113,2 m x 1105,4 m = ~123,05 ha (esperado ~123,6, tolerancia 1%)
        RespostaDeValidacao equador = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (0.0, 0.0, 0.01, 0.01), GeometriaTest.lista (0.0, 0.01, 0.01, 0.0)));
        GeometriaTest.verificar ("quadrado de 0,01 grau no equador ~123,6 ha (+-1%)",
                                  equador.isValido () && GeometriaTest.perto (equador.getNormalizados ().get ("areaHectares"), 123.6, 1.236));
        GeometriaTest.verificar ("centro do quadrado e o meio (0,005; 0,005)",
                                  GeometriaTest.perto (equador.getNormalizados ().get ("centroLat"), 0.005, 0.000001) &&
                                  GeometriaTest.perto (equador.getNormalizados ().get ("centroLng"), 0.005, 0.000001));

        // ~100 m x ~100 m perto de Campinas (-22,9): 100 m = 0,000905 grau de latitude e 0,000975 de longitude
        double dLat = 100 / 110540.0;
        double dLng = 100 / (111320.0 * Math.cos (Math.toRadians (-22.9)));
        RespostaDeValidacao hectare = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (-22.9, -22.9, -22.9 + dLat, -22.9 + dLat), GeometriaTest.lista (-47.06, -47.06 + dLng, -47.06 + dLng, -47.06)));
        GeometriaTest.verificar ("quadrado de ~100 m perto de Campinas ~1 ha (+-2%)",
                                  hectare.isValido () && GeometriaTest.perto (hectare.getNormalizados ().get ("areaHectares"), 1, 0.02));

        RespostaDeValidacao horario = GeometriaTest.validacao (GeometriaTest.localizacao ("poligono",
            GeometriaTest.lista (0.0, 0.01, 0.01, 0.0), GeometriaTest.lista (0.0, 0.0, 0.01, 0.01)));
        GeometriaTest.verificar ("sentido horario ou anti-horario da a mesma area",
                                  horario.getNormalizados ().get ("areaHectares").equals (equador.getNormalizados ().get ("areaHectares")));
    }

    @Test
    void pontoNoTalhao ()
    {
        ArrayList<Double> lats = GeometriaTest.lista (-22.905, -22.905, -22.9065, -22.9065);
        ArrayList<Double> lngs = GeometriaTest.lista (-47.061, -47.059, -47.059, -47.061);

        RespostaDeValidacao dentro = GeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.9057123456, -47.0600987654, lats, lngs));
        GeometriaTest.verificar ("ponto dentro do poligono aceito e arredondado",
                                  dentro.isValido () && Double.valueOf (-22.905712).equals (dentro.getNormalizados ().get ("lat")) &&
                                  Double.valueOf (-47.060099).equals (dentro.getNormalizados ().get ("lng")));

        RespostaDeValidacao fora = GeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.9, -47.06, lats, lngs));
        GeometriaTest.verificar ("ponto fora do poligono recusado no campo localizacao",
                                  !fora.isValido () && GeometriaTest.erroEm (fora, "localizacao").contains ("fora"));

        // decisao documentada em GeometriaDeTalhao: a borda conta como dentro
        RespostaDeValidacao borda = GeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.905, -47.060, lats, lngs));
        GeometriaTest.verificar ("ponto na borda conta como dentro do talhao", borda.isValido ());
        GeometriaTest.verificar ("vertice do poligono conta como dentro",
                                  GeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.9065, -47.061, lats, lngs)).isValido ());

        RespostaDeValidacao invalido = GeometriaTest.validacao (new PedidoDePontoNoTalhao (95.0, -47.06, lats, lngs));
        GeometriaTest.verificar ("ponto com latitude 95 recusado", GeometriaTest.erroEm (invalido, "localizacao") != null);

        RespostaDeValidacao semLat = GeometriaTest.validacao (new PedidoDePontoNoTalhao (null, -47.06, lats, lngs));
        GeometriaTest.verificar ("ponto sem latitude recusado", GeometriaTest.erroEm (semLat, "localizacao") != null);

        // poligono concavo em "L": o canto vazio fica fora
        ArrayList<Double> latsL = GeometriaTest.lista (0.0, 0.0, 0.01, 0.01, 0.02, 0.02);
        ArrayList<Double> lngsL = GeometriaTest.lista (0.0, 0.02, 0.02, 0.01, 0.01, 0.0);
        GeometriaTest.verificar ("poligono concavo: ponto no canto vazio fica fora",
                                  !GeometriaTest.validacao (new PedidoDePontoNoTalhao (0.015, 0.015, latsL, lngsL)).isValido () &&
                                  GeometriaTest.validacao (new PedidoDePontoNoTalhao (0.015, 0.005, latsL, lngsL)).isValido ());

        GeometriaTest.verificar ("poligono do talhao ausente e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (GeometriaTest.codigo (new PedidoDePontoNoTalhao (-22.9, -47.06, null, null))));
    }

    @Test
    void mapa ()
    {
        ArrayList<GeometriaParaMapa> talhoes = new ArrayList<GeometriaParaMapa> ();
        talhoes.add (new GeometriaParaMapa ("norte", "poligono", GeometriaTest.lista (0.0, 0.0, 0.01, 0.01), GeometriaTest.lista (0.0, 0.01, 0.01, 0.0)));
        talhoes.add (new GeometriaParaMapa ("sede", "ponto", GeometriaTest.lista (-0.005), GeometriaTest.lista (0.02)));

        Resposta resposta = GeometriaTest.atender (new PedidoDeGeometriaDoMapa (talhoes));
        GeometriaTest.verificar ("mapa com 2 talhoes devolve RespostaDeGeometriaDoMapa", resposta instanceof RespostaDeGeometriaDoMapa);

        RespostaDeGeometriaDoMapa geo = (RespostaDeGeometriaDoMapa) resposta;
        GeometriaTest.verificar ("mapa: centro do poligono e do ponto",
                                  GeometriaTest.perto (geo.getCentroLat ().get ("norte"), 0.005, 0.000001) &&
                                  GeometriaTest.perto (geo.getCentroLng ().get ("norte"), 0.005, 0.000001) &&
                                  GeometriaTest.perto (geo.getCentroLat ().get ("sede"), -0.005, 0) &&
                                  GeometriaTest.perto (geo.getCentroLng ().get ("sede"), 0.02, 0));
        GeometriaTest.verificar ("mapa: area so do poligono",
                                  GeometriaTest.perto (geo.getAreaHectares ().get ("norte"), 123.05, 0.1) &&
                                  !geo.getAreaHectares ().containsKey ("sede"));
        GeometriaTest.verificar ("mapa: limites cobrem todos os vertices",
                                  GeometriaTest.perto (geo.getLatMin (), -0.005, 0) && GeometriaTest.perto (geo.getLatMax (), 0.01, 0) &&
                                  GeometriaTest.perto (geo.getLngMin (), 0.0, 0) && GeometriaTest.perto (geo.getLngMax (), 0.02, 0));

        RespostaDeGeometriaDoMapa vazio = (RespostaDeGeometriaDoMapa) GeometriaTest.atender (new PedidoDeGeometriaDoMapa (new ArrayList<GeometriaParaMapa> ()));
        GeometriaTest.verificar ("mapa vazio: limites null e nada inventado",
                                  vazio.getLatMin () == null && vazio.getLatMax () == null && vazio.getLngMin () == null &&
                                  vazio.getLngMax () == null && vazio.getCentroLat ().isEmpty ());

        GeometriaTest.verificar ("lista null e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (GeometriaTest.codigo (new PedidoDeGeometriaDoMapa (null))));

        ArrayList<GeometriaParaMapa> quebrado = new ArrayList<GeometriaParaMapa> ();
        quebrado.add (new GeometriaParaMapa ("x", "poligono", GeometriaTest.lista (0.0, 0.0), GeometriaTest.lista (0.0, 0.01)));
        GeometriaTest.verificar ("talhao com poligono incompleto e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (GeometriaTest.codigo (new PedidoDeGeometriaDoMapa (quebrado))));
    }
}
