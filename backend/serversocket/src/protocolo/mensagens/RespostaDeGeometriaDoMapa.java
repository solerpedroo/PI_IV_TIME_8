/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.util.LinkedHashMap;
import java.util.Map;
import protocolo.Resposta;

/*
    Para cada talhao (chave = talhaoId): centro (lat/lng) e, so no poligono, a area calculada em hectares.
    Os limites cobrem todos os vertices de todos os talhoes; ficam null quando a lista vem vazia.
*/
public class RespostaDeGeometriaDoMapa extends Resposta
{
    private static final long serialVersionUID = 1L;

    private final LinkedHashMap<String, Double> centroLat;
    private final LinkedHashMap<String, Double> centroLng;
    private final LinkedHashMap<String, Double> areaHectares;
    private final Double                        latMin;
    private final Double                        latMax;
    private final Double                        lngMin;
    private final Double                        lngMax;

    public RespostaDeGeometriaDoMapa (long idDoPedido, Map<String, Double> centroLat, Map<String, Double> centroLng,
                                      Map<String, Double> areaHectares, Double latMin, Double latMax,
                                      Double lngMin, Double lngMax)
    {
        super (idDoPedido);

        this.centroLat    = new LinkedHashMap<String, Double> (centroLat);
        this.centroLng    = new LinkedHashMap<String, Double> (centroLng);
        this.areaHectares = new LinkedHashMap<String, Double> (areaHectares);
        this.latMin       = latMin;
        this.latMax       = latMax;
        this.lngMin       = lngMin;
        this.lngMax       = lngMax;
    }

    public Map<String, Double> getCentroLat ()    { return this.centroLat; }
    public Map<String, Double> getCentroLng ()    { return this.centroLng; }
    public Map<String, Double> getAreaHectares () { return this.areaHectares; }
    public Double              getLatMin ()       { return this.latMin; }
    public Double              getLatMax ()       { return this.latMax; }
    public Double              getLngMin ()       { return this.lngMin; }
    public Double              getLngMax ()       { return this.lngMax; }
}
