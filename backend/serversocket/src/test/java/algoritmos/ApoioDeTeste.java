package algoritmos;

import protocolo.Pedido;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.RespostaDeValidacao;
import static org.junit.jupiter.api.Assertions.*;

final class ApoioDeTeste
{
    private ApoioDeTeste () {}

    static <T extends Resposta> T resposta (Pedido pedido, Resposta resposta, Class<T> tipo)
    {
        T resultado = assertInstanceOf (tipo, resposta);
        assertEquals (pedido.getId (), resultado.getIdDoPedido (), "Correlação entre pedido e resposta");
        return resultado;
    }

    static void erro (Pedido pedido, Resposta resposta, String codigo)
    {
        assertEquals (codigo, resposta (pedido, resposta, RespostaDeErro.class).getCodigo ());
    }

    static void campoInvalido (RespostaDeValidacao resposta, String campo)
    {
        assertAll (
            () -> assertFalse (resposta.isValido ()),
            () -> assertNotNull (resposta.getErros ().get (campo), "Erro esperado no campo " + campo),
            () -> assertTrue (resposta.getNormalizados ().isEmpty (), "Não devolver dados parciais para gravação"));
    }
}
