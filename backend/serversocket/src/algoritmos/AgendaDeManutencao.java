/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package algoritmos;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeAgendaDeManutencao;
import protocolo.mensagens.PedidoDeProximaPreventiva;
import protocolo.mensagens.PreventivaParaAgenda;
import protocolo.mensagens.RespostaDeAgendaDeManutencao;
import protocolo.mensagens.RespostaDeProximaPreventiva;

/*
    Agenda das preventivas (#235, #237). Funcoes puras: "hoje" vem no pedido.
      atrasada: dataPrevista antes de hoje (diasAte negativo)
      proxima:  de hoje ate hoje + diasDeAviso (padrao 7), inclusive
      futura:   depois disso
    Preventivas realizadas ou canceladas nao entram na agenda.
    Proxima preventiva: data realizada + intervalo em dias (LocalDate.plusDays: vira mes, ano e 29/02 certo).
*/
public class AgendaDeManutencao
{
    public static final String  PROGRAMADA       = "programada";
    public static final String  ATRASADA         = "atrasada";
    public static final String  PROXIMA          = "proxima";
    public static final String  FUTURA           = "futura";
    public static final int     AVISO_PADRAO     = 7;
    public static final int     INTERVALO_MAXIMO = 3650;

    public static Resposta agenda (PedidoDeAgendaDeManutencao pedido)
    {
        LocalDate                  hoje        = ValidadorDeManutencao.data (pedido.getHoje ());
        List<PreventivaParaAgenda> preventivas = pedido.getPreventivas ();
        int                        aviso       = pedido.getDiasDeAviso () == null ? AgendaDeManutencao.AVISO_PADRAO
                                                                                 : pedido.getDiasDeAviso ().intValue ();

        if (hoje == null || preventivas == null || aviso < 0 || aviso > AgendaDeManutencao.INTERVALO_MAXIMO)
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Agenda sem data de hoje, lista ou aviso v\u00e1lido.");

        LinkedHashMap<String, String> situacao  = new LinkedHashMap<String, String> ();
        LinkedHashMap<String, Long>   diasAte   = new LinkedHashMap<String, Long> ();
        long                          atrasadas = 0;
        long                          proximas  = 0;
        long                          futuras   = 0;

        for (PreventivaParaAgenda preventiva : preventivas)
        {
            if (preventiva == null || preventiva.getId () == null)
                return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Preventiva sem identifica\u00e7\u00e3o.");

            if (!AgendaDeManutencao.PROGRAMADA.equals (preventiva.getStatus ()))
                continue;

            LocalDate prevista = ValidadorDeManutencao.data (preventiva.getDataPrevista ());

            if (prevista == null)
                return new RespostaDeErro (pedido.getId (), RespostaDeErro.DADOS_INCONSISTENTES,
                                           "Preventiva " + preventiva.getId () + " sem data prevista v\u00e1lida.");

            long   dias  = ChronoUnit.DAYS.between (hoje, prevista);
            String quando;

            if (dias < 0)
            {
                quando = AgendaDeManutencao.ATRASADA;
                atrasadas++;
            }
            else if (dias <= aviso)
            {
                quando = AgendaDeManutencao.PROXIMA;
                proximas++;
            }
            else
            {
                quando = AgendaDeManutencao.FUTURA;
                futuras++;
            }

            situacao.put (preventiva.getId (), quando);
            diasAte.put  (preventiva.getId (), Long.valueOf (dias));
        }

        return new RespostaDeAgendaDeManutencao (pedido.getId (), situacao, diasAte, atrasadas, proximas, futuras);
    }

    public static Resposta proxima (PedidoDeProximaPreventiva pedido)
    {
        LocalDate realizada = ValidadorDeManutencao.data (pedido.getDataRealizada ());
        Integer   intervalo = pedido.getIntervaloDias ();

        if (realizada == null)
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Data realizada ausente ou inv\u00e1lida.");

        if (intervalo != null && (intervalo.intValue () < 1 || intervalo.intValue () > AgendaDeManutencao.INTERVALO_MAXIMO))
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Intervalo fora de 1 a 3650 dias.");

        String data = intervalo == null ? null : realizada.plusDays (intervalo.longValue ()).toString ();

        return new RespostaDeProximaPreventiva (pedido.getId (), data);
    }
}
