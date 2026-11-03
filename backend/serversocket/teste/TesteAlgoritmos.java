import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.ItemDoRelatorio;
import protocolo.mensagens.PedidoDeMovimentacaoDeEstoque;
import protocolo.mensagens.PedidoDePing;
import protocolo.mensagens.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.PedidoDeResumoDeTalhoes;
import protocolo.mensagens.PedidoDeValidacaoDeCadastro;
import protocolo.mensagens.PedidoDeValidacaoDeSenha;
import protocolo.mensagens.PedidoDeValidacaoDeTalhao;
import protocolo.mensagens.RegistroDaSafra;
import protocolo.mensagens.RespostaDeMovimentacaoDeEstoque;
import protocolo.mensagens.RespostaDePing;
import protocolo.mensagens.RespostaDeRelatorioDeSafra;
import protocolo.mensagens.RespostaDeResumoDeTalhoes;
import protocolo.mensagens.RespostaDeValidacao;
import protocolo.mensagens.TalhaoDaSafra;
import protocolo.mensagens.TalhaoParaResumo;

/* Casos deterministicos dos algoritmos, chamados pelo Atendente, sem socket. */
public class TesteAlgoritmos
{
    private static int total;

    private static void verificar (String nome, boolean ok)
    {
        if (!ok)
            throw new AssertionError (nome);

        total++;
        System.out.println ("ok " + nome);
    }

    // atende o pedido e confere o tipo da resposta (mostrando o erro, se o servidor devolveu um)
    private static <T extends Resposta> T atender (Pedido pedido, Class<T> tipo)
    {
        Resposta resposta = Atendente.atender (pedido);

        if (!tipo.isInstance (resposta))
        {
            String detalhe = resposta instanceof RespostaDeErro ?
                ((RespostaDeErro) resposta).getCodigo () + ": " + ((RespostaDeErro) resposta).getMensagem () : resposta.toString ();
            throw new AssertionError ("esperava " + tipo.getSimpleName () + " mas veio " + detalhe);
        }

        // toda resposta devolve o id do pedido (silencioso: seria uma linha por chamada)
        if (resposta.getIdDoPedido () != pedido.getId ())
            throw new AssertionError ("a resposta de " + pedido + " veio com o id " + resposta.getIdDoPedido ());

        return tipo.cast (resposta);
    }

    private static RespostaDeErro erro (Pedido pedido)
    {
        return atender (pedido, RespostaDeErro.class);
    }

    private static PedidoDeValidacaoDeCadastro cadastro (String name, String email, String phone, String senha,
                                                         String confirma, String farm, String area, boolean termos)
    {
        return new PedidoDeValidacaoDeCadastro (name, email, phone, senha, confirma, farm, area, termos);
    }

    private static PedidoDeMovimentacaoDeEstoque movimento (double saldo, double custo, String tipo, Double quantidade,
                                                            Double custoInformado, String observacao)
    {
        return new PedidoDeMovimentacaoDeEstoque (saldo, custo, tipo, quantidade, custoInformado, observacao);
    }

    private static RegistroDaSafra atividade (String id, String data, String talhao)
    {
        return new RegistroDaSafra ("atividade", id, "Mesmo texto", data, "Pendente", null, talhao, null, null, false);
    }

    private static RegistroDaSafra custo (String id, String data, String valor, String talhaoId)
    {
        return new RegistroDaSafra ("custo", id, "Custo", data, null, talhaoId, null, "Outros", new BigDecimal (valor), false);
    }

    private static RegistroDaSafra ocorrencia (String id, String data, String talhaoId, boolean excluida)
    {
        return new RegistroDaSafra ("ocorrencia", id, "Ocorrência", data, null, talhaoId, "Norte", null, null, excluida);
    }

    private static void cadastros ()
    {
        RespostaDeValidacao ok = atender (cadastro ("  Ana Souza ", "ANA@Teste.com", "(11) 91234-5678", "Senha@123", "Senha@123",
                                                    "Fazenda Sol", "120 ha", true), RespostaDeValidacao.class);
        verificar ("cadastro válido", ok.isValido ());
        verificar ("cadastro normaliza nome sem espaços e e-mail em minúsculas",
                   "Ana Souza".equals (ok.getNormalizados ().get ("name")) && "ana@teste.com".equals (ok.getNormalizados ().get ("email")));

        RespostaDeValidacao vazio = atender (cadastro (null, null, null, null, null, null, null, false), RespostaDeValidacao.class);
        verificar ("cadastro vazio acusa os 8 campos", vazio.getErros ().size () == 8 &&
                   vazio.getErros ().keySet ().containsAll (List.of ("name", "email", "phone", "password", "password-confirm", "farm", "area", "terms")));
        verificar ("cadastro inválido não devolve valores normalizados", vazio.getNormalizados ().isEmpty ());

        RespostaDeValidacao ruim = atender (cadastro ("Ana", "sem-arroba", "11 91234-5678", "fraca", "outra", "F", "1", true), RespostaDeValidacao.class);
        verificar ("e-mail inválido", "Digite um e-mail válido.".equals (ruim.getErros ().get ("email")));
        verificar ("telefone fora do formato", "Use o formato (DD) 9XXXX-XXXX.".equals (ruim.getErros ().get ("phone")));
        verificar ("senha fraca", "Atenda a todas as regras de senha.".equals (ruim.getErros ().get ("password")));
        verificar ("senha fraca não gera erro duplicado de confirmação", !"As senhas não coincidem.".equals (ruim.getErros ().get ("password-confirm")));

        RespostaDeValidacao diferente = atender (cadastro ("Ana", "a@b.co", "(11) 91234-5678", "Senha@123", "Senha@124", "F", "1", true), RespostaDeValidacao.class);
        verificar ("confirmação diferente", "As senhas não coincidem.".equals (diferente.getErros ().get ("password-confirm")) && diferente.getErros ().size () == 1);

        RespostaDeValidacao longo = atender (cadastro ("x".repeat (121), "a@b.co", "(11) 91234-5678", "Senha@123", "Senha@123", "F", "1", true), RespostaDeValidacao.class);
        verificar ("nome acima de 120 caracteres", "Use no máximo 120 caracteres.".equals (longo.getErros ().get ("name")));

        verificar ("senha só com espaço como símbolo é aceita", atender (new PedidoDeValidacaoDeSenha ("Senha 123", "Senha 123"), RespostaDeValidacao.class).isValido ());
        RespostaDeValidacao senha = atender (new PedidoDeValidacaoDeSenha ("senhafraca", ""), RespostaDeValidacao.class);
        verificar ("redefinir senha acusa senha e confirmação", senha.getErros ().containsKey ("password") && senha.getErros ().containsKey ("password-confirm"));
    }

    private static void talhoes ()
    {
        RespostaDeValidacao ok = atender (new PedidoDeValidacaoDeTalhao ("  Norte 1 ", "Soja", null, 12.5, null), RespostaDeValidacao.class);
        verificar ("talhão válido", ok.isValido ());
        verificar ("talhão aplica safra e status padrão", "2025/26".equals (ok.getNormalizados ().get ("safra")) && "Ativo".equals (ok.getNormalizados ().get ("status")));
        verificar ("talhão guarda nome normalizado em minúsculas", "norte 1".equals (ok.getNormalizados ().get ("nomeNormalizado")) && "Norte 1".equals (ok.getNormalizados ().get ("nome")));
        verificar ("talhão devolve hectares como número", Double.valueOf (12.5).equals (ok.getNormalizados ().get ("hectares")));

        verificar ("status vazio vale Ativo", "Ativo".equals (atender (new PedidoDeValidacaoDeTalhao ("T", "Milho", "2026/27", 1.0, ""), RespostaDeValidacao.class).getNormalizados ().get ("status")));

        RespostaDeValidacao ruim = atender (new PedidoDeValidacaoDeTalhao ("", "Banana", "1999/00", 0.0, "Quebrado"), RespostaDeValidacao.class);
        verificar ("talhão acusa nome, cultura, safra, hectares e status", ruim.getErros ().keySet ().containsAll (List.of ("nome", "cultura", "safra", "hectares", "status")) && ruim.getErros ().size () == 5);

        for (Double hectares : new Double[] { null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1000000.01 })
            verificar ("hectares inválidos recusados: " + hectares, atender (new PedidoDeValidacaoDeTalhao ("T", "Soja", null, hectares, null), RespostaDeValidacao.class).getErros ().containsKey ("hectares"));

        verificar ("hectares no limite (1.000.000) aceito", atender (new PedidoDeValidacaoDeTalhao ("T", "Soja", null, 1000000.0, null), RespostaDeValidacao.class).isValido ());
        verificar ("nome acima de 80 caracteres", atender (new PedidoDeValidacaoDeTalhao ("x".repeat (81), "Soja", null, 1.0, null), RespostaDeValidacao.class).getErros ().containsKey ("nome"));
        verificar ("cultura com acento (Café) aceita", atender (new PedidoDeValidacaoDeTalhao ("T", "Café", null, 1.0, "Irrigação pendente"), RespostaDeValidacao.class).isValido ());
    }

    private static void resumo ()
    {
        RespostaDeResumoDeTalhoes r = atender (new PedidoDeResumoDeTalhoes (List.of (
            new TalhaoParaResumo (12.5, "Ativo"), new TalhaoParaResumo (7.5, "Em preparo"), new TalhaoParaResumo (10, "Ativo"))), RespostaDeResumoDeTalhoes.class);
        verificar ("resumo soma hectares, conta total e ativos", r.getTotalHectares () == 30 && r.getQuantidadeTotal () == 3 && r.getQuantidadeAtivos () == 2);

        RespostaDeResumoDeTalhoes vazio = atender (new PedidoDeResumoDeTalhoes (new ArrayList<TalhaoParaResumo> ()), RespostaDeResumoDeTalhoes.class);
        verificar ("resumo de lista vazia", vazio.getTotalHectares () == 0 && vazio.getQuantidadeTotal () == 0 && vazio.getQuantidadeAtivos () == 0);

        verificar ("resumo sem lista é pedido inválido", RespostaDeErro.PEDIDO_INVALIDO.equals (erro (new PedidoDeResumoDeTalhoes (null)).getCodigo ()));
        verificar ("resumo com talhão nulo é pedido inválido", RespostaDeErro.PEDIDO_INVALIDO.equals (erro (new PedidoDeResumoDeTalhoes (java.util.Arrays.asList (new TalhaoParaResumo (1, "Ativo"), null))).getCodigo ()));
    }

    private static void estoque ()
    {
        RespostaDeMovimentacaoDeEstoque entrada = atender (movimento (10, 5, "entrada", 10.0, 7.0, null), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("entrada com custo pondera o custo médio: (10x5 + 10x7) / 20 = 6", entrada.isOk () && entrada.getSaldoAnterior () == 10 && entrada.getSaldoPosterior () == 20 && entrada.getCustoUnitario () == 6);

        RespostaDeMovimentacaoDeEstoque semCusto = atender (movimento (10, 5, "entrada", 2.5, null, null), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("entrada sem custo conserva o custo médio", semCusto.isOk () && semCusto.getSaldoPosterior () == 12.5 && semCusto.getCustoUnitario () == 5);

        RespostaDeMovimentacaoDeEstoque saida = atender (movimento (10, 5, "saida", 4.0, 99.0, null), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("saída baixa o saldo e ignora o custo informado", saida.isOk () && saida.getSaldoPosterior () == 6 && saida.getCustoUnitario () == 5);

        RespostaDeMovimentacaoDeEstoque tudo = atender (movimento (10, 5, "saida", 10.0, null, null), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("saída de todo o saldo deixa zero", tudo.isOk () && tudo.getSaldoPosterior () == 0);

        RespostaDeMovimentacaoDeEstoque falta = atender (movimento (10, 5, "saida", 10.000001, null, null), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("saída acima do saldo é recusada", RespostaDeMovimentacaoDeEstoque.SALDO_INSUFICIENTE.equals (falta.getSituacao ()) &&
                   "A saída supera o saldo disponível.".equals (falta.getErros ().get ("quantidade")) && falta.getSaldoPosterior () == 0);

        RespostaDeMovimentacaoDeEstoque ajuste = atender (movimento (10, 5, "ajuste", 3.0, null, "Contagem do inventário"), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("ajuste define o saldo", ajuste.isOk () && ajuste.getSaldoAnterior () == 10 && ajuste.getSaldoPosterior () == 3);
        verificar ("ajuste para zero é permitido", atender (movimento (10, 5, "ajuste", 0.0, null, "Perda total"), RespostaDeMovimentacaoDeEstoque.class).isOk ());
        verificar ("ajuste exige o motivo", atender (movimento (10, 5, "ajuste", 3.0, null, "  "), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("observacao"));

        verificar ("tipo inválido", atender (movimento (10, 5, "doacao", 1.0, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("tipo"));
        verificar ("tipo ausente", atender (movimento (10, 5, null, 1.0, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("tipo"));
        verificar ("quantidade ausente", atender (movimento (10, 5, "entrada", null, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("quantidade"));
        verificar ("entrada com quantidade zero", atender (movimento (10, 5, "entrada", 0.0, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("quantidade"));
        verificar ("quantidade negativa", atender (movimento (10, 5, "saida", -1.0, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("quantidade"));
        verificar ("quantidade NaN", atender (movimento (10, 5, "entrada", Double.NaN, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("quantidade"));
        verificar ("quantidade acima de um bilhão", atender (movimento (10, 5, "entrada", 1000000001.0, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("quantidade"));
        verificar ("mais de 6 casas decimais", "Use no máximo 6 casas decimais.".equals (atender (movimento (10, 5, "entrada", 0.1234567, null, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().get ("quantidade")));
        verificar ("custo informado negativo", atender (movimento (10, 5, "entrada", 1.0, -2.0, null), RespostaDeMovimentacaoDeEstoque.class).getErros ().containsKey ("custoUnitario"));

        RespostaDeMovimentacaoDeEstoque soma = atender (movimento (0.1, 0, "entrada", 0.2, null, null), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("0,1 + 0,2 = 0,3 (arredondamento de 6 casas)", soma.getSaldoPosterior () == 0.3);

        RespostaDeMovimentacaoDeEstoque primeira = atender (movimento (0, 0, "entrada", 4.0, 2.5, null), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("primeira entrada em estoque zerado assume o custo informado", primeira.getSaldoPosterior () == 4 && primeira.getCustoUnitario () == 2.5);

        verificar ("saldo atual negativo é pedido inválido", RespostaDeErro.PEDIDO_INVALIDO.equals (erro (movimento (-1, 5, "entrada", 1.0, null, null)).getCodigo ()));
        verificar ("custo atual NaN é pedido inválido", RespostaDeErro.PEDIDO_INVALIDO.equals (erro (movimento (1, Double.NaN, "entrada", 1.0, null, null)).getCodigo ()));
    }

    private static PedidoDeRelatorioDeSafra relatorio (String inicio, String fim, List<String> ids, List<TalhaoDaSafra> talhoes, List<RegistroDaSafra> registros)
    {
        return new PedidoDeRelatorioDeSafra (inicio, fim, ids, talhoes, registros);
    }

    private static void relatorioDeSafra ()
    {
        TalhaoDaSafra norte = new TalhaoDaSafra ("t1", "Norte", 12.5);
        List<String>  ids   = List.of ("t1");

        List<RegistroDaSafra> registros = new ArrayList<RegistroDaSafra> ();
        // o vínculo da atividade aceita a capitalização permitida no cadastro
        registros.add (atividade ("a1", "2026-01-01", "norte"));
        registros.add (atividade ("a2", "2026-12-31", "Norte"));
        registros.add (atividade ("a3", "12 ago", "Norte"));
        registros.add (atividade ("a4", "2027-01-01", "Norte"));
        registros.add (atividade ("a5", "2026-02-30", "Norte"));
        registros.add (atividade ("a6", "2026-01-01", "Inexistente"));
        registros.add (custo ("c1", "2026-06-01", "0.1", null));
        registros.add (custo ("c2", "2026-06-01", "0.2", null));
        registros.add (custo ("c3", "2027-01-01", "100", null));
        registros.add (custo ("c4", "2026-06-01", "0.1", "inexistente"));
        registros.add (ocorrencia ("o1", "2026-01-01", "t1", false));
        registros.add (ocorrencia ("o2", "2026-12-31", "t1", false));
        registros.add (ocorrencia ("o3", "2026-01-01", "t1", true));
        registros.add (ocorrencia ("o4", "2026-01-01", null, false));
        registros.add (ocorrencia ("o5", "2027-01-01", "t1", false));

        RespostaDeRelatorioDeSafra r = atender (relatorio ("2026-01-01", "2026-12-31", ids, List.of (norte), registros), RespostaDeRelatorioDeSafra.class);
        verificar ("área atual dos talhões vinculados", r.getAreaHectares () == 12.5 && r.getTalhoes ().size () == 1);
        verificar ("atividades: datas inclusivas e vínculo sem distinguir maiúsculas", r.getContagens ().get ("atividade") == 2);
        verificar ("ocorrências por talhão e data, sem as excluídas", r.getContagens ().get ("ocorrencia") == 2);
        verificar ("custos gerais da safra", r.getContagens ().get ("custo") == 2);
        verificar ("soma exata 0,10 + 0,20 = 30 centavos", r.getTotalCentavos () == 30);
        verificar ("datas e vínculos inválidos viram 6 pendências", r.getPendencias ().size () == 6);
        verificar ("toda pendência tem motivo", r.getPendencias ().stream ().allMatch (p -> p.getMotivo () != null && !p.getMotivo ().isBlank ()));
        verificar ("registros contabilizados ordenados por data", "2026-01-01".equals (r.getRegistros ().get (0).getData ()) &&
                   "2026-12-31".equals (r.getRegistros ().get (r.getRegistros ().size () - 1).getData ()) && r.getRegistros ().size () == 6);

        ItemDoRelatorio c4 = r.getPendencias ().stream ().filter (p -> "c4".equals (p.getId ())).findFirst ().get ();
        verificar ("custo de talhão inexistente: pendência com valor e categoria", c4.getValorCentavos () == 10 && "Outros".equals (c4.getCategoria ()) && "Sem talhão".equals (c4.getTalhao ()));

        RespostaDeRelatorioDeSafra vazio = atender (relatorio ("2026-01-01", "2026-12-31", ids, List.of (norte), new ArrayList<RegistroDaSafra> ()), RespostaDeRelatorioDeSafra.class);
        verificar ("conjunto vazio real", vazio.getRegistros ().isEmpty () && vazio.getTotalCentavos () == 0 && vazio.getPendencias ().isEmpty ());

        RespostaDeRelatorioDeSafra semTalhao = atender (relatorio ("2026-01-01", "2026-12-31", ids, new ArrayList<TalhaoDaSafra> (), new ArrayList<RegistroDaSafra> ()), RespostaDeRelatorioDeSafra.class);
        verificar ("talhão removido não inventa área", semTalhao.getAreaHectares () == 0 && semTalhao.getPendencias ().size () == 1);

        for (String fim : List.of ("2025-01-01", "2026-02-30", "sem data"))
            verificar ("período inválido rejeitado (" + fim + ")", RespostaDeErro.DADOS_INCONSISTENTES.equals (erro (relatorio ("2026-01-01", fim, ids, List.of (norte), new ArrayList<RegistroDaSafra> ())).getCodigo ()));

        verificar ("custo negativo interrompe o relatório (nunca vira total errado)",
                   RespostaDeErro.DADOS_INCONSISTENTES.equals (erro (relatorio ("2026-01-01", "2026-12-31", ids, List.of (norte), List.of (custo ("c", "2026-06-01", "-1", null)))).getCodigo ()));
        verificar ("custo sem valor interrompe o relatório",
                   RespostaDeErro.DADOS_INCONSISTENTES.equals (erro (relatorio ("2026-01-01", "2026-12-31", ids, List.of (norte), List.of (new RegistroDaSafra ("custo", "c", "x", "2026-06-01", null, null, null, null, null, false)))).getCodigo ()));
        verificar ("tipo de registro desconhecido é pedido inválido",
                   RespostaDeErro.PEDIDO_INVALIDO.equals (erro (relatorio ("2026-01-01", "2026-12-31", ids, List.of (norte), List.of (new RegistroDaSafra ("outro", "x", "x", "2026-06-01", null, null, null, null, null, false)))).getCodigo ()));
        verificar ("listas ausentes são pedido inválido", RespostaDeErro.PEDIDO_INVALIDO.equals (erro (relatorio ("2026-01-01", "2026-12-31", null, null, null)).getCodigo ()));

        List<RegistroDaSafra> muitos = new ArrayList<RegistroDaSafra> ();
        for (int i = 0; i < 1000; i++)
            muitos.add (custo ("c" + i, "2026-06-01", "0.01", "t1"));
        verificar ("mil custos de 1 centavo somam exatos 1000 centavos", atender (relatorio ("2026-01-01", "2026-12-31", ids, List.of (norte), muitos), RespostaDeRelatorioDeSafra.class).getTotalCentavos () == 1000);
    }

    public static void main (String[] args) throws Exception
    {
        RespostaDePing ping = atender (new PedidoDePing (), RespostaDePing.class);
        verificar ("ping devolve a versão do protocolo", protocolo.Protocolo.VERSAO.equals (ping.getVersao ()));

        TesteAlgoritmos.cadastros ();
        TesteAlgoritmos.talhoes ();
        TesteAlgoritmos.resumo ();
        TesteAlgoritmos.estoque ();
        TesteAlgoritmos.relatorioDeSafra ();

        // um pedido de tipo que o servidor nao conhece vira erro, nunca excecao
        Pedido desconhecido = new Pedido () {};
        verificar ("pedido desconhecido vira PEDIDO_DESCONHECIDO", RespostaDeErro.PEDIDO_DESCONHECIDO.equals (erro (desconhecido).getCodigo ()));

        System.out.println ("Algoritmos: " + TesteAlgoritmos.total + " verificações aprovadas.");
    }
}
