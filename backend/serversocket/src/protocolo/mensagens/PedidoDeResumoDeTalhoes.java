package protocolo.mensagens;

import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;

// Soma a area e conta os talhoes (total e ativos) da lista que a API leu do banco.
public class PedidoDeResumoDeTalhoes extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final List<TalhaoParaResumo> talhoes;

    public PedidoDeResumoDeTalhoes (List<TalhaoParaResumo> talhoes)
    {
        this.talhoes = talhoes == null ? null : new ArrayList<TalhaoParaResumo> (talhoes);
    }

    public List<TalhaoParaResumo> getTalhoes ()
    {
        return this.talhoes;
    }
}
