package algoritmos;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class ValidadorDeTalhaoTest
{
    private static RespostaDeValidacao validar (String nome, String cultura, String safra, Double hectares, String status)
    {
        var pedido = new PedidoDeValidacaoDeTalhao (nome, cultura, safra, hectares, status);
        return resposta (pedido, ValidadorDeTalhao.validar (pedido), RespostaDeValidacao.class);
    }

    @Test
    void aplicaPadroesENormalizaNome ()
    {
        var r = validar (" Norte 1 ", " Soja ", null, 12.5, null);
        assertTrue (r.isValido ());
        assertAll (
            () -> assertEquals ("Norte 1", r.getNormalizados ().get ("nome")),
            () -> assertEquals ("norte 1", r.getNormalizados ().get ("nomeNormalizado")),
            () -> assertEquals ("Soja", r.getNormalizados ().get ("cultura")),
            () -> assertEquals ("2025/26", r.getNormalizados ().get ("safra")),
            () -> assertEquals ("Ativo", r.getNormalizados ().get ("status")),
            () -> assertEquals (12.5, r.getNormalizados ().get ("hectares")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1000000.000001})
    void recusaHectaresInvalidos (Double hectares)
    {
        campoInvalido (validar ("T", "Soja", null, hectares, null), "hectares");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.000001, 1, 1000000})
    void aceitaHectaresNosLimites (double hectares)
    {
        assertTrue (validar ("T", "Soja", null, hectares, null).isValido ());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Soja", "Milho", "Trigo", "Café", "Algodão"})
    void aceitaCulturasDoCadastro (String cultura)
    {
        assertTrue (validar ("T", cultura, null, 1.0, null).isValido ());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ativo", "Irrigação pendente", "Em preparo", ""})
    void aceitaStatusDoCadastro (String status)
    {
        assertTrue (validar ("T", "Soja", null, 1.0, status).isValido ());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024/25", "2025/26", "2026/27"})
    void aceitaSafrasDoCadastro (String safra)
    {
        assertTrue (validar ("T", "Soja", safra, 1.0, null).isValido ());
    }

    @Test
    void recusaTodosOsCamposInvalidosSemNormalizacaoParcial ()
    {
        var r = validar ("", "Banana", "1999/00", 0.0, "Quebrado");
        assertEquals (Set.of ("nome", "cultura", "safra", "hectares", "status"), r.getErros ().keySet ());
        assertTrue (r.getNormalizados ().isEmpty ());
    }

    @Test
    void distingueSafraAusenteDeSafraEmBranco ()
    {
        campoInvalido (validar ("T", "Soja", " ", 1.0, null), "safra");
    }

    @Test
    void nomeAceita80CaracteresERecusa81 ()
    {
        assertTrue (validar ("x".repeat (80), "Soja", null, 1.0, null).isValido ());
        campoInvalido (validar ("x".repeat (81), "Soja", null, 1.0, null), "nome");
    }
}
