/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
import com.mongodb.ConnectionString;
import dados.ConexaoMongo;
import dados.RepositorioCustos;
import dados.RepositorioCustosMemoria;
import dados.RepositorioCustosMongo;
import dados.RepositorioEquipamentos;
import dados.RepositorioEquipamentosMemoria;
import dados.RepositorioEquipamentosMongo;
import dados.RepositorioIntervencoes;
import dados.RepositorioIntervencoesMemoria;
import dados.RepositorioIntervencoesMongo;
import dados.RepositorioPreventivas;
import dados.RepositorioPreventivasMemoria;
import dados.RepositorioPreventivasMongo;
import dados.RepositorioSafras;
import dados.RepositorioSafrasMemoria;
import dados.RepositorioSafrasMongo;
import dados.RepositorioTalhoes;
import dados.RepositorioTalhoesMemoria;
import dados.RepositorioTalhoesMongo;
import handlers.CustosHandler;
import handlers.ManutencaoHandler;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;
import servicos.CustosDeManutencao;

/*
    Manutencao de equipamentos (#234, #235, #236) sem JUnit, direto no roteador, com a API de custos junto.
    Precisa do servidor de algoritmos (o testar.sh sobe um na porta 30100 e exporta SERVIDOR_ALGORITMOS_PORT).
    Uso: java -cp "bin:lib/*" TesteManutencao            (memoria)
         java -cp "bin:lib/*" TesteManutencao --mongo    (MongoDB real, banco terminado em _teste)
    Datas relativas a hoje (America/Sao_Paulo): a agenda tem uma atrasada (ontem), uma proxima (+3) e uma futura (+30).
*/
public class TesteManutencao
{
    private static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");

    /** Simula falha na primeira gravacao de custo de manutencao (custoPendente). */
    private static final class RepositorioCustosFalhaMemoria extends RepositorioCustosMemoria
    {
        private boolean falharManutencao = true;

        @Override
        public synchronized boolean criarSeAusente (Document custo) throws Exception
        {
            Document origem = custo.get ("origem", Document.class);

            if (this.falharManutencao && origem != null && "manutencao".equals (origem.getString ("tipo")))
            {
                this.falharManutencao = false;
                throw new Exception ("Falha simulada na gravacao do custo");
            }

            return super.criarSeAusente (custo);
        }
    }

    private static int total  = 0;
    private static int falhas = 0;

    private static void conferir (String nome, boolean condicao)
    {
        TesteManutencao.total++;

        if (condicao)
            System.out.println ("ok " + nome);
        else
        {
            TesteManutencao.falhas++;
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
        return Document.parse (TesteManutencao.texto (resposta));
    }

    private static List<Document> lista (Resposta resposta)
    {
        return Document.parse ("{\"itens\":" + TesteManutencao.texto (resposta) + "}").getList ("itens", Document.class);
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

    private static int status (Roteador roteador, String token, String metodo, String caminho, String corpo) throws Exception
    {
        return TesteManutencao.chamar (roteador, token, metodo, caminho, corpo).getStatus ();
    }

    private static String campo (Resposta resposta, String nome)
    {
        Document fields = TesteManutencao.json (resposta).get ("fields", Document.class);

        return fields == null ? null : fields.getString (nome);
    }

    private static String dia (int deslocamento)
    {
        return LocalDate.now (TesteManutencao.FUSO).plusDays (deslocamento).toString ();
    }

    private static String talhao (RepositorioTalhoes talhoes, String propriedadeId, String nome) throws Exception
    {
        Document talhao = new Document ("propriedadeId", propriedadeId).append ("nome", nome)
                              .append ("nomeNormalizado", nome.toLowerCase ()).append ("cultura", "Soja")
                              .append ("hectares", 20.0);
        talhoes.criar (talhao);
        return talhao.getObjectId ("_id").toHexString ();
    }

    private static String safra (RepositorioSafras safras, String propriedadeId, String nome, String etapa, String talhaoId) throws Exception
    {
        ArrayList<String> vinculos = new ArrayList<String> ();
        vinculos.add (talhaoId);

        Document safra = new Document ("propriedadeId", propriedadeId).append ("nome", nome)
                             .append ("nomeNormalizado", nome.toLowerCase ()).append ("cultura", "Soja")
                             .append ("talhoes", vinculos).append ("dataInicio", "2026-01-01").append ("dataFim", "2026-12-31")
                             .append ("etapa", etapa).append ("historicoEtapas", new ArrayList<Document> ());
        safras.criar (safra);
        return safra.getObjectId ("_id").toHexString ();
    }

    private static Document equipamento (String nome)
    {
        return new Document ("nome", nome).append ("tipo", "Trator").append ("marca", "John Deere").append ("modelo", "6110J")
                   .append ("ano", 2020).append ("horimetro", 1200);
    }

    private static Document intervencao (String equipamentoId, String tipo, String data, String descricao)
    {
        return new Document ("equipamentoId", equipamentoId).append ("tipo", tipo).append ("data", data)
                   .append ("descricao", descricao).append ("responsavel", "Oficina Central");
    }

    private static String uriTeste () throws Exception
    {
        String uri   = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null : new ConnectionString (uri).getDatabase ();

        if (banco == null || !banco.endsWith ("_teste"))
            throw new Exception ("Use MONGODB_URI com banco terminado em _teste");

        return uri;
    }

    private static Roteador roteador (RepositorioEquipamentos equipamentos, RepositorioPreventivas preventivas,
                                      RepositorioIntervencoes intervencoes, RepositorioTalhoes talhoes, RepositorioSafras safras,
                                      RepositorioCustos custos, Sessoes sessoes) throws Exception
    {
        Roteador ret = new Roteador (sessoes);

        new ManutencaoHandler (equipamentos, preventivas, intervencoes, talhoes, safras, custos,
                               new CustosDeManutencao (custos), sessoes).registrar (ret);
        new CustosHandler (custos, safras, talhoes, sessoes).registrar (ret);

        return ret;
    }

    public static void main (String[] args) throws Exception
    {
        boolean      mongo   = args.length > 0 && args[0].equals ("--mongo");
        ConexaoMongo conexao = mongo ? new ConexaoMongo (TesteManutencao.uriTeste ()) : null;

        try
        {
            if (mongo)
                conexao.getBanco ().drop ();

            RepositorioEquipamentos equipamentos = mongo ? new RepositorioEquipamentosMongo (conexao.getBanco ()) : new RepositorioEquipamentosMemoria ();
            RepositorioPreventivas  preventivas  = mongo ? new RepositorioPreventivasMongo (conexao.getBanco ())  : new RepositorioPreventivasMemoria ();
            RepositorioIntervencoes intervencoes = mongo ? new RepositorioIntervencoesMongo (conexao.getBanco ()) : new RepositorioIntervencoesMemoria ();
            RepositorioTalhoes      talhoes      = mongo ? new RepositorioTalhoesMongo (conexao.getBanco ())      : new RepositorioTalhoesMemoria ();
            RepositorioSafras       safras       = mongo ? new RepositorioSafrasMongo (conexao.getBanco ())       : new RepositorioSafrasMemoria ();
            RepositorioCustos       custos       = mongo ? new RepositorioCustosMongo (conexao.getBanco ())       : new RepositorioCustosMemoria ();

            Sessoes  sessoes     = new Sessoes ();
            Roteador roteador    = TesteManutencao.roteador (equipamentos, preventivas, intervencoes, talhoes, safras, custos, sessoes);
            String   propriedade = new ObjectId ().toHexString ();
            String   outra       = new ObjectId ().toHexString ();
            String   a           = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            String   b           = sessoes.criar (new ObjectId ().toHexString (), outra);
            String   norte       = TesteManutencao.talhao (talhoes, propriedade, "Norte");
            String   sul         = TesteManutencao.talhao (talhoes, propriedade, "Sul");
            String   aberta      = TesteManutencao.safra (safras, propriedade, "Soja 26", "Manejo", norte);
            String   fechada     = TesteManutencao.safra (safras, propriedade, "Soja 25", "Finaliza\u00e7\u00e3o", norte);

            // ---------------------------------------------------------------- equipamentos
            TesteManutencao.conferir ("sem token -> 401 em equipamentos, preventivas, intervencoes e resumo",
                                      TesteManutencao.status (roteador, null, "GET", "/api/equipamentos", null) == 401 &&
                                      TesteManutencao.status (roteador, null, "POST", "/api/preventivas", "{}") == 401 &&
                                      TesteManutencao.status (roteador, null, "GET", "/api/intervencoes", null) == 401 &&
                                      TesteManutencao.status (roteador, null, "GET", "/api/manutencao/resumo", null) == 401);

            Document resumoVazio = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "GET", "/api/manutencao/resumo", null));
            TesteManutencao.conferir ("resumo sem dados: tudo zero (nada inventado)",
                                      resumoVazio.getInteger ("equipamentos") == 0 && TesteManutencao.igual (resumoVazio.get ("gastoNoAno"), 0) &&
                                      TesteManutencao.igual (resumoVazio.get ("preventivasAtrasadas"), 0));

            Resposta semNome = TesteManutencao.chamar (roteador, a, "POST", "/api/equipamentos", TesteManutencao.equipamento (" ").toJson ());
            TesteManutencao.conferir ("equipamento sem nome -> 400 no campo nome",
                                      semNome.getStatus () == 400 && TesteManutencao.campo (semNome, "nome") != null);
            Resposta tipoRuim = TesteManutencao.chamar (roteador, a, "POST", "/api/equipamentos", TesteManutencao.equipamento ("X").append ("tipo", "Drone").toJson ());
            TesteManutencao.conferir ("tipo fora da lista -> 400 no campo tipo", tipoRuim.getStatus () == 400 && TesteManutencao.campo (tipoRuim, "tipo") != null);
            Resposta anoTexto = TesteManutencao.chamar (roteador, a, "POST", "/api/equipamentos", TesteManutencao.equipamento ("X").append ("ano", "dois mil").toJson ());
            TesteManutencao.conferir ("ano em texto -> 400 no campo ano (sem chegar ao socket)", anoTexto.getStatus () == 400 && TesteManutencao.campo (anoTexto, "ano") != null);
            Resposta anoFuturo = TesteManutencao.chamar (roteador, a, "POST", "/api/equipamentos",
                                                         TesteManutencao.equipamento ("X").append ("ano", LocalDate.now (TesteManutencao.FUSO).getYear () + 2).toJson ());
            TesteManutencao.conferir ("ano atual + 2 -> 400 no campo ano", anoFuturo.getStatus () == 400 && TesteManutencao.campo (anoFuturo, "ano") != null);
            Resposta horasNegativas = TesteManutencao.chamar (roteador, a, "POST", "/api/equipamentos", TesteManutencao.equipamento ("X").append ("horimetro", -3).toJson ());
            TesteManutencao.conferir ("horimetro negativo -> 400 no campo horimetro",
                                      horasNegativas.getStatus () == 400 && TesteManutencao.campo (horasNegativas, "horimetro") != null);

            Resposta criado  = TesteManutencao.chamar (roteador, a, "POST", "/api/equipamentos", TesteManutencao.equipamento ("Trator 01").toJson ());
            Document trator  = TesteManutencao.json (criado);
            String   tratorId = trator.getString ("id");
            TesteManutencao.conferir ("equipamento criado -> 201 com status Ativo, sem campos internos",
                                      criado.getStatus () == 201 && "Ativo".equals (trator.getString ("status")) &&
                                      !trator.containsKey ("propriedadeId") && !trator.containsKey ("nomeNormalizado") &&
                                      trator.get ("proximaPreventiva") == null && trator.get ("ultimaIntervencao") == null);
            TesteManutencao.conferir ("nome repetido com maiusculas diferentes -> 409 no campo nome",
                                      TesteManutencao.status (roteador, a, "POST", "/api/equipamentos", TesteManutencao.equipamento ("TRATOR 01").toJson ()) == 409);
            String pulverizadorId = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "POST", "/api/equipamentos",
                TesteManutencao.equipamento ("Pulverizador").append ("tipo", "Pulverizador").append ("status", "Em manuten\u00e7\u00e3o").toJson ())).getString ("id");
            TesteManutencao.conferir ("renomear para nome existente -> 409; mesmo nome no proprio -> 200",
                                      TesteManutencao.status (roteador, a, "PUT", "/api/equipamentos/" + pulverizadorId, TesteManutencao.equipamento ("trator 01").toJson ()) == 409 &&
                                      TesteManutencao.status (roteador, a, "PUT", "/api/equipamentos/" + tratorId, TesteManutencao.equipamento ("Trator 01").append ("modelo", "6125J").toJson ()) == 200);
            TesteManutencao.conferir ("B pode usar o mesmo nome na propria propriedade",
                                      TesteManutencao.status (roteador, b, "POST", "/api/equipamentos", TesteManutencao.equipamento ("Trator 01").toJson ()) == 201);
            TesteManutencao.conferir ("filtro por status e por tipo",
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/equipamentos?status=Ativo", null)).size () == 1 &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/equipamentos?tipo=Pulverizador", null)).size () == 1 &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/equipamentos?q=trat", null)).size () == 1);

            // ---------------------------------------------------------------- preventivas e agenda
            String rotaP = "/api/preventivas";
            Resposta semEquip = TesteManutencao.chamar (roteador, a, "POST", rotaP, new Document ("descricao", "Troca de \u00f3leo").append ("dataPrevista", TesteManutencao.dia (3)).toJson ());
            TesteManutencao.conferir ("preventiva sem equipamento -> 400 no campo equipamentoId",
                                      semEquip.getStatus () == 400 && TesteManutencao.campo (semEquip, "equipamentoId") != null);
            Resposta semDescricao = TesteManutencao.chamar (roteador, a, "POST", rotaP, new Document ("equipamentoId", tratorId).append ("dataPrevista", TesteManutencao.dia (3)).toJson ());
            TesteManutencao.conferir ("preventiva sem descricao -> 400 no campo descricao",
                                      semDescricao.getStatus () == 400 && TesteManutencao.campo (semDescricao, "descricao") != null);
            Resposta dataRuim = TesteManutencao.chamar (roteador, a, "POST", rotaP, new Document ("equipamentoId", tratorId).append ("descricao", "X").append ("dataPrevista", "2026-02-30").toJson ());
            TesteManutencao.conferir ("preventiva com 2026-02-30 -> 400 no campo dataPrevista",
                                      dataRuim.getStatus () == 400 && TesteManutencao.campo (dataRuim, "dataPrevista") != null);
            Resposta intervaloZero = TesteManutencao.chamar (roteador, a, "POST", rotaP, new Document ("equipamentoId", tratorId).append ("descricao", "X")
                                                             .append ("dataPrevista", TesteManutencao.dia (3)).append ("intervaloDias", 0).toJson ());
            TesteManutencao.conferir ("intervalo 0 -> 400 no campo intervaloDias",
                                      intervaloZero.getStatus () == 400 && TesteManutencao.campo (intervaloZero, "intervaloDias") != null);

            String vencida = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "POST", rotaP, new Document ("equipamentoId", tratorId)
                .append ("descricao", "Troca de \u00f3leo do motor").append ("dataPrevista", TesteManutencao.dia (-1)).append ("intervaloDias", 90).toJson ())).getString ("id");
            Resposta emTres = TesteManutencao.chamar (roteador, a, "POST", rotaP, new Document ("equipamentoId", tratorId)
                .append ("descricao", "Filtro de ar").append ("dataPrevista", TesteManutencao.dia (3)).toJson ());
            String proximaId = TesteManutencao.json (emTres).getString ("id");
            String futuraId  = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "POST", rotaP, new Document ("equipamentoId", pulverizadorId)
                .append ("descricao", "Bicos").append ("dataPrevista", TesteManutencao.dia (30)).toJson ())).getString ("id");
            TesteManutencao.conferir ("preventiva criada -> 201 programada, com situacao proxima e 3 dias",
                                      emTres.getStatus () == 201 && "programada".equals (TesteManutencao.json (emTres).getString ("status")) &&
                                      "proxima".equals (TesteManutencao.json (emTres).getString ("situacao")) &&
                                      TesteManutencao.igual (TesteManutencao.json (emTres).get ("diasAte"), 3));

            List<Document> agenda = TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaP, null));
            TesteManutencao.conferir ("agenda: atrasada (ontem), proxima (+3) e futura (+30), em ordem de data",
                                      agenda.size () == 3 && vencida.equals (agenda.get (0).getString ("id")) &&
                                      "atrasada".equals (agenda.get (0).getString ("situacao")) && TesteManutencao.igual (agenda.get (0).get ("diasAte"), -1) &&
                                      "proxima".equals (agenda.get (1).getString ("situacao")) && "futura".equals (agenda.get (2).getString ("situacao")) &&
                                      "Trator 01".equals (agenda.get (0).getString ("equipamentoNome")));
            TesteManutencao.conferir ("agenda filtrada por situacao e por equipamento",
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaP + "?situacao=atrasada", null)).size () == 1 &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaP + "?equipamentoId=" + pulverizadorId, null)).size () == 1 &&
                                      TesteManutencao.status (roteador, a, "GET", rotaP + "?situacao=talvez", null) == 400);
            Document comProxima = TesteManutencao.item (TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/equipamentos", null)), "id", tratorId);
            TesteManutencao.conferir ("lista de equipamentos traz a proxima preventiva (a mais cedo)",
                                      vencida.equals (comProxima.get ("proximaPreventiva", Document.class).getString ("id")));

            Document resumo1 = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "GET", "/api/manutencao/resumo", null));
            TesteManutencao.conferir ("resumo: 2 equipamentos, 1 ativo, 1 em manutencao, 1 atrasada, 1 proxima",
                                      resumo1.getInteger ("equipamentos") == 2 && resumo1.getInteger ("ativos") == 1 && resumo1.getInteger ("emManutencao") == 1 &&
                                      TesteManutencao.igual (resumo1.get ("preventivasAtrasadas"), 1) && TesteManutencao.igual (resumo1.get ("preventivasProximas"), 1));

            Resposta cancelada = TesteManutencao.chamar (roteador, a, "PUT", rotaP + "/" + futuraId, new Document ("descricao", "Bicos").append ("dataPrevista", TesteManutencao.dia (30))
                                                         .append ("status", "cancelada").toJson ());
            TesteManutencao.conferir ("cancelar = PUT com status cancelada; sai da agenda",
                                      cancelada.getStatus () == 200 && "cancelada".equals (TesteManutencao.json (cancelada).getString ("situacao")) &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaP + "?situacao=futura", null)).isEmpty ());
            TesteManutencao.conferir ("status realizada nao vem por PUT -> 400",
                                      TesteManutencao.status (roteador, a, "PUT", rotaP + "/" + futuraId, new Document ("descricao", "Bicos").append ("dataPrevista", TesteManutencao.dia (30))
                                                              .append ("status", "realizada").toJson ()) == 400);
            TesteManutencao.conferir ("DELETE de preventiva cancelada -> 204",
                                      TesteManutencao.status (roteador, a, "DELETE", rotaP + "/" + futuraId, null) == 204);

            // ---------------------------------------------------------------- intervencoes
            String   rotaI     = "/api/intervencoes";
            Resposta futura    = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "corretiva", TesteManutencao.dia (1), "Troca da correia").toJson ());
            TesteManutencao.conferir ("intervencao com data futura -> 400 no campo data", futura.getStatus () == 400 && TesteManutencao.campo (futura, "data") != null);
            Resposta valorZero = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "corretiva", TesteManutencao.dia (0), "X").append ("valor", 0).toJson ());
            TesteManutencao.conferir ("valor zero -> 400 no campo valor (sem despesa zerada)", valorZero.getStatus () == 400 && TesteManutencao.campo (valorZero, "valor") != null);
            Resposta valorTexto = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "corretiva", TesteManutencao.dia (0), "X").append ("valor", "350").toJson ());
            TesteManutencao.conferir ("valor em texto -> 400 no campo valor", valorTexto.getStatus () == 400 && TesteManutencao.campo (valorTexto, "valor") != null);
            Resposta tipoInt = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "reforma", TesteManutencao.dia (0), "X").toJson ());
            TesteManutencao.conferir ("tipo de intervencao invalido -> 400 no campo tipo", tipoInt.getStatus () == 400 && TesteManutencao.campo (tipoInt, "tipo") != null);
            Resposta semEquipI = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (null, "corretiva", TesteManutencao.dia (0), "X").toJson ());
            TesteManutencao.conferir ("intervencao sem equipamento -> 400 no campo equipamentoId",
                                      semEquipI.getStatus () == 400 && TesteManutencao.campo (semEquipI, "equipamentoId") != null);
            Resposta talhaoFora = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "corretiva", TesteManutencao.dia (0), "X")
                                                          .append ("valor", 10).append ("talhaoId", sul).append ("safraId", aberta).toJson ());
            TesteManutencao.conferir ("talhao fora da safra -> 400 no campo talhaoId",
                                      talhaoFora.getStatus () == 400 && TesteManutencao.campo (talhaoFora, "talhaoId") != null);
            Resposta corretivaNaPreventiva = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "corretiva", TesteManutencao.dia (0), "X")
                                                                     .append ("preventivaId", vencida).toJson ());
            TesteManutencao.conferir ("intervencao corretiva ligada a preventiva -> 400 no campo tipo",
                                      corretivaNaPreventiva.getStatus () == 400 && TesteManutencao.campo (corretivaNaPreventiva, "tipo") != null);
            Resposta preventivaDeOutro = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (pulverizadorId, "preventiva", TesteManutencao.dia (0), "X")
                                                                 .append ("preventivaId", vencida).toJson ());
            TesteManutencao.conferir ("preventiva de outro equipamento -> 400 no campo preventivaId",
                                      preventivaDeOutro.getStatus () == 400 && TesteManutencao.campo (preventivaDeOutro, "preventivaId") != null);

            Resposta naFechada = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "corretiva", TesteManutencao.dia (0), "Na safra fechada")
                                                         .append ("valor", 99.9).append ("safraId", fechada).toJson ());
            TesteManutencao.conferir ("safra finalizada -> 409 e nada gravado (nem intervencao, nem custo)",
                                      naFechada.getStatus () == 409 &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaI, null)).isEmpty () &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null)).isEmpty ());

            Resposta semValor = TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (pulverizadorId, "corretiva", TesteManutencao.dia (0), "Ajuste dos bicos").toJson ());
            TesteManutencao.conferir ("intervencao sem valor -> 201 e nenhum custo (GET /api/custos vazio)",
                                      semValor.getStatus () == 201 && TesteManutencao.json (semValor).get ("custoId") == null &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null)).isEmpty ());

            String agoraAtomico = Instant.now ().toString ();
            TesteManutencao.conferir ("marcarRealizadaSeProgramada: segunda chamada recusa (TOCTOU)",
                                      preventivas.marcarRealizadaSeProgramada (propriedade, proximaId, "000000000000000000000001", TesteManutencao.dia (0), agoraAtomico) &&
                                      !preventivas.marcarRealizadaSeProgramada (propriedade, proximaId, "000000000000000000000002", TesteManutencao.dia (0), agoraAtomico));
            preventivas.atualizar (propriedade, proximaId, new Document ("status", "programada").append ("intervencaoId", null)
                                       .append ("realizadaEm", null).append ("atualizadoEm", agoraAtomico));

            RepositorioCustos custosPendentes = new TesteManutencao.RepositorioCustosFalhaMemoria ();
            Roteador          roteadorPendente = TesteManutencao.roteador (equipamentos, preventivas, intervencoes, talhoes, safras, custosPendentes, sessoes);
            Document          corpoPendente    = TesteManutencao.intervencao (pulverizadorId, "corretiva", TesteManutencao.dia (0), "Peca com falha de custo")
                                                     .append ("valor", 80);
            Resposta          comPendente       = TesteManutencao.chamar (roteadorPendente, a, "POST", rotaI, corpoPendente.toJson ());
            Document          jsonPendente       = TesteManutencao.json (comPendente);
            String            pendenteId         = jsonPendente.getString ("id");
            TesteManutencao.conferir ("custo pendente -> 201 com custoPendente e sem custoId",
                                      comPendente.getStatus () == 201 && Boolean.TRUE.equals (jsonPendente.get ("custoPendente")) &&
                                      jsonPendente.get ("custoId") == null &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteadorPendente, a, "GET", "/api/custos", null)).isEmpty ());
            Resposta recupera = TesteManutencao.chamar (roteadorPendente, a, "PUT", rotaI + "/" + pendenteId, corpoPendente.toJson ());
            TesteManutencao.conferir ("editar intervencao recria o custo pendente",
                                      recupera.getStatus () == 200 && TesteManutencao.json (recupera).getString ("custoId") != null &&
                                      !TesteManutencao.json (recupera).containsKey ("custoPendente") &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteadorPendente, a, "GET", "/api/custos", null)).size () == 1);
            TesteManutencao.conferir ("limpar intervencao do teste de custo pendente",
                                      TesteManutencao.status (roteadorPendente, a, "DELETE", rotaI + "/" + pendenteId, null) == 204 &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null)).isEmpty ());

            // a preventiva vencida e realizada com valor, talhao e safra: gera custo e a proxima preventiva
            Document realizar = TesteManutencao.intervencao (tratorId, "preventiva", TesteManutencao.dia (0), "Troca de \u00f3leo do motor")
                                    .append ("preventivaId", vencida).append ("valor", 350.50).append ("horimetro", 1300)
                                    .append ("talhaoId", norte).append ("safraId", aberta);
            Resposta feita  = TesteManutencao.chamar (roteador, a, "POST", rotaI, realizar.toJson ());
            Document comCusto = TesteManutencao.json (feita);
            String   intervencaoId = comCusto.getString ("id");
            String   custoId       = comCusto.getString ("custoId");
            TesteManutencao.conferir ("intervencao com valor 350,50 -> 201 com custoId e custoValor",
                                      feita.getStatus () == 201 && custoId != null && TesteManutencao.igual (comCusto.get ("custoValor"), 350.50) &&
                                      !comCusto.containsKey ("custoPendente"));

            List<Document> listaCustos = TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null));
            Document       custo       = listaCustos.isEmpty () ? new Document () : listaCustos.get (0);
            TesteManutencao.conferir ("custo em Maquinario com origem manutencao, mesmo talhao, safra e data",
                                      listaCustos.size () == 1 && "Maquin\u00e1rio".equals (custo.getString ("category")) &&
                                      TesteManutencao.igual (custo.get ("value"), 350.50) && norte.equals (custo.getString ("talhaoId")) &&
                                      aberta.equals (custo.getString ("safraId")) && TesteManutencao.dia (0).equals (custo.getString ("date")) &&
                                      "manutencao".equals (custo.get ("origem", Document.class).getString ("tipo")) &&
                                      intervencaoId.equals (custo.get ("origem", Document.class).getString ("intervencaoId")) &&
                                      custo.getString ("description").startsWith ("Manuten\u00e7\u00e3o preventiva: Trator 01"));

            CustosDeManutencao servico = new CustosDeManutencao (custos);
            Document           repetido = servico.registrar (propriedade, intervencoes.buscarPorId (propriedade, intervencaoId),
                                                             equipamentos.buscarPorId (propriedade, tratorId));
            TesteManutencao.conferir ("repetir o registro nao duplica (devolve o mesmo custo)",
                                      custoId.equals (repetido.getObjectId ("_id").toHexString ()) &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null)).size () == 1);

            Resposta putCusto = TesteManutencao.chamar (roteador, a, "PUT", "/api/custos/" + custoId,
                new Document ("description", "x").append ("category", "Outros").append ("value", 1).append ("date", TesteManutencao.dia (0)).toJson ());
            Resposta delCusto = TesteManutencao.chamar (roteador, a, "DELETE", "/api/custos/" + custoId, null);
            TesteManutencao.conferir ("PUT e DELETE do custo de manutencao -> 409 com a mensagem da manutencao",
                                      putCusto.getStatus () == 409 && delCusto.getStatus () == 409 &&
                                      TesteManutencao.json (putCusto).getString ("error").contains ("manuten\u00e7\u00e3o do equipamento") &&
                                      TesteManutencao.json (delCusto).getString ("error").contains ("Corrija pela interven\u00e7\u00e3o"));

            List<Document> depois  = TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaP, null));
            Document       gerada  = null;
            for (Document preventiva : depois)
                if ("programada".equals (preventiva.getString ("status")) && "Troca de \u00f3leo do motor".equals (preventiva.getString ("descricao")))
                    gerada = preventiva;
            Document realizadaP = TesteManutencao.item (depois, "id", vencida);
            TesteManutencao.conferir ("preventiva vira realizada e a proxima nasce em hoje + 90 dias",
                                      "realizada".equals (realizadaP.getString ("status")) && intervencaoId.equals (realizadaP.getString ("intervencaoId")) &&
                                      gerada != null && TesteManutencao.dia (90).equals (gerada.getString ("dataPrevista")) &&
                                      "futura".equals (gerada.getString ("situacao")));
            TesteManutencao.conferir ("preventiva realizada: PUT e DELETE -> 409",
                                      TesteManutencao.status (roteador, a, "PUT", rotaP + "/" + vencida, new Document ("descricao", "X").append ("dataPrevista", TesteManutencao.dia (1)).toJson ()) == 409 &&
                                      TesteManutencao.status (roteador, a, "DELETE", rotaP + "/" + vencida, null) == 409);
            TesteManutencao.conferir ("horimetro maior (1300) atualiza o equipamento",
                                      TesteManutencao.igual (TesteManutencao.json (TesteManutencao.chamar (roteador, a, "GET", "/api/equipamentos/" + tratorId, null)).get ("horimetro"), 1300));

            List<Document> historico = TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/equipamentos/" + tratorId + "/historico", null));
            TesteManutencao.conferir ("historico do equipamento traz a intervencao com custoId e valor",
                                      historico.size () == 1 && custoId.equals (historico.get (0).getString ("custoId")) &&
                                      TesteManutencao.igual (historico.get (0).get ("valor"), 350.50) && "Norte".equals (historico.get (0).getString ("talhaoNome")));

            Document resumo2 = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "GET", "/api/manutencao/resumo", null));
            TesteManutencao.conferir ("resumo: gasto no ano 350,50; nenhuma atrasada; 1 proxima",
                                      TesteManutencao.igual (resumo2.get ("gastoNoAno"), 350.50) &&
                                      TesteManutencao.igual (resumo2.get ("preventivasAtrasadas"), 0) && TesteManutencao.igual (resumo2.get ("preventivasProximas"), 1));

            // editar valor, remover valor e voltar a ter valor
            Document editar = new Document (realizar).append ("valor", 410.25);
            Resposta editada = TesteManutencao.chamar (roteador, a, "PUT", rotaI + "/" + intervencaoId, editar.toJson ());
            TesteManutencao.conferir ("editar o valor atualiza o mesmo custo (410,25)",
                                      editada.getStatus () == 200 && custoId.equals (TesteManutencao.json (editada).getString ("custoId")) &&
                                      TesteManutencao.igual (TesteManutencao.json (TesteManutencao.chamar (roteador, a, "GET", "/api/custos/" + custoId, null)).get ("value"), 410.25));
            Resposta semValorAgora = TesteManutencao.chamar (roteador, a, "PUT", rotaI + "/" + intervencaoId, new Document (realizar).append ("valor", null).toJson ());
            TesteManutencao.conferir ("remover o valor exclui o custo",
                                      semValorAgora.getStatus () == 200 && TesteManutencao.json (semValorAgora).get ("custoId") == null &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null)).isEmpty ());
            Resposta deNovo = TesteManutencao.chamar (roteador, a, "PUT", rotaI + "/" + intervencaoId, new Document (realizar).append ("valor", 120).toJson ());
            String   novoCusto = TesteManutencao.json (deNovo).getString ("custoId");
            TesteManutencao.conferir ("valor novo onde nao havia cria o custo de novo",
                                      deNovo.getStatus () == 200 && novoCusto != null &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null)).size () == 1);
            TesteManutencao.conferir ("trocar o equipamento ou a preventiva da intervencao -> 400",
                                      TesteManutencao.status (roteador, a, "PUT", rotaI + "/" + intervencaoId, new Document (realizar).append ("equipamentoId", pulverizadorId).toJson ()) == 400 &&
                                      TesteManutencao.status (roteador, a, "PUT", rotaI + "/" + intervencaoId, new Document (realizar).append ("preventivaId", proximaId).toJson ()) == 400);
            TesteManutencao.conferir ("PUT para safra finalizada -> 409",
                                      TesteManutencao.status (roteador, a, "PUT", rotaI + "/" + intervencaoId, new Document (realizar).append ("safraId", fechada).append ("talhaoId", null).toJson ()) == 409);
            TesteManutencao.conferir ("lista de intervencoes filtrada por tipo e equipamento",
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaI + "?tipo=preventiva", null)).size () == 1 &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaI + "?equipamentoId=" + pulverizadorId, null)).size () == 1 &&
                                      TesteManutencao.status (roteador, a, "GET", rotaI + "?tipo=reforma", null) == 400);

            // ---------------------------------------------------------------- isolamento e contexto
            String rotaE = "/api/equipamentos/" + tratorId;
            TesteManutencao.conferir ("B: equipamento de A -> 404 em GET, PUT e historico",
                                      TesteManutencao.status (roteador, b, "GET", rotaE, null) == 404 &&
                                      TesteManutencao.status (roteador, b, "PUT", rotaE, TesteManutencao.equipamento ("Roubado").toJson ()) == 404 &&
                                      TesteManutencao.status (roteador, b, "GET", rotaE + "/historico", null) == 404);
            TesteManutencao.conferir ("B: preventiva e intervencao de A -> 404; programar no equipamento de A -> 404",
                                      TesteManutencao.status (roteador, b, "PUT", rotaP + "/" + proximaId, new Document ("descricao", "X").append ("dataPrevista", TesteManutencao.dia (1)).toJson ()) == 404 &&
                                      TesteManutencao.status (roteador, b, "DELETE", rotaP + "/" + proximaId, null) == 404 &&
                                      TesteManutencao.status (roteador, b, "PUT", rotaI + "/" + intervencaoId, realizar.toJson ()) == 404 &&
                                      TesteManutencao.status (roteador, b, "DELETE", rotaI + "/" + intervencaoId, null) == 404 &&
                                      TesteManutencao.status (roteador, b, "POST", rotaP, new Document ("equipamentoId", tratorId).append ("descricao", "X").append ("dataPrevista", TesteManutencao.dia (1)).toJson ()) == 404 &&
                                      TesteManutencao.status (roteador, b, "POST", rotaI, TesteManutencao.intervencao (tratorId, "corretiva", TesteManutencao.dia (0), "X").toJson ()) == 404);
            TesteManutencao.conferir ("B nao ve agenda, intervencoes, custos nem gasto de A",
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, b, "GET", rotaP, null)).isEmpty () &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, b, "GET", rotaI, null)).isEmpty () &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, b, "GET", "/api/custos", null)).isEmpty () &&
                                      TesteManutencao.igual (TesteManutencao.json (TesteManutencao.chamar (roteador, b, "GET", "/api/manutencao/resumo", null)).get ("gastoNoAno"), 0));

            String                  trocada    = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            HashMap<String, String> cabecalhos = new HashMap<String, String> ();
            cabecalhos.put ("authorization", "Bearer " + trocada);
            Requisicao requisicao = new Requisicao ("POST", "/api/equipamentos", new HashMap<String, String> (), cabecalhos,
                                                    TesteManutencao.equipamento ("Colhedora").toJson ().getBytes (StandardCharsets.UTF_8), "127.0.0.1");
            requisicao.setSessao (sessoes.buscar (trocada));
            sessoes.selecionarPropriedade (trocada, outra);
            TesteManutencao.conferir ("propriedade trocada no meio da escrita -> 409",
                                      new ManutencaoHandler (equipamentos, preventivas, intervencoes, talhoes, safras, custos,
                                                             new CustosDeManutencao (custos), sessoes).tratar (requisicao).getStatus () == 409);

            // ---------------------------------------------------------------- excluir desfaz
            TesteManutencao.conferir ("DELETE da intervencao -> 204 e o custo vai junto",
                                      TesteManutencao.status (roteador, a, "DELETE", rotaI + "/" + intervencaoId, null) == 204 &&
                                      TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", "/api/custos", null)).isEmpty () &&
                                      TesteManutencao.status (roteador, a, "GET", "/api/custos/" + novoCusto, null) == 404);
            List<Document> desfeita = TesteManutencao.lista (TesteManutencao.chamar (roteador, a, "GET", rotaP, null));
            TesteManutencao.conferir ("excluir a intervencao devolve a preventiva para programada e apaga a proxima gerada",
                                      desfeita.size () == 2 && "programada".equals (TesteManutencao.item (desfeita, "id", vencida).getString ("status")) &&
                                      TesteManutencao.item (desfeita, "id", vencida).get ("intervencaoId") == null &&
                                      "atrasada".equals (TesteManutencao.item (desfeita, "id", vencida).getString ("situacao")));

            // a proxima gerada ja realizada impede excluir a primeira intervencao
            String primeira = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "preventiva", TesteManutencao.dia (-2), "Troca de \u00f3leo do motor")
                .append ("preventivaId", vencida).toJson ())).getString ("id");
            String geradaId = intervencoes.buscarPorId (propriedade, primeira).getString ("proximaPreventivaId");
            TesteManutencao.conferir ("realizar de novo gera outra proxima (hoje - 2 + 90)",
                                      geradaId != null && TesteManutencao.dia (88).equals (preventivas.buscarPorId (propriedade, geradaId).getString ("dataPrevista")));
            String segunda = TesteManutencao.json (TesteManutencao.chamar (roteador, a, "POST", rotaI, TesteManutencao.intervencao (tratorId, "preventiva", TesteManutencao.dia (0), "Troca de \u00f3leo antecipada")
                .append ("preventivaId", geradaId).toJson ())).getString ("id");
            TesteManutencao.conferir ("excluir a intervencao cuja proxima ja foi realizada -> 409",
                                      segunda != null && TesteManutencao.status (roteador, a, "DELETE", rotaI + "/" + primeira, null) == 409);

            // ---------------------------------------------------------------- Mongo: indices e reinicio
            if (mongo)
            {
                Document duplicado = new Document ("propriedadeId", propriedade).append ("nome", "TRATOR 01").append ("nomeNormalizado", "trator 01");
                TesteManutencao.conferir ("Mongo: indice unico recusa nome repetido direto no repositorio",
                                          !new RepositorioEquipamentosMongo (conexao.getBanco ()).criar (duplicado));

                Document origem   = new Document ("tipo", "manutencao").append ("intervencaoId", segunda).append ("equipamentoId", tratorId);
                Document primeiro = new Document ("description", "a").append ("category", "Maquin\u00e1rio").append ("value", 1.0)
                                        .append ("date", TesteManutencao.dia (0)).append ("origem", origem).append ("propriedadeId", propriedade);
                Document segundo  = new Document ("description", "b").append ("category", "Maquin\u00e1rio").append ("value", 2.0)
                                        .append ("date", TesteManutencao.dia (0)).append ("origem", new Document (origem)).append ("propriedadeId", propriedade);
                RepositorioCustosMongo novosCustos = new RepositorioCustosMongo (conexao.getBanco ());
                TesteManutencao.conferir ("Mongo: indice parcial de origem.intervencaoId recusa o segundo custo da mesma intervencao",
                                          novosCustos.criarSeAusente (primeiro) && !novosCustos.criarSeAusente (segundo));

                Roteador reiniciado = TesteManutencao.roteador (new RepositorioEquipamentosMongo (conexao.getBanco ()), new RepositorioPreventivasMongo (conexao.getBanco ()),
                                                            new RepositorioIntervencoesMongo (conexao.getBanco ()), new RepositorioTalhoesMongo (conexao.getBanco ()),
                                                            new RepositorioSafrasMongo (conexao.getBanco ()), novosCustos, sessoes);
                TesteManutencao.conferir ("Mongo: repositorios novos (reinicio) leem equipamentos, agenda e historico",
                                          TesteManutencao.lista (TesteManutencao.chamar (reiniciado, a, "GET", "/api/equipamentos", null)).size () == 2 &&
                                          TesteManutencao.lista (TesteManutencao.chamar (reiniciado, a, "GET", "/api/equipamentos/" + tratorId + "/historico", null)).size () == 2 &&
                                          TesteManutencao.lista (TesteManutencao.chamar (reiniciado, a, "GET", rotaP, null)).size () == 4);
            }
        }
        catch (Exception erro)
        {
            TesteManutencao.falhas++;
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

        System.out.println ("TesteManutencao: " + (TesteManutencao.total - TesteManutencao.falhas) + "/" +
                            TesteManutencao.total + " verifica\u00e7\u00f5es OK" + (mongo ? " (MongoDB)" : " (mem\u00f3ria)"));

        if (TesteManutencao.falhas > 0)
            System.exit (1);
    }
}
