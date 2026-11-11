/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
import java.math.BigDecimal;
import java.util.ArrayList;
import protocolo.Pedido;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeAgendaDeManutencao;
import protocolo.mensagens.PedidoDeProximaPreventiva;
import protocolo.mensagens.PedidoDeValidacaoDeEquipamento;
import protocolo.mensagens.PedidoDeValidacaoDeIntervencao;
import protocolo.mensagens.PedidoDeValidacaoDePreventiva;
import protocolo.mensagens.PreventivaParaAgenda;
import protocolo.mensagens.RespostaDeAgendaDeManutencao;
import protocolo.mensagens.RespostaDeProximaPreventiva;
import protocolo.mensagens.RespostaDeValidacao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

/*
    Manutencao (#235, #236, #237): validadores de equipamento, preventiva e intervencao, agenda e proxima preventiva.
    Contratos pelo Atendente, sem socket. "hoje" e fixo nos pedidos.
*/
@Tag("contrato")
class AtendenteManutencaoTest
{
    private static final String HOJE = "2026-11-03";


    private static void verificar (String nome, boolean ok)
    {
        assertTrue (ok, nome);
    }

    private static Resposta atender (Pedido pedido)
    {
        Resposta ret = Atendente.atender (pedido);

        assertEquals (pedido.getId (), ret.getIdDoPedido ());

        return ret;
    }

    private static RespostaDeValidacao validacao (Pedido pedido)
    {
        Resposta resposta = AtendenteManutencaoTest.atender (pedido);

        return assertInstanceOf (RespostaDeValidacao.class, resposta);
    }

    private static String codigo (Pedido pedido)
    {
        Resposta resposta = AtendenteManutencaoTest.atender (pedido);
        String   ret      = null;

        if (resposta instanceof RespostaDeErro)
            ret = ((RespostaDeErro) resposta).getCodigo ();

        return ret;
    }

    private static String erro (RespostaDeValidacao resposta, String campo)
    {
        return resposta.getErros ().get (campo);
    }

    private static PedidoDeValidacaoDeEquipamento equipamento (String nome, String tipo, Integer ano, Double horimetro, String status)
    {
        return new PedidoDeValidacaoDeEquipamento (nome, tipo, " John Deere ", "6110J", "", ano, horimetro, status, Integer.valueOf (2026));
    }

    private static PedidoDeValidacaoDeIntervencao intervencao (String tipo, String data, String descricao, BigDecimal valor)
    {
        return new PedidoDeValidacaoDeIntervencao (tipo, data, descricao, "Oficina Central", Double.valueOf (1250.5), valor, AtendenteManutencaoTest.HOJE);
    }

    private static PreventivaParaAgenda preventiva (String id, String data, String status)
    {
        return new PreventivaParaAgenda (id, "eq1", "Troca de \u00f3leo", data, Integer.valueOf (90), status);
    }

    private static String proxima (String data, Integer intervalo)
    {
        return ((RespostaDeProximaPreventiva) AtendenteManutencaoTest.atender (new PedidoDeProximaPreventiva (data, intervalo))).getProximaData ();
    }

    @Test
    void equipamentos ()
    {
        RespostaDeValidacao ok = AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("  Trator 01  ", "Trator", Integer.valueOf (2020), Double.valueOf (1200.04), null));
        AtendenteManutencaoTest.verificar ("equipamento valido: nome sem espacos, nome normalizado e status padrao Ativo",
                                           ok.isValido () && "Trator 01".equals (ok.getNormalizados ().get ("nome")) &&
                                           "trator 01".equals (ok.getNormalizados ().get ("nomeNormalizado")) &&
                                           "Ativo".equals (ok.getNormalizados ().get ("status")));
        AtendenteManutencaoTest.verificar ("opcionais aparados; vazio vira null; horimetro com uma casa",
                                           "John Deere".equals (ok.getNormalizados ().get ("marca")) && ok.getNormalizados ().get ("identificacao") == null &&
                                           Double.valueOf (1200.0).equals (ok.getNormalizados ().get ("horimetro")));

        AtendenteManutencaoTest.verificar ("nome ausente recusado",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento (" ", "Trator", null, null, null)), "nome") != null);
        String longo = "x";
        while (longo.length () < 81)
            longo += "x";
        AtendenteManutencaoTest.verificar ("nome com 81 caracteres recusado",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento (longo, "Trator", null, null, null)), "nome") != null);
        AtendenteManutencaoTest.verificar ("tipo ausente e tipo fora da lista recusados",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("T", null, null, null, null)), "tipo") != null &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("T", "Drone", null, null, null)), "tipo") != null);
        AtendenteManutencaoTest.verificar ("tipo Ve\u00edculo aceito",
                                           AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("Caminhonete", "Ve\u00edculo", null, null, null)).isValido ());
        AtendenteManutencaoTest.verificar ("ano limite: 1950 e ano atual + 1 aceitos",
                                           AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (1950), null, null)).isValido () &&
                                           AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (2027), null, null)).isValido ());
        AtendenteManutencaoTest.verificar ("ano 1949 e ano atual + 2 recusados",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (1949), null, null)), "ano") != null &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (2028), null, null)), "ano").contains ("2027"));
        AtendenteManutencaoTest.verificar ("horimetro negativo recusado",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", null, Double.valueOf (-1), null)), "horimetro") != null);
        AtendenteManutencaoTest.verificar ("horimetro NaN recusado",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", null, Double.valueOf (Double.NaN), null)), "horimetro") != null);
        AtendenteManutencaoTest.verificar ("status Em manuten\u00e7\u00e3o aceito e status inventado recusado",
                                           "Em manuten\u00e7\u00e3o".equals (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", null, null, "Em manuten\u00e7\u00e3o")).getNormalizados ().get ("status")) &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.equipamento ("A", "Trator", null, null, "Vendido")), "status") != null);
        AtendenteManutencaoTest.verificar ("marca com 61 caracteres recusada",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDeEquipamento ("A", "Trator", longo.substring (0, 61), null, null, null, null, null, Integer.valueOf (2026))), "marca") != null);
        AtendenteManutencaoTest.verificar ("equipamento sem ano atual no pedido e PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteManutencaoTest.codigo (new PedidoDeValidacaoDeEquipamento ("A", "Trator", null, null, null, null, null, null, null))));
    }

    @Test
    void preventivas ()
    {
        RespostaDeValidacao ok = AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva (" Troca de \u00f3leo ", "2026-12-01", Integer.valueOf (90)));
        AtendenteManutencaoTest.verificar ("preventiva valida (data no passado tambem: pode estar atrasada)",
                                           ok.isValido () && "Troca de \u00f3leo".equals (ok.getNormalizados ().get ("descricao")) &&
                                           AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("Filtro", "2026-01-01", null)).isValido ());
        AtendenteManutencaoTest.verificar ("preventiva sem descricao recusada",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva (null, "2026-12-01", null)), "descricao") != null);
        AtendenteManutencaoTest.verificar ("preventiva sem data e com data impossivel (2026-02-30) recusada",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", null, null)), "dataPrevista") != null &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-02-30", null)), "dataPrevista") != null);
        AtendenteManutencaoTest.verificar ("intervalo 0 e 3651 recusados; 1 e 3650 aceitos",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (0))), "intervaloDias") != null &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (3651))), "intervaloDias") != null &&
                                           AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (1))).isValido () &&
                                           AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (3650))).isValido ());
    }

    @Test
    void intervencoes ()
    {
        RespostaDeValidacao ok = AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-11-03", "Troca da correia", new BigDecimal ("350.505")));
        AtendenteManutencaoTest.verificar ("intervencao valida hoje; valor com 3 casas arredondado a 2",
                                           ok.isValido () && new BigDecimal ("350.51").equals (ok.getNormalizados ().get ("valor")));
        AtendenteManutencaoTest.verificar ("valor normalizado e BigDecimal (dinheiro nunca em double)",
                                           ok.getNormalizados ().get ("valor") instanceof BigDecimal);

        RespostaDeValidacao semValor = AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("preventiva", "2026-10-01", "Revis\u00e3o", null));
        AtendenteManutencaoTest.verificar ("sem valor: valida e sem a chave valor (nao cria custo zerado)",
                                           semValor.isValido () && !semValor.getNormalizados ().containsKey ("valor"));
        AtendenteManutencaoTest.verificar ("data futura recusada (hoje fixo no pedido)",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-11-04", "X", null)), "data").contains ("futuro"));
        AtendenteManutencaoTest.verificar ("data impossivel e data ausente recusadas",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-02-30", "X", null)), "data") != null &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", null, "X", null)), "data") != null);
        AtendenteManutencaoTest.verificar ("tipo fora de preventiva/corretiva recusado",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("reforma", "2026-10-01", "X", null)), "tipo") != null);
        AtendenteManutencaoTest.verificar ("descricao ausente recusada",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-10-01", "", null)), "descricao") != null);
        AtendenteManutencaoTest.verificar ("valor zero, 0,004 (vira 0,00) e acima de 1 bilhao recusados",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", BigDecimal.ZERO)), "valor") != null &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("0.004"))), "valor") != null &&
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("1000000000.01"))), "valor") != null);
        AtendenteManutencaoTest.verificar ("valor 0,01 aceito",
                                           AtendenteManutencaoTest.validacao (AtendenteManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("0.01"))).isValido ());
        AtendenteManutencaoTest.verificar ("horimetro negativo na intervencao recusado",
                                           AtendenteManutencaoTest.erro (AtendenteManutencaoTest.validacao (new PedidoDeValidacaoDeIntervencao ("corretiva", "2026-10-01", "X", null, Double.valueOf (-5), null, AtendenteManutencaoTest.HOJE)), "horimetro") != null);
        AtendenteManutencaoTest.verificar ("intervencao sem hoje no pedido e PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteManutencaoTest.codigo (new PedidoDeValidacaoDeIntervencao ("corretiva", "2026-10-01", "X", null, null, null, null))));
    }

    @Test
    void agenda ()
    {
        ArrayList<PreventivaParaAgenda> lista = new ArrayList<PreventivaParaAgenda> ();
        lista.add (AtendenteManutencaoTest.preventiva ("vencida",   "2026-11-02", "programada"));
        lista.add (AtendenteManutencaoTest.preventiva ("em3dias",   "2026-11-06", "programada"));
        lista.add (AtendenteManutencaoTest.preventiva ("em30dias",  "2026-12-03", "programada"));
        lista.add (AtendenteManutencaoTest.preventiva ("feita",     "2026-10-01", "realizada"));
        lista.add (AtendenteManutencaoTest.preventiva ("cancelada", "2026-10-15", "cancelada"));
        lista.add (AtendenteManutencaoTest.preventiva ("hoje",      AtendenteManutencaoTest.HOJE, "programada"));
        lista.add (AtendenteManutencaoTest.preventiva ("limite",    "2026-11-10", "programada"));

        RespostaDeAgendaDeManutencao agenda = (RespostaDeAgendaDeManutencao) AtendenteManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (lista, AtendenteManutencaoTest.HOJE, null));
        AtendenteManutencaoTest.verificar ("ontem atrasada (-1 dia); em 3 dias proxima; em 30 dias futura",
                                           "atrasada".equals (agenda.getSituacao ().get ("vencida")) && agenda.getDiasAte ().get ("vencida").longValue () == -1 &&
                                           "proxima".equals (agenda.getSituacao ().get ("em3dias")) && agenda.getDiasAte ().get ("em3dias").longValue () == 3 &&
                                           "futura".equals (agenda.getSituacao ().get ("em30dias")) && agenda.getDiasAte ().get ("em30dias").longValue () == 30);
        AtendenteManutencaoTest.verificar ("realizada e cancelada ficam fora da agenda",
                                           !agenda.getSituacao ().containsKey ("feita") && !agenda.getSituacao ().containsKey ("cancelada"));
        AtendenteManutencaoTest.verificar ("hoje e hoje + 7 (padrao) contam como proxima",
                                           "proxima".equals (agenda.getSituacao ().get ("hoje")) && "proxima".equals (agenda.getSituacao ().get ("limite")));
        AtendenteManutencaoTest.verificar ("totais por situacao: 1 atrasada, 3 proximas, 1 futura",
                                           agenda.getAtrasadas () == 1 && agenda.getProximas () == 3 && agenda.getFuturas () == 1);

        RespostaDeAgendaDeManutencao curto = (RespostaDeAgendaDeManutencao) AtendenteManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (lista, AtendenteManutencaoTest.HOJE, Integer.valueOf (2)));
        AtendenteManutencaoTest.verificar ("diasDeAviso = 2: em 3 dias vira futura",
                                           "futura".equals (curto.getSituacao ().get ("em3dias")) && curto.getProximas () == 1);
        RespostaDeAgendaDeManutencao longo = (RespostaDeAgendaDeManutencao) AtendenteManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (lista, AtendenteManutencaoTest.HOJE, Integer.valueOf (30)));
        AtendenteManutencaoTest.verificar ("diasDeAviso = 30: em 30 dias vira proxima", "proxima".equals (longo.getSituacao ().get ("em30dias")));

        RespostaDeAgendaDeManutencao vazia = (RespostaDeAgendaDeManutencao) AtendenteManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (new ArrayList<PreventivaParaAgenda> (), AtendenteManutencaoTest.HOJE, null));
        AtendenteManutencaoTest.verificar ("agenda vazia: zero em tudo", vazia.getSituacao ().isEmpty () && vazia.getAtrasadas () == 0);

        ArrayList<PreventivaParaAgenda> quebrada = new ArrayList<PreventivaParaAgenda> ();
        quebrada.add (AtendenteManutencaoTest.preventiva ("x", "2026-02-30", "programada"));
        AtendenteManutencaoTest.verificar ("data gravada invalida e DADOS_INCONSISTENTES",
                                           RespostaDeErro.DADOS_INCONSISTENTES.equals (AtendenteManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (quebrada, AtendenteManutencaoTest.HOJE, null))));
        AtendenteManutencaoTest.verificar ("lista null, hoje ausente e aviso negativo sao PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (null, AtendenteManutencaoTest.HOJE, null))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (lista, null, null))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (lista, AtendenteManutencaoTest.HOJE, Integer.valueOf (-1)))));
    }

    @Test
    void proximaPreventiva ()
    {
        AtendenteManutencaoTest.verificar ("proxima com intervalo de 90 dias", "2027-02-01".equals (AtendenteManutencaoTest.proxima ("2026-11-03", Integer.valueOf (90))));
        AtendenteManutencaoTest.verificar ("sem intervalo nao ha proxima (null)", AtendenteManutencaoTest.proxima ("2026-11-03", null) == null);
        AtendenteManutencaoTest.verificar ("virada de ano: 20/12 + 15 dias = 04/01", "2027-01-04".equals (AtendenteManutencaoTest.proxima ("2026-12-20", Integer.valueOf (15))));
        AtendenteManutencaoTest.verificar ("29/02: 2028-02-28 + 1 = 2028-02-29 e 2028-02-29 + 365 = 2029-02-28",
                                           "2028-02-29".equals (AtendenteManutencaoTest.proxima ("2028-02-28", Integer.valueOf (1))) &&
                                           "2029-02-28".equals (AtendenteManutencaoTest.proxima ("2028-02-29", Integer.valueOf (365))));
        AtendenteManutencaoTest.verificar ("ano sem 29/02: 2027-02-28 + 1 = 2027-03-01", "2027-03-01".equals (AtendenteManutencaoTest.proxima ("2027-02-28", Integer.valueOf (1))));
        AtendenteManutencaoTest.verificar ("data realizada invalida e intervalo 0 sao PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteManutencaoTest.codigo (new PedidoDeProximaPreventiva ("2026-02-30", Integer.valueOf (10)))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AtendenteManutencaoTest.codigo (new PedidoDeProximaPreventiva ("2026-11-03", Integer.valueOf (0)))));
    }
}
