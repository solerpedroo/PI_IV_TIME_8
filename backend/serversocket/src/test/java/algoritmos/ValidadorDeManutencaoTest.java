package algoritmos;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import protocolo.RespostaDeErro;
import protocolo.mensagens.*;
import static algoritmos.ApoioDeTeste.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unitario")
class ValidadorDeManutencaoTest
{
    private static PedidoDeValidacaoDeEquipamento equipamento (Integer ano, Double horas)
    {
        return new PedidoDeValidacaoDeEquipamento (" Trator 01 ", "Trator", " John Deere ", " ", null, ano, horas, null, 2026);
    }

    private static RespostaDeValidacao validarEquipamento (PedidoDeValidacaoDeEquipamento p)
    {
        return resposta (p, ValidadorDeManutencao.validarEquipamento (p), RespostaDeValidacao.class);
    }

    private static RespostaDeValidacao preventiva (String descricao, String data, Integer intervalo)
    {
        var p = new PedidoDeValidacaoDePreventiva (descricao, data, intervalo);
        return resposta (p, ValidadorDeManutencao.validarPreventiva (p), RespostaDeValidacao.class);
    }

    private static RespostaDeValidacao intervencao (String data, BigDecimal valor)
    {
        var p = new PedidoDeValidacaoDeIntervencao ("corretiva", data, " Revisão ", " Oficina ", 1200.04, valor, "2026-11-03");
        return resposta (p, ValidadorDeManutencao.validarIntervencao (p), RespostaDeValidacao.class);
    }

    @Test
    void equipamentoNormalizaTextosOpcionaisEHorimetro ()
    {
        var r = validarEquipamento (equipamento (2020, 1200.04));
        assertTrue (r.isValido ());
        assertAll (
            () -> assertEquals ("Trator 01", r.getNormalizados ().get ("nome")),
            () -> assertEquals ("trator 01", r.getNormalizados ().get ("nomeNormalizado")),
            () -> assertEquals ("John Deere", r.getNormalizados ().get ("marca")),
            () -> assertNull (r.getNormalizados ().get ("modelo")),
            () -> assertNull (r.getNormalizados ().get ("identificacao")),
            () -> assertEquals (1200.0, r.getNormalizados ().get ("horimetro")),
            () -> assertEquals ("Ativo", r.getNormalizados ().get ("status")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1950, 2027})
    void equipamentoAceitaAnoOpcionalENosLimites (Integer ano)
    {
        assertTrue (validarEquipamento (equipamento (ano, null)).isValido ());
    }

    @ParameterizedTest
    @ValueSource(ints = {1949, 2028})
    void equipamentoRecusaAnoForaDosLimites (int ano)
    {
        campoInvalido (validarEquipamento (equipamento (ano, null)), "ano");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Trator", "Colheitadeira", "Pulverizador", "Plantadeira", "Implemento", "Veículo", "Outro"})
    void equipamentoAceitaTiposCadastrados (String tipo)
    {
        assertTrue (validarEquipamento (new PedidoDeValidacaoDeEquipamento ("E", tipo, null, null, null, null, null, null, 2026)).isValido ());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ativo", "Em manutenção", "Inativo", "", " "})
    void equipamentoAceitaStatusCadastradosOuPadrao (String status)
    {
        assertTrue (validarEquipamento (new PedidoDeValidacaoDeEquipamento ("E", "Trator", null, null, null, null, null, status, 2026)).isValido ());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1000000.1})
    void equipamentoEIntervencaoRecusamHorimetroInvalido (double horas)
    {
        campoInvalido (validarEquipamento (equipamento (null, horas)), "horimetro");
        var p = new PedidoDeValidacaoDeIntervencao ("preventiva", "2026-11-03", "Revisão", null, horas, null, "2026-11-03");
        campoInvalido (resposta (p, ValidadorDeManutencao.validarIntervencao (p), RespostaDeValidacao.class), "horimetro");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1000000})
    void equipamentoAceitaHorimetroNosLimites (double horas)
    {
        assertTrue (validarEquipamento (equipamento (null, horas)).isValido ());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026-02-30", "2025-02-29", "1999-12-31", "2101-01-01", "03/11/2026", "2026-1-01"})
    void parserRecusaDatasInvalidasOuForaDoDominio (String data)
    {
        assertNull (ValidadorDeManutencao.data (data));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2000-01-01", "2024-02-29", "2100-12-31"})
    void parserAceitaDatasNosLimitesEAnoBissexto (String data)
    {
        assertEquals (LocalDate.parse (data), ValidadorDeManutencao.data (" " + data + " "));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1, 3650})
    void preventivaAceitaIntervaloOpcionalENosLimites (Integer intervalo)
    {
        var r = preventiva (" Troca de óleo ", "2026-01-01", intervalo);
        assertTrue (r.isValido ());
        assertEquals ("Troca de óleo", r.getNormalizados ().get ("descricao"));
        assertEquals (intervalo, r.getNormalizados ().get ("intervaloDias"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 3651})
    void preventivaRecusaIntervaloForaDosLimites (int intervalo)
    {
        campoInvalido (preventiva ("Troca de óleo", "2026-11-03", intervalo), "intervaloDias");
    }

    @ParameterizedTest
    @CsvSource({"0.005, 0.01", "350.505, 350.51", "1000000000, 1000000000.00"})
    void intervencaoNormalizaDinheiroComoBigDecimal (BigDecimal informado, BigDecimal esperado)
    {
        var r = intervencao ("2026-11-03", informado);
        assertTrue (r.isValido ());
        assertEquals (esperado, r.getNormalizados ().get ("valor"));
        assertEquals ("Revisão", r.getNormalizados ().get ("descricao"));
        assertEquals ("Oficina", r.getNormalizados ().get ("responsavel"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "0", "0.004", "1000000000.01"})
    void intervencaoRecusaCustoZeradoNegativoOuExcessivo (BigDecimal valor)
    {
        campoInvalido (intervencao ("2026-11-03", valor), "valor");
    }

    @Test
    void intervencaoSemValorNaoCriaDespesa ()
    {
        var r = intervencao ("2026-11-03", null);
        assertTrue (r.isValido ());
        assertFalse (r.getNormalizados ().containsKey ("valor"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026-02-30", "2026-11-04"})
    void intervencaoRecusaDataAusenteImpossivelOuFutura (String data)
    {
        campoInvalido (intervencao (data, null), "data");
    }

    @Test
    void contextoDeDataOuAnoAusenteEErroDePedido ()
    {
        var equipamento = new PedidoDeValidacaoDeEquipamento ("T", "Trator", null, null, null, null, null, null, null);
        var intervencao = new PedidoDeValidacaoDeIntervencao ("corretiva", "2026-11-03", "Revisão", null, null, null, null);
        erro (equipamento, ValidadorDeManutencao.validarEquipamento (equipamento), RespostaDeErro.PEDIDO_INVALIDO);
        erro (intervencao, ValidadorDeManutencao.validarIntervencao (intervencao), RespostaDeErro.PEDIDO_INVALIDO);
    }
}
