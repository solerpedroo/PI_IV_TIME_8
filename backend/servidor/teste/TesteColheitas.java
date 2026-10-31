/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
import com.mongodb.ConnectionString;
import dados.ConexaoMongo;
import dados.RepositorioAtividadesMemoria;
import dados.RepositorioColheitas;
import dados.RepositorioColheitasMemoria;
import dados.RepositorioColheitasMongo;
import dados.RepositorioCustosMemoria;
import dados.RepositorioSafras;
import dados.RepositorioSafrasMemoria;
import dados.RepositorioSafrasMongo;
import dados.RepositorioTalhoes;
import dados.RepositorioTalhoesMemoria;
import dados.RepositorioTalhoesMongo;
import handlers.ColheitasHandler;
import handlers.SafrasHandler;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;

/*
    Colheita e produtividade (#222, #223, #224) sem JUnit, direto no roteador.
    Uso: java -cp "bin:lib/*" TesteColheitas            (memoria)
         java -cp "bin:lib/*" TesteColheitas --mongo    (MongoDB real, banco terminado em _teste)
    Conjunto conhecido: Soja 24/25 colhe 150 t em 50 ha (3.000 kg/ha = 50 sc/ha) e
    Soja 25/26 colhe 186 t nos mesmos 50 ha (3.720 kg/ha = 62 sc/ha, +24%).
*/
public class TesteColheitas
{
    private static int total  = 0;
    private static int falhas = 0;

    private static void conferir (String nome, boolean condicao)
    {
        TesteColheitas.total++;

        if (condicao)
            System.out.println ("ok " + nome);
        else
        {
            TesteColheitas.falhas++;
            System.out.println ("FALHA " + nome);
        }
    }

    private static Resposta chamar (Roteador roteador, String token, String metodo, String caminho, String corpo) throws Exception
    {
        String[]                partes     = caminho.split ("\\?", 2);
        HashMap<String, String> consulta   = new HashMap<String, String> ();
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
        return Document.parse (TesteColheitas.texto (resposta));
    }

    private static List<Document> lista (Resposta resposta)
    {
        return Document.parse ("{\"itens\":" + TesteColheitas.texto (resposta) + "}").getList ("itens", Document.class);
    }

    private static Document item (List<Document> itens, String chave, String valor)
    {
        Document ret = null;

        for (Document item : itens)
            if (valor.equals (item.getString (chave)))
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

    private static String safra (RepositorioSafras safras, String propriedadeId, String nome, String cultura, String etapa,
                                 String inicio, String fim, String... vinculos) throws Exception
    {
        ArrayList<String> lista = new ArrayList<String> ();
        for (int i = 0; i < vinculos.length; i++)
            lista.add (vinculos[i]);

        Document safra = new Document ("propriedadeId", propriedadeId).append ("nome", nome)
                             .append ("nomeNormalizado", nome.toLowerCase ()).append ("cultura", cultura)
                             .append ("talhoes", lista).append ("dataInicio", inicio).append ("dataFim", fim)
                             .append ("etapa", etapa).append ("historicoEtapas", new ArrayList<Document> ());
        safras.criar (safra);
        return safra.getObjectId ("_id").toHexString ();
    }

    private static String colheita (String safraId, String talhaoId, double quantidade, String unidade, String data)
    {
        return new Document ("safraId", safraId).append ("talhaoId", talhaoId).append ("quantidade", quantidade)
                   .append ("unidade", unidade).append ("data", data).toJson ();
    }

    private static int status (Roteador roteador, String token, String metodo, String caminho, String corpo) throws Exception
    {
        return TesteColheitas.chamar (roteador, token, metodo, caminho, corpo).getStatus ();
    }

    private static String uriTeste () throws Exception
    {
        String uri   = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null : new ConnectionString (uri).getDatabase ();

        if (banco == null || !banco.endsWith ("_teste"))
            throw new Exception ("Use MONGODB_URI com banco terminado em _teste");

        return uri;
    }

    public static void main (String[] args) throws Exception
    {
        boolean      mongo   = args.length > 0 && args[0].equals ("--mongo");
        ConexaoMongo conexao = mongo ? new ConexaoMongo (TesteColheitas.uriTeste ()) : null;

        try
        {
            TesteColheitas.conferir ("1 t = 1.000 kg e 1 sc = 60 kg",
                                     ColheitasHandler.fator ("t") == 1000 && ColheitasHandler.fator ("sc") == 60 &&
                                     ColheitasHandler.fator ("kg") == 1);

            if (mongo)
                conexao.getBanco ().drop ();

            RepositorioTalhoes   talhoes   = mongo ? new RepositorioTalhoesMongo (conexao.getBanco ())   : new RepositorioTalhoesMemoria ();
            RepositorioSafras    safras    = mongo ? new RepositorioSafrasMongo (conexao.getBanco ())    : new RepositorioSafrasMemoria ();
            RepositorioColheitas colheitas = mongo ? new RepositorioColheitasMongo (conexao.getBanco ()) : new RepositorioColheitasMemoria ();

            String propriedade = new ObjectId ().toHexString ();
            String outra       = new ObjectId ().toHexString ();
            String norte       = TesteColheitas.talhao (talhoes, propriedade, "Norte", 40);
            String sul         = TesteColheitas.talhao (talhoes, propriedade, "Sul",   10);
            String leste       = TesteColheitas.talhao (talhoes, propriedade, "Leste", 0);
            String oeste       = TesteColheitas.talhao (talhoes, outra,       "Oeste", 30);
            String antiga      = TesteColheitas.safra (safras, propriedade, "Soja 24/25", "Soja",  "Colheita",
                                                       "2025-01-01", "2025-06-30", norte, sul);
            String atual       = TesteColheitas.safra (safras, propriedade, "Soja 25/26", "Soja",  "Plantio",
                                                       "2026-01-01", "2026-06-30", norte, sul);
            String milho       = TesteColheitas.safra (safras, propriedade, "Milho 26",   "Milho", "Manejo",
                                                       "2026-01-01", "2026-06-30", leste);
            String trigo       = TesteColheitas.safra (safras, propriedade, "Trigo 26",   "Trigo", "Planejamento",
                                                       "2026-07-01", "2026-12-31", leste);
            String alheia      = TesteColheitas.safra (safras, outra,       "Alheia",     "Soja",  "Colheita",
                                                       "2026-01-01", "2026-06-30", oeste);

            Sessoes  sessoes  = new Sessoes ();
            Roteador roteador = new Roteador (sessoes);
            String   a        = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            String   b        = sessoes.criar (new ObjectId ().toHexString (), outra);

            SafrasHandler handlerSafras = new SafrasHandler (safras, talhoes, new RepositorioCustosMemoria (),
                                                             new RepositorioAtividadesMemoria (), sessoes);
            handlerSafras.setColheitas (colheitas);
            handlerSafras.registrar (roteador);
            new ColheitasHandler (colheitas, safras, talhoes, sessoes).registrar (roteador);

            String rota = "/api/colheitas";

            // ---------------------------------------------------------------- acesso e validacao
            TesteColheitas.conferir ("sem token -> 401", TesteColheitas.status (roteador, null, "GET", rota, null) == 401);

            Resposta vazia  = TesteColheitas.chamar (roteador, a, "POST", rota, "{}");
            Document campos = vazia.getStatus () == 400 ? TesteColheitas.json (vazia).get ("fields", Document.class) : new Document ();
            TesteColheitas.conferir ("corpo vazio -> 400 em safra, talhao, quantidade, unidade e data",
                                     campos.containsKey ("safraId") && campos.containsKey ("talhaoId") && campos.containsKey ("quantidade") &&
                                     campos.containsKey ("unidade") && campos.containsKey ("data"));

            String amanha = LocalDate.now (ZoneId.of ("America/Sao_Paulo")).plusDays (1).toString ();
            String[][] recusas = {
                { "quantidade zero",               TesteColheitas.colheita (atual, norte, 0,    "kg", "2026-03-01"), "quantidade" },
                { "quantidade negativa",           TesteColheitas.colheita (atual, norte, -5,   "kg", "2026-03-01"), "quantidade" },
                { "unidade fora da lista",         TesteColheitas.colheita (atual, norte, 10,   "saca", "2026-03-01"), "unidade" },
                { "talhao fora da safra",          TesteColheitas.colheita (atual, leste, 10,   "kg", "2026-03-01"), "talhaoId" },
                { "talhao de outra propriedade",   TesteColheitas.colheita (atual, oeste, 10,   "kg", "2026-03-01"), "talhaoId" },
                { "safra de outra propriedade",    TesteColheitas.colheita (alheia, oeste, 10,  "kg", "2026-03-01"), "safraId" },
                { "data antes do inicio da safra", TesteColheitas.colheita (atual, norte, 10,   "kg", "2025-12-31"), "data" },
                { "data no futuro",                TesteColheitas.colheita (atual, norte, 10,   "kg", amanha), "data" },
                { "data impossivel",               TesteColheitas.colheita (atual, norte, 10,   "kg", "2026-02-30"), "data" },
            };
            for (int i = 0; i < recusas.length; i++)
            {
                Resposta resposta = TesteColheitas.chamar (roteador, a, "POST", rota, recusas[i][1]);
                TesteColheitas.conferir (recusas[i][0] + " -> 400 no campo " + recusas[i][2],
                                         resposta.getStatus () == 400 &&
                                         TesteColheitas.json (resposta).get ("fields", Document.class).containsKey (recusas[i][2]));
            }
            TesteColheitas.conferir ("quantidade em texto -> 400",
                                     TesteColheitas.status (roteador, a, "POST", rota,
                                                            "{\"safraId\":\"" + atual + "\",\"talhaoId\":\"" + norte +
                                                            "\",\"quantidade\":\"10\",\"unidade\":\"kg\",\"data\":\"2026-03-01\"}") == 400);
            TesteColheitas.conferir ("observacao com mais de 200 caracteres -> 400",
                                     TesteColheitas.status (roteador, a, "POST", rota,
                                                            Document.parse (TesteColheitas.colheita (atual, norte, 10, "kg", "2026-03-01"))
                                                                    .append ("observacao", "x".repeat (201)).toJson ()) == 400);
            Resposta planejamento = TesteColheitas.chamar (roteador, a, "POST", rota, TesteColheitas.colheita (trigo, leste, 10, "kg", "2026-07-02"));
            TesteColheitas.conferir ("safra antes do plantio -> 409",
                                     planejamento.getStatus () == 409 &&
                                     TesteColheitas.json (planejamento).get ("fields", Document.class).containsKey ("safraId"));
            TesteColheitas.conferir ("nada gravado nas recusas", colheitas.listar (propriedade).isEmpty ());

            // ---------------------------------------------------------------- registrar o conjunto conhecido
            Resposta criada = TesteColheitas.chamar (roteador, a, "POST", rota,
                                                     Document.parse (TesteColheitas.colheita (antiga, norte, 120, "t", "2025-04-10"))
                                                             .append ("observacao", "  Primeira carga  ").toJson ());
            Document primeira = TesteColheitas.json (criada);
            TesteColheitas.conferir ("POST -> 201 com quantidade, unidade, kg, nomes e observacao aparada",
                                     criada.getStatus () == 201 && TesteColheitas.igual (primeira.get ("quantidade"), 120) &&
                                     "t".equals (primeira.getString ("unidade")) && TesteColheitas.igual (primeira.get ("quantidadeKg"), 120000) &&
                                     "Soja 24/25".equals (primeira.getString ("safraNome")) && "Norte".equals (primeira.getString ("talhaoNome")) &&
                                     "Soja".equals (primeira.getString ("cultura")) && "Primeira carga".equals (primeira.getString ("observacao")));

            String sulAntiga = TesteColheitas.json (TesteColheitas.chamar (roteador, a, "POST", rota, TesteColheitas.colheita (antiga, sul, 500, "sc", "2025-04-12"))).getString ("id");
            TesteColheitas.chamar (roteador, a, "POST", rota, TesteColheitas.colheita (atual, norte, 2000,  "sc", "2026-03-10"));
            TesteColheitas.chamar (roteador, a, "POST", rota, TesteColheitas.colheita (atual, norte, 30000, "kg", "2026-03-15"));
            String sulAtual  = TesteColheitas.json (TesteColheitas.chamar (roteador, a, "POST", rota, TesteColheitas.colheita (atual, sul, 30, "t", "2026-03-12"))).getString ("id");
            TesteColheitas.chamar (roteador, a, "POST", rota, TesteColheitas.colheita (milho, leste, 5000, "kg", "2026-05-01"));

            // alteracao: 30 t -> 600 sc (36.000 kg)
            Resposta alterada = TesteColheitas.chamar (roteador, a, "PUT", rota + "/" + sulAtual, TesteColheitas.colheita (atual, sul, 600, "sc", "2026-03-12"));
            TesteColheitas.conferir ("PUT -> 200 e recalcula kg (600 sc = 36.000 kg)",
                                     alterada.getStatus () == 200 && TesteColheitas.igual (TesteColheitas.json (alterada).get ("quantidadeKg"), 36000));
            TesteColheitas.conferir ("PUT com talhao fora da safra -> 400",
                                     TesteColheitas.status (roteador, a, "PUT", rota + "/" + sulAtual, TesteColheitas.colheita (atual, leste, 1, "kg", "2026-03-12")) == 400);

            List<Document> todas = TesteColheitas.lista (TesteColheitas.chamar (roteador, a, "GET", rota, null));
            TesteColheitas.conferir ("lista tem as 6 colheitas, da mais recente para a mais antiga",
                                     todas.size () == 6 && "2026-05-01".equals (todas.get (0).getString ("data")) &&
                                     "2025-04-10".equals (todas.get (5).getString ("data")));

            // ---------------------------------------------------------------- produtividade
            Document daAtual = TesteColheitas.json (TesteColheitas.chamar (roteador, a, "GET", rota + "/resumo?safraId=" + atual, null));
            TesteColheitas.conferir ("safra 25/26: 186.000 kg (3.100 sc) em 50 ha",
                                     TesteColheitas.igual (daAtual.get ("totalKg"), 186000) && TesteColheitas.igual (daAtual.get ("totalSacas"), 3100) &&
                                     TesteColheitas.igual (daAtual.get ("areaHectares"), 50) && daAtual.getInteger ("registros").intValue () == 3);
            TesteColheitas.conferir ("safra 25/26: 3.720 kg/ha = 62 sc/ha",
                                     TesteColheitas.igual (daAtual.get ("produtividadeKgHa"), 3720) && TesteColheitas.igual (daAtual.get ("produtividadeScHa"), 62));
            List<Document> porTalhao = daAtual.getList ("porTalhao", Document.class);
            TesteColheitas.conferir ("por talhao usa a area de cada um: Norte 3.750 e Sul 3.600 kg/ha",
                                     TesteColheitas.igual (TesteColheitas.item (porTalhao, "talhaoId", norte).get ("produtividadeKgHa"), 3750) &&
                                     TesteColheitas.igual (TesteColheitas.item (porTalhao, "talhaoId", sul).get ("produtividadeKgHa"), 3600));

            Document doNorte = TesteColheitas.json (TesteColheitas.chamar (roteador, a, "GET", rota + "/resumo?talhaoId=" + norte, null));
            TesteColheitas.conferir ("talhao em dois ciclos: area conta duas vezes (270.000 kg / 80 ha = 3.375 kg/ha)",
                                     TesteColheitas.igual (doNorte.get ("totalKg"), 270000) && TesteColheitas.igual (doNorte.get ("areaHectares"), 80) &&
                                     TesteColheitas.igual (doNorte.get ("produtividadeKgHa"), 3375));

            Document soja = TesteColheitas.json (TesteColheitas.chamar (roteador, a, "GET", rota + "/resumo?cultura=soja", null));
            TesteColheitas.conferir ("cultura Soja: 336.000 kg em 100 ha (3.360 kg/ha)",
                                     TesteColheitas.igual (soja.get ("totalKg"), 336000) && TesteColheitas.igual (soja.get ("produtividadeKgHa"), 3360));

            Document semArea = TesteColheitas.json (TesteColheitas.chamar (roteador, a, "GET", rota + "/resumo?safraId=" + milho, null));
            TesteColheitas.conferir ("talhao sem area: producao registrada e produtividade null",
                                     TesteColheitas.igual (semArea.get ("totalKg"), 5000) && semArea.get ("produtividadeKgHa") == null &&
                                     semArea.get ("produtividadeScHa") == null);

            Document periodo = TesteColheitas.json (TesteColheitas.chamar (roteador, a, "GET", rota + "/resumo?de=2026-03-11&ate=2026-03-31", null));
            TesteColheitas.conferir ("periodo 11 a 31/03/2026: 2 registros (66.000 kg)",
                                     periodo.getInteger ("registros").intValue () == 2 && TesteColheitas.igual (periodo.get ("totalKg"), 66000));
            TesteColheitas.conferir ("periodo invertido -> 400",
                                     TesteColheitas.status (roteador, a, "GET", rota + "/resumo?de=2026-04-01&ate=2026-03-01", null) == 400);

            List<Document> ciclos = TesteColheitas.lista (TesteColheitas.chamar (roteador, a, "GET", rota + "/comparativo?cultura=Soja", null));
            TesteColheitas.conferir ("comparativo Soja: dois ciclos, do mais antigo ao mais novo",
                                     ciclos.size () == 2 && antiga.equals (ciclos.get (0).getString ("safraId")) && atual.equals (ciclos.get (1).getString ("safraId")));
            TesteColheitas.conferir ("comparativo: 50 sc/ha e 62 sc/ha, variacao +24% sobre o ciclo anterior",
                                     TesteColheitas.igual (ciclos.get (0).get ("produtividadeScHa"), 50) && ciclos.get (0).get ("variacaoPercentual") == null &&
                                     TesteColheitas.igual (ciclos.get (1).get ("produtividadeScHa"), 62) && TesteColheitas.igual (ciclos.get (1).get ("variacaoPercentual"), 24));
            TesteColheitas.conferir ("comparativo: talhoes colhidos e ultima colheita",
                                     ciclos.get (1).getInteger ("talhoesColhidos").intValue () == 2 && "2026-03-15".equals (ciclos.get (1).getString ("ultimaColheita")));

            List<Document> geral = TesteColheitas.lista (TesteColheitas.chamar (roteador, a, "GET", rota + "/comparativo", null));
            Document       semColheita = TesteColheitas.item (geral, "safraId", trigo);
            TesteColheitas.conferir ("ciclo sem colheita aparece com 0 kg e produtividade null (nao inventa)",
                                     geral.size () == 4 && semColheita != null && TesteColheitas.igual (semColheita.get ("totalKg"), 0) &&
                                     semColheita.get ("produtividadeKgHa") == null && semColheita.get ("variacaoPercentual") == null);

            // ---------------------------------------------------------------- isolamento, contexto e vinculo com a safra
            TesteColheitas.conferir ("B nao ve as colheitas de A", TesteColheitas.lista (TesteColheitas.chamar (roteador, b, "GET", rota, null)).isEmpty ());
            TesteColheitas.conferir ("B nao consulta, altera nem exclui colheita de A",
                                     TesteColheitas.status (roteador, b, "GET",    rota + "/" + sulAtual, null) == 404 &&
                                     TesteColheitas.status (roteador, b, "PUT",    rota + "/" + sulAtual, TesteColheitas.colheita (alheia, oeste, 1, "kg", "2026-03-01")) == 404 &&
                                     TesteColheitas.status (roteador, b, "DELETE", rota + "/" + sulAtual, null) == 404);
            TesteColheitas.conferir ("resumo de B zerado", TesteColheitas.igual (TesteColheitas.json (TesteColheitas.chamar (roteador, b, "GET", rota + "/resumo", null)).get ("totalKg"), 0));

            String                  trocada    = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            HashMap<String, String> cabecalhos = new HashMap<String, String> ();
            cabecalhos.put ("authorization", "Bearer " + trocada);
            Requisicao requisicao = new Requisicao ("POST", rota, new HashMap<String, String> (), cabecalhos,
                                                    TesteColheitas.colheita (atual, norte, 1, "kg", "2026-03-01").getBytes (StandardCharsets.UTF_8),
                                                    "127.0.0.1");
            requisicao.setSessao (sessoes.buscar (trocada));
            sessoes.selecionarPropriedade (trocada, outra);
            TesteColheitas.conferir ("propriedade trocada no meio da escrita -> 409",
                                     new ColheitasHandler (colheitas, safras, talhoes, sessoes).tratar (requisicao).getStatus () == 409);

            String semSul = new Document ("nome", "Soja 25/26").append ("cultura", "Soja").append ("dataInicio", "2026-01-01")
                                .append ("dataFim", "2026-06-30").append ("talhoes", List.of (norte)).toJson ();
            Resposta tirarSul = TesteColheitas.chamar (roteador, a, "PUT", "/api/safras/" + atual, semSul);
            TesteColheitas.conferir ("safra nao perde talhao que ja tem colheita (409 no campo talhoes)",
                                     tirarSul.getStatus () == 409 && TesteColheitas.json (tirarSul).get ("fields", Document.class).getString ("talhoes").contains ("Sul"));

            // ---------------------------------------------------------------- excluir e safra finalizada
            TesteColheitas.conferir ("DELETE -> 204 e depois 404",
                                     TesteColheitas.status (roteador, a, "DELETE", rota + "/" + sulAtual, null) == 204 &&
                                     TesteColheitas.status (roteador, a, "GET",    rota + "/" + sulAtual, null) == 404);
            TesteColheitas.conferir ("sem a colheita do Sul, a safra pode sair do talhao",
                                     TesteColheitas.status (roteador, a, "PUT", "/api/safras/" + atual, semSul) == 200);

            TesteColheitas.chamar (roteador, a, "POST", "/api/safras/" + antiga + "/avancar", "{}");
            TesteColheitas.conferir ("safra finalizada: POST, PUT e DELETE -> 409",
                                     TesteColheitas.status (roteador, a, "POST",   rota, TesteColheitas.colheita (antiga, norte, 1, "kg", "2025-05-01")) == 409 &&
                                     TesteColheitas.status (roteador, a, "PUT",    rota + "/" + sulAntiga, TesteColheitas.colheita (antiga, sul, 1, "kg", "2025-05-01")) == 409 &&
                                     TesteColheitas.status (roteador, a, "DELETE", rota + "/" + sulAntiga, null) == 409);
            TesteColheitas.conferir ("safra finalizada continua no comparativo com a produtividade",
                                     TesteColheitas.igual (TesteColheitas.item (TesteColheitas.lista (TesteColheitas.chamar (roteador, a, "GET", rota + "/comparativo?cultura=Soja", null)),
                                                                                "safraId", antiga).get ("produtividadeScHa"), 50));

            // ---------------------------------------------------------------- Mongo: leitura depois de reiniciar
            if (mongo)
            {
                Roteador depois = new Roteador (sessoes);
                new ColheitasHandler (new RepositorioColheitasMongo (conexao.getBanco ()), new RepositorioSafrasMongo (conexao.getBanco ()),
                                      new RepositorioTalhoesMongo (conexao.getBanco ()), sessoes).registrar (depois);
                Document lido = TesteColheitas.json (TesteColheitas.chamar (depois, a, "GET", rota + "/resumo?safraId=" + antiga, null));
                TesteColheitas.conferir ("Mongo: repositorios novos (reinicio) leem 150.000 kg e 50 sc/ha",
                                         TesteColheitas.igual (lido.get ("totalKg"), 150000) && TesteColheitas.igual (lido.get ("produtividadeScHa"), 50));
                TesteColheitas.conferir ("Mongo: indices de safra e de data criados",
                                         conexao.getBanco ().getCollection ("colheitas").listIndexes ().into (new ArrayList<Document> ()).size () == 3);
            }
        }
        catch (Exception erro)
        {
            TesteColheitas.falhas++;
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

        System.out.println ("TesteColheitas: " + (TesteColheitas.total - TesteColheitas.falhas) + "/" +
                            TesteColheitas.total + " verifica\u00e7\u00f5es OK" + (mongo ? " (MongoDB)" : " (mem\u00f3ria)"));

        if (TesteColheitas.falhas > 0)
            System.exit (1);
    }
}
