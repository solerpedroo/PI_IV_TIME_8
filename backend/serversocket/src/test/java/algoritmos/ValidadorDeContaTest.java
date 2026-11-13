package algoritmos;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class ValidadorDeContaTest
{
    private static PedidoDeValidacaoDeCadastro cadastro (String nome, String email, String telefone,
        String senha, String confirmacao, String propriedade, String area, boolean termos)
    {
        return new PedidoDeValidacaoDeCadastro (nome, email, telefone, senha, confirmacao, propriedade, area, termos);
    }

    private static RespostaDeValidacao validar (PedidoDeValidacaoDeCadastro pedido)
    {
        return resposta (pedido, ValidadorDeConta.validarCadastro (pedido), RespostaDeValidacao.class);
    }

    @Test
    void normalizaCadastroSemDevolverSenhas ()
    {
        var r = validar (cadastro (" Ana Souza ", "ANA@Teste.com", " (11) 91234-5678 ", "Senha@123", "Senha@123", " Fazenda Sol ", " 120 ha ", true));
        assertAll (
            () -> assertTrue (r.isValido ()),
            () -> assertEquals ("Ana Souza", r.getNormalizados ().get ("name")),
            () -> assertEquals ("ana@teste.com", r.getNormalizados ().get ("email")),
            () -> assertEquals ("(11) 91234-5678", r.getNormalizados ().get ("phone")),
            () -> assertEquals ("Fazenda Sol", r.getNormalizados ().get ("farm")),
            () -> assertEquals ("120 ha", r.getNormalizados ().get ("area")),
            () -> assertEquals (Set.of ("name", "email", "phone", "farm", "area"), r.getNormalizados ().keySet ()));
    }

    @Test
    void cadastroVazioInformaTodosOsCamposObrigatorios ()
    {
        var r = validar (cadastro (null, null, null, null, null, null, null, false));
        assertEquals (Set.of ("name", "email", "phone", "password", "password-confirm", "farm", "area", "terms"), r.getErros ().keySet ());
        assertTrue (r.getNormalizados ().isEmpty ());
    }

    static Stream<Arguments> camposInvalidos ()
    {
        return Stream.of (
            Arguments.of ("name", "x".repeat (121), "a@b.co", "(11) 91234-5678", "F", "1", true),
            Arguments.of ("email", "Ana", "sem-arroba", "(11) 91234-5678", "F", "1", true),
            Arguments.of ("phone", "Ana", "a@b.co", "11 91234-5678", "F", "1", true),
            Arguments.of ("phone", "Ana", "a@b.co", "(11) 81234-5678", "F", "1", true),
            Arguments.of ("farm", "Ana", "a@b.co", "(11) 91234-5678", "x".repeat (121), "1", true),
            Arguments.of ("area", "Ana", "a@b.co", "(11) 91234-5678", "F", "x".repeat (61), true),
            Arguments.of ("terms", "Ana", "a@b.co", "(11) 91234-5678", "F", "1", false));
    }

    @ParameterizedTest(name = "campo inválido: {0} [{index}]")
    @MethodSource("camposInvalidos")
    void cadastroRecusaCampoIsolado (String campo, String nome, String email, String telefone, String propriedade, String area, boolean termos)
    {
        var r = validar (cadastro (nome, email, telefone, "Senha@123", "Senha@123", propriedade, area, termos));
        campoInvalido (r, campo);
        assertEquals (Set.of (campo), r.getErros ().keySet ());
    }

    @Test
    void aceitaCamposNoTamanhoMaximo ()
    {
        assertTrue (validar (cadastro ("x".repeat (120), "a".repeat (249) + "@b.co", "(11) 91234-5678", "Senha@123", "Senha@123", "x".repeat (120), "x".repeat (60), true)).isValido ());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Senha@124"})
    void senhaForteExigeConfirmacaoIgual (String confirmacao)
    {
        var pedido = new PedidoDeValidacaoDeSenha ("Senha@123", confirmacao);
        var r = resposta (pedido, ValidadorDeConta.validarSenha (pedido), RespostaDeValidacao.class);
        campoInvalido (r, "password-confirm");
        assertEquals (Set.of ("password-confirm"), r.getErros ().keySet ());
    }

    @Test
    void senhaFracaNaoAcumulaErroDeDivergencia ()
    {
        var pedido = new PedidoDeValidacaoDeSenha ("fraca", "outra");
        var r = resposta (pedido, ValidadorDeConta.validarSenha (pedido), RespostaDeValidacao.class);
        assertEquals (Set.of ("password"), r.getErros ().keySet ());
    }

    @Test
    void redefinicaoValidaNaoDevolveSenha ()
    {
        var pedido = new PedidoDeValidacaoDeSenha ("Senha 123", "Senha 123");
        var r = resposta (pedido, ValidadorDeConta.validarSenha (pedido), RespostaDeValidacao.class);
        assertTrue (r.isValido ());
        assertTrue (r.getNormalizados ().isEmpty ());
    }
}
