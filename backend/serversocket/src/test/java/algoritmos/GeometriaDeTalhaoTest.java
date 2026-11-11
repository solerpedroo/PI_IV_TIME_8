package algoritmos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class GeometriaDeTalhaoTest
{
    private static final List<Double> LATS = List.of (0.0, 0.0, 0.01, 0.01);
    private static final List<Double> LNGS = List.of (0.0, 0.01, 0.01, 0.0);

    private static RespostaDeValidacao localizacao (String tipo, List<Double> lats, List<Double> lngs)
    {
        var p = new PedidoDeValidacaoDeLocalizacao (tipo, lats, lngs);
        return resposta (p, GeometriaDeTalhao.validarLocalizacao (p), RespostaDeValidacao.class);
    }

    @Test
    void quadradoNoEquadorTemAreaECentroConhecidos ()
    {
        // 0,01 grau: 1113,2 m na longitude e 1105,4 m na latitude.
        assertEquals (1230531.28, GeometriaDeTalhao.area (LATS, LNGS), 0.01);
        assertArrayEquals (new double[] {0.005, 0.005}, GeometriaDeTalhao.centro (LATS, LNGS), 0.000001);
        assertEquals (123.05, localizacao ("poligono", LATS, LNGS).getNormalizados ().get ("areaHectares"));
    }

    @Test
    void inverterSentidoDosVerticesPreservaAreaECentro ()
    {
        var lats = new ArrayList<> (LATS);
        var lngs = new ArrayList<> (LNGS);
        Collections.reverse (lats);
        Collections.reverse (lngs);
        assertEquals (GeometriaDeTalhao.area (LATS, LNGS), GeometriaDeTalhao.area (lats, lngs), 0.1);
        assertArrayEquals (GeometriaDeTalhao.centro (LATS, LNGS), GeometriaDeTalhao.centro (lats, lngs), 0.000001);
    }

    @ParameterizedTest
    @CsvSource({"0.005, 0.005, true", "0, 0.005, true", "0, 0, true", "0.02, 0.005, false", "0.005, -0.001, false"})
    void interiorBordaEVerticeContamComoDentro (double lat, double lng, boolean dentro)
    {
        var p = new PedidoDePontoNoTalhao (lat, lng, LATS, LNGS);
        var r = resposta (p, GeometriaDeTalhao.pontoNoTalhao (p), RespostaDeValidacao.class);
        assertEquals (dentro, r.isValido ());
        if (dentro)
        {
            assertEquals (lat, r.getNormalizados ().get ("lat"));
            assertEquals (lng, r.getNormalizados ().get ("lng"));
        }
        else
            campoInvalido (r, "localizacao");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -90.000001, 90.000001})
    void latitudeInvalidaRecusaLocalizacao (double latitude)
    {
        campoInvalido (localizacao ("ponto", List.of (latitude), List.of (0.0)), "pontos");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -180.000001, 180.000001})
    void longitudeInvalidaRecusaLocalizacao (double longitude)
    {
        campoInvalido (localizacao ("ponto", List.of (0.0), List.of (longitude)), "pontos");
    }

    @ParameterizedTest
    @CsvSource({"-90, -180", "90, 180"})
    void pontoAceitaLimitesGeograficos (double latitude, double longitude)
    {
        var r = localizacao ("ponto", List.of (latitude), List.of (longitude));
        assertTrue (r.isValido ());
        assertNull (r.getNormalizados ().get ("areaHectares"));
    }

    @Test
    void arredondamentoNaoPodeCriarLadoDeComprimentoZero ()
    {
        var r = localizacao ("poligono", List.of (0.0, 0.0000001, 0.01, 0.01), List.of (0.0, 0.0000001, 0.01, 0.0));
        campoInvalido (r, "pontos");
        assertTrue (r.getErros ().get ("pontos").contains ("seguidos"));
    }

    @Test
    void fechamentoExplicitoEDescartadoSemAlterarListaDeEntrada ()
    {
        var lats = new ArrayList<> (LATS);
        var lngs = new ArrayList<> (LNGS);
        lats.add (lats.get (0));
        lngs.add (lngs.get (0));
        var r = localizacao ("poligono", lats, lngs);
        assertTrue (r.isValido ());
        assertEquals (LATS, r.getNormalizados ().get ("latitudes"));
        assertEquals (5, lats.size ());
        assertEquals (5, lngs.size ());
    }
}
