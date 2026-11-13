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
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

/*
    Georreferenciamento (#231, #232): localizacao do talhao, area, centro, ponto no poligono e mapa.
    Contratos entre as mensagens de geometria e o Atendente, sem socket.
*/
@Tag("contrato")
class AtendenteGeometriaTest
{

    private static void verificar (String nome, boolean ok)
    {
        assertTrue (ok, nome);
    }

    private static Resposta atender (Pedido pedido)
    {
        Resposta ret = Atendente.atender (pedido);

        assertEquals (pedido.getId (), ret.getIdDoPedido ());

        return ret;
    }

    private static RespostaDeValidacao validacao (Pedido pedido)
    {
        Resposta resposta = AtendenteGeometriaTest.atender (pedido);

        return assertInstanceOf (RespostaDeValidacao.class, resposta);
    }

    private static String codigo (Pedido pedido)
    {
        Resposta resposta = AtendenteGeometriaTest.atender (pedido);
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
        RespostaDeValidacao ok = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("ponto",
            AtendenteGeometriaTest.lista (-22.90561234), AtendenteGeometriaTest.lista (-47.06084567)));
        List<?> lats = (List<?>) ok.getNormalizados ().get ("latitudes");
        List<?> lngs = (List<?>) ok.getNormalizados ().get ("longitudes");
        AtendenteGeometriaTest.verificar ("ponto valido arredondado a 6 casas",
                                  ok.isValido () && lats.size () == 1 && Double.valueOf (-22.905612).equals (lats.get (0)) &&
                                  Double.valueOf (-47.060846).equals (lngs.get (0)));
        AtendenteGeometriaTest.verificar ("ponto: area null e centro no proprio ponto",
                                  ok.getNormalizados ().get ("areaHectares") == null &&
                                  Double.valueOf (-22.905612).equals (ok.getNormalizados ().get ("centroLat")) &&
                                  Double.valueOf (-47.060846).equals (ok.getNormalizados ().get ("centroLng")));
        AtendenteGeometriaTest.verificar ("ponto: as listas normalizadas sao ArrayList (serializaveis)",
                                  lats instanceof ArrayList && lngs instanceof ArrayList);

        RespostaDeValidacao lat91 = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("ponto", AtendenteGeometriaTest.lista (91.0), AtendenteGeometriaTest.lista (-47.0)));
        AtendenteGeometriaTest.verificar ("latitude 91 recusada no campo pontos",
                                  !lat91.isValido () && AtendenteGeometriaTest.erroEm (lat91, "pontos").contains ("Latitude"));

        RespostaDeValidacao lng181 = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("ponto", AtendenteGeometriaTest.lista (-22.0), AtendenteGeometriaTest.lista (-181.0)));
        AtendenteGeometriaTest.verificar ("longitude -181 recusada no campo pontos",
                                  !lng181.isValido () && AtendenteGeometriaTest.erroEm (lng181, "pontos").contains ("Longitude"));

        RespostaDeValidacao nan = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("ponto", AtendenteGeometriaTest.lista (Double.NaN), AtendenteGeometriaTest.lista (-47.0)));
        AtendenteGeometriaTest.verificar ("latitude NaN recusada", !nan.isValido ());

        RespostaDeValidacao nulo = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("ponto", AtendenteGeometriaTest.lista ((Double) null), AtendenteGeometriaTest.lista (-47.0)));
        AtendenteGeometriaTest.verificar ("latitude ausente recusada", !nulo.isValido () && AtendenteGeometriaTest.erroEm (nulo, "pontos") != null);

        RespostaDeValidacao circulo = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("circulo", AtendenteGeometriaTest.lista (-22.0), AtendenteGeometriaTest.lista (-47.0)));
        AtendenteGeometriaTest.verificar ("tipo circulo recusado no campo tipo", !circulo.isValido () && AtendenteGeometriaTest.erroEm (circulo, "tipo") != null);

        RespostaDeValidacao semTipo = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao (null, AtendenteGeometriaTest.lista (-22.0), AtendenteGeometriaTest.lista (-47.0)));
        AtendenteGeometriaTest.verificar ("tipo ausente recusado", AtendenteGeometriaTest.erroEm (semTipo, "tipo") != null);

        RespostaDeValidacao doisPontos = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("ponto",
            AtendenteGeometriaTest.lista (-22.0, -22.1), AtendenteGeometriaTest.lista (-47.0, -47.1)));
        AtendenteGeometriaTest.verificar ("ponto com dois pares recusado", AtendenteGeometriaTest.erroEm (doisPontos, "pontos").contains ("exatamente um"));

        RespostaDeValidacao semPontos = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("ponto", null, null));
        AtendenteGeometriaTest.verificar ("pontos ausentes recusados", AtendenteGeometriaTest.erroEm (semPontos, "pontos") != null);

        RespostaDeValidacao tamanhos = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (-22.0, -22.1, -22.2), AtendenteGeometriaTest.lista (-47.0, -47.1)));
        AtendenteGeometriaTest.verificar ("latitudes e longitudes de tamanhos diferentes recusadas", AtendenteGeometriaTest.erroEm (tamanhos, "pontos") != null);
    }

    @Test
    void poligono ()
    {
        RespostaDeValidacao dois = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (-22.0, -22.1), AtendenteGeometriaTest.lista (-47.0, -47.1)));
        AtendenteGeometriaTest.verificar ("poligono com 2 vertices recusado", AtendenteGeometriaTest.erroEm (dois, "pontos").contains ("3 a 50"));

        ArrayList<Double> lats51 = new ArrayList<Double> ();
        ArrayList<Double> lngs51 = new ArrayList<Double> ();
        for (int i = 0; i < 51; i++)
        {
            double angulo = 2 * Math.PI * i / 51;
            lats51.add (Double.valueOf (-22.9 + 0.001 * Math.sin (angulo)));
            lngs51.add (Double.valueOf (-47.0 + 0.001 * Math.cos (angulo)));
        }
        RespostaDeValidacao cinquentaEUm = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono", lats51, lngs51));
        AtendenteGeometriaTest.verificar ("poligono com 51 vertices recusado", AtendenteGeometriaTest.erroEm (cinquentaEUm, "pontos").contains ("3 a 50"));

        lats51.remove (50);
        lngs51.remove (50);
        AtendenteGeometriaTest.verificar ("poligono com 50 vertices aceito",
                                  AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono", lats51, lngs51)).isValido ());

        RespostaDeValidacao fechado = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (-22.905, -22.905, -22.9065, -22.905), AtendenteGeometriaTest.lista (-47.061, -47.059, -47.059, -47.061)));
        AtendenteGeometriaTest.verificar ("ultimo vertice igual ao primeiro e descartado",
                                  fechado.isValido () && ((List<?>) fechado.getNormalizados ().get ("latitudes")).size () == 3);

        RespostaDeValidacao laco = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (0.0, 0.01, 0.01, 0.0), AtendenteGeometriaTest.lista (0.0, 0.01, 0.0, 0.01)));
        AtendenteGeometriaTest.verificar ("poligono em laco (lados se cruzando) recusado",
                                  !laco.isValido () && AtendenteGeometriaTest.erroEm (laco, "pontos").contains ("cruzar"));

        RespostaDeValidacao reta = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (0.0, 0.001, 0.002), AtendenteGeometriaTest.lista (0.0, 0.001, 0.002)));
        AtendenteGeometriaTest.verificar ("tres pontos na mesma reta (area zero) recusados",
                                  !reta.isValido () && AtendenteGeometriaTest.erroEm (reta, "pontos").contains ("maior que zero"));

        RespostaDeValidacao espinho = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (0.0, 0.0, 0.01, 0.01, 0.01), AtendenteGeometriaTest.lista (0.0, 0.01, 0.01, 0.02, 0.0)));
        AtendenteGeometriaTest.verificar ("lado que volta sobre o vizinho recusado", !espinho.isValido ());

        RespostaDeValidacao repetido = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (0.0, 0.0, 0.01, 0.01), AtendenteGeometriaTest.lista (0.0, 0.0, 0.01, 0.0)));
        AtendenteGeometriaTest.verificar ("dois vertices seguidos iguais recusados", AtendenteGeometriaTest.erroEm (repetido, "pontos").contains ("seguidos"));

        // quadrado de 0,01 x 0,01 grau no equador: 1113,2 m x 1105,4 m = ~123,05 ha (esperado ~123,6, tolerancia 1%)
        RespostaDeValidacao equador = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (0.0, 0.0, 0.01, 0.01), AtendenteGeometriaTest.lista (0.0, 0.01, 0.01, 0.0)));
        AtendenteGeometriaTest.verificar ("quadrado de 0,01 grau no equador ~123,6 ha (+-1%)",
                                  equador.isValido () && AtendenteGeometriaTest.perto (equador.getNormalizados ().get ("areaHectares"), 123.6, 1.236));
        AtendenteGeometriaTest.verificar ("centro do quadrado e o meio (0,005; 0,005)",
                                  AtendenteGeometriaTest.perto (equador.getNormalizados ().get ("centroLat"), 0.005, 0.000001) &&
                                  AtendenteGeometriaTest.perto (equador.getNormalizados ().get ("centroLng"), 0.005, 0.000001));

        // ~100 m x ~100 m perto de Campinas (-22,9): 100 m = 0,000905 grau de latitude e 0,000975 de longitude
        double dLat = 100 / 110540.0;
        double dLng = 100 / (111320.0 * Math.cos (Math.toRadians (-22.9)));
        RespostaDeValidacao hectare = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (-22.9, -22.9, -22.9 + dLat, -22.9 + dLat), AtendenteGeometriaTest.lista (-47.06, -47.06 + dLng, -47.06 + dLng, -47.06)));
        AtendenteGeometriaTest.verificar ("quadrado de ~100 m perto de Campinas ~1 ha (+-2%)",
                                  hectare.isValido () && AtendenteGeometriaTest.perto (hectare.getNormalizados ().get ("areaHectares"), 1, 0.02));

        RespostaDeValidacao horario = AtendenteGeometriaTest.validacao (AtendenteGeometriaTest.localizacao ("poligono",
            AtendenteGeometriaTest.lista (0.0, 0.01, 0.01, 0.0), AtendenteGeometriaTest.lista (0.0, 0.0, 0.01, 0.01)));
        AtendenteGeometriaTest.verificar ("sentido horario ou anti-horario da a mesma area",
                                  horario.getNormalizados ().get ("areaHectares").equals (equador.getNormalizados ().get ("areaHectares")));
    }

    @Test
    void pontoNoTalhao ()
    {
        ArrayList<Double> lats = AtendenteGeometriaTest.lista (-22.905, -22.905, -22.9065, -22.9065);
        ArrayList<Double> lngs = AtendenteGeometriaTest.lista (-47.061, -47.059, -47.059, -47.061);

        RespostaDeValidacao dentro = AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.9057123456, -47.0600987654, lats, lngs));
        AtendenteGeometriaTest.verificar ("ponto dentro do poligono aceito e arredondado",
                                  dentro.isValido () && Double.valueOf (-22.905712).equals (dentro.getNormalizados ().get ("lat")) &&
                                  Double.valueOf (-47.060099).equals (dentro.getNormalizados ().get ("lng")));

        RespostaDeValidacao fora = AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.9, -47.06, lats, lngs));
        AtendenteGeometriaTest.verificar ("ponto fora do poligono recusado no campo localizacao",
                                  !fora.isValido () && AtendenteGeometriaTest.erroEm (fora, "localizacao").contains ("fora"));

        // decisao documentada em GeometriaDeTalhao: a borda conta como dentro
        RespostaDeValidacao borda = AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.905, -47.060, lats, lngs));
        AtendenteGeometriaTest.verificar ("ponto na borda conta como dentro do talhao", borda.isValido ());
        AtendenteGeometriaTest.verificar ("vertice do poligono conta como dentro",
                                  AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (-22.9065, -47.061, lats, lngs)).isValido ());

        RespostaDeValidacao invalido = AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (95.0, -47.06, lats, lngs));
        AtendenteGeometriaTest.verificar ("ponto com latitude 95 recusado", AtendenteGeometriaTest.erroEm (invalido, "localizacao") != null);

        RespostaDeValidacao semLat = AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (null, -47.06, lats, lngs));
        AtendenteGeometriaTest.verificar ("ponto sem latitude recusado", AtendenteGeometriaTest.erroEm (semLat, "localizacao") != null);

        // poligono concavo em "L": o canto vazio fica fora
        ArrayList<Double> latsL = AtendenteGeometriaTest.lista (0.0, 0.0, 0.01, 0.01, 0.02, 0.02);
        ArrayList<Double> lngsL = AtendenteGeometriaTest.lista (0.0, 0.02, 0.02, 0.01, 0.01, 0.0);
        AtendenteGeometriaTest.verificar ("poligono concavo: ponto no canto vazio fica fora",
                                  !AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (0.015, 0.015, latsL, lngsL)).isValido () &&
                                  AtendenteGeometriaTest.validacao (new PedidoDePontoNoTalhao (0.015, 0.005, latsL, lngsL)).isValido ());

        AtendenteGeometriaTest.verificar ("poligono do talhao ausente e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteGeometriaTest.codigo (new PedidoDePontoNoTalhao (-22.9, -47.06, null, null))));
    }

    @Test
    void mapa ()
    {
        ArrayList<GeometriaParaMapa> talhoes = new ArrayList<GeometriaParaMapa> ();
        talhoes.add (new GeometriaParaMapa ("norte", "poligono", AtendenteGeometriaTest.lista (0.0, 0.0, 0.01, 0.01), AtendenteGeometriaTest.lista (0.0, 0.01, 0.01, 0.0)));
        talhoes.add (new GeometriaParaMapa ("sede", "ponto", AtendenteGeometriaTest.lista (-0.005), AtendenteGeometriaTest.lista (0.02)));

        Resposta resposta = AtendenteGeometriaTest.atender (new PedidoDeGeometriaDoMapa (talhoes));
        AtendenteGeometriaTest.verificar ("mapa com 2 talhoes devolve RespostaDeGeometriaDoMapa", resposta instanceof RespostaDeGeometriaDoMapa);

        RespostaDeGeometriaDoMapa geo = (RespostaDeGeometriaDoMapa) resposta;
        AtendenteGeometriaTest.verificar ("mapa: centro do poligono e do ponto",
                                  AtendenteGeometriaTest.perto (geo.getCentroLat ().get ("norte"), 0.005, 0.000001) &&
                                  AtendenteGeometriaTest.perto (geo.getCentroLng ().get ("norte"), 0.005, 0.000001) &&
                                  AtendenteGeometriaTest.perto (geo.getCentroLat ().get ("sede"), -0.005, 0) &&
                                  AtendenteGeometriaTest.perto (geo.getCentroLng ().get ("sede"), 0.02, 0));
        AtendenteGeometriaTest.verificar ("mapa: area so do poligono",
                                  AtendenteGeometriaTest.perto (geo.getAreaHectares ().get ("norte"), 123.05, 0.1) &&
                                  !geo.getAreaHectares ().containsKey ("sede"));
        AtendenteGeometriaTest.verificar ("mapa: limites cobrem todos os vertices",
                                  AtendenteGeometriaTest.perto (geo.getLatMin (), -0.005, 0) && AtendenteGeometriaTest.perto (geo.getLatMax (), 0.01, 0) &&
                                  AtendenteGeometriaTest.perto (geo.getLngMin (), 0.0, 0) && AtendenteGeometriaTest.perto (geo.getLngMax (), 0.02, 0));

        RespostaDeGeometriaDoMapa vazio = (RespostaDeGeometriaDoMapa) AtendenteGeometriaTest.atender (new PedidoDeGeometriaDoMapa (new ArrayList<GeometriaParaMapa> ()));
        AtendenteGeometriaTest.verificar ("mapa vazio: limites null e nada inventado",
                                  vazio.getLatMin () == null && vazio.getLatMax () == null && vazio.getLngMin () == null &&
                                  vazio.getLngMax () == null && vazio.getCentroLat ().isEmpty ());

        AtendenteGeometriaTest.verificar ("lista null e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteGeometriaTest.codigo (new PedidoDeGeometriaDoMapa (null))));

        ArrayList<GeometriaParaMapa> quebrado = new ArrayList<GeometriaParaMapa> ();
        quebrado.add (new GeometriaParaMapa ("x", "poligono", AtendenteGeometriaTest.lista (0.0, 0.0), AtendenteGeometriaTest.lista (0.0, 0.01)));
        AtendenteGeometriaTest.verificar ("talhao com poligono incompleto e PEDIDO_INVALIDO",
                                  RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteGeometriaTest.codigo (new PedidoDeGeometriaDoMapa (quebrado))));
    }
}
