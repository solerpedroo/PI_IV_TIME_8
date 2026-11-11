package algoritmos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.ItemDoRelatorio;
import protocolo.mensagens.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.RegistroDaSafra;
import protocolo.mensagens.RespostaDeRelatorioDeSafra;
import protocolo.mensagens.TalhaoDaSafra;

/*
    Consolida a safra: somente leitura e sem efeito colateral. O mesmo resultado alimenta a tela e a exportacao.
    Registro sem data valida (ou sem ano), fora do periodo ou ligado a talhao inexistente nao entra nos totais:
    vira pendencia com o motivo. Datas sem ano continuam preservadas na origem, mas nao provam o recorte.
*/
public class ConsolidadorDeSafra
{
    private static final String CUSTO_INVALIDO = "Custo inválido na safra.";

    // AAAA-MM-DD valido ou null (recusa 2026-02-30)
    public static LocalDate dia (String valor)
    {
        if (valor == null || !valor.matches ("\\d{4}-\\d{2}-\\d{2}"))
            return null;

        try
        {
            return LocalDate.parse (valor);
        }
        catch (Exception erro)
        {
            return null;
        }
    }

    // soma em centavos (long): a soma exata evita a perda silenciosa do double
    public static long centavos (BigDecimal valor) throws Exception
    {
        if (valor == null || valor.signum () < 0)
            throw new Exception (ConsolidadorDeSafra.CUSTO_INVALIDO);

        return valor.movePointRight (2).setScale (0, RoundingMode.HALF_UP).longValueExact ();
    }

    private static boolean noPeriodo (ItemDoRelatorio item, LocalDate inicio, LocalDate fim, List<ItemDoRelatorio> pendencias)
    {
        LocalDate data   = ConsolidadorDeSafra.dia (item.getData ());
        String    motivo = null;

        if (data == null)
            motivo = "Data ausente, inválida ou sem ano; não incluído nos totais.";
        else if (data.isBefore (inicio) || data.isAfter (fim))
            motivo = "Data fora do período da safra; não incluído nos totais.";

        if (motivo != null)
            pendencias.add (item.comMotivo (motivo));

        return motivo == null;
    }

    private static ItemDoRelatorio item (RegistroDaSafra registro, String talhao, String categoria, Long centavos)
    {
        return new ItemDoRelatorio (registro.getTipo (), registro.getId (), registro.getDescricao (), registro.getData (),
                                    talhao, registro.getStatus (), categoria, centavos, null);
    }

    private static RespostaDeErro inconsistente (long id, String mensagem)
    {
        return new RespostaDeErro (id, RespostaDeErro.DADOS_INCONSISTENTES, mensagem);
    }

    public static Resposta consolidar (PedidoDeRelatorioDeSafra pedido)
    {
        long id = pedido.getId ();

        if (pedido.getIdsDosTalhoes () == null || pedido.getTalhoes () == null || pedido.getRegistros () == null)
            return new RespostaDeErro (id, RespostaDeErro.PEDIDO_INVALIDO, "Listas ausentes no pedido de relatório.");

        LocalDate inicio = ConsolidadorDeSafra.dia (pedido.getDataInicio ());
        LocalDate fim    = ConsolidadorDeSafra.dia (pedido.getDataFim ());

        if (inicio == null || fim == null || fim.isBefore (inicio))
            return ConsolidadorDeSafra.inconsistente (id, "A safra possui período inválido; corrija o cadastro antes de consultar.");

        Map<String, TalhaoDaSafra> mapa = new LinkedHashMap<String, TalhaoDaSafra> ();
        for (TalhaoDaSafra talhao : pedido.getTalhoes ())
        {
            if (talhao == null)
                return new RespostaDeErro (id, RespostaDeErro.PEDIDO_INVALIDO, "Talhão ausente na lista do relatório.");

            mapa.put (talhao.getId (), talhao);
        }

        List<String>          ids        = pedido.getIdsDosTalhoes ();
        List<TalhaoDaSafra>   areas      = new ArrayList<TalhaoDaSafra> ();
        List<ItemDoRelatorio> registros  = new ArrayList<ItemDoRelatorio> ();
        List<ItemDoRelatorio> pendencias = new ArrayList<ItemDoRelatorio> ();

        // vinculo com talhao que nao existe mais: avisa e nao inventa area
        for (String talhaoId : ids)
        {
            TalhaoDaSafra talhao = mapa.get (talhaoId);

            if (talhao == null)
                pendencias.add (new ItemDoRelatorio ("talhao", talhaoId, "Talhão indisponível", null, null, null, null, null,
                                                     "Vínculo inexistente; área não contabilizada."));
            else
                areas.add (talhao);
        }

        long totalCentavos = 0;

        for (RegistroDaSafra registro : pedido.getRegistros ())
        {
            if (registro == null)
                return new RespostaDeErro (id, RespostaDeErro.PEDIDO_INVALIDO, "Registro ausente na lista do relatório.");

            String tipo = registro.getTipo ();

            if (RegistroDaSafra.ATIVIDADE.equals (tipo))
            {
                ItemDoRelatorio item = ConsolidadorDeSafra.item (registro, registro.getTalhaoNome (), null, null);

                // a atividade guarda o NOME do talhao, sem distinguir maiusculas
                boolean doTalhao = false;
                for (TalhaoDaSafra area : areas)
                    if (area.getNome () != null && area.getNome ().equalsIgnoreCase (registro.getTalhaoNome ()))
                        doTalhao = true;

                if (!doTalhao)
                    pendencias.add (item.comMotivo ("Talhão da atividade não encontrado na safra; não incluído nos totais."));
                else if (ConsolidadorDeSafra.noPeriodo (item, inicio, fim, pendencias))
                    registros.add (item);
            }
            else if (RegistroDaSafra.OCORRENCIA.equals (tipo))
            {
                // ocorrencia nao tem safra: a associacao e por talhao e data
                if (registro.isExcluido () || registro.getTalhaoId () == null || !ids.contains (registro.getTalhaoId ()))
                    continue;

                ItemDoRelatorio item = ConsolidadorDeSafra.item (registro, registro.getTalhaoNome (), null, null);
                LocalDate       data = ConsolidadorDeSafra.dia (registro.getData ());

                if (data == null)
                    ConsolidadorDeSafra.noPeriodo (item, inicio, fim, pendencias);
                else if (!data.isBefore (inicio) && !data.isAfter (fim))
                    registros.add (item);
            }
            else if (RegistroDaSafra.CUSTO.equals (tipo))
            {
                long centavos;
                try
                {
                    centavos = ConsolidadorDeSafra.centavos (registro.getValor ());
                }
                catch (Exception erro)
                {
                    return ConsolidadorDeSafra.inconsistente (id, ConsolidadorDeSafra.CUSTO_INVALIDO);
                }

                TalhaoDaSafra   talhao = registro.getTalhaoId () == null ? null : mapa.get (registro.getTalhaoId ());
                ItemDoRelatorio item   = ConsolidadorDeSafra.item (registro, talhao == null ? "Sem talhão" : talhao.getNome (),
                                                                   registro.getCategoria (), centavos);

                if (registro.getTalhaoId () != null && (!ids.contains (registro.getTalhaoId ()) || talhao == null))
                    pendencias.add (item.comMotivo ("Talhão do custo não encontrado na safra; não incluído nos totais."));
                else if (ConsolidadorDeSafra.noPeriodo (item, inicio, fim, pendencias))
                {
                    registros.add (item);

                    try
                    {
                        totalCentavos = Math.addExact (totalCentavos, centavos);
                    }
                    catch (ArithmeticException erro)
                    {
                        return ConsolidadorDeSafra.inconsistente (id, ConsolidadorDeSafra.CUSTO_INVALIDO);
                    }
                }
            }
            else
                return new RespostaDeErro (id, RespostaDeErro.PEDIDO_INVALIDO, "Tipo de registro desconhecido: " + tipo);
        }

        // ordem estavel: data e, no empate, tipo + id
        registros.sort ((a, b) ->
        {
            int data = a.getData ().compareTo (b.getData ());
            return data != 0 ? data : (a.getTipo () + a.getId ()).compareTo (b.getTipo () + b.getId ());
        });

        Map<String, Long> contagens = new LinkedHashMap<String, Long> ();
        for (String tipo : List.of (RegistroDaSafra.ATIVIDADE, RegistroDaSafra.OCORRENCIA, RegistroDaSafra.CUSTO))
            contagens.put (tipo, registros.stream ().filter (r -> tipo.equals (r.getTipo ())).count ());

        double area = 0;
        for (TalhaoDaSafra talhao : areas)
            area += talhao.getHectares ();

        return new RespostaDeRelatorioDeSafra (id, area, areas, registros, pendencias, contagens, totalCentavos);
    }
}
