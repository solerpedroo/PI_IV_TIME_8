/* Miguel — RA: 25003110 */
import dados.*;
import handlers.RelatorioSafraHandler;
import http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;
import servicos.RelatorioSafra;

/* Casos determinísticos do recorte e da segurança, sem depender da interface. */
public class TesteRelatorioSafra
{
    private static int total;
    private static void verificar (String nome, boolean ok)
    {
        if (!ok) throw new AssertionError (nome);
        total++;
        System.out.println ("ok " + nome);
    }
    private static Resposta consultar (Roteador rotas, String token, String id, int status) throws Exception
    {
        Map<String, String> cabecalhos = new HashMap<> ();
        if (token != null) cabecalhos.put ("authorization", "Bearer " + token);
        Resposta resposta = rotas.tratar (new Requisicao ("GET", "/api/relatorios/safras/" + id,
            Map.of (), cabecalhos, new byte[0], "127.0.0.1"));
        verificar ("HTTP " + status, resposta.getStatus () == status);
        return resposta;
    }
    private static Document registro (String safra, String data)
    {
        return new Document ("_id", new ObjectId ()).append ("safraId", safra).append ("data", data)
            .append ("nome", "Mesmo texto").append ("talhao", "Norte").append ("status", "Pendente");
    }
    public static void main (String[] args) throws Exception
    {
        Document propriedade = new Document ("_id", new ObjectId ()).append ("farm", "Fazenda teste");
        Document talhao = new Document ("_id", new ObjectId ()).append ("nome", "Norte").append ("hectares", 12.5);
        String tid = talhao.getObjectId ("_id").toHexString ();
        Document safra = new Document ("_id", new ObjectId ()).append ("nome", "Safra teste").append ("cultura", "Soja")
            .append ("dataInicio", "2026-01-01").append ("dataFim", "2026-12-31").append ("etapa", "Planejamento")
            .append ("talhoes", List.of (tid));
        String sid = RelatorioSafra.id (safra);
        List<Document> atividades = List.of (registro (sid, "2026-01-01"), registro (sid, "2026-12-31"),
            registro (sid, "12 ago"), registro (sid, "2027-01-01"), registro ("outra", "2026-02-01"),
            registro (sid, "2026-02-30"), registro (sid, "2026-01-01").append ("talhao", "Inexistente"));
        List<Document> custos = new ArrayList<> ();
        for (double valor : List.of (0.1, 0.2)) custos.add (new Document ("_id", new ObjectId ()).append ("safraId", sid)
            .append ("description", "Custo").append ("category", "Outros").append ("date", "2026-06-01").append ("value", valor));
        custos.add (new Document (custos.get (0)).append ("_id", new ObjectId ()).append ("safraId", null).append ("value", 999));
        custos.add (new Document (custos.get (0)).append ("_id", new ObjectId ()).append ("date", "2027-01-01").append ("value", 100));
        custos.add (new Document (custos.get (0)).append ("_id", new ObjectId ()).append ("talhaoId", "inexistente"));
        Document ocorrencia = new Document ("_id", new ObjectId ()).append ("titulo", "Ocorrência")
            .append ("talhaoId", tid).append ("talhaoNome", "Norte").append ("data", "2026-01-01");
        List<Document> ocorrencias = List.of (ocorrencia, new Document (ocorrencia).append ("_id", new ObjectId ()).append ("data", "2026-12-31"),
            new Document (ocorrencia).append ("excluida", true), new Document (ocorrencia).append ("talhaoId", null),
            new Document (ocorrencia).append ("data", "2027-01-01"));
        Document resultado = RelatorioSafra.compor (propriedade, safra, List.of (talhao), atividades, ocorrencias, custos);
        verificar ("área atual", resultado.getDouble ("areaHectares") == 12.5);
        verificar ("nome legado da propriedade", resultado.get ("propriedade", Document.class).getString ("nome").equals ("Fazenda teste"));
        Document contagens = resultado.get ("contagens", Document.class);
        verificar ("atividades por ID, datas inclusivas e vínculo explícito", contagens.getLong ("atividade") == 2);
        verificar ("ocorrências por talhão e data, sem excluídas", contagens.getLong ("ocorrencia") == 2);
        verificar ("custos gerais da safra sem ratear custos sem safra", contagens.getLong ("custo") == 2);
        verificar ("soma exata 0,10 + 0,20", resultado.getLong ("totalCentavos") == 30);
        verificar ("datas e vínculos inválidos como pendências", resultado.getList ("pendencias", Document.class).size () == 6);
        Document vazio = RelatorioSafra.compor (propriedade, safra, List.of (talhao), List.of (), List.of (), List.of ());
        verificar ("conjunto vazio real", vazio.getList ("registros", Document.class).isEmpty () && vazio.getLong ("totalCentavos") == 0);
        Document semTalhao = RelatorioSafra.compor (propriedade, safra, List.of (), List.of (), List.of (), List.of ());
        verificar ("talhão removido não inventa área", semTalhao.getDouble ("areaHectares") == 0 && semTalhao.getList ("pendencias", Document.class).size () == 1);
        for (String fim : List.of ("2025-01-01", "2026-02-30"))
        {
            try { RelatorioSafra.compor (propriedade, new Document (safra).append ("dataFim", fim), List.of (talhao), List.of (), List.of (), List.of ()); throw new AssertionError ("Período aceito"); }
            catch (ErroHttp erro) { verificar ("período inválido rejeitado", erro.getStatus () == 409); }
        }
        RepositorioUsuariosMemoria usuarios = new RepositorioUsuariosMemoria ();
        Document usuario = usuarios.criar (new Document ("email", "relatorio@teste"), new Document ("farm", "Teste"));
        String uid = RelatorioSafra.id (usuario), pid = usuario.getString ("propriedadeId");
        RepositorioSafrasMemoria safras = new RepositorioSafrasMemoria ();
        Document persistida = new Document (safra).append ("propriedadeId", pid).append ("nomeNormalizado", "teste");
        safras.criar (persistida);
        RepositorioTalhoesMemoria talhoes = new RepositorioTalhoesMemoria ();
        RepositorioAtividadesMemoria atividadesRepo = new RepositorioAtividadesMemoria ();
        RepositorioOcorrenciasMemoria ocorrenciasRepo = new RepositorioOcorrenciasMemoria ();
        RepositorioCustosMemoria custosRepo = new RepositorioCustosMemoria ();
        Sessoes sessoes = new Sessoes ();
        String token = sessoes.criar (uid, pid), alheio = sessoes.criar (uid, "outra");
        Roteador rotas = new Roteador (sessoes);
        new RelatorioSafraHandler (usuarios, safras, talhoes, atividadesRepo, ocorrenciasRepo, custosRepo, sessoes).registrar (rotas);
        String id = RelatorioSafra.id (persistida);
        consultar (rotas, null, id, 401);
        consultar (rotas, "token-inválido", id, 401);
        consultar (rotas, token, "inválido", 400);
        consultar (rotas, token, new ObjectId ().toHexString (), 404);
        consultar (rotas, alheio, id, 404);
        verificar ("cache privado desativado", "no-store".equals (consultar (rotas, token, id, 200).getCabecalhos ().get ("Cache-Control")));
        RepositorioCustosMemoria falha = new RepositorioCustosMemoria () {
            public List<Document> listar (String propriedadeId) throws Exception { throw new Exception ("Fonte indisponível (teste)"); }
        };
        Roteador indisponivel = new Roteador (sessoes);
        new RelatorioSafraHandler (usuarios, safras, talhoes, atividadesRepo, ocorrenciasRepo, falha, sessoes).registrar (indisponivel);
        consultar (indisponivel, token, id, 500);
        RepositorioCustosMemoria troca = new RepositorioCustosMemoria () {
            public List<Document> listar (String propriedadeId) throws Exception { sessoes.selecionarPropriedade (token, "outra"); return List.of (); }
        };
        Roteador concorrente = new Roteador (sessoes);
        new RelatorioSafraHandler (usuarios, safras, talhoes, atividadesRepo, ocorrenciasRepo, troca, sessoes).registrar (concorrente);
        consultar (concorrente, token, id, 409);
        sessoes.encerrar (token);
        consultar (rotas, token, id, 401);
        System.out.println ("Relatório safra: " + total + " verificações aprovadas.");
    }
}
