package algoritmos;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import protocolo.RespostaDeErro;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class CalculadoraDeTalhoesTest
{
    @Test
    void somaAreasEContaApenasStatusAtivo ()
    {
        var pedido = new PedidoDeResumoDeTalhoes (List.of (new TalhaoParaResumo (12.5, "Ativo"), new TalhaoParaResumo (7.5, "Em preparo"), new TalhaoParaResumo (10, "Ativo")));
        var r = resposta (pedido, CalculadoraDeTalhoes.resumir (pedido), RespostaDeResumoDeTalhoes.class);
        assertAll (
            () -> assertEquals (30, r.getTotalHectares ()),
            () -> assertEquals (3, r.getQuantidadeTotal ()),
            () -> assertEquals (2, r.getQuantidadeAtivos ()));
    }

    @Test
    void listaVaziaRetornaTotaisZerados ()
    {
        var pedido = new PedidoDeResumoDeTalhoes (List.of ());
        var r = resposta (pedido, CalculadoraDeTalhoes.resumir (pedido), RespostaDeResumoDeTalhoes.class);
        assertEquals (0, r.getTotalHectares ());
        assertEquals (0, r.getQuantidadeTotal ());
        assertEquals (0, r.getQuantidadeAtivos ());
    }

    @Test
    void listaAusenteOuElementoNuloSaoPedidosInvalidos ()
    {
        var semLista = new PedidoDeResumoDeTalhoes (null);
        var semTalhao = new PedidoDeResumoDeTalhoes (Arrays.asList (new TalhaoParaResumo (1, "Ativo"), null));
        erro (semLista, CalculadoraDeTalhoes.resumir (semLista), RespostaDeErro.PEDIDO_INVALIDO);
        erro (semTalhao, CalculadoraDeTalhoes.resumir (semTalhao), RespostaDeErro.PEDIDO_INVALIDO);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void areaPersistidaInvalidaNaoContaminaResumo (double hectares)
    {
        var pedido = new PedidoDeResumoDeTalhoes (List.of (new TalhaoParaResumo (hectares, "Ativo")));
        erro (pedido, CalculadoraDeTalhoes.resumir (pedido), RespostaDeErro.DADOS_INCONSISTENTES);
    }
}
