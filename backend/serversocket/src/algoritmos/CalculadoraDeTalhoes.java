package algoritmos;

import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeResumoDeTalhoes;
import protocolo.mensagens.RespostaDeResumoDeTalhoes;
import protocolo.mensagens.TalhaoParaResumo;

/*
    Soma area e conta talhoes que a API ja leu. Nao valida nome nem cultura:
    isso e do ValidadorDeTalhao, na gravacao. Lista nula ou item nulo e pedido invalido,
    nao erro de campo. Area negativa ou NaN e dado inconsistente vindo do banco.
*/
public class CalculadoraDeTalhoes
{
    // total de hectares, quantidade de talhoes e quantidade de ativos (status "Ativo")
    public static Resposta resumir (PedidoDeResumoDeTalhoes pedido)
    {
        if (pedido.getTalhoes () == null)
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Lista de talhões ausente.");

        double hectares = 0;
        int    total    = 0;
        int    ativos   = 0;

        for (TalhaoParaResumo talhao : pedido.getTalhoes ())
        {
            if (talhao == null)
                return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Talhão ausente na lista.");

            if (!Double.isFinite (talhao.getHectares ()) || talhao.getHectares () < 0)
                return new RespostaDeErro (pedido.getId (), RespostaDeErro.DADOS_INCONSISTENTES, "Área inválida no talhão.");

            hectares += talhao.getHectares ();
            total++;

            if (ValidadorDeTalhao.STATUS_PADRAO.equals (talhao.getStatus ()))
                ativos++;
        }

        return new RespostaDeResumoDeTalhoes (pedido.getId (), hectares, total, ativos);
    }
}
