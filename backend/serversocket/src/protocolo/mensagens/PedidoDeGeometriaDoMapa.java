/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;

// Centro, area e limites dos talhoes com localizacao que a API leu do banco (para desenhar o mapa).
public class PedidoDeGeometriaDoMapa extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final ArrayList<GeometriaParaMapa> talhoes;

    public PedidoDeGeometriaDoMapa (List<GeometriaParaMapa> talhoes)
    {
        this.talhoes = talhoes == null ? null : new ArrayList<GeometriaParaMapa> (talhoes);
    }

    public List<GeometriaParaMapa> getTalhoes ()
    {
        return this.talhoes;
    }
}
