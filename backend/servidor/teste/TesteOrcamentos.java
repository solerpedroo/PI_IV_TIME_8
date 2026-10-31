/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
import com.mongodb.ConnectionString;
import dados.ConexaoMongo;
import dados.RepositorioAtividades;
import dados.RepositorioAtividadesMemoria;
import dados.RepositorioCustos;
import dados.RepositorioCustosMemoria;
import dados.RepositorioCustosMongo;
import dados.RepositorioOrcamentos;
import dados.RepositorioOrcamentosMemoria;
import dados.RepositorioOrcamentosMongo;
import dados.RepositorioSafras;
import dados.RepositorioSafrasMemoria;
import dados.RepositorioSafrasMongo;
import dados.RepositorioTalhoes;
import dados.RepositorioTalhoesMemoria;
import dados.RepositorioTalhoesMongo;
import handlers.CustosHandler;
import handlers.OrcamentosHandler;
import handlers.SafrasHandler;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;

/*
    Orcamento por safra (#226, #227, #228) sem JUnit, direto no roteador.
    Uso: java -cp "bin:lib/*" TesteOrcamentos            (memoria)
         java -cp "bin:lib/*" TesteOrcamentos --mongo    (MongoDB real, banco terminado em _teste)
    No modo --mongo tambem confere o indice unico e a leitura depois de "reiniciar" (repositorios novos).
*/
public class TesteOrcamentos
{
    private static int total  = 0;
    private static int falhas = 0;

    private static void conferir (String nome, boolean condicao)
    {
        TesteOrcamentos.total++;

        if (condicao)
            System.out.println ("ok " + nome);
        else
        {
            TesteOrcamentos.falhas++;
            System.out.println ("FALHA " + nome);
        }
    }

    private static Resposta chamar (Roteador roteador, String token, String metodo, String caminho, String corpo) throws Exception
    {
        String[]                partes   = caminho.split ("\\?", 2);
        HashMap<String, String> consulta = new HashMap<String, String> ();
        HashMap<String, String> cabecalhos = new HashMap<String, String> ();

        if (partes.length > 1)
            for (String par : partes[1].split ("&"))
            {
                String[] valor = par.split ("=", 2);
                consulta.put (valor[0], valor.length > 1 ? valor[1] : "");
            }

        if (token != null)
            cabecalhos.put ("authorization", "Bearer " + token);

        return roteador.tratar (new Requisicao (metodo, partes[0], consulta, cabecalhos,
                                                corpo == null ? new byte[0] : corpo.getBytes (StandardCharsets.UTF_8),
                                                "127.0.0.1"));
    }

    private static String texto (Resposta resposta)
    {
        return new String (resposta.getCorpo (), StandardCharsets.UTF_8);
    }

    private static Document json (Resposta resposta)
    {
        return Document.parse (TesteOrcamentos.texto (resposta));
    }

    private static List<Document> lista (Resposta resposta)
    {
        return Document.parse ("{\"itens\":" + TesteOrcamentos.texto (resposta) + "}").getList ("itens", Document.class);
    }

    private static Document daSafra (List<Document> itens, String safraId)
    {
        Document ret = null;

        for (Document item : itens)
            if (safraId.equals (item.getString ("safraId")))
                ret = item;

        return ret;
    }

    private static boolean igual (Object valor, double esperado)
    {
        return valor instanceof Number && Math.abs (((Number) valor).doubleValue () - esperado) < 0.0001;
    }

    private static String talhao (RepositorioTalhoes talhoes, String propriedadeId, String nome, double hectares) throws Exception
    {
        Document talhao = new Document ("propriedadeId", propriedadeId).append ("nome", nome)
                              .append ("nomeNormalizado", nome.toLowerCase ()).append ("cultura", "Soja")
                              .append ("hectares", hectares);
        talhoes.criar (talhao);
        return talhao.getObjectId ("_id").toHexString ();
    }

    private static String safra (RepositorioSafras safras, String propriedadeId, String nome, String talhaoId,
                                 String inicio, String fim) throws Exception
    {
        ArrayList<String> vinculos = new ArrayList<String> ();
        vinculos.add (talhaoId);

        Document safra = new Document ("propriedadeId", propriedadeId).append ("nome", nome)
                             .append ("nomeNormalizado", nome.toLowerCase ()).append ("cultura", "Soja")
                             .append ("talhoes", vinculos).append ("dataInicio", inicio).append ("dataFim", fim)
                             .append ("etapa", "Planejamento")
                             .append ("historicoEtapas", new ArrayList<Document> ());
        safras.criar (safra);
        return safra.getObjectId ("_id").toHexString ();
    }

    private static String custo (String descricao, double valor, String talhaoId, String safraId)
    {
        return new Document ("description", descricao).append ("category", "Insumos").append ("value", valor)
                   .append ("date", "2026-03-10").append ("talhaoId", talhaoId).append ("safraId", safraId).toJson ();
    }

    private static String uriTeste () throws Exception
    {
        String uri   = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null : new ConnectionString (uri).getDatabase ();

        if (banco == null || !banco.endsWith ("_teste"))
            throw new Exception ("Use MONGODB_URI com banco terminado em _teste");

        return uri;
    }

    private static void testarSituacao ()
    {
        TesteOrcamentos.conferir ("sem planejado -> sem-orcamento", "sem-orcamento".equals (OrcamentosHandler.situacao (0, 500, 80)));
        TesteOrcamentos.conferir ("79% com alerta 80 -> dentro",    "dentro".equals  (OrcamentosHandler.situacao (10000, 7900, 80)));
        TesteOrcamentos.conferir ("80% com alerta 80 -> proximo",   "proximo".equals (OrcamentosHandler.situacao (10000, 8000, 80)));
        TesteOrcamentos.conferir ("100% exato -> proximo",          "proximo".equals (OrcamentosHandler.situacao (10000, 10000, 80)));
        TesteOrcamentos.conferir ("1 centavo acima -> acima",       "acima".equals   (OrcamentosHandler.situacao (10000, 10001, 80)));
        TesteOrcamentos.conferir ("99% com alerta 100 -> dentro",   "dentro".equals  (OrcamentosHandler.situacao (10000, 9900, 100)));
    }

    public static void main (String[] args) throws Exception
    {
        boolean      mongo   = args.length > 0 && args[0].equals ("--mongo");
        ConexaoMongo conexao = mongo ? new ConexaoMongo (TesteOrcamentos.uriTeste ()) : null;

        try
        {
            TesteOrcamentos.testarSituacao ();

            if (mongo)
                conexao.getBanco ().drop ();

            RepositorioTalhoes    talhoes    = mongo ? new RepositorioTalhoesMongo (conexao.getBanco ())    : new RepositorioTalhoesMemoria ();
            RepositorioSafras     safras     = mongo ? new RepositorioSafrasMongo (conexao.getBanco ())     : new RepositorioSafrasMemoria ();
            RepositorioCustos     custos     = mongo ? new RepositorioCustosMongo (conexao.getBanco ())     : new RepositorioCustosMemoria ();
            RepositorioOrcamentos orcamentos = mongo ? new RepositorioOrcamentosMongo (conexao.getBanco ()) : new RepositorioOrcamentosMemoria ();
            RepositorioAtividades atividades = new RepositorioAtividadesMemoria ();

            String propriedade = new ObjectId ().toHexString ();
            String outra       = new ObjectId ().toHexString ();
            String norte       = TesteOrcamentos.talhao (talhoes, propriedade, "Norte", 40);
            String sul         = TesteOrcamentos.talhao (talhoes, propriedade, "Sul",   10);
            String estranho    = TesteOrcamentos.talhao (talhoes, outra,       "Leste", 5);
            String verao       = TesteOrcamentos.safra   (safras, propriedade, "Soja verao",   norte, "2026-01-01", "2026-06-30");
            String inverno     = TesteOrcamentos.safra   (safras, propriedade, "Milho inverno", sul,  "2026-07-01", "2026-12-31");
            String alheia      = TesteOrcamentos.safra   (safras, outra,       "Alheia",        estranho, "2026-01-01", "2026-06-30");

            Sessoes  sessoes  = new Sessoes ();
            Roteador roteador = new Roteador (sessoes);
            String   a        = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            String   b        = sessoes.criar (new ObjectId ().toHexString (), outra);

            new SafrasHandler     (safras, talhoes, custos, atividades, sessoes).registrar (roteador);
            new CustosHandler     (custos, safras, talhoes, sessoes).registrar (roteador);
            new OrcamentosHandler (orcamentos, safras, talhoes, custos, sessoes).registrar (roteador);

            String rotaVerao   = "/api/orcamentos/" + verao;
            String rotaInverno = "/api/orcamentos/" + inverno;

            // ---------------------------------------------------------------- acesso
            TesteOrcamentos.conferir ("sem token -> 401", TesteOrcamentos.chamar (roteador, null, "GET", "/api/orcamentos", null).getStatus () == 401);
            TesteOrcamentos.conferir ("POST na lista -> 405", TesteOrcamentos.chamar (roteador, a, "POST", "/api/orcamentos", "{}").getStatus () == 405);

            // ---------------------------------------------------------------- sem orcamento nao inventa meta
            List<Document> inicio = TesteOrcamentos.lista (TesteOrcamentos.chamar (roteador, a, "GET", "/api/orcamentos", null));
            Document       vazio  = TesteOrcamentos.daSafra (inicio, verao);
            TesteOrcamentos.conferir ("lista traz as 2 safras da propriedade e nenhuma alheia",
                                      inicio.size () == 2 && TesteOrcamentos.daSafra (inicio, alheia) == null);
            TesteOrcamentos.conferir ("safra sem orcamento: planejado, desvio e percentual null",
                                      vazio != null && vazio.get ("planejado") == null && vazio.get ("desvio") == null &&
                                      vazio.get ("percentualUsado") == null && "sem-orcamento".equals (vazio.getString ("situacao")));
            TesteOrcamentos.conferir ("safra sem orcamento: realizado 0 e area dos talhoes",
                                      TesteOrcamentos.igual (vazio.get ("realizado"), 0) && TesteOrcamentos.igual (vazio.get ("areaHectares"), 40));

            // ---------------------------------------------------------------- validacao
            Resposta semValor = TesteOrcamentos.chamar (roteador, a, "PUT", rotaVerao, "{}");
            TesteOrcamentos.conferir ("sem valor -> 400 no campo valor",
                                      semValor.getStatus () == 400 && TesteOrcamentos.json (semValor).get ("fields", Document.class).containsKey ("valor"));

            String[] invalidos = { "{\"valor\":0}", "{\"valor\":-10}", "{\"valor\":\"1000\"}", "{\"valor\":2000000000}",
                                   "{\"valor\":1000,\"alerta\":30}", "{\"valor\":1000,\"alerta\":101}",
                                   "{\"valor\":1000,\"alerta\":85.5}", "{\"valor\":1000,\"alerta\":\"80\"}" };
            boolean todos400 = true;
            for (int i = 0; i < invalidos.length; i++)
                if (TesteOrcamentos.chamar (roteador, a, "PUT", rotaVerao, invalidos[i]).getStatus () != 400)
                    todos400 = false;
            TesteOrcamentos.conferir ("valor <= 0, texto, acima do maximo e alerta fora de 50..100 -> 400", todos400);
            TesteOrcamentos.conferir ("JSON invalido -> 400",
                                      TesteOrcamentos.chamar (roteador, a, "PUT", rotaVerao, "valor=10").getStatus () == 400);
            TesteOrcamentos.conferir ("nada gravado depois das recusas", orcamentos.listar (propriedade).isEmpty ());

            TesteOrcamentos.conferir ("safra inexistente -> 404",
                                      TesteOrcamentos.chamar (roteador, a, "PUT", "/api/orcamentos/" + new ObjectId ().toHexString (), "{\"valor\":10}").getStatus () == 404);
            TesteOrcamentos.conferir ("id que nao e ObjectId -> 404",
                                      TesteOrcamentos.chamar (roteador, a, "PUT", "/api/orcamentos/xyz", "{\"valor\":10}").getStatus () == 404);
            TesteOrcamentos.conferir ("safra de outra propriedade -> 404",
                                      TesteOrcamentos.chamar (roteador, a, "PUT", "/api/orcamentos/" + alheia, "{\"valor\":10}").getStatus () == 404);

            // ---------------------------------------------------------------- criar e acompanhar
            Resposta criado = TesteOrcamentos.chamar (roteador, a, "PUT", rotaVerao, "{\"valor\":1000}");
            Document item   = TesteOrcamentos.json (criado);
            TesteOrcamentos.conferir ("primeira definicao -> 201", criado.getStatus () == 201);
            TesteOrcamentos.conferir ("planejado 1000 com alerta padrao 80 e situacao dentro",
                                      TesteOrcamentos.igual (item.get ("planejado"), 1000) && item.getInteger ("alerta").intValue () == 80 &&
                                      "dentro".equals (item.getString ("situacao")) && TesteOrcamentos.igual (item.get ("percentualUsado"), 0));
            TesteOrcamentos.conferir ("planejado por hectare usa a area da safra (1000 / 40 ha)",
                                      TesteOrcamentos.igual (item.get ("planejadoPorHectare"), 25));

            TesteOrcamentos.chamar (roteador, a, "POST", "/api/custos", TesteOrcamentos.custo ("Semente", 300,    norte, verao));
            TesteOrcamentos.chamar (roteador, a, "POST", "/api/custos", TesteOrcamentos.custo ("Adubo",   450.25, null,  verao));
            TesteOrcamentos.chamar (roteador, a, "POST", "/api/custos", TesteOrcamentos.custo ("Diesel",  999,    null,  null));
            TesteOrcamentos.chamar (roteador, a, "POST", "/api/custos", TesteOrcamentos.custo ("Ureia",   200,    sul,   inverno));

            item = TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "GET", rotaVerao, null));
            TesteOrcamentos.conferir ("realizado soma so os custos da safra (300 + 450,25)",
                                      TesteOrcamentos.igual (item.get ("realizado"), 750.25) && item.getInteger ("lancamentos").intValue () == 2);
            TesteOrcamentos.conferir ("realizado igual ao total de /api/custos/resumo?safraId=",
                                      TesteOrcamentos.igual (TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "GET", "/api/custos/resumo?safraId=" + verao, null)).get ("total"), 750.25));
            TesteOrcamentos.conferir ("saldo 249,75, desvio -249,75 e -25%",
                                      TesteOrcamentos.igual (item.get ("saldo"), 249.75) && TesteOrcamentos.igual (item.get ("desvio"), -249.75) &&
                                      TesteOrcamentos.igual (item.get ("desvioPercentual"), -25));
            TesteOrcamentos.conferir ("75% usado -> dentro",
                                      TesteOrcamentos.igual (item.get ("percentualUsado"), 75) && "dentro".equals (item.getString ("situacao")));

            TesteOrcamentos.chamar (roteador, a, "POST", "/api/custos", TesteOrcamentos.custo ("Defensivo", 100, norte, verao));
            item = TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "GET", rotaVerao, null));
            TesteOrcamentos.conferir ("85% usado com alerta 80 -> proximo",
                                      TesteOrcamentos.igual (item.get ("percentualUsado"), 85) && "proximo".equals (item.getString ("situacao")));

            Resposta alterado = TesteOrcamentos.chamar (roteador, a, "PUT", rotaVerao, "{\"valor\":800}");
            item = TesteOrcamentos.json (alterado);
            TesteOrcamentos.conferir ("alteracao -> 200 e mantem o alerta", alterado.getStatus () == 200 && item.getInteger ("alerta").intValue () == 80);
            TesteOrcamentos.conferir ("850,25 sobre 800 -> acima, desvio 50,25 e 6,3%",
                                      "acima".equals (item.getString ("situacao")) && TesteOrcamentos.igual (item.get ("desvio"), 50.25) &&
                                      TesteOrcamentos.igual (item.get ("desvioPercentual"), 6.3) && TesteOrcamentos.igual (item.get ("saldo"), -50.25));

            item = TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "PUT", rotaVerao, "{\"valor\":1000,\"alerta\":90}"));
            TesteOrcamentos.conferir ("alerta 90 com 85% -> dentro",
                                      item.getInteger ("alerta").intValue () == 90 && "dentro".equals (item.getString ("situacao")));
            TesteOrcamentos.conferir ("continua um unico orcamento por safra", orcamentos.listar (propriedade).size () == 1);

            // excluir o custo tira do realizado
            String defensivo = null;
            for (Document custo : custos.listar (propriedade))
                if ("Defensivo".equals (custo.getString ("description")))
                    defensivo = custo.getObjectId ("_id").toHexString ();
            TesteOrcamentos.chamar (roteador, a, "DELETE", "/api/custos/" + defensivo, null);
            item = TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "GET", rotaVerao, null));
            TesteOrcamentos.conferir ("custo excluido sai do realizado", TesteOrcamentos.igual (item.get ("realizado"), 750.25));

            // ---------------------------------------------------------------- isolamento e contexto
            List<Document> deB = TesteOrcamentos.lista (TesteOrcamentos.chamar (roteador, b, "GET", "/api/orcamentos", null));
            TesteOrcamentos.conferir ("B so ve a propria safra, sem orcamento", deB.size () == 1 && TesteOrcamentos.daSafra (deB, alheia) != null &&
                                      TesteOrcamentos.daSafra (deB, alheia).get ("planejado") == null);
            TesteOrcamentos.conferir ("B nao consulta, altera nem exclui orcamento de A",
                                      TesteOrcamentos.chamar (roteador, b, "GET",    rotaVerao, null).getStatus () == 404 &&
                                      TesteOrcamentos.chamar (roteador, b, "PUT",    rotaVerao, "{\"valor\":1}").getStatus () == 404 &&
                                      TesteOrcamentos.chamar (roteador, b, "DELETE", rotaVerao, null).getStatus () == 404);

            // a sessao lida pelo roteador e trocada de propriedade antes da escrita (outra aba)
            String                  trocada    = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            HashMap<String, String> cabecalhos = new HashMap<String, String> ();
            HashMap<String, String> parametro  = new HashMap<String, String> ();
            cabecalhos.put ("authorization", "Bearer " + trocada);
            parametro.put  ("safraId", verao);
            Requisicao requisicao = new Requisicao ("PUT", rotaVerao, new HashMap<String, String> (), cabecalhos,
                                                    "{\"valor\":5}".getBytes (StandardCharsets.UTF_8), "127.0.0.1");
            requisicao.setSessao     (sessoes.buscar (trocada));
            requisicao.setParametros (parametro);
            sessoes.selecionarPropriedade (trocada, outra);
            Resposta mudou = new OrcamentosHandler (orcamentos, safras, talhoes, custos, sessoes).tratar (requisicao);
            TesteOrcamentos.conferir ("propriedade trocada no meio da escrita -> 409", mudou.getStatus () == 409);

            // ---------------------------------------------------------------- remover e safra finalizada
            TesteOrcamentos.conferir ("DELETE -> 204", TesteOrcamentos.chamar (roteador, a, "DELETE", rotaVerao, null).getStatus () == 204);
            item = TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "GET", rotaVerao, null));
            TesteOrcamentos.conferir ("depois de remover volta a sem-orcamento e mantem o realizado",
                                      item.get ("planejado") == null && "sem-orcamento".equals (item.getString ("situacao")) &&
                                      TesteOrcamentos.igual (item.get ("realizado"), 750.25));
            TesteOrcamentos.conferir ("DELETE de novo -> 404", TesteOrcamentos.chamar (roteador, a, "DELETE", rotaVerao, null).getStatus () == 404);

            TesteOrcamentos.chamar (roteador, a, "PUT", rotaInverno, "{\"valor\":150,\"alerta\":50}");
            item = TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "GET", rotaInverno, null));
            TesteOrcamentos.conferir ("200 sobre 150 -> acima (inverno)", "acima".equals (item.getString ("situacao")) &&
                                      TesteOrcamentos.igual (item.get ("realizadoPorHectare"), 20));

            for (int i = 0; i < 5; i++)
                TesteOrcamentos.chamar (roteador, a, "POST", "/api/safras/" + inverno + "/avancar", "{}");

            item = TesteOrcamentos.json (TesteOrcamentos.chamar (roteador, a, "GET", rotaInverno, null));
            TesteOrcamentos.conferir ("safra finalizada continua consultavel com o planejado",
                                      item.getBoolean ("finalizada").booleanValue () && TesteOrcamentos.igual (item.get ("planejado"), 150));
            TesteOrcamentos.conferir ("safra finalizada: PUT e DELETE -> 409",
                                      TesteOrcamentos.chamar (roteador, a, "PUT",    rotaInverno, "{\"valor\":500}").getStatus () == 409 &&
                                      TesteOrcamentos.chamar (roteador, a, "DELETE", rotaInverno, null).getStatus () == 409);

            // ---------------------------------------------------------------- Mongo: reinicio e indice unico
            if (mongo)
            {
                RepositorioOrcamentos depois = new RepositorioOrcamentosMongo (conexao.getBanco ());
                Document              lido   = depois.buscarPorSafra (propriedade, inverno);
                TesteOrcamentos.conferir ("Mongo: orcamento lido por um repositorio novo (reinicio)",
                                          lido != null && TesteOrcamentos.igual (lido.get ("valor"), 150) && lido.getString ("criadoEm") != null);

                Document repetido = new Document ("propriedadeId", propriedade).append ("safraId", inverno)
                                        .append ("valor", 999.0).append ("alerta", 80).append ("atualizadoEm", "x");
                TesteOrcamentos.conferir ("Mongo: salvar de novo altera, nao duplica",
                                          !depois.salvar (repetido) && conexao.getBanco ().getCollection ("orcamentos")
                                                                                .countDocuments (new Document ("safraId", inverno)) == 1);
                TesteOrcamentos.conferir ("Mongo: criadoEm original preservado",
                                          lido.getString ("criadoEm").equals (depois.buscarPorSafra (propriedade, inverno).getString ("criadoEm")));

                boolean recusou = false;
                try
                {
                    conexao.getBanco ().getCollection ("orcamentos").insertOne (new Document ("propriedadeId", propriedade)
                                                                                    .append ("safraId", inverno));
                }
                catch (Exception erro)
                {
                    recusou = true;
                }
                TesteOrcamentos.conferir ("Mongo: indice unico recusa segundo orcamento da mesma safra", recusou);
            }
        }
        catch (Exception erro)
        {
            TesteOrcamentos.falhas++;
            System.out.println ("FALHA inesperada: " + erro);
            erro.printStackTrace ();
        }
        finally
        {
            if (conexao != null)
            {
                conexao.getBanco ().drop ();
                conexao.fechar ();
            }
        }

        System.out.println ("TesteOrcamentos: " + (TesteOrcamentos.total - TesteOrcamentos.falhas) + "/" +
                            TesteOrcamentos.total + " verifica\u00e7\u00f5es OK" + (mongo ? " (MongoDB)" : " (mem\u00f3ria)"));

        if (TesteOrcamentos.falhas > 0)
            System.exit (1);
    }
}
