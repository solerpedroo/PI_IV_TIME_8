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

/*
    Manutencao (#235, #236, #237): validadores de equipamento, preventiva e intervencao, agenda e proxima preventiva.
    Chama o Atendente direto, sem socket (mesmo estilo de TesteAlgoritmos). "hoje" e fixo nos pedidos.
*/
public class TesteAgendaDeManutencao
{
    private static final String HOJE = "2026-11-03";

    private static int total;

    private static void verificar (String nome, boolean ok)
    {
        if (!ok)
            throw new AssertionError (nome);

        TesteAgendaDeManutencao.total++;
        System.out.println ("ok " + nome);
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
        Resposta resposta = TesteAgendaDeManutencao.atender (pedido);

        if (!(resposta instanceof RespostaDeValidacao))
            throw new AssertionError ("esperava RespostaDeValidacao mas veio " + resposta);

        return (RespostaDeValidacao) resposta;
    }

    private static String codigo (Pedido pedido)
    {
        Resposta resposta = TesteAgendaDeManutencao.atender (pedido);
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
        return new PedidoDeValidacaoDeIntervencao (tipo, data, descricao, "Oficina Central", Double.valueOf (1250.5), valor, TesteAgendaDeManutencao.HOJE);
    }

    private static PreventivaParaAgenda preventiva (String id, String data, String status)
    {
        return new PreventivaParaAgenda (id, "eq1", "Troca de \u00f3leo", data, Integer.valueOf (90), status);
    }

    private static String proxima (String data, Integer intervalo)
    {
        return ((RespostaDeProximaPreventiva) TesteAgendaDeManutencao.atender (new PedidoDeProximaPreventiva (data, intervalo))).getProximaData ();
    }

    private static void equipamentos ()
    {
        RespostaDeValidacao ok = TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("  Trator 01  ", "Trator", Integer.valueOf (2020), Double.valueOf (1200.04), null));
        TesteAgendaDeManutencao.verificar ("equipamento valido: nome sem espacos, nome normalizado e status padrao Ativo",
                                           ok.isValido () && "Trator 01".equals (ok.getNormalizados ().get ("nome")) &&
                                           "trator 01".equals (ok.getNormalizados ().get ("nomeNormalizado")) &&
                                           "Ativo".equals (ok.getNormalizados ().get ("status")));
        TesteAgendaDeManutencao.verificar ("opcionais aparados; vazio vira null; horimetro com uma casa",
                                           "John Deere".equals (ok.getNormalizados ().get ("marca")) && ok.getNormalizados ().get ("identificacao") == null &&
                                           Double.valueOf (1200.0).equals (ok.getNormalizados ().get ("horimetro")));

        TesteAgendaDeManutencao.verificar ("nome ausente recusado",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento (" ", "Trator", null, null, null)), "nome") != null);
        String longo = "x";
        while (longo.length () < 81)
            longo += "x";
        TesteAgendaDeManutencao.verificar ("nome com 81 caracteres recusado",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento (longo, "Trator", null, null, null)), "nome") != null);
        TesteAgendaDeManutencao.verificar ("tipo ausente e tipo fora da lista recusados",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("T", null, null, null, null)), "tipo") != null &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("T", "Drone", null, null, null)), "tipo") != null);
        TesteAgendaDeManutencao.verificar ("tipo Ve\u00edculo aceito",
                                           TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("Caminhonete", "Ve\u00edculo", null, null, null)).isValido ());
        TesteAgendaDeManutencao.verificar ("ano limite: 1950 e ano atual + 1 aceitos",
                                           TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", Integer.valueOf (1950), null, null)).isValido () &&
                                           TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", Integer.valueOf (2027), null, null)).isValido ());
        TesteAgendaDeManutencao.verificar ("ano 1949 e ano atual + 2 recusados",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", Integer.valueOf (1949), null, null)), "ano") != null &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", Integer.valueOf (2028), null, null)), "ano").contains ("2027"));
        TesteAgendaDeManutencao.verificar ("horimetro negativo recusado",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", null, Double.valueOf (-1), null)), "horimetro") != null);
        TesteAgendaDeManutencao.verificar ("horimetro NaN recusado",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", null, Double.valueOf (Double.NaN), null)), "horimetro") != null);
        TesteAgendaDeManutencao.verificar ("status Em manuten\u00e7\u00e3o aceito e status inventado recusado",
                                           "Em manuten\u00e7\u00e3o".equals (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", null, null, "Em manuten\u00e7\u00e3o")).getNormalizados ().get ("status")) &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.equipamento ("A", "Trator", null, null, "Vendido")), "status") != null);
        TesteAgendaDeManutencao.verificar ("marca com 61 caracteres recusada",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDeEquipamento ("A", "Trator", longo.substring (0, 61), null, null, null, null, null, Integer.valueOf (2026))), "marca") != null);
        TesteAgendaDeManutencao.verificar ("equipamento sem ano atual no pedido e PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (TesteAgendaDeManutencao.codigo (new PedidoDeValidacaoDeEquipamento ("A", "Trator", null, null, null, null, null, null, null))));
    }

    private static void preventivas ()
    {
        RespostaDeValidacao ok = TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva (" Troca de \u00f3leo ", "2026-12-01", Integer.valueOf (90)));
        TesteAgendaDeManutencao.verificar ("preventiva valida (data no passado tambem: pode estar atrasada)",
                                           ok.isValido () && "Troca de \u00f3leo".equals (ok.getNormalizados ().get ("descricao")) &&
                                           TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva ("Filtro", "2026-01-01", null)).isValido ());
        TesteAgendaDeManutencao.verificar ("preventiva sem descricao recusada",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva (null, "2026-12-01", null)), "descricao") != null);
        TesteAgendaDeManutencao.verificar ("preventiva sem data e com data impossivel (2026-02-30) recusada",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva ("F", null, null)), "dataPrevista") != null &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-02-30", null)), "dataPrevista") != null);
        TesteAgendaDeManutencao.verificar ("intervalo 0 e 3651 recusados; 1 e 3650 aceitos",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (0))), "intervaloDias") != null &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (3651))), "intervaloDias") != null &&
                                           TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (1))).isValido () &&
                                           TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDePreventiva ("F", "2026-12-01", Integer.valueOf (3650))).isValido ());
    }

    private static void intervencoes ()
    {
        RespostaDeValidacao ok = TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-11-03", "Troca da correia", new BigDecimal ("350.505")));
        TesteAgendaDeManutencao.verificar ("intervencao valida hoje; valor com 3 casas arredondado a 2",
                                           ok.isValido () && new BigDecimal ("350.51").equals (ok.getNormalizados ().get ("valor")));
        TesteAgendaDeManutencao.verificar ("valor normalizado e BigDecimal (dinheiro nunca em double)",
                                           ok.getNormalizados ().get ("valor") instanceof BigDecimal);

        RespostaDeValidacao semValor = TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("preventiva", "2026-10-01", "Revis\u00e3o", null));
        TesteAgendaDeManutencao.verificar ("sem valor: valida e sem a chave valor (nao cria custo zerado)",
                                           semValor.isValido () && !semValor.getNormalizados ().containsKey ("valor"));
        TesteAgendaDeManutencao.verificar ("data futura recusada (hoje fixo no pedido)",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-11-04", "X", null)), "data").contains ("futuro"));
        TesteAgendaDeManutencao.verificar ("data impossivel e data ausente recusadas",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-02-30", "X", null)), "data") != null &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", null, "X", null)), "data") != null);
        TesteAgendaDeManutencao.verificar ("tipo fora de preventiva/corretiva recusado",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("reforma", "2026-10-01", "X", null)), "tipo") != null);
        TesteAgendaDeManutencao.verificar ("descricao ausente recusada",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-10-01", "", null)), "descricao") != null);
        TesteAgendaDeManutencao.verificar ("valor zero, 0,004 (vira 0,00) e acima de 1 bilhao recusados",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-10-01", "X", BigDecimal.ZERO)), "valor") != null &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("0.004"))), "valor") != null &&
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("1000000000.01"))), "valor") != null);
        TesteAgendaDeManutencao.verificar ("valor 0,01 aceito",
                                           TesteAgendaDeManutencao.validacao (TesteAgendaDeManutencao.intervencao ("corretiva", "2026-10-01", "X", new BigDecimal ("0.01"))).isValido ());
        TesteAgendaDeManutencao.verificar ("horimetro negativo na intervencao recusado",
                                           TesteAgendaDeManutencao.erro (TesteAgendaDeManutencao.validacao (new PedidoDeValidacaoDeIntervencao ("corretiva", "2026-10-01", "X", null, Double.valueOf (-5), null, TesteAgendaDeManutencao.HOJE)), "horimetro") != null);
        TesteAgendaDeManutencao.verificar ("intervencao sem hoje no pedido e PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (TesteAgendaDeManutencao.codigo (new PedidoDeValidacaoDeIntervencao ("corretiva", "2026-10-01", "X", null, null, null, null))));
    }

    private static void agenda ()
    {
        ArrayList<PreventivaParaAgenda> lista = new ArrayList<PreventivaParaAgenda> ();
        lista.add (TesteAgendaDeManutencao.preventiva ("vencida",   "2026-11-02", "programada"));
        lista.add (TesteAgendaDeManutencao.preventiva ("em3dias",   "2026-11-06", "programada"));
        lista.add (TesteAgendaDeManutencao.preventiva ("em30dias",  "2026-12-03", "programada"));
        lista.add (TesteAgendaDeManutencao.preventiva ("feita",     "2026-10-01", "realizada"));
        lista.add (TesteAgendaDeManutencao.preventiva ("cancelada", "2026-10-15", "cancelada"));
        lista.add (TesteAgendaDeManutencao.preventiva ("hoje",      TesteAgendaDeManutencao.HOJE, "programada"));
        lista.add (TesteAgendaDeManutencao.preventiva ("limite",    "2026-11-10", "programada"));

        RespostaDeAgendaDeManutencao agenda = (RespostaDeAgendaDeManutencao) TesteAgendaDeManutencao.atender (
            new PedidoDeAgendaDeManutencao (lista, TesteAgendaDeManutencao.HOJE, null));
        TesteAgendaDeManutencao.verificar ("ontem atrasada (-1 dia); em 3 dias proxima; em 30 dias futura",
                                           "atrasada".equals (agenda.getSituacao ().get ("vencida")) && agenda.getDiasAte ().get ("vencida").longValue () == -1 &&
                                           "proxima".equals (agenda.getSituacao ().get ("em3dias")) && agenda.getDiasAte ().get ("em3dias").longValue () == 3 &&
                                           "futura".equals (agenda.getSituacao ().get ("em30dias")) && agenda.getDiasAte ().get ("em30dias").longValue () == 30);
        TesteAgendaDeManutencao.verificar ("realizada e cancelada ficam fora da agenda",
                                           !agenda.getSituacao ().containsKey ("feita") && !agenda.getSituacao ().containsKey ("cancelada"));
        TesteAgendaDeManutencao.verificar ("hoje e hoje + 7 (padrao) contam como proxima",
                                           "proxima".equals (agenda.getSituacao ().get ("hoje")) && "proxima".equals (agenda.getSituacao ().get ("limite")));
        TesteAgendaDeManutencao.verificar ("totais por situacao: 1 atrasada, 3 proximas, 1 futura",
                                           agenda.getAtrasadas () == 1 && agenda.getProximas () == 3 && agenda.getFuturas () == 1);

        RespostaDeAgendaDeManutencao curto = (RespostaDeAgendaDeManutencao) TesteAgendaDeManutencao.atender (
            new PedidoDeAgendaDeManutencao (lista, TesteAgendaDeManutencao.HOJE, Integer.valueOf (2)));
        TesteAgendaDeManutencao.verificar ("diasDeAviso = 2: em 3 dias vira futura",
                                           "futura".equals (curto.getSituacao ().get ("em3dias")) && curto.getProximas () == 1);
        RespostaDeAgendaDeManutencao longo = (RespostaDeAgendaDeManutencao) TesteAgendaDeManutencao.atender (
            new PedidoDeAgendaDeManutencao (lista, TesteAgendaDeManutencao.HOJE, Integer.valueOf (30)));
        TesteAgendaDeManutencao.verificar ("diasDeAviso = 30: em 30 dias vira proxima", "proxima".equals (longo.getSituacao ().get ("em30dias")));

        RespostaDeAgendaDeManutencao vazia = (RespostaDeAgendaDeManutencao) TesteAgendaDeManutencao.atender (
            new PedidoDeAgendaDeManutencao (new ArrayList<PreventivaParaAgenda> (), TesteAgendaDeManutencao.HOJE, null));
        TesteAgendaDeManutencao.verificar ("agenda vazia: zero em tudo", vazia.getSituacao ().isEmpty () && vazia.getAtrasadas () == 0);

        ArrayList<PreventivaParaAgenda> quebrada = new ArrayList<PreventivaParaAgenda> ();
        quebrada.add (TesteAgendaDeManutencao.preventiva ("x", "2026-02-30", "programada"));
        TesteAgendaDeManutencao.verificar ("data gravada invalida e DADOS_INCONSISTENTES",
                                           RespostaDeErro.DADOS_INCONSISTENTES.equals (TesteAgendaDeManutencao.codigo (new PedidoDeAgendaDeManutencao (quebrada, TesteAgendaDeManutencao.HOJE, null))));
        TesteAgendaDeManutencao.verificar ("lista null, hoje ausente e aviso negativo sao PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (TesteAgendaDeManutencao.codigo (new PedidoDeAgendaDeManutencao (null, TesteAgendaDeManutencao.HOJE, null))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (TesteAgendaDeManutencao.codigo (new PedidoDeAgendaDeManutencao (lista, null, null))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (TesteAgendaDeManutencao.codigo (new PedidoDeAgendaDeManutencao (lista, TesteAgendaDeManutencao.HOJE, Integer.valueOf (-1)))));
    }

    private static void proximaPreventiva ()
    {
        TesteAgendaDeManutencao.verificar ("proxima com intervalo de 90 dias", "2027-02-01".equals (TesteAgendaDeManutencao.proxima ("2026-11-03", Integer.valueOf (90))));
        TesteAgendaDeManutencao.verificar ("sem intervalo nao ha proxima (null)", TesteAgendaDeManutencao.proxima ("2026-11-03", null) == null);
        TesteAgendaDeManutencao.verificar ("virada de ano: 20/12 + 15 dias = 04/01", "2027-01-04".equals (TesteAgendaDeManutencao.proxima ("2026-12-20", Integer.valueOf (15))));
        TesteAgendaDeManutencao.verificar ("29/02: 2028-02-28 + 1 = 2028-02-29 e 2028-02-29 + 365 = 2029-02-28",
                                           "2028-02-29".equals (TesteAgendaDeManutencao.proxima ("2028-02-28", Integer.valueOf (1))) &&
                                           "2029-02-28".equals (TesteAgendaDeManutencao.proxima ("2028-02-29", Integer.valueOf (365))));
        TesteAgendaDeManutencao.verificar ("ano sem 29/02: 2027-02-28 + 1 = 2027-03-01", "2027-03-01".equals (TesteAgendaDeManutencao.proxima ("2027-02-28", Integer.valueOf (1))));
        TesteAgendaDeManutencao.verificar ("data realizada invalida e intervalo 0 sao PEDIDO_INVALIDO",
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (TesteAgendaDeManutencao.codigo (new PedidoDeProximaPreventiva ("2026-02-30", Integer.valueOf (10)))) &&
                                           RespostaDeErro.PEDIDO_INVALIDO.equals (TesteAgendaDeManutencao.codigo (new PedidoDeProximaPreventiva ("2026-11-03", Integer.valueOf (0)))));
    }

    public static void main (String[] args) throws Exception
    {
        TesteAgendaDeManutencao.equipamentos ();
        TesteAgendaDeManutencao.preventivas ();
        TesteAgendaDeManutencao.intervencoes ();
        TesteAgendaDeManutencao.agenda ();
        TesteAgendaDeManutencao.proximaPreventiva ();

        System.out.println ("Manuten\u00e7\u00e3o: " + TesteAgendaDeManutencao.total + " verifica\u00e7\u00f5es aprovadas.");
    }
}
