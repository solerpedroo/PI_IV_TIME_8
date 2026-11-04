/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// So o que o mapa precisa de um talhao com localizacao: o id, o tipo e os vertices ja validados.
public class GeometriaParaMapa implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String            talhaoId;
    private final String            tipo;        // "ponto" ou "poligono"
    private final ArrayList<Double> latitudes;
    private final ArrayList<Double> longitudes;

    public GeometriaParaMapa (String talhaoId, String tipo, List<Double> latitudes, List<Double> longitudes)
    {
        this.talhaoId   = talhaoId;
        this.tipo       = tipo;
        this.latitudes  = latitudes  == null ? null : new ArrayList<Double> (latitudes);
        this.longitudes = longitudes == null ? null : new ArrayList<Double> (longitudes);
    }

    public String       getTalhaoId ()   { return this.talhaoId; }
    public String       getTipo ()       { return this.tipo; }
    public List<Double> getLatitudes ()  { return this.latitudes; }
    public List<Double> getLongitudes () { return this.longitudes; }
}
