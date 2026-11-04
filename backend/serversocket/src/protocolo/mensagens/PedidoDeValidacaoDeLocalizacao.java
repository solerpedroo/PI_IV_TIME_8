/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;

/*
    Valida a localizacao simplificada de um talhao: um ponto ou um poligono leve.
    latitudes.get (i) e longitudes.get (i) formam o vertice i. Sem satelite, sem GIS.
*/
public class PedidoDeValidacaoDeLocalizacao extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String            tipo;        // "ponto" ou "poligono"
    private final ArrayList<Double> latitudes;
    private final ArrayList<Double> longitudes;

    public PedidoDeValidacaoDeLocalizacao (String tipo, List<Double> latitudes, List<Double> longitudes)
    {
        this.tipo       = tipo;
        this.latitudes  = latitudes  == null ? null : new ArrayList<Double> (latitudes);
        this.longitudes = longitudes == null ? null : new ArrayList<Double> (longitudes);
    }

    public String       getTipo ()       { return this.tipo; }
    public List<Double> getLatitudes ()  { return this.latitudes; }
    public List<Double> getLongitudes () { return this.longitudes; }
}
