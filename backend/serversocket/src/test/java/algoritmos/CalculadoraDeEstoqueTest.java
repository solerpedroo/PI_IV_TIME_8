package algoritmos;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import protocolo.RespostaDeErro;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class CalculadoraDeEstoqueTest
{
    private static PedidoDeMovimentacaoDeEstoque pedido (double saldo, double custo, String tipo, Double quantidade, Double compra, String obs)
    {
        return new PedidoDeMovimentacaoDeEstoque (saldo, custo, tipo, quantidade, compra, obs);
    }

    private static RespostaDeMovimentacaoDeEstoque movimentar (PedidoDeMovimentacaoDeEstoque pedido)
    {
        return resposta (pedido, CalculadoraDeEstoque.movimentar (pedido), RespostaDeMovimentacaoDeEstoque.class);
    }

    @ParameterizedTest(name = "saldo {0}, entrada {2}: saldo final {4}, custo médio {5}")
    @CsvSource({"10, 5, 10, 7, 20, 6", "0, 0, 4, 2.5, 4, 2.5", "2, 1, 1, 2, 3, 1.333333", "10, 5, 10, 0, 20, 2.5"})
    void entradaCalculaMediaPonderadaComSeisCasas (double saldo, double custo, double quantidade, double compra, double saldoEsperado, double custoEsperado)
    {
        var r = movimentar (pedido (saldo, custo, "entrada", quantidade, compra, null));
        assertAll (
            () -> assertTrue (r.isOk ()),
            () -> assertTrue (r.getErros ().isEmpty ()),
            () -> assertEquals (saldo, r.getSaldoAnterior ()),
            () -> assertEquals (saldoEsperado, r.getSaldoPosterior (), 0.0000001),
            () -> assertEquals (custoEsperado, r.getCustoUnitario (), 0.0000001));
    }

    @Test
    void entradaSemCustoPreservaMediaAtual ()
    {
        var r = movimentar (pedido (10, 5, "entrada", 2.5, null, null));
        assertTrue (r.isOk ());
        assertEquals (12.5, r.getSaldoPosterior ());
        assertEquals (5, r.getCustoUnitario ());
    }

    @ParameterizedTest
    @CsvSource({"4, 6", "10, 0"})
    void saidaBaixaSaldoSemRecalcularMedia (double quantidade, double saldoEsperado)
    {
        var r = movimentar (pedido (10, 5, "saida", quantidade, 99.0, null));
        assertTrue (r.isOk ());
        assertEquals (saldoEsperado, r.getSaldoPosterior ());
        assertEquals (5, r.getCustoUnitario ());
    }

    @Test
    void saidaUmMilionesimoAcimaDoSaldoERecusada ()
    {
        var r = movimentar (pedido (10, 5, "saida", 10.000001, null, null));
        assertFalse (r.isOk ());
        assertEquals (RespostaDeMovimentacaoDeEstoque.SALDO_INSUFICIENTE, r.getSituacao ());
        assertEquals ("A saída supera o saldo disponível.", r.getErros ().get ("quantidade"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 3, 1000000000})
    void ajusteSubstituiSaldoInclusivePorZero (double quantidade)
    {
        var r = movimentar (pedido (10, 5, "ajuste", quantidade, null, "Inventário"));
        assertTrue (r.isOk ());
        assertEquals (quantidade, r.getSaldoPosterior ());
        assertEquals (5, r.getCustoUnitario ());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void ajusteExigeJustificativa (String obs)
    {
        assertTrue (movimentar (pedido (10, 5, "ajuste", 3.0, null, obs)).getErros ().containsKey ("observacao"));
    }

    static Stream<Double> numerosInvalidos ()
    {
        return Stream.of (null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1000000000.000001, 0.1234567);
    }

    @ParameterizedTest
    @MethodSource("numerosInvalidos")
    void quantidadeInvalidaNaoGeraMovimentacao (Double quantidade)
    {
        var r = movimentar (pedido (10, 5, "entrada", quantidade, null, null));
        assertEquals (RespostaDeMovimentacaoDeEstoque.INVALIDO, r.getSituacao ());
        assertTrue (r.getErros ().containsKey ("quantidade"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1000000001, 0.1234567})
    void custoInformadoInvalidoNaoGeraMovimentacao (double compra)
    {
        var r = movimentar (pedido (10, 5, "entrada", 1.0, compra, null));
        assertEquals (RespostaDeMovimentacaoDeEstoque.INVALIDO, r.getSituacao ());
        assertTrue (r.getErros ().containsKey ("custoUnitario"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"entrada", "saida"})
    void entradaESaidaRecusamQuantidadeZero (String tipo)
    {
        assertTrue (movimentar (pedido (10, 5, tipo, 0.0, null, null)).getErros ().containsKey ("quantidade"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"doacao", "x1234567890"})
    void recusaTipoAusenteOuDesconhecido (String tipo)
    {
        var r = movimentar (pedido (10, 5, tipo, 1.0, null, null));
        assertTrue (r.getErros ().containsKey ("tipo"));
        assertFalse (r.isOk ());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void saldoOuCustoPersistidoInvalidoEErroDePedido (double valor)
    {
        var saldo = pedido (valor, 5, "entrada", 1.0, null, null);
        var custo = pedido (1, valor, "entrada", 1.0, null, null);
        erro (saldo, CalculadoraDeEstoque.movimentar (saldo), RespostaDeErro.PEDIDO_INVALIDO);
        erro (custo, CalculadoraDeEstoque.movimentar (custo), RespostaDeErro.PEDIDO_INVALIDO);
    }

    @ParameterizedTest
    @CsvSource({"0.1, entrada, 0.2, 0.3", "0.3, saida, 0.2, 0.1", "0, entrada, 0.000001, 0.000001"})
    void saldoArredondadoNaoAcumulaResiduoBinario (double saldo, String tipo, double quantidade, double esperado)
    {
        assertEquals (esperado, movimentar (pedido (saldo, 0, tipo, quantidade, null, null)).getSaldoPosterior ());
    }

    @Test
    void observacaoRespeitaLimiteDe240Caracteres ()
    {
        assertTrue (movimentar (pedido (10, 5, "ajuste", 0.0, null, "x".repeat (240))).isOk ());
        assertTrue (movimentar (pedido (10, 5, "entrada", 1.0, null, "x".repeat (241))).getErros ().containsKey ("observacao"));
    }
}
