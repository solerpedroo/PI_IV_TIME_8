import algoritmos.conta.ValidadorDeConta;
import algoritmos.estoque.CalculadoraDeEstoque;
import algoritmos.geo.GeometriaDeTalhao;
import algoritmos.manutencao.AgendaDeManutencao;
import algoritmos.manutencao.ValidadorDeManutencao;
import algoritmos.safra.ConsolidadorDeSafra;
import algoritmos.talhao.CalculadoraDeTalhoes;
import algoritmos.talhao.ValidadorDeTalhao;
import protocolo.Pedido;
import protocolo.Protocolo;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.estoque.PedidoDeMovimentacaoDeEstoque;
import protocolo.mensagens.geo.PedidoDeGeometriaDoMapa;
import protocolo.mensagens.geo.PedidoDePontoNoTalhao;
import protocolo.mensagens.geo.PedidoDeValidacaoDeLocalizacao;
import protocolo.mensagens.ping.PedidoDePing;
import protocolo.mensagens.safra.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.talhao.PedidoDeResumoDeTalhoes;
import protocolo.mensagens.conta.PedidoDeValidacaoDeCadastro;
import protocolo.mensagens.conta.PedidoDeValidacaoDeSenha;
import protocolo.mensagens.talhao.PedidoDeValidacaoDeTalhao;
import protocolo.mensagens.manutencao.PedidoDeAgendaDeManutencao;
import protocolo.mensagens.manutencao.PedidoDeProximaPreventiva;
import protocolo.mensagens.manutencao.PedidoDeValidacaoDeEquipamento;
import protocolo.mensagens.manutencao.PedidoDeValidacaoDeIntervencao;
import protocolo.mensagens.manutencao.PedidoDeValidacaoDePreventiva;
import protocolo.mensagens.ping.RespostaDePing;

/*
    Recebe um Pedido e devolve a Resposta, chamando o algoritmo certo.
    Sem estado e sem entrada e saida: varias threads usam ao mesmo tempo sem trava, e os testes chamam
    direto, sem socket. Para incluir um algoritmo novo: classe na pasta da funcionalidade
    (algoritmos/conta, talhao, estoque, safra, manutencao ou geo), par Pedido/Resposta em
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

            if (pedido instanceof PedidoDeValidacaoDeLocalizacao)
                return GeometriaDeTalhao.validarLocalizacao ((PedidoDeValidacaoDeLocalizacao) pedido);

            if (pedido instanceof PedidoDePontoNoTalhao)
                return GeometriaDeTalhao.pontoNoTalhao ((PedidoDePontoNoTalhao) pedido);

            if (pedido instanceof PedidoDeGeometriaDoMapa)
                return GeometriaDeTalhao.geometriaDoMapa ((PedidoDeGeometriaDoMapa) pedido);

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
