package algoritmos;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import protocolo.RespostaDeErro;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class ConsolidadorDeSafraTest
{
    private static final TalhaoDaSafra NORTE = new TalhaoDaSafra ("t1", "Norte", 12.5);

    private static RegistroDaSafra registro (String tipo, String id, String data, String talhaoId, String nome, BigDecimal valor, boolean excluido)
    {
        return new RegistroDaSafra (tipo, id, "Registro " + id, data, "Pendente", talhaoId, nome, "Outros", valor, excluido);
    }

    private static RegistroDaSafra custo (String id, String data, BigDecimal valor)
    {
        return registro ("custo", id, data, null, null, valor, false);
    }

    private static PedidoDeRelatorioDeSafra pedido (List<RegistroDaSafra> registros)
    {
        return new PedidoDeRelatorioDeSafra ("2026-01-01", "2026-12-31", List.of ("t1"), List.of (NORTE), registros);
    }

    private static RespostaDeRelatorioDeSafra consolidar (List<RegistroDaSafra> registros)
    {
        var p = pedido (registros);
        return resposta (p, ConsolidadorDeSafra.consolidar (p), RespostaDeRelatorioDeSafra.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0.004, 0", "0.005, 1", "1.005, 101", "12.345, 1235", "92233720368547758.07, 9223372036854775807"})
    void converteDinheiroParaCentavosComArredondamentoExato (BigDecimal valor, long esperado) throws Exception
    {
        assertEquals (esperado, ConsolidadorDeSafra.centavos (valor));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1", "-0.001", "92233720368547758.08"})
    void custoInvalidoOuAcimaDoLongInterrompeRelatorio (BigDecimal valor)
    {
        var p = pedido (List.of (custo ("c1", "2026-06-01", valor)));
        erro (p, ConsolidadorDeSafra.consolidar (p), RespostaDeErro.DADOS_INCONSISTENTES);
    }

    @Test
    void somaDinheiroSemPerderCentavos ()
    {
        var r = consolidar (List.of (custo ("c1", "2026-06-01", new BigDecimal ("0.1")), custo ("c2", "2026-06-01", new BigDecimal ("0.2"))));
        assertEquals (30, r.getTotalCentavos ());
        assertEquals (2L, r.getContagens ().get ("custo"));
    }

    @Test
    void somaMilDespesasDeUmCentavo ()
    {
        var registros = IntStream.range (0, 1000).mapToObj (i -> custo ("c" + i, "2026-06-01", new BigDecimal ("0.01"))).toList ();
        assertEquals (1000, consolidar (registros).getTotalCentavos ());
    }

    @Test
    void estouroNaSomaNaoProduzTotalNegativo ()
    {
        var p = pedido (List.of (custo ("c1", "2026-06-01", new BigDecimal ("92233720368547758.07")), custo ("c2", "2026-06-01", new BigDecimal ("0.01"))));
        erro (p, ConsolidadorDeSafra.consolidar (p), RespostaDeErro.DADOS_INCONSISTENTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01", "2026-12-31"})
    void incluiOsDoisLimitesDoPeriodo (String data)
    {
        var r = consolidar (List.of (
            registro ("atividade", "a1", data, null, "nOrTe", null, false),
            registro ("ocorrencia", "o1", data, "t1", "Norte", null, false),
            custo ("c1", data, BigDecimal.ONE)));
        assertEquals (Map.of ("atividade", 1L, "ocorrencia", 1L, "custo", 1L), r.getContagens ());
        assertEquals (100, r.getTotalCentavos ());
        assertTrue (r.getPendencias ().isEmpty ());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12 ago", "2026-02-30", "2025-12-31", "2027-01-01"})
    void atividadeComDataInvalidaOuForaDoPeriodoViraPendencia (String data)
    {
        var r = consolidar (List.of (registro ("atividade", "a1", data, null, "Norte", null, false)));
        assertTrue (r.getRegistros ().isEmpty ());
        assertEquals (1, r.getPendencias ().size ());
        assertFalse (r.getPendencias ().get (0).getMotivo ().isBlank ());
        assertEquals ("a1", r.getPendencias ().get (0).getId ());
    }

    @Test
    void custoForaDoPeriodoNaoEntraNaSoma ()
    {
        var r = consolidar (List.of (custo ("c1", "2027-01-01", BigDecimal.TEN)));
        assertEquals (0, r.getTotalCentavos ());
        assertEquals (1, r.getPendencias ().size ());
        assertEquals (1000L, r.getPendencias ().get (0).getValorCentavos ());
    }

    @Test
    void ocorrenciasExcluidasSemVinculoOuForaDoPeriodoSaoIgnoradas ()
    {
        var r = consolidar (List.of (
            registro ("ocorrencia", "excluida", "2026-06-01", "t1", "Norte", null, true),
            registro ("ocorrencia", "sem-vinculo", "2026-06-01", null, null, null, false),
            registro ("ocorrencia", "outro", "2026-06-01", "t2", "Sul", null, false),
            registro ("ocorrencia", "fora", "2027-01-01", "t1", "Norte", null, false)));
        assertTrue (r.getRegistros ().isEmpty ());
        assertTrue (r.getPendencias ().isEmpty ());
    }

    @Test
    void ocorrenciaSemDataViraPendencia ()
    {
        var r = consolidar (List.of (registro ("ocorrencia", "o1", null, "t1", "Norte", null, false)));
        assertTrue (r.getRegistros ().isEmpty ());
        assertEquals ("o1", r.getPendencias ().get (0).getId ());
        assertTrue (r.getPendencias ().get (0).getMotivo ().contains ("Data"));
    }

    @Test
    void vinculosDeAtividadeECustoInexistentesGeramPendencia ()
    {
        var r = consolidar (List.of (
            registro ("atividade", "a1", "2026-06-01", null, "Sul", null, false),
            registro ("custo", "c1", "2026-06-01", "removido", null, BigDecimal.ONE, false)));
        assertEquals (0, r.getTotalCentavos ());
        assertEquals (2, r.getPendencias ().size ());
        var custo = r.getPendencias ().get (1);
        assertEquals ("Outros", custo.getCategoria ());
        assertEquals ("Sem talhão", custo.getTalhao ());
        assertEquals (100L, custo.getValorCentavos ());
    }

    @Test
    void talhaoRemovidoNaoInventaArea ()
    {
        var p = new PedidoDeRelatorioDeSafra ("2026-01-01", "2026-12-31", List.of ("removido"), List.of (NORTE), List.of ());
        var r = resposta (p, ConsolidadorDeSafra.consolidar (p), RespostaDeRelatorioDeSafra.class);
        assertEquals (0, r.getAreaHectares ());
        assertTrue (r.getTalhoes ().isEmpty ());
        assertEquals ("removido", r.getPendencias ().get (0).getId ());
    }

    @Test
    void ordenaPorDataTipoEIdIndependentementeDaEntrada ()
    {
        var r = consolidar (List.of (
            custo ("c2", "2026-06-01", BigDecimal.ONE),
            registro ("ocorrencia", "o1", "2026-06-01", "t1", "Norte", null, false),
            registro ("atividade", "a1", "2026-06-01", null, "Norte", null, false),
            custo ("c1", "2026-06-01", BigDecimal.ONE),
            custo ("anterior", "2026-05-01", BigDecimal.ONE)));
        assertEquals (List.of ("anterior", "a1", "c1", "c2", "o1"), r.getRegistros ().stream ().map (ItemDoRelatorio::getId).toList ());
    }

    @Test
    void relatorioVazioMantemAreaETodasAsContagensZeradas ()
    {
        var r = consolidar (List.of ());
        assertEquals (12.5, r.getAreaHectares ());
        assertEquals (Map.of ("atividade", 0L, "ocorrencia", 0L, "custo", 0L), r.getContagens ());
        assertEquals (0, r.getTotalCentavos ());
        assertTrue (r.getPendencias ().isEmpty ());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2025-12-31", "2026-02-30", "sem data"})
    void periodoInvalidoEErroDeConsistencia (String fim)
    {
        var p = new PedidoDeRelatorioDeSafra ("2026-01-01", fim, List.of (), List.of (), List.of ());
        erro (p, ConsolidadorDeSafra.consolidar (p), RespostaDeErro.DADOS_INCONSISTENTES);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void cadaListaAusenteEErroDePedido (int ausente)
    {
        var p = new PedidoDeRelatorioDeSafra ("2026-01-01", "2026-12-31", ausente == 0 ? null : List.of (), ausente == 1 ? null : List.of (), ausente == 2 ? null : List.of ());
        erro (p, ConsolidadorDeSafra.consolidar (p), RespostaDeErro.PEDIDO_INVALIDO);
    }

    @Test
    void elementoNuloNasListasEErroDePedido ()
    {
        var talhaoNulo = new PedidoDeRelatorioDeSafra ("2026-01-01", "2026-12-31", List.of ("t1"), Arrays.asList (NORTE, null), List.of ());
        var registroNulo = pedido (Arrays.asList ((RegistroDaSafra) null));
        erro (talhaoNulo, ConsolidadorDeSafra.consolidar (talhaoNulo), RespostaDeErro.PEDIDO_INVALIDO);
        erro (registroNulo, ConsolidadorDeSafra.consolidar (registroNulo), RespostaDeErro.PEDIDO_INVALIDO);
    }

    @Test
    void tipoDesconhecidoEErroDePedido ()
    {
        var p = pedido (List.of (registro ("outro", "r1", "2026-06-01", null, null, null, false)));
        erro (p, ConsolidadorDeSafra.consolidar (p), RespostaDeErro.PEDIDO_INVALIDO);
    }
}
