package algoritmos;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import protocolo.RespostaDeErro;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class AgendaDeManutencaoTest
{
    private static PreventivaParaAgenda preventiva (String id, String data, String status)
    {
        return new PreventivaParaAgenda (id, "eq1", "Revisão", data, 90, status);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-02, atrasada, -1", "2026-11-03, proxima, 0", "2026-11-10, proxima, 7", "2026-11-11, futura, 8"})
    void classificaDatasNosLimitesDoAvisoPadrao (String data, String situacao, long dias)
    {
        var p = new PedidoDeAgendaDeManutencao (List.of (preventiva ("p1", data, "programada")), "2026-11-03", null);
        var r = resposta (p, AgendaDeManutencao.agenda (p), RespostaDeAgendaDeManutencao.class);
        assertEquals (situacao, r.getSituacao ().get ("p1"));
        assertEquals (dias, r.getDiasAte ().get ("p1"));
        assertEquals (situacao.equals ("atrasada") ? 1 : 0, r.getAtrasadas ());
        assertEquals (situacao.equals ("proxima") ? 1 : 0, r.getProximas ());
        assertEquals (situacao.equals ("futura") ? 1 : 0, r.getFuturas ());
    }

    @ParameterizedTest
    @CsvSource({"0, 2026-11-03, proxima", "0, 2026-11-04, futura", "2, 2026-11-06, futura", "30, 2026-12-03, proxima"})
    void respeitaJanelaPersonalizada (int aviso, String data, String situacao)
    {
        var p = new PedidoDeAgendaDeManutencao (List.of (preventiva ("p1", data, "programada")), "2026-11-03", aviso);
        var r = resposta (p, AgendaDeManutencao.agenda (p), RespostaDeAgendaDeManutencao.class);
        assertEquals (situacao, r.getSituacao ().get ("p1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"realizada", "cancelada"})
    void ignoraPreventivaEncerradaMesmoSemData (String status)
    {
        var p = new PedidoDeAgendaDeManutencao (List.of (preventiva ("p1", null, status)), "2026-11-03", null);
        var r = resposta (p, AgendaDeManutencao.agenda (p), RespostaDeAgendaDeManutencao.class);
        assertTrue (r.getSituacao ().isEmpty ());
        assertTrue (r.getDiasAte ().isEmpty ());
        assertEquals (0, r.getAtrasadas () + r.getProximas () + r.getFuturas ());
    }

    @Test
    void listaVaziaProduzAgendaZerada ()
    {
        var p = new PedidoDeAgendaDeManutencao (List.of (), "2026-11-03", null);
        var r = resposta (p, AgendaDeManutencao.agenda (p), RespostaDeAgendaDeManutencao.class);
        assertTrue (r.getSituacao ().isEmpty ());
        assertTrue (r.getDiasAte ().isEmpty ());
        assertEquals (0, r.getAtrasadas () + r.getProximas () + r.getFuturas ());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3651})
    void avisoForaDosLimitesEErroDePedido (int aviso)
    {
        var p = new PedidoDeAgendaDeManutencao (List.of (), "2026-11-03", aviso);
        erro (p, AgendaDeManutencao.agenda (p), RespostaDeErro.PEDIDO_INVALIDO);
    }

    @Test
    void preventivaSemIdentificacaoEErroDePedido ()
    {
        for (PreventivaParaAgenda preventiva : Arrays.asList (null, preventiva (null, "2026-11-03", "programada")))
        {
            var p = new PedidoDeAgendaDeManutencao (Arrays.asList (preventiva), "2026-11-03", null);
            erro (p, AgendaDeManutencao.agenda (p), RespostaDeErro.PEDIDO_INVALIDO);
        }
    }

    @Test
    void dataPersistidaImpossivelEErroDeConsistencia ()
    {
        var p = new PedidoDeAgendaDeManutencao (List.of (preventiva ("p1", "2026-02-30", "programada")), "2026-11-03", null);
        erro (p, AgendaDeManutencao.agenda (p), RespostaDeErro.DADOS_INCONSISTENTES);
    }

    @ParameterizedTest
    @CsvSource({"2026-01-31, 1, 2026-02-01", "2026-12-31, 1, 2027-01-01", "2024-02-28, 1, 2024-02-29", "2026-02-28, 1, 2026-03-01", "2026-01-01, 3650, 2035-12-30"})
    void proximaPreventivaRespeitaCalendario (String realizada, int intervalo, String esperada)
    {
        var p = new PedidoDeProximaPreventiva (realizada, intervalo);
        var r = resposta (p, AgendaDeManutencao.proxima (p), RespostaDeProximaPreventiva.class);
        assertEquals (esperada, r.getProximaData ());
    }

    @Test
    void semIntervaloNaoAgendaNovaPreventiva ()
    {
        var p = new PedidoDeProximaPreventiva ("2026-11-03", null);
        assertNull (resposta (p, AgendaDeManutencao.proxima (p), RespostaDeProximaPreventiva.class).getProximaData ());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 3651})
    void proximaPreventivaRecusaIntervaloInvalido (int intervalo)
    {
        var p = new PedidoDeProximaPreventiva ("2026-11-03", intervalo);
        erro (p, AgendaDeManutencao.proxima (p), RespostaDeErro.PEDIDO_INVALIDO);
    }
}
