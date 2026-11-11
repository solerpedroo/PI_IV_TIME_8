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
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
    Manutencao (#235, #236, #237): validadores de equipamento, preventiva e intervencao, agenda e proxima preventiva.
    Chama o Atendente direto, sem socket (mesmo estilo de AlgoritmosTest). "hoje" e fixo nos pedidos.
*/
class AgendaDeManutencaoTest
{
    private static final String HOJE = "2026-11-03";


    private static void verificar (String nome, boolean ok)
    {
        assertTrue (ok, nome);
    }

    private static Resposta atender (Pedido pedido)
    {
        Resposta ret = Atendente.atender (pedido);

        if (ret.getIdDoPedido () != pedido.getId ())
            throw new AssertionError ("a resposta de " + pedido + " veio com o id " + ret.getIdDoPedido ());

        return ret;
    }

    private static RespostaDeValidacao validacao (Pedido pedido)
    {
        Resposta resposta = AgendaDeManutencaoTest.atender (pedido);

        if (!(resposta instanceof RespostaDeValidacao))
            throw new AssertionError ("esperava RespostaDeValidacao mas veio " + resposta);

        return (RespostaDeValidacao) resposta;
    }

    private static String codigo (Pedido pedido)
    {
        Resposta resposta = AgendaDeManutencaoTest.atender (pedido);
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
        return new PedidoDeValidacaoDeIntervencao (tipo, data, descricao, "Oficina Central", Double.valueOf (1250.5), valor, AgendaDeManutencaoTest.HOJE);
    }

    private static PreventivaParaAgenda preventiva (String id, String data, String status)
    {
        return new PreventivaParaAgenda (id, "eq1", "Troca de \u00f3leo", data, Integer.valueOf (90), status);
    }

    private static String proxima (String data, Integer intervalo)
    {
        return ((RespostaDeProximaPreventiva) AgendaDeManutencaoTest.atender (new PedidoDeProximaPreventiva (data, intervalo))).getProximaData ();
    }

    @Test
    void equipamentos ()
    {
        RespostaDeValidacao ok = AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("  Trator 01  ", "Trator", Integer.valueOf (2020), Double.valueOf (1200.04), null));
        AgendaDeManutencaoTest.verificar ("equipamento valido: nome sem espacos, nome normalizado e status padrao Ativo",
                                           ok.isValido () && "Trator 01".equals (ok.getNormalizados ().get ("nome")) &&
                                           "trator 01".equals (ok.getNormalizados ().get ("nomeNormalizado")) &&
                                           "Ativo".equals (ok.getNormalizados ().get ("status")));
        AgendaDeManutencaoTest.verificar ("opcionais aparados; vazio vira null; horimetro com uma casa",
                                           "John Deere".equals (ok.getNormalizados ().get ("marca")) && ok.getNormalizados ().get ("identificacao") == null &&
                                           Double.valueOf (1200.0).equals (ok.getNormalizados ().get ("horimetro")));

        AgendaDeManutencaoTest.verificar ("nome ausente recusado",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento (" ", "Trator", null, null, null)), "nome") != null);
        String longo = "x";
        while (longo.length () < 81)
            longo += "x";
        AgendaDeManutencaoTest.verificar ("nome com 81 caracteres recusado",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento (longo, "Trator", null, null, null)), "nome") != null);
        AgendaDeManutencaoTest.verificar ("tipo ausente e tipo fora da lista recusados",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("T", null, null, null, null)), "tipo") != null &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("T", "Drone", null, null, null)), "tipo") != null);
        AgendaDeManutencaoTest.verificar ("tipo Ve\u00edculo aceito",
                                           AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("Caminhonete", "Ve\u00edculo", null, null, null)).isValido ());
        AgendaDeManutencaoTest.verificar ("ano limite: 1950 e ano atual + 1 aceitos",
                                           AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (1950), null, null)).isValido () &&
                                           AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (2027), null, null)).isValido ());
        AgendaDeManutencaoTest.verificar ("ano 1949 e ano atual + 2 recusados",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (1949), null, null)), "ano") != null &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", Integer.valueOf (2028), null, null)), "ano").contains ("2027"));
        AgendaDeManutencaoTest.verificar ("horimetro negativo recusado",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", null, Double.valueOf (-1), null)), "horimetro") != null);
        AgendaDeManutencaoTest.verificar ("horimetro NaN recusado",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", null, Double.valueOf (Double.NaN), null)), "horimetro") != null);
        AgendaDeManutencaoTest.verificar ("status Em manuten\u00e7\u00e3o aceito e status inventado recusado",
                                           "Em manuten\u00e7\u00e3o".equals (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", null, null, "Em manuten\u00e7\u00e3o")).getNormalizados ().get ("status")) &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.equipamento ("A", "Trator", null, null, "Vendido")), "status") != null);
        AgendaDeManutencaoTest.verificar ("marca com 61 caracteres recusada",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDeEquipamento ("A", "Trator", longo.substring (0, 61), null, null, null, null, null, Integer.valueOf (2026))), "marca") != null);
        AgendaDeManutencaoTest.verificar ("equipamento sem ano atual no pedido e PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AgendaDeManutencaoTest.codigo (new PedidoDeValidacaoDeEquipamento ("A", "Trator", null, null, null, null, null, null, null))));
    }

    @Test
    void preventivas ()
    {
        RespostaDeValidacao ok = AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva (" Troca de \u00f3leo ", "2026-12-01", Integer.valueOf (90)));
        AgendaDeManutencaoTest.verificar ("preventiva valida (data no passado tambem: pode estar atrasada)",
                                           ok.isValido () && "Troca de \u00f3leo".equals (ok.getNormalizados ().get ("descricao")) &&
                                           AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("Filtro", "2026-01-01", null)).isValido ());
        AgendaDeManutencaoTest.verificar ("preventiva sem descricao recusada",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva (null, "2026-12-01", null)), "descricao") != null);
        AgendaDeManutencaoTest.verificar ("preventiva sem data e com data impossivel (2026-02-30) recusada",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", null, null)), "dataPrevista") != null &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-02-30", null)), "dataPrevista") != null);
        AgendaDeManutencaoTest.verificar ("intervalo 0 e 3651 recusados; 1 e 3650 aceitos",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (0))), "intervaloDias") != null &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (3651))), "intervaloDias") != null &&
                                           AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (1))).isValido () &&
                                           AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (3650))).isValido ());
    }

    @Test
    void intervencoes ()
    {
        RespostaDeValidacao ok = AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-11-03", "Troca da correia", new BigDecimal ("350.505")));
        AgendaDeManutencaoTest.verificar ("intervencao valida hoje; valor com 3 casas arredondado a 2",
                                           ok.isValido () && new BigDecimal ("350.51").equals (ok.getNormalizados ().get ("valor")));
        AgendaDeManutencaoTest.verificar ("valor normalizado e BigDecimal (dinheiro nunca em double)",
                                           ok.getNormalizados ().get ("valor") instanceof BigDecimal);

        RespostaDeValidacao semValor = AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("preventiva", "2026-10-01", "Revis\u00e3o", null));
        AgendaDeManutencaoTest.verificar ("sem valor: valida e sem a chave valor (nao cria custo zerado)",
                                           semValor.isValido () && !semValor.getNormalizados ().containsKey ("valor"));
        AgendaDeManutencaoTest.verificar ("data futura recusada (hoje fixo no pedido)",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-11-04", "X", null)), "data").contains ("futuro"));
        AgendaDeManutencaoTest.verificar ("data impossivel e data ausente recusadas",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-02-30", "X", null)), "data") != null &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", null, "X", null)), "data") != null);
        AgendaDeManutencaoTest.verificar ("tipo fora de preventiva/corretiva recusado",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("reforma", "2026-10-01", "X", null)), "tipo") != null);
        AgendaDeManutencaoTest.verificar ("descricao ausente recusada",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-10-01", "", null)), "descricao") != null);
        AgendaDeManutencaoTest.verificar ("valor zero, 0,004 (vira 0,00) e acima de 1 bilhao recusados",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", BigDecimal.ZERO)), "valor") != null &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("0.004"))), "valor") != null &&
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("1000000000.01"))), "valor") != null);
        AgendaDeManutencaoTest.verificar ("valor 0,01 aceito",
                                           AgendaDeManutencaoTest.validacao (AgendaDeManutencaoTest.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("0.01"))).isValido ());
        AgendaDeManutencaoTest.verificar ("horimetro negativo na intervencao recusado",
                                           AgendaDeManutencaoTest.erro (AgendaDeManutencaoTest.validacao (new PedidoDeValidacaoDeIntervencao ("corretiva", "2026-10-01", "X", null, Double.valueOf (-5), null, AgendaDeManutencaoTest.HOJE)), "horimetro") != null);
        AgendaDeManutencaoTest.verificar ("intervencao sem hoje no pedido e PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AgendaDeManutencaoTest.codigo (new PedidoDeValidacaoDeIntervencao ("corretiva", "2026-10-01", "X", null, null, null, null))));
    }

    @Test
    void agenda ()
    {
        ArrayList<PreventivaParaAgenda> lista = new ArrayList<PreventivaParaAgenda> ();
        lista.add (AgendaDeManutencaoTest.preventiva ("vencida",   "2026-11-02", "programada"));
        lista.add (AgendaDeManutencaoTest.preventiva ("em3dias",   "2026-11-06", "programada"));
        lista.add (AgendaDeManutencaoTest.preventiva ("em30dias",  "2026-12-03", "programada"));
        lista.add (AgendaDeManutencaoTest.preventiva ("feita",     "2026-10-01", "realizada"));
        lista.add (AgendaDeManutencaoTest.preventiva ("cancelada", "2026-10-15", "cancelada"));
        lista.add (AgendaDeManutencaoTest.preventiva ("hoje",      AgendaDeManutencaoTest.HOJE, "programada"));
        lista.add (AgendaDeManutencaoTest.preventiva ("limite",    "2026-11-10", "programada"));

        RespostaDeAgendaDeManutencao agenda = (RespostaDeAgendaDeManutencao) AgendaDeManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (lista, AgendaDeManutencaoTest.HOJE, null));
        AgendaDeManutencaoTest.verificar ("ontem atrasada (-1 dia); em 3 dias proxima; em 30 dias futura",
                                           "atrasada".equals (agenda.getSituacao ().get ("vencida")) && agenda.getDiasAte ().get ("vencida").longValue () == -1 &&
                                           "proxima".equals (agenda.getSituacao ().get ("em3dias")) && agenda.getDiasAte ().get ("em3dias").longValue () == 3 &&
                                           "futura".equals (agenda.getSituacao ().get ("em30dias")) && agenda.getDiasAte ().get ("em30dias").longValue () == 30);
        AgendaDeManutencaoTest.verificar ("realizada e cancelada ficam fora da agenda",
                                           !agenda.getSituacao ().containsKey ("feita") && !agenda.getSituacao ().containsKey ("cancelada"));
        AgendaDeManutencaoTest.verificar ("hoje e hoje + 7 (padrao) contam como proxima",
                                           "proxima".equals (agenda.getSituacao ().get ("hoje")) && "proxima".equals (agenda.getSituacao ().get ("limite")));
        AgendaDeManutencaoTest.verificar ("totais por situacao: 1 atrasada, 3 proximas, 1 futura",
                                           agenda.getAtrasadas () == 1 && agenda.getProximas () == 3 && agenda.getFuturas () == 1);

        RespostaDeAgendaDeManutencao curto = (RespostaDeAgendaDeManutencao) AgendaDeManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (lista, AgendaDeManutencaoTest.HOJE, Integer.valueOf (2)));
        AgendaDeManutencaoTest.verificar ("diasDeAviso = 2: em 3 dias vira futura",
                                           "futura".equals (curto.getSituacao ().get ("em3dias")) && curto.getProximas () == 1);
        RespostaDeAgendaDeManutencao longo = (RespostaDeAgendaDeManutencao) AgendaDeManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (lista, AgendaDeManutencaoTest.HOJE, Integer.valueOf (30)));
        AgendaDeManutencaoTest.verificar ("diasDeAviso = 30: em 30 dias vira proxima", "proxima".equals (longo.getSituacao ().get ("em30dias")));

        RespostaDeAgendaDeManutencao vazia = (RespostaDeAgendaDeManutencao) AgendaDeManutencaoTest.atender (
            new PedidoDeAgendaDeManutencao (new ArrayList<PreventivaParaAgenda> (), AgendaDeManutencaoTest.HOJE, null));
        AgendaDeManutencaoTest.verificar ("agenda vazia: zero em tudo", vazia.getSituacao ().isEmpty () && vazia.getAtrasadas () == 0);

        ArrayList<PreventivaParaAgenda> quebrada = new ArrayList<PreventivaParaAgenda> ();
        quebrada.add (AgendaDeManutencaoTest.preventiva ("x", "2026-02-30", "programada"));
        AgendaDeManutencaoTest.verificar ("data gravada invalida e DADOS_INCONSISTENTES",
                                           RespostaDeErro.DADOS_INCONSISTENTES.equals (AgendaDeManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (quebrada, AgendaDeManutencaoTest.HOJE, null))));
        AgendaDeManutencaoTest.verificar ("lista null, hoje ausente e aviso negativo sao PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AgendaDeManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (null, AgendaDeManutencaoTest.HOJE, null))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AgendaDeManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (lista, null, null))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AgendaDeManutencaoTest.codigo (new PedidoDeAgendaDeManutencao (lista, AgendaDeManutencaoTest.HOJE, Integer.valueOf (-1)))));
    }

    @Test
    void proximaPreventiva ()
    {
        AgendaDeManutencaoTest.verificar ("proxima com intervalo de 90 dias", "2027-02-01".equals (AgendaDeManutencaoTest.proxima ("2026-11-03", Integer.valueOf (90))));
        AgendaDeManutencaoTest.verificar ("sem intervalo nao ha proxima (null)", AgendaDeManutencaoTest.proxima ("2026-11-03", null) == null);
        AgendaDeManutencaoTest.verificar ("virada de ano: 20/12 + 15 dias = 04/01", "2027-01-04".equals (AgendaDeManutencaoTest.proxima ("2026-12-20", Integer.valueOf (15))));
        AgendaDeManutencaoTest.verificar ("29/02: 2028-02-28 + 1 = 2028-02-29 e 2028-02-29 + 365 = 2029-02-28",
                                           "2028-02-29".equals (AgendaDeManutencaoTest.proxima ("2028-02-28", Integer.valueOf (1))) &&
                                           "2029-02-28".equals (AgendaDeManutencaoTest.proxima ("2028-02-29", Integer.valueOf (365))));
        AgendaDeManutencaoTest.verificar ("ano sem 29/02: 2027-02-28 + 1 = 2027-03-01", "2027-03-01".equals (AgendaDeManutencaoTest.proxima ("2027-02-28", Integer.valueOf (1))));
        AgendaDeManutencaoTest.verificar ("data realizada invalida e intervalo 0 sao PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AgendaDeManutencaoTest.codigo (new PedidoDeProximaPreventiva ("2026-02-30", Integer.valueOf (10)))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (AgendaDeManutencaoTest.codigo (new PedidoDeProximaPreventiva ("2026-11-03", Integer.valueOf (0)))));
    }
}
