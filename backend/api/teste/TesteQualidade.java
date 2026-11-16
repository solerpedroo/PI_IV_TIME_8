/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
import dados.conta.RepositorioUsuarios;
import dados.conta.RepositorioUsuariosMemoria;
import dados.cultivo.RepositorioSafras;
import dados.cultivo.RepositorioSafrasMemoria;
import dados.cultivo.RepositorioTalhoes;
import dados.cultivo.RepositorioTalhoesMemoria;
import dados.estoque.RepositorioEstoque;
import dados.estoque.RepositorioEstoqueMemoria;
import dados.operacao.RepositorioAtividades;
import dados.financeiro.RepositorioCustos;
import dados.financeiro.RepositorioCustosMemoria;
import dados.infra.ConexaoMongo;
import dados.operacao.RepositorioAtividadesMemoria;
import http.Roteador;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bson.Document;

/*
    Issues #36, #183 e #184. Prova três regras do painel:
    o resumo de cada módulo fecha com a lista (custos, ocorrências, estoque);
    outro usuário não herda os lançamentos (isolamento);
    leituras paralelas do resumo de custos devolvem o mesmo total.
    Também mede o tempo dessas consultas e, com --mongo, o índice do histórico do talhão.
*/
public class TesteQualidade
{
    private static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");
    /** Referencia local para demo (memoria); a equipe ajusta no docs/qualidade-backend.md. */
    private static final long LIMITE_MEDIANA_MS = 750;

    private static int falhas;
    private static int total;
    private static int porta;

    private static RepositorioUsuarios usuarios;
    private static RepositorioAtividades atividades;
    private static RepositorioEstoque estoque;

    private static void conferir (String nome, boolean ok)
    {
        TesteQualidade.total++;
        if (ok) System.out.println ("ok    " + nome);
        else { TesteQualidade.falhas++; System.out.println ("FALHA " + nome); }
    }

    private static class Resp
    {
        int status;
        String corpo;

        Document json () { return Document.parse (this.corpo.isBlank () ? "{}" : this.corpo); }
    }

    private static Resp bruto (String metodo, String caminho, String json, String token) throws Exception
    {
        byte[] corpo = json == null ? new byte[0] : json.getBytes (StandardCharsets.UTF_8);
        /*
            Texto HTTP cru, o mesmo formato que Aceitadora/Supervisora leem.
            Linha 1: método, caminho e versão.
            Host, Authorization (se houver token) e Content-Type (se houver JSON).
            Content-Length é o tamanho do corpo em bytes.
            \r\n\r\n fecha o cabeçalho. O corpo JSON vai em seguida, se existir.
        */
        String cabeca = metodo + " " + caminho + " HTTP/1.1\r\nHost: 127.0.0.1\r\n" +
            (token == null ? "" : "Authorization: Bearer " + token + "\r\n") +
            (json == null ? "" : "Content-Type: application/json\r\n") +
            "Content-Length: " + corpo.length + "\r\n\r\n";
        ByteArrayOutputStream envio = new ByteArrayOutputStream ();
        envio.write (cabeca.getBytes (StandardCharsets.UTF_8));
        envio.write (corpo);
        try (Socket conexao = new Socket ("127.0.0.1", TesteQualidade.porta))
        {
            conexao.setSoTimeout (30000);
            OutputStream saida = conexao.getOutputStream ();
            saida.write (envio.toByteArray ());
            saida.flush ();
            InputStream entrada = conexao.getInputStream ();
            ByteArrayOutputStream lido = new ByteArrayOutputStream ();
            byte[] pedaco = new byte[4096];
            int n;
            while ((n = entrada.read (pedaco)) != -1) lido.write (pedaco, 0, n);
            String texto = lido.toString (StandardCharsets.UTF_8);
            int fim = texto.indexOf ("\r\n\r\n");
            Resp ret = new Resp ();
            if (fim < 0) { ret.status = -1; ret.corpo = texto; return ret; }
            ret.corpo = texto.substring (fim + 4);
            ret.status = Integer.parseInt (texto.substring (0, fim).split (" ")[1]);
            return ret;
        }
    }

    private static Resp get (String caminho, String token) throws Exception
    {
        return TesteQualidade.bruto ("GET", caminho, null, token);
    }

    private static Resp post (String caminho, String json, String token) throws Exception
    {
        return TesteQualidade.bruto ("POST", caminho, json, token);
    }

    private static List<Document> lista (Resp resposta)
    {
        return Document.parse ("{\"itens\":" + resposta.corpo + "}").getList ("itens", Document.class);
    }

    /* Login devolve o Bearer. O segundo usuário usa outro token para provar o isolamento. */
    private static String login (String email, String senha) throws Exception
    {
        Document corpo = Document.parse (post ("/api/auth/login", new Document ("email", email).append ("password", senha).toJson (), null).corpo);
        return corpo.getString ("token");
    }

    /* Regra de conta: o cadastro só libera o login depois que o token de confirmação de e-mail é aceito. */
    private static void cadastroConfirmado (String email) throws Exception
    {
        String json = new Document ("name", "Qualidade").append ("email", email).append ("phone", "(19) 99999-0000")
            .append ("password", "Senha@123").append ("password-confirm", "Senha@123").append ("farm", "Fazenda qualidade")
            .append ("area", "80 ha").append ("terms", true).toJson ();
        conferir ("cadastro", post ("/api/auth/register", json, null).status == 201);
        String tokenConfirmacao = TesteQualidade.usuarios.buscarPor ("email", email).getString ("tokenConfirmacao");
        conferir ("confirmacao", post ("/api/auth/confirm-email", new Document ("token", tokenConfirmacao).toJson (), null).status == 200);
    }

    /* Monta a propriedade do cenário: duas culturas, dois talhões, uma safra, custos,
       saldo de insumos e uma ocorrência aberta ao lado de outra já resolvida. */
    private static String prepararDados (String token) throws Exception
    {
        LocalDate hoje = LocalDate.now (TesteQualidade.FUSO);
        String inicio = hoje.minusDays (10).toString ();
        String fim = hoje.plusDays (200).toString ();

        post ("/api/culturas", new Document ("nome", "Soja").toJson (), token);
        post ("/api/culturas", new Document ("nome", "Milho").toJson (), token);

        Resp norte = post ("/api/talhoes", new Document ("nome", "Q Norte").append ("cultura", "Soja").append ("hectares", 40).toJson (), token);
        Resp sul   = post ("/api/talhoes", new Document ("nome", "Q Sul").append ("cultura", "Milho").append ("hectares", 30).toJson (), token);
        conferir ("talhao norte", norte.status == 201);
        conferir ("talhao sul", sul.status == 201);
        if (norte.status != 201 || sul.status != 201)
            throw new Exception ("Falha ao preparar talhoes: " + norte.corpo + " | " + sul.corpo);
        String t1 = norte.json ().getString ("id");
        String t2 = sul.json ().getString ("id");

        String safra = post ("/api/safras", new Document ("nome", "Safra Q").append ("cultura", "Soja")
            .append ("dataInicio", inicio).append ("dataFim", fim).append ("talhoes", List.of (t1)).toJson (), token).json ().getString ("id");

        for (int i = 0; i < 5; i++)
        {
            post ("/api/atividades", new Document ("nome", "Atividade Q" + i).append ("tipo", "Aplicação")
                .append ("talhao", "Q Norte").append ("data", hoje.toString ()).append ("safraId", safra).toJson (), token);
            post ("/api/custos", new Document ("description", "Custo Q" + i).append ("category", "Insumos")
                .append ("value", 100 + i).append ("date", hoje.toString ()).append ("talhaoId", t1).append ("safraId", safra).toJson (), token);
        }

        post ("/api/insumos", new Document ("nome", "Insumo Q1").append ("categoria", "Fertilizante").append ("unidade", "kg")
            .append ("saldo", 100).append ("minimo", 10).append ("custoUnitario", 2).toJson (), token);
        post ("/api/insumos", new Document ("nome", "Insumo Q2").append ("categoria", "Herbicida").append ("unidade", "L")
            .append ("saldo", 50).append ("minimo", 5).append ("custoUnitario", 10).toJson (), token);

        post ("/api/ocorrencias", new Document ("titulo", "Ocorrencia aberta Q").append ("tipo", "Praga")
            .append ("prioridade", "Alta").append ("data", hoje.toString ()).append ("descricao", "Monitorar.")
            .append ("talhaoId", t1).toJson (), token);
        Document fechada = post ("/api/ocorrencias", new Document ("titulo", "Ocorrencia fechada Q").append ("tipo", "Clima")
            .append ("prioridade", "Baixa").append ("data", hoje.toString ()).append ("descricao", "Resolvida.")
            .append ("talhaoId", t2).toJson (), token).json ();
        String occId = fechada.getString ("id");
        long versao = fechada.get ("versao", Number.class).longValue ();
        post ("/api/ocorrencias/" + occId + "/timeline", new Document ("versao", versao).append ("acao", "resolucao")
            .append ("descricao", "Sem impacto").toJson (), token);

        return t1;
    }

    /* Prova consistência e isolamento: total e quantidade do resumo batem com a lista,
       o painel repete essas contagens, outro usuário não vê os custos e o histórico
       do talhão responde 200 para o dono. */
    private static void conferirConsistencia (String token, String t1) throws Exception
    {
        System.out.println ("\n# qualidade — consistencia (#183)");

        List<Document> custos = lista (get ("/api/custos", token));
        Document resumoCustos = get ("/api/custos/resumo", token).json ();
        double soma = 0;
        for (Document c : custos) soma += c.get ("value", Number.class).doubleValue ();
        conferir ("custos: total do resumo = soma da lista", Math.abs (resumoCustos.get ("total", Number.class).doubleValue () - soma) < 0.01);
        conferir ("custos: quantidade do resumo = tamanho da lista", resumoCustos.getInteger ("quantidade") == custos.size ());

        List<Document> ocorrencias = lista (get ("/api/ocorrencias", token));
        Document resumoOcc = get ("/api/ocorrencias/resumo", token).json ();
        int abertas = 0;
        for (Document o : ocorrencias)
            if (!"RESOLVIDA".equals (o.getString ("status"))) abertas++;
        conferir ("ocorrencias: abertas do resumo = lista ativa", resumoOcc.getInteger ("abertas") == abertas);

        List<Document> insumos = lista (get ("/api/insumos", token));
        Document resumoEst = get ("/api/insumos/resumo", token).json ();
        conferir ("estoque: totalProdutos = itens listados", resumoEst.getInteger ("totalProdutos") == insumos.size ());

        LocalDate hoje = LocalDate.now (TesteQualidade.FUSO);
        String de = hoje.minusYears (1).toString ();
        String ate = hoje.plusDays (1).toString ();
        Document painel = get ("/api/relatorios/propriedade?de=" + de + "&ate=" + ate, token).json ();
        Document contagens = painel.get ("contagens", Document.class);
        conferir ("painel: contagens batem com listas",
                  contagens.getInteger ("atividade") == lista (get ("/api/atividades", token)).size () &&
                  contagens.getInteger ("custo") >= custos.size () &&
                  contagens.getInteger ("ocorrencia") >= 1);

        String tokenB = login ("intruso-qualidade@agro.test", "Senha@123");
        if (tokenB != null)
        {
            List<Document> custosB = lista (get ("/api/custos", tokenB));
            conferir ("isolamento: outro usuario nao herda custos", custosB.isEmpty () || !custosB.get (0).getString ("description").startsWith ("Custo Q"));
        }

        Resp hist = get ("/api/talhoes/" + t1 + "/historico", token);
        conferir ("historico do talhao: resposta 200", hist.status == 200);
    }

    /* Prova que doze leituras paralelas do resumo de custos enxergam o mesmo total. */
    private static void conferirConcorrencia (String token) throws Exception
    {
        System.out.println ("\n# qualidade — concorrencia (#183)");

        ExecutorService pool = Executors.newFixedThreadPool (12);
        ArrayList<Callable<Double>> tarefas = new ArrayList<> ();
        for (int i = 0; i < 12; i++)
            tarefas.add (() -> get ("/api/custos/resumo", token).json ().get ("total", Number.class).doubleValue ());
        List<Future<Double>> resultados = pool.invokeAll (tarefas);
        double referencia = resultados.get (0).get ();
        boolean igual = true;
        for (Future<Double> f : resultados)
            if (Math.abs (f.get () - referencia) > 0.001) igual = false;
        pool.shutdown ();
        conferir ("resumo de custos estavel sob leituras paralelas", igual);
    }

    private static long mediana (long[] amostras)
    {
        long[] copia = Arrays.copyOf (amostras, amostras.length);
        Arrays.sort (copia);
        return copia[copia.length / 2];
    }

    /* Prova que a mediana das consultas do painel cabe no limite local de tempo. */
    private static void medirConsultas (String token) throws Exception
    {
        System.out.println ("\n# qualidade — medicao (#184)");
        String[] rotas = {
            "/api/atividades",
            "/api/talhoes",
            "/api/custos/resumo",
            "/api/insumos/resumo",
            "/api/ocorrencias/resumo",
            "/api/ocorrencias",
            "/api/safras",
        };
        final int amostras = 15;
        for (String rota : rotas)
        {
            for (int i = 0; i < 3; i++) get (rota, token);
            long[] tempos = new long[amostras];
            for (int i = 0; i < amostras; i++)
            {
                long inicio = System.nanoTime ();
                Resp r = get (rota, token);
                tempos[i] = (System.nanoTime () - inicio) / 1_000_000;
                if (r.status != 200) tempos[i] = Long.MAX_VALUE;
            }
            long med = TesteQualidade.mediana (tempos);
            System.out.println ("medicao " + rota + " medianaMs=" + med + " amostras=" + amostras);
            conferir ("mediana aceitavel " + rota + " (<=" + TesteQualidade.LIMITE_MEDIANA_MS + "ms)", med <= TesteQualidade.LIMITE_MEDIANA_MS);
        }
    }

    /* Prova o índice de historicos_talhoes por propriedade e talhão, usado na consulta do histórico. */
    private static void conferirIndicesMongo (ConexaoMongo conexao) throws Exception
    {
        System.out.println ("\n# qualidade — indices Mongo (#183)");
        boolean historico = false;
        for (Document indice : conexao.getBanco ().getCollection ("historicos_talhoes").listIndexes ())
        {
            Document chave = indice.get ("key", Document.class);
            if (chave != null && Integer.valueOf (1).equals (chave.get ("propriedadeId")) &&
                Integer.valueOf (1).equals (chave.get ("talhaoId")))
                historico = true;
        }
        conferir ("indice historicos_talhoes (propriedadeId, talhaoId)", historico);
    }

    /* Com --mongo exige banco terminado em _teste. Sem a flag, as mesmas regras rodam em memória. */
    public static void main (String[] args) throws Exception
    {
        boolean mongo = args.length > 0 && "--mongo".equals (args[0]);
        ConexaoMongo conexao = null;
        if (mongo)
        {
            String uri = System.getenv ("MONGODB_URI");
            String banco = ConexaoMongo.resolverNomeDoBanco (uri, System.getenv ("MONGODB_DATABASE"));
            if (banco == null || !banco.endsWith ("_teste"))
                throw new IllegalArgumentException ("Use banco terminado em _teste.");
            conexao = new ConexaoMongo (uri);
        }

        TesteQualidade.usuarios = mongo ? new dados.conta.RepositorioUsuariosMongo (conexao) : new RepositorioUsuariosMemoria ();
        TesteQualidade.atividades = mongo ? new dados.operacao.RepositorioAtividadesMongo (conexao)
                                          : new RepositorioAtividadesMemoria ();
        TesteQualidade.estoque = mongo ? new dados.estoque.RepositorioEstoqueMongo (conexao)
                                       : new RepositorioEstoqueMemoria (TesteQualidade.atividades);
        RepositorioTalhoes talhoes = mongo ? new dados.cultivo.RepositorioTalhoesMongo (conexao.getBanco ()) : new RepositorioTalhoesMemoria ();
        RepositorioSafras safras = mongo ? new dados.cultivo.RepositorioSafrasMongo (conexao.getBanco ()) : new RepositorioSafrasMemoria ();
        RepositorioCustos custos = mongo ? new dados.financeiro.RepositorioCustosMongo (conexao.getBanco ()) : new RepositorioCustosMemoria ();

        Roteador roteador = Servidor.montarRoteador (TesteQualidade.usuarios, talhoes, TesteQualidade.atividades,
            TesteQualidade.estoque, safras, custos, conexao, Path.of ("../../frontend"));
        Servidor servidor = new Servidor ("127.0.0.1", 0, roteador, conexao, Servidor.MAXIMO_DE_CONEXOES);
        TesteQualidade.porta = servidor.getPorta ();

        try
        {
            String email = "qualidade-" + System.currentTimeMillis () + "@agro.test";
            cadastroConfirmado (email);
            post ("/api/auth/register", new Document ("name", "Intruso").append ("email", "intruso-qualidade@agro.test")
                .append ("phone", "(19) 99999-0001").append ("password", "Senha@123").append ("password-confirm", "Senha@123")
                .append ("farm", "Outra").append ("area", "1 ha").append ("terms", true).toJson (), null);
            String tokenConfirmacao = TesteQualidade.usuarios.buscarPor ("email", "intruso-qualidade@agro.test").getString ("tokenConfirmacao");
            post ("/api/auth/confirm-email", new Document ("token", tokenConfirmacao).toJson (), null);

            String token = login (email, "Senha@123");
            String t1 = prepararDados (token);
            conferirConsistencia (token, t1);
            conferirConcorrencia (token);
            medirConsultas (token);
            if (mongo) TesteQualidade.conferirIndicesMongo (conexao);
        }
        finally
        {
            servidor.desativar ();
        }

        System.out.println ("\n" + (TesteQualidade.total - TesteQualidade.falhas) + "/" + TesteQualidade.total + " qualidade ok");
        System.exit (TesteQualidade.falhas == 0 ? 0 : 1);
    }
}