import algoritmos.CalculadoraDeEstoque;
import algoritmos.CalculadoraDeTalhoes;
import algoritmos.ConsolidadorDeSafra;
import algoritmos.ValidadorDeConta;
import algoritmos.ValidadorDeTalhao;
import algoritmos.AgendaDeManutencao;
import algoritmos.ValidadorDeManutencao;
import protocolo.Pedido;
import protocolo.Protocolo;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeMovimentacaoDeEstoque;
import protocolo.mensagens.PedidoDePing;
import protocolo.mensagens.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.PedidoDeResumoDeTalhoes;
import protocolo.mensagens.PedidoDeValidacaoDeCadastro;
import protocolo.mensagens.PedidoDeValidacaoDeSenha;
import protocolo.mensagens.PedidoDeValidacaoDeTalhao;
import protocolo.mensagens.PedidoDeAgendaDeManutencao;
import protocolo.mensagens.PedidoDeProximaPreventiva;
import protocolo.mensagens.PedidoDeValidacaoDeEquipamento;
import protocolo.mensagens.PedidoDeValidacaoDeIntervencao;
import protocolo.mensagens.PedidoDeValidacaoDePreventiva;
import protocolo.mensagens.RespostaDePing;

/*
    Recebe um Pedido e devolve a Resposta, chamando o algoritmo certo.
    Sem estado e sem entrada e saida: varias threads usam ao mesmo tempo sem trava, e os testes chamam
    direto, sem socket. Para incluir um algoritmo novo: classe em algoritmos, par Pedido/Resposta em
    protocolo.mensagens e um "if" aqui.
*/
public class Atendente
{
    // nunca lanca excecao: o que nao der certo vira RespostaDeErro
    public static Resposta atender (Pedido pedido)
    {
        try
        {
            if (pedido instanceof PedidoDePing)
                return new RespostaDePing (pedido.getId (), Protocolo.VERSAO);

            if (pedido instanceof PedidoDeValidacaoDeCadastro)
                return ValidadorDeConta.validarCadastro ((PedidoDeValidacaoDeCadastro) pedido);

            if (pedido instanceof PedidoDeValidacaoDeSenha)
                return ValidadorDeConta.validarSenha ((PedidoDeValidacaoDeSenha) pedido);

            if (pedido instanceof PedidoDeValidacaoDeTalhao)
                return ValidadorDeTalhao.validar ((PedidoDeValidacaoDeTalhao) pedido);

            if (pedido instanceof PedidoDeResumoDeTalhoes)
                return CalculadoraDeTalhoes.resumir ((PedidoDeResumoDeTalhoes) pedido);

            if (pedido instanceof PedidoDeMovimentacaoDeEstoque)
                return CalculadoraDeEstoque.movimentar ((PedidoDeMovimentacaoDeEstoque) pedido);

            if (pedido instanceof PedidoDeRelatorioDeSafra)
                return ConsolidadorDeSafra.consolidar ((PedidoDeRelatorioDeSafra) pedido);

            if (pedido instanceof PedidoDeValidacaoDeEquipamento)
                return ValidadorDeManutencao.validarEquipamento ((PedidoDeValidacaoDeEquipamento) pedido);

            if (pedido instanceof PedidoDeValidacaoDePreventiva)
                return ValidadorDeManutencao.validarPreventiva ((PedidoDeValidacaoDePreventiva) pedido);

            if (pedido instanceof PedidoDeValidacaoDeIntervencao)
                return ValidadorDeManutencao.validarIntervencao ((PedidoDeValidacaoDeIntervencao) pedido);

            if (pedido instanceof PedidoDeAgendaDeManutencao)
                return AgendaDeManutencao.agenda ((PedidoDeAgendaDeManutencao) pedido);

            if (pedido instanceof PedidoDeProximaPreventiva)
                return AgendaDeManutencao.proxima ((PedidoDeProximaPreventiva) pedido);

            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_DESCONHECIDO,
                                       "Pedido desconhecido: " + pedido.getClass ().getSimpleName ());
        }
        catch (Exception erro)
        {
            // o detalhe fica so no console do servidor; quem chamou recebe uma mensagem generica
            System.err.println ("Erro ao atender " + pedido + ": " + erro);

            return new RespostaDeErro (pedido.getId (), RespostaDeErro.ERRO_INTERNO, "Erro interno ao processar o pedido.");
        }
    }
}
