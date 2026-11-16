/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
import dados.conta.RepositorioUsuariosMemoria;
import dados.cultivo.RepositorioTalhoes;
import dados.cultivo.RepositorioTalhoesMemoria;
import dados.cultivo.RepositorioTalhoesMongo;
import dados.infra.ConexaoMongo;
import dados.operacao.RepositorioOcorrencias;
import dados.operacao.RepositorioOcorrenciasMemoria;
import dados.operacao.RepositorioOcorrenciasMongo;
import handlers.operacao.OcorrenciasHandler;
import handlers.propriedade.PropriedadesHandler;
import http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;
import com.mongodb.client.model.Filters;

/* Testa o contrato HTTP das ocorrências sem JUnit. Prova 401 sem token,
   validação do cadastro, isolamento entre propriedades (404 no outro contexto),
   versão otimista (409), histórico que preserva a descrição, resumo mensal no
   fuso de Brasília e concorrência em que só um cliente grava. --mongo repete
   com o driver real, reconexão e dois clientes. Nunca usa banco de produção. */
public class TesteOcorrencias
{
    private static int total;
    private static final String PREFIXO = "/api/ocorrencias";
    /** PNG 1×1 px válido. A evidência só entra se os bytes e o MIME forem de imagem (#175/#176). */
    private static final String PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAD0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    /* Relógio fixo em 02:59:59Z: ainda é 23:59 em Brasília, para o resumo contar janeiro. */
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
        // Não abre socket. O Roteador recebe a Requisicao já montada, como a supervisora faria.
        Resposta ret = router.tratar (new Requisicao (metodo, partes[0], query, headers,
            corpo == null ? new byte[0] : corpo.toJson ().getBytes (StandardCharsets.UTF_8), "127.0.0.1"));
        verificar (metodo + " " + caminho + " -> " + esperado + " (veio " + ret.getStatus () + ")", ret.getStatus () == esperado);
        return ret;
    }

    private static Resposta chamadaRaw (Roteador router, String token, String metodo, String caminho, Document corpo, int esperado) throws Exception
    {
        Map<String, String> headers = new HashMap<> ();
        if (token != null) headers.put ("authorization", "Bearer " + token);
        Resposta ret = router.tratar (new Requisicao (metodo, caminho, Map.of (), headers,
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
    /* Cadastro válido. O texto com marcação deve ser guardado literal, sem interpretar HTML. */
    private static Document dados (String talhao)
    {
        // Corpo de POST /api/ocorrencias. O <script> precisa voltar igual no JSON, sem virar HTML.
        return new Document ("titulo", "Praga <literal>").append ("tipo", "Praga").append ("data", "2026-01-25")
            .append ("prioridade", "Alta").append ("descricao", "Descrição única <script>alert(1)</script>\nLinha dois.").append ("talhaoId", talhao);
    }
    /* Evento da timeline. A versão enviada precisa ser a vigente; a anterior recebe 409. */
    private static Document acao (long versao, String acao)
    {
        return new Document ("versao", versao).append ("acao", acao).append ("descricao", "Texto de " + acao);
    }

    /* Recusa URI cujo banco não termine em _teste. */
    private static String uriTeste () throws Exception
    {
        String uri = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null
            : ConexaoMongo.resolverNomeDoBanco (uri, System.getenv ("MONGODB_DATABASE"));
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
            /* Sem token a lista é 401. Cadastro inválido, data, tipo, prioridade, descrição, status, foto e talhão de outra propriedade são 400. Contexto alheio na criação é 409. */
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
            /* A descrição fica literal e o talhão grava a safra vigente do lote. */
            verificar ("descrição literal e vínculo persistidos", registro.getString ("descricao").equals (dados (plotId).getString ("descricao")) && plotId.equals (registro.getString ("talhaoId")) && "2025/26".equals (registro.getString ("safra")));
            /* Foto inexistente é 404. Base64 inválido é 400. O PNG válido marca temFoto e a leitura devolve os bytes, sem alterar a descrição. */
            chamada (router, token, "GET", path + "/foto", null, 404);
            chamada (router, token, "POST", path + "/foto", new Document ("versao", 1).append ("conteudoBase64", "!!!"), 400);
            registro = json (chamada (router, token, "POST", path + "/foto",
                new Document ("versao", 1).append ("conteudoBase64", PNG_1X1).append ("tipoMime", "image/png").append ("nomeArquivo", "campo.png"), 200));
            verificar ("metadado temFoto após upload", Boolean.TRUE.equals (registro.getBoolean ("temFoto")));
            byte[] bytesFoto = chamadaRaw (router, token, "GET", path + "/foto", null, 200).getCorpo ();
            verificar ("bytes PNG recuperados", bytesFoto.length > 8 && bytesFoto[0] == (byte) 0x89);
            verificar ("cadastro intacto após foto", json (chamada (router, token, "GET", path, null, 200)).getString ("descricao").equals (dados (plotId).getString ("descricao")));
            /* Isolamento: outra propriedade e outro usuário recebem 404 no registro, na timeline e na escrita. Talhão alheio no filtro é 400. */
            chamada (router, tokenOutra, "GET", path, null, 404);
            chamada (router, intruso, "GET", path + "/timeline", null, 404);
            chamada (router, intruso, "PUT", path, dados (plotId).append ("versao", 2), 404);
            chamada (router, tokenOutra, "POST", path + "/timeline", acao (1, "resolucao"), 404);
            chamada (router, token, "GET", PREFIXO + "?talhaoId=" + estrangeiro.getObjectId ("_id"), null, 400);
            chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-13", null, 400);
            /* Edição sem a versão vigente é 409. Status não muda pelo PUT do cadastro. Acompanhamento, atualização e resolução exigem a versão certa e uma descrição. */
            chamada (router, token, "PUT", path, dados (plotId), 409);
            chamada (router, token, "PUT", path, dados (plotId).append ("versao", 2).append ("status", "RESOLVIDA"), 400);
            registro = json (chamada (router, token, "PUT", path, dados (plotId).append ("versao", 2).append ("descricao", "Descrição editada"), 200));
            chamada (router, token, "PUT", path, dados (plotId).append ("versao", 2), 409);
            chamada (router, token, "POST", path + "/timeline", acao (3, "atualizacao"), 409);
            chamada (router, token, "POST", path + "/timeline", acao (3, "reabrir"), 400);
            chamada (router, token, "POST", path + "/timeline", acao (3, "acompanhamento").append ("descricao", " "), 400);
            chamada (router, token, "POST", path + "/timeline", acao (3, "acompanhamento"), 200);
            chamada (router, token, "POST", path + "/timeline", acao (4, "acompanhamento"), 409);
            chamada (router, token, "POST", path + "/timeline", acao (4, "atualizacao"), 200);
            chamada (router, token, "POST", path + "/timeline", acao (5, "resolucao"), 200);
            chamada (router, token, "POST", path + "/timeline", acao (6, "acompanhamento"), 409);
            chamada (router, token, "PUT", path, dados (plotId).append ("versao", 6), 409);
            /* O resumo mensal usa o fuso de Brasília: a resolução das 23:59 locais cai em janeiro. */
            verificar ("janeiro conta resolução pelo fuso de Brasília", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-01", null, 200)).getInteger ("resolvidasMes") == 1);
            relogio.agora = Instant.parse ("2026-02-01T03:00:00Z");
            Document geral = json (chamada (router, token, "POST", PREFIXO, dados (null), 201));
            String geralPath = PREFIXO + "/" + geral.getString ("id");
            chamada (router, token, "POST", geralPath + "/timeline", acao (1, "resolucao"), 200);
            /* Fevereiro conta só a resolução de fevereiro. Outro ano não herda a contagem. Filtro de talhão exclui a ocorrência geral. A outra propriedade continua vazia. */
            verificar ("fevereiro conta somente a resolução de fevereiro", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-02", null, 200)).getInteger ("resolvidasMes") == 1);
            verificar ("outro ano não herda a contagem", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2027-02", null, 200)).getInteger ("resolvidasMes") == 0);
            verificar ("talhão exclui a ocorrência geral do indicador", json (chamada (router, token, "GET", PREFIXO + "/resumo?mes=2026-02&talhaoId=" + plotId, null, 200)).getInteger ("resolvidasMes") == 0);
            verificar ("outra propriedade sem contaminação", lista (chamada (router, tokenOutra, "GET", PREFIXO, null, 200)).isEmpty ());
            List<Document> eventos = lista (chamada (router, token, "GET", path + "/timeline", null, 200));
            /* A timeline guarda o texto original e a descrição editada, na ordem dos eventos. */
            verificar ("cinco eventos completos, descrição original preservada", eventos.size () == 5 && eventos.get (0).getString ("descricao").equals (dados (plotId).getString ("descricao")) && eventos.get (1).get ("dados", Document.class).getString ("descricao").equals ("Descrição editada"));
            /* Exclusão lógica tira o registro da lista ativa, mantém a consulta com incluirExcluidas e preserva a resolução no histórico. Repetir com versão velha é 409. */
            chamada (router, token, "DELETE", path, new Document ("versao", 6), 200);
            verificar ("excluída sai da lista ativa", lista (chamada (router, token, "GET", PREFIXO, null, 200)).size () == 1);
            verificar ("excluída continua consultável", lista (chamada (router, token, "GET", PREFIXO + "?incluirExcluidas=true", null, 200)).size () == 2);
            verificar ("histórico após exclusão preserva resolução", lista (chamada (router, token, "GET", path + "/timeline", null, 200)).size () == 6);
            chamada (router, token, "DELETE", path, new Document ("versao", 7), 409);
            verificar ("propriedade com histórico mantém vínculo", repo.temRegistros (propriedade));

            /* Dois clientes leem a mesma versão e disputam a próxima ordem: exatamente um grava, e o histórico não duplica nem perde o evento. */
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
                /* Ordem de evento repetida falha e desfaz a alteração: a versão do registro não avança. */
                verificar ("falha no histórico desfaz alteração do registro", falhou && ((Number) repo.buscar (usuario, propriedade, concorrenteId).get ("versao")).longValue () == 2);
                conexao.fechar (); conexao = new ConexaoMongo (uri);
                RepositorioOcorrencias reconectado = new RepositorioOcorrenciasMongo (conexao);
                /* Reabrir a conexão recupera exclusão, descrição editada e a timeline em ordem, com o evento excluir no fim. */
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

    /* Prova que propriedade com ocorrência não é excluída: o vínculo histórico responde 409. */
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

    /* A suíte de navegador remove somente contas fictícias cujo e-mail casa o padrão do teste. */
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
