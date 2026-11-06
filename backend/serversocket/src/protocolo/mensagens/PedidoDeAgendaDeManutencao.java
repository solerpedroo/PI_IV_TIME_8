/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;

/*
    Situacao de cada preventiva programada em relacao a hoje: atrasada, proxima (dentro de diasDeAviso, padrao 7) ou futura.
*/
public class PedidoDeAgendaDeManutencao extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final ArrayList<PreventivaParaAgenda> preventivas;
    private final String                          hoje;
    private final Integer                         diasDeAviso;

    public PedidoDeAgendaDeManutencao (List<PreventivaParaAgenda> preventivas, String hoje, Integer diasDeAviso)
    {
        this.preventivas = preventivas == null ? null : new ArrayList<PreventivaParaAgenda> (preventivas);
        this.hoje        = hoje;
        this.diasDeAviso = diasDeAviso;
    }

    public List<PreventivaParaAgenda> getPreventivas () { return this.preventivas; }
    public String                     getHoje () { return this.hoje; }
    public Integer                    getDiasDeAviso () { return this.diasDeAviso; }
}
