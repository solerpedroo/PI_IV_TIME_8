/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;

/*
    O ponto (lat, lng) de uma ocorrencia cai dentro do poligono do talhao?
    latitudes e longitudes sao os vertices do poligono ja gravado (vertice i = latitudes.get (i), longitudes.get (i)).
    Resposta: RespostaDeValidacao, com erro no campo "localizacao" ou "lat"/"lng" normalizados.
*/
public class PedidoDePontoNoTalhao extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final Double            lat;
    private final Double            lng;
    private final ArrayList<Double> latitudes;
    private final ArrayList<Double> longitudes;

    public PedidoDePontoNoTalhao (Double lat, Double lng, List<Double> latitudes, List<Double> longitudes)
    {
        this.lat        = lat;
        this.lng        = lng;
        this.latitudes  = latitudes  == null ? null : new ArrayList<Double> (latitudes);
        this.longitudes = longitudes == null ? null : new ArrayList<Double> (longitudes);
    }

    public Double       getLat ()        { return this.lat; }
    public Double       getLng ()        { return this.lng; }
    public List<Double> getLatitudes ()  { return this.latitudes; }
    public List<Double> getLongitudes () { return this.longitudes; }
}
