/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
import dados.*;
import handlers.OcorrenciasHandler;
import handlers.PropriedadesHandler;
import http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;

/* Testa o contrato HTTP e as regras sem JUnit. --mongo repete os casos com o
   driver real, reconexão e dois clientes independentes. Nunca usa banco de produção. */
public class TesteOcorrencias
{
    private static int total;
    private static final String PREFIXO = "/api/ocorrencias";
    private static class Relogio extends Clock
    {
        Instant agora = Instant.parse ("2026-02-01T02:59:59Z");
        public ZoneId getZone () { return ZoneOffset.UTC; }
        public Clock withZone (ZoneId fuso) { return Clock.fixed (this.agora, fuso); }
        public Instant instant () { return this.agora; }
    }

    private static void verificar (String nome, boolean ok)
    {
        total++;
        if (!ok) throw new AssertionError (nome);
        System.out.println ("ok " + nome);
    }

    private static Resposta chamada (Roteador router, String token, String metodo, String caminho, Document corpo, int esperado) throws Exception
    {
        String[] partes = caminho.split ("\\?", 2);
        Map<String, String> query = new HashMap<> ();
        if (partes.length > 1)
            for (String par : partes[1].split ("&"))
            {
                String[] valores = par.split ("=", 2);
                query.put (valores[0], valores.length > 1 ? valores[1] : "");
            }
        Map<String, String> headers = new HashMap<> ();
        if (token != null) headers.put ("authorization", "Bearer " + token);
        Resposta ret = router.tratar (new Requisicao (metodo, partes[0], query, headers,
            corpo == null ? new byte[0] : corpo.toJson ().getBytes (StandardCharsets.UTF_8), "127.0.0.1"));
        verificar (metodo + " " + caminho + " -> " + esperado + " (veio " + ret.getStatus () + ")", ret.getStatus () == esperado);
        return ret;
    }

    private static String texto (Resposta resposta) { return new String (resposta.getCorpo (), StandardCharsets.UTF_8); }
    private static Document json (Resposta resposta) { return Document.parse (texto (resposta)); }
    private static List<Document> lista (Resposta resposta)
    {
        return Document.parse ("{\"itens\":" + texto (resposta) + "}").getList ("itens", Document.class);
    }
    private static Document dados (String talhao)
    {
        return new Document ("titulo", "Praga <literal>").append ("tipo", "Praga").append ("data", "2026-01-25")
            .append ("prioridade", "Alta").append ("descricao", "Descrição única <script>alert(1)</script>\nLinha dois.").append ("talhaoId", talhao);
    }
    private static Document acao (long versao, String acao)
    {
        return new Document ("versao", versao).append ("acao", acao).append ("descricao", "Texto de " + acao);
    }

    private static String uriTeste ()
    {
        String uri = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null : new ConnectionString (uri).getDatabase ();
        if (banco == null || !banco.endsWith ("_teste")) throw new IllegalArgumentException ("Use um banco dedicado com nome terminado em _teste.");
        return uri;
    }

    public static void main (String[] args) throws Exception
    {
        if (args.length > 0 && args[0].equals ("--limpar-e2e"))
        {
            limparE2e (args);
            return;
        }
        boolean mongo = args.length > 0 && args[0].equals ("--mongo");
        String uri = mongo ? uriTeste () : null;
        ConexaoMongo conexao = mongo ? new ConexaoMongo (uri) : null;
        String propriedade = new ObjectId ().toHexString (), outra = new ObjectId ().toHexString ();
        String usuario = new ObjectId ().toHexString ();
        try
        {
            RepositorioOcorrencias repo = mongo ? new RepositorioOcorrenciasMongo (conexao) : new RepositorioOcorrenciasMemoria ();
            RepositorioTalhoes plots = mongo ? new RepositorioTalhoesMongo (conexao.getBanco ()) : new RepositorioTalhoesMemoria ();
            Document plot = new Document ("propriedadeId", propriedade).append ("nome", "Norte").append ("nomeNormalizado", "norte").append ("safra", "2025/26");
            plots.criar (plot); String plotId = plot.getObjectId ("_id").toHexString ();
            Document estrangeiro = new Document ("propriedadeId", outra).append ("nome", "Sul").append ("nomeNormalizado", "sul");
            plots.criar (estrangeiro);
            Sessoes sessoes = new Sessoes ();
            String token = sessoes.criar (usuario, propriedade), tokenOutra = sessoes.criar (usuario, outra);
            String intruso = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            Relogio relogio = new Relogio ();
            Roteador router = new Roteador (sessoes);
            new OcorrenciasHandler (repo, plots, sessoes, relogio).registrar (router);
            chamada (router, null, "GET", PREFIXO, null, 401);
            chamada (router, token, "POST", PREFIXO, new Document (), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("data", "2026-02-30"), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("data", "Hoje"), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("tipo", "inválido"), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("prioridade", "Urgente"), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("descricao", 42), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("descricao", "x".repeat (6001)), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("status", "RESOLVIDA"), 400);
            chamada (router, token, "POST", PREFIXO, dados (plotId).append ("foto", "arquivo"), 400);
            chamada (router, token, "POST", PREFIXO, dados (estrangeiro.getObjectId ("_id").toHexString ()), 400);
            chamada (router, token, "POST", PREFIXO, dados (null).append ("contextoPropriedadeId", outra), 409);
            Document registro = json (chamada (router, token, "POST", PREFIXO, dados (plotId), 201));
            String id = registro.getString ("id"), path = PREFIXO + "/" + id;
            verificar ("descrição literal e vínculo persistidos", registro.getString ("descricao").equals (dados (plotId).getString ("descricao")) && plotId.equals (registro.getString ("talhaoId")) && "2025/26".equals (registro.getString ("safra")));
            chamada (router, tokenOutra, "GET", path, null, 404);
            chamada (router, intruso, "GET", path + "/timeline", null, 404);
            chamada (router, intruso, "PUT", path, dados (plotId).append ("versao", 1), 404);
            chamada (router, tokenOutra, "POST", path + "/timeline", acao (1, "resolucao"), 404);
            chamada (router, token, "GET", PREFIXO + "?talhaoId=" + estrangeiro.getObjectId ("_id"), null, 400);
            chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-13", null, 400);
            chamada (router, token, "PUT", path, dados (plotId), 409);
            chamada (router, token, "PUT", path, dados (plotId).append ("versao", 1).append ("status", "RESOLVIDA"), 400);
            registro = json (chamada (router, token, "PUT", path, dados (plotId).append ("versao", 1).append ("descricao", "Descrição editada"), 200));
            chamada (router, token, "PUT", path, dados (plotId).append ("versao", 1), 409);
            chamada (router, token, "POST", path + "/timeline", acao (2, "atualizacao"), 409);
            chamada (router, token, "POST", path + "/timeline", acao (2, "reabrir"), 400);
            chamada (router, token, "POST", path + "/timeline", acao (2, "acompanhamento").append ("descricao", " "), 400);
            chamada (router, token, "POST", path + "/timeline", acao (2, "acompanhamento"), 200);
            chamada (router, token, "POST", path + "/timeline", acao (3, "acompanhamento"), 409);
            chamada (router, token, "POST", path + "/timeline", acao (3, "atualizacao"), 200);
            chamada (router, token, "POST", path + "/timeline", acao (4, "resolucao"), 200);
            chamada (router, token, "POST", path + "/timeline", acao (5, "acompanhamento"), 409);
            chamada (router, token, "PUT", path, dados (plotId).append ("versao", 5), 409);
            verificar ("janeiro conta resolução pelo fuso de Brasília", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-01", null, 200)).getInteger ("resolvidasMes") == 1);
            relogio.agora = Instant.parse ("2026-02-01T03:00:00Z");
            Document geral = json (chamada (router, token, "POST", PREFIXO, dados (null), 201));
            String geralPath = PREFIXO + "/" + geral.getString ("id");
            chamada (router, token, "POST", geralPath + "/timeline", acao (1, "resolucao"), 200);
            verificar ("fevereiro conta somente a resolução de fevereiro", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-02", null, 200)).getInteger ("resolvidasMes") == 1);
            verificar ("outro ano não herda a contagem", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2027-02", null, 200)).getInteger ("resolvidasMes") == 0);
            verificar ("talhão exclui a ocorrência geral do indicador", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-02&talhaoId=" + plotId, null, 200)).getInteger ("resolvidasMes") == 0);
            verificar ("outra propriedade sem contaminação", lista (chamada (router, tokenOutra, "GET", PREFIXO, null, 200)).isEmpty ());
            List<Document> eventos = lista (chamada (router, token, "GET", path + "/timeline", null, 200));
            verificar ("cinco eventos completos, descrição original preservada", eventos.size () == 5 && eventos.get (0).getString ("descricao").equals (dados (plotId).getString ("descricao")) && eventos.get (1).get ("dados", Document.class).getString ("descricao").equals ("Descrição editada"));
            chamada (router, token, "DELETE", path, new Document ("versao", 5), 200);
            verificar ("excluída sai da lista ativa", lista (chamada (router, token, "GET", PREFIXO, null, 200)).size () == 1);
            verificar ("excluída continua consultável", lista (chamada (router, token, "GET", PREFIXO + "?incluirExcluidas=true", null, 200)).size () == 2);
            verificar ("histórico após exclusão preserva resolução", lista (chamada (router, token, "GET", path + "/timeline", null, 200)).size () == 6);
            chamada (router, token, "DELETE", path, new Document ("versao", 6), 409);
            verificar ("propriedade com histórico mantém vínculo", repo.temRegistros (propriedade));

            // Dois clientes leem a mesma versão antes de concorrerem pela próxima ordem.
            Document concorrente = json (chamada (router, token, "POST", PREFIXO, dados (plotId), 201));
            String concorrenteId = concorrente.getString ("id");
            ConexaoMongo segunda = mongo ? new ConexaoMongo (uri) : null;
            try
            {
                RepositorioOcorrencias repo2 = mongo ? new RepositorioOcorrenciasMongo (segunda) : repo;
                Document anterior1 = repo.buscar (usuario, propriedade, concorrenteId), anterior2 = repo2.buscar (usuario, propriedade, concorrenteId);
                Document nova1 = new Document (anterior1).append ("versao", 2L), nova2 = new Document (anterior2).append ("versao", 2L);
                Document evento = new Document ("usuarioId", usuario).append ("propriedadeId", propriedade).append ("ocorrenciaId", concorrenteId).append ("ordem", 2L);
                ExecutorService pool = Executors.newFixedThreadPool (2);
                try
                {
                    CountDownLatch inicio = new CountDownLatch (1);
                    Future<Boolean> a = pool.submit (() -> { inicio.await (); return repo.salvar (anterior1, nova1, new Document (evento).append ("_id", new ObjectId ())); });
                    Future<Boolean> b = pool.submit (() -> { inicio.await (); return repo2.salvar (anterior2, nova2, new Document (evento).append ("_id", new ObjectId ())); });
                    inicio.countDown ();
                    verificar ("concorrência: exatamente um cliente grava", a.get () ^ b.get ());
                    verificar ("concorrência não duplica nem perde evento", repo.timeline (usuario, propriedade, concorrenteId).size () == 2);
                }
                finally { pool.shutdownNow (); }
            }
            finally { if (segunda != null) segunda.fechar (); }
            if (mongo)
            {
                Document anterior = repo.buscar (usuario, propriedade, concorrenteId);
                Document duplicado = new Document ("usuarioId", usuario).append ("propriedadeId", propriedade).append ("ocorrenciaId", concorrenteId).append ("ordem", 1L);
                boolean falhou = false;
                try { repo.salvar (anterior, new Document (anterior).append ("versao", 3L), duplicado); }
                catch (Exception erro) { falhou = true; }
                verificar ("falha no histórico desfaz alteração do registro", falhou && ((Number) repo.buscar (usuario, propriedade, concorrenteId).get ("versao")).longValue () == 2);
                conexao.fechar (); conexao = new ConexaoMongo (uri);
                RepositorioOcorrencias reconectado = new RepositorioOcorrenciasMongo (conexao);
                verificar ("reconexão mantém descrição, resolução e exclusão", reconectado.buscar (usuario, propriedade, id).getBoolean ("excluida") && reconectado.buscar (usuario, propriedade, id).getString ("descricao").equals ("Descrição editada"));
                verificar ("reconexão recupera todos os eventos em ordem", reconectado.timeline (usuario, propriedade, id).size () == 6 && reconectado.timeline (usuario, propriedade, id).get (5).getString ("acao").equals ("excluir"));
            }
            testarExclusaoPropriedade ();
            System.out.println ("TesteOcorrencias: " + total + " verificações OK (" + (mongo ? "Mongo real" : "memória") + ")");
        }
        finally
        {
            if (conexao != null)
            {
                for (String colecao : List.of ("ocorrencias", "ocorrencias_timeline", "talhoes"))
                    conexao.getBanco ().getCollection (colecao).deleteMany (Filters.in ("propriedadeId", propriedade, outra));
                conexao.fechar ();
            }
        }
    }

    private static void testarExclusaoPropriedade () throws Exception
    {
        RepositorioUsuariosMemoria usuarios = new RepositorioUsuariosMemoria ();
        Document usuario = usuarios.criar (new Document ("email", "vinculo@agro.test"), new Document ("nome", "Primeira"));
        String uid = usuario.getObjectId ("_id").toHexString ();
        Document outra = usuarios.criarPropriedade (uid, new Document ("nome", "Segunda"));
        String pid = outra.getObjectId ("_id").toHexString ();
        Sessoes sessoes = new Sessoes ();
        String token = sessoes.criar (uid, pid);
        RepositorioOcorrencias repo = new RepositorioOcorrenciasMemoria ();
        RepositorioTalhoes plots = new RepositorioTalhoesMemoria ();
        Roteador router = new Roteador (sessoes);
        new OcorrenciasHandler (repo, plots, sessoes).registrar (router);
        new PropriedadesHandler (usuarios, plots, sessoes, repo).registrar (router);
        chamada (router, token, "POST", PREFIXO, dados (null), 201);
        sessoes.selecionarPropriedade (token, usuario.getString ("propriedadeId"));
        chamada (router, token, "DELETE", "/api/propriedades/" + pid, null, 409);
    }

    // A suíte de navegador remove somente contas fictícias explicitamente informadas.
    private static void limparE2e (String[] args) throws Exception
    {
        ConexaoMongo conexao = new ConexaoMongo (uriTeste ());
        try
        {
            for (int i = 1; i < args.length; i++)
            {
                String email = args[i];
                if (!email.matches ("ocorrencias-e2e-[a-z0-9-]+@agro\\.test")) throw new IllegalArgumentException ("Conta de teste inválida");
                Document usuario = conexao.getBanco ().getCollection ("usuarios").find (Filters.eq ("email", email)).first ();
                if (usuario == null) continue;
                String uid = usuario.getObjectId ("_id").toHexString ();
                for (Document prop : conexao.getBanco ().getCollection ("propriedades").find (Filters.eq ("usuarioId", uid)))
                {
                    String pid = prop.getObjectId ("_id").toHexString ();
                    for (String colecao : List.of ("ocorrencias", "ocorrencias_timeline", "talhoes", "historicos_talhoes"))
                        conexao.getBanco ().getCollection (colecao).deleteMany (Filters.eq ("propriedadeId", pid));
                }
                conexao.getBanco ().getCollection ("propriedades").deleteMany (Filters.eq ("usuarioId", uid));
                conexao.getBanco ().getCollection ("usuarios").deleteOne (Filters.eq ("_id", usuario.get ("_id")));
            }
        }
        finally { conexao.fechar (); }
    }
}
