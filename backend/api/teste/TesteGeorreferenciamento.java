/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
import com.mongodb.ConnectionString;
import dados.ConexaoMongo;
import dados.RepositorioAtividades;
import dados.RepositorioAtividadesMemoria;
import dados.RepositorioAtividadesMongo;
import dados.RepositorioOcorrencias;
import dados.RepositorioOcorrenciasMemoria;
import dados.RepositorioOcorrenciasMongo;
import dados.RepositorioTalhoes;
import dados.RepositorioTalhoesMemoria;
import dados.RepositorioTalhoesMongo;
import handlers.GeorreferenciamentoHandler;
import handlers.OcorrenciasHandler;
import handlers.TalhoesHandler;
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
    Georreferenciamento (#230, #231, #232) sem JUnit, direto no roteador.
    Precisa do servidor de algoritmos (o testar.sh sobe um na porta 30100 e exporta SERVIDOR_ALGORITMOS_PORT).
    Uso: java -cp "bin:lib/*" TesteGeorreferenciamento            (memoria)
         java -cp "bin:lib/*" TesteGeorreferenciamento --mongo    (MongoDB real, banco terminado em _teste)
    Conjunto: Norte com poligono de 0,003 x 0,003 grau perto de Campinas (~10,2 ha calculados),
    Sul com um ponto e Leste sem localizacao.
*/
public class TesteGeorreferenciamento
{
    private static int total  = 0;
    private static int falhas = 0;

    private static void conferir (String nome, boolean condicao)
    {
        TesteGeorreferenciamento.total++;

        if (condicao)
            System.out.println ("ok " + nome);
        else
        {
            TesteGeorreferenciamento.falhas++;
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
        return Document.parse (TesteGeorreferenciamento.texto (resposta));
    }

    private static List<Document> lista (Resposta resposta)
    {
        return Document.parse ("{\"itens\":" + TesteGeorreferenciamento.texto (resposta) + "}").getList ("itens", Document.class);
    }

    private static Document item (List<Document> itens, String chave, String valor)
    {
        Document ret = null;

        for (Document item : itens)
            if (valor.equals (item.getString (chave)))
                ret = item;

        return ret;
    }

    private static boolean perto (Object valor, double esperado, double tolerancia)
    {
        return valor instanceof Number && Math.abs (((Number) valor).doubleValue () - esperado) <= tolerancia;
    }

    private static int status (Roteador roteador, String token, String metodo, String caminho, String corpo) throws Exception
    {
        return TesteGeorreferenciamento.chamar (roteador, token, metodo, caminho, corpo).getStatus ();
    }

    private static String campo (Resposta resposta, String nome)
    {
        Document fields = TesteGeorreferenciamento.json (resposta).get ("fields", Document.class);

        return fields == null ? null : fields.getString (nome);
    }

    // {"tipo": tipo, "pontos": [{lat, lng}, ...]} a partir de pares lat, lng
    private static String localizacao (String tipo, double... coordenadas)
    {
        ArrayList<Document> pontos = new ArrayList<Document> ();

        for (int i = 0; i + 1 < coordenadas.length; i += 2)
            pontos.add (new Document ("lat", coordenadas[i]).append ("lng", coordenadas[i + 1]));

        return new Document ("tipo", tipo).append ("pontos", pontos).toJson ();
    }

    private static String talhao (Roteador roteador, String token, String nome, double hectares) throws Exception
    {
        String corpo = new Document ("nome", nome).append ("cultura", "Soja").append ("hectares", hectares).toJson ();

        return TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, token, "POST", "/api/talhoes", corpo)).getString ("id");
    }

    private static Document ocorrencia (String titulo, String talhaoId, Document posicao)
    {
        Document ret = new Document ("titulo", titulo).append ("tipo", "Praga").append ("prioridade", "Alta")
                           .append ("data", "2026-10-01").append ("descricao", "Registro de teste");

        if (talhaoId != null)
            ret.append ("talhaoId", talhaoId);

        if (posicao != null)
            ret.append ("localizacao", posicao);

        return ret;
    }

    private static Document posicao (double lat, double lng)
    {
        return new Document ("lat", lat).append ("lng", lng);
    }

    private static String uriTeste () throws Exception
    {
        String uri   = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null : new ConnectionString (uri).getDatabase ();

        if (banco == null || !banco.endsWith ("_teste"))
            throw new Exception ("Use MONGODB_URI com banco terminado em _teste");

        return uri;
    }

    private static Roteador roteador (RepositorioTalhoes talhoes, RepositorioAtividades atividades,
                                      RepositorioOcorrencias ocorrencias, Sessoes sessoes) throws Exception
    {
        Roteador ret = new Roteador (sessoes);

        new TalhoesHandler (talhoes, atividades, sessoes).registrar (ret);
        new OcorrenciasHandler (ocorrencias, talhoes, sessoes).registrar (ret);
        new GeorreferenciamentoHandler (talhoes, ocorrencias, sessoes).registrar (ret);

        return ret;
    }

    public static void main (String[] args) throws Exception
    {
        boolean      mongo   = args.length > 0 && args[0].equals ("--mongo");
        ConexaoMongo conexao = mongo ? new ConexaoMongo (TesteGeorreferenciamento.uriTeste ()) : null;

        try
        {
            if (mongo)
                conexao.getBanco ().drop ();

            RepositorioTalhoes     talhoes     = mongo ? new RepositorioTalhoesMongo (conexao.getBanco ()) : new RepositorioTalhoesMemoria ();
            RepositorioAtividades  atividades  = mongo ? new RepositorioAtividadesMongo (conexao)          : new RepositorioAtividadesMemoria ();
            RepositorioOcorrencias ocorrencias = mongo ? new RepositorioOcorrenciasMongo (conexao)         : new RepositorioOcorrenciasMemoria ();

            Sessoes  sessoes     = new Sessoes ();
            Roteador roteador    = TesteGeorreferenciamento.roteador (talhoes, atividades, ocorrencias, sessoes);
            String   propriedade = new ObjectId ().toHexString ();
            String   outra       = new ObjectId ().toHexString ();
            String   a           = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            String   b           = sessoes.criar (new ObjectId ().toHexString (), outra);

            String norte = TesteGeorreferenciamento.talhao (roteador, a, "Norte", 10);
            String sul   = TesteGeorreferenciamento.talhao (roteador, a, "Sul",   5);
            String leste = TesteGeorreferenciamento.talhao (roteador, a, "Leste", 8);
            String oeste = TesteGeorreferenciamento.talhao (roteador, b, "Oeste", 3);
            String rotaN = "/api/talhoes/" + norte + "/localizacao";
            String rotaS = "/api/talhoes/" + sul   + "/localizacao";

            String poligono = TesteGeorreferenciamento.localizacao ("poligono", -22.905, -47.061, -22.905, -47.058,
                                                                    -22.908, -47.058, -22.908, -47.061);

            // ---------------------------------------------------------------- autenticacao e validacao
            TesteGeorreferenciamento.conferir ("sem token -> 401 no mapa e na localizacao",
                                               TesteGeorreferenciamento.status (roteador, null, "GET", "/api/mapa", null) == 401 &&
                                               TesteGeorreferenciamento.status (roteador, null, "PUT", rotaN, poligono) == 401 &&
                                               TesteGeorreferenciamento.status (roteador, null, "DELETE", rotaN, null) == 401);

            Resposta vazio = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, "{}");
            TesteGeorreferenciamento.conferir ("corpo vazio -> 400 em tipo e pontos",
                                               vazio.getStatus () == 400 && TesteGeorreferenciamento.campo (vazio, "tipo") != null &&
                                               TesteGeorreferenciamento.campo (vazio, "pontos") != null);

            Resposta circulo = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, TesteGeorreferenciamento.localizacao ("circulo", -22.9, -47.0));
            TesteGeorreferenciamento.conferir ("tipo invalido -> 400 no campo tipo",
                                               circulo.getStatus () == 400 && TesteGeorreferenciamento.campo (circulo, "tipo") != null);

            Resposta pontosTexto = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, "{\"tipo\":\"ponto\",\"pontos\":\"-22.9,-47\"}");
            TesteGeorreferenciamento.conferir ("pontos em texto -> 400 no campo pontos",
                                               pontosTexto.getStatus () == 400 && TesteGeorreferenciamento.campo (pontosTexto, "pontos") != null);

            Resposta latTexto = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, "{\"tipo\":\"ponto\",\"pontos\":[{\"lat\":\"x\",\"lng\":-47}]}");
            TesteGeorreferenciamento.conferir ("lat que nao e numero -> 400 no campo pontos",
                                               latTexto.getStatus () == 400 && TesteGeorreferenciamento.campo (latTexto, "pontos").contains ("n\u00fameros"));

            Resposta lat95 = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, TesteGeorreferenciamento.localizacao ("ponto", 95, -47.0));
            TesteGeorreferenciamento.conferir ("latitude 95 -> 400 no campo pontos",
                                               lat95.getStatus () == 400 && TesteGeorreferenciamento.campo (lat95, "pontos").contains ("Latitude"));

            Resposta laco = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, TesteGeorreferenciamento.localizacao ("poligono",
                                                             -22.905, -47.061, -22.908, -47.058, -22.905, -47.058, -22.908, -47.061));
            TesteGeorreferenciamento.conferir ("poligono em laco -> 400 (lados se cruzam)",
                                               laco.getStatus () == 400 && TesteGeorreferenciamento.campo (laco, "pontos").contains ("cruzar"));

            TesteGeorreferenciamento.conferir ("talhao de outra propriedade -> 404 em PUT e DELETE",
                                               TesteGeorreferenciamento.status (roteador, b, "PUT", rotaN, poligono) == 404 &&
                                               TesteGeorreferenciamento.status (roteador, b, "DELETE", rotaN, null) == 404);
            TesteGeorreferenciamento.conferir ("talhao inexistente -> 404",
                                               TesteGeorreferenciamento.status (roteador, a, "PUT", "/api/talhoes/" + new ObjectId ().toHexString () + "/localizacao", poligono) == 404);

            String                  trocada    = sessoes.criar (new ObjectId ().toHexString (), propriedade);
            HashMap<String, String> cabecalhos = new HashMap<String, String> ();
            cabecalhos.put ("authorization", "Bearer " + trocada);
            HashMap<String, String> parametros = new HashMap<String, String> ();
            parametros.put ("id", norte);
            Requisicao requisicao = new Requisicao ("PUT", rotaN, new HashMap<String, String> (), cabecalhos,
                                                    poligono.getBytes (StandardCharsets.UTF_8), "127.0.0.1");
            requisicao.setSessao (sessoes.buscar (trocada));
            requisicao.setParametros (parametros);
            sessoes.selecionarPropriedade (trocada, outra);
            TesteGeorreferenciamento.conferir ("propriedade trocada no meio da escrita -> 409",
                                               new GeorreferenciamentoHandler (talhoes, ocorrencias, sessoes).tratar (requisicao).getStatus () == 409);

            // ---------------------------------------------------------------- gravar ponto e poligono
            Resposta gravado = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, poligono);
            Document norteJson = TesteGeorreferenciamento.json (gravado);
            TesteGeorreferenciamento.conferir ("poligono gravado -> 200 com 4 vertices e area ~10,2 ha",
                                               gravado.getStatus () == 200 &&
                                               norteJson.get ("localizacao", Document.class).getList ("pontos", Document.class).size () == 4 &&
                                               TesteGeorreferenciamento.perto (norteJson.get ("areaCalculadaHectares"), 10.2, 0.2));
            TesteGeorreferenciamento.conferir ("poligono: centro no meio e hectares digitados intactos",
                                               TesteGeorreferenciamento.perto (norteJson.get ("centro", Document.class).get ("lat"), -22.9065, 0.0001) &&
                                               TesteGeorreferenciamento.perto (norteJson.get ("centro", Document.class).get ("lng"), -47.0595, 0.0001) &&
                                               TesteGeorreferenciamento.perto (norteJson.get ("hectares"), 10, 0));

            Resposta ponto = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaS, TesteGeorreferenciamento.localizacao ("ponto", -22.91234567, -47.0512));
            Document sulJson = TesteGeorreferenciamento.json (ponto);
            TesteGeorreferenciamento.conferir ("ponto gravado arredondado a 6 casas, sem area calculada",
                                               ponto.getStatus () == 200 && sulJson.get ("areaCalculadaHectares") == null &&
                                               TesteGeorreferenciamento.perto (sulJson.get ("localizacao", Document.class).getList ("pontos", Document.class).get (0).get ("lat"), -22.912346, 0.0000001));

            List<Document> listaTalhoes = TesteGeorreferenciamento.lista (TesteGeorreferenciamento.chamar (roteador, a, "GET", "/api/talhoes", null));
            TesteGeorreferenciamento.conferir ("GET /api/talhoes traz a localizacao (e Leste sem)",
                                               "poligono".equals (TesteGeorreferenciamento.item (listaTalhoes, "id", norte).get ("localizacao", Document.class).getString ("tipo")) &&
                                               TesteGeorreferenciamento.item (listaTalhoes, "id", leste).get ("localizacao") == null);

            String edicao = new Document ("nome", "Norte").append ("cultura", "Milho").append ("hectares", 12).toJson ();
            TesteGeorreferenciamento.conferir ("PUT /api/talhoes/{id} (edicao normal) preserva a localizacao",
                                               TesteGeorreferenciamento.status (roteador, a, "PUT", "/api/talhoes/" + norte, edicao) == 200 &&
                                               "poligono".equals (talhoes.buscarPorId (propriedade, norte).get ("localizacao", Document.class).getString ("tipo")));

            // ---------------------------------------------------------------- ocorrencias com posicao
            Resposta semTalhao = TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Solta", null, TesteGeorreferenciamento.posicao (-22.906, -47.059)).toJson ());
            TesteGeorreferenciamento.conferir ("ocorrencia com localizacao e sem talhao -> 400 no campo localizacao",
                                               semTalhao.getStatus () == 400 && TesteGeorreferenciamento.campo (semTalhao, "localizacao") != null);

            Resposta noPonto = TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("No ponto", sul, TesteGeorreferenciamento.posicao (-22.9123, -47.0512)).toJson ());
            TesteGeorreferenciamento.conferir ("talhao sem poligono -> 400 pedindo o poligono",
                                               noPonto.getStatus () == 400 && TesteGeorreferenciamento.campo (noPonto, "localizacao").contains ("Cadastre o pol\u00edgono"));

            Resposta semLocal = TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Sem local", leste, TesteGeorreferenciamento.posicao (-22.9, -47.0)).toJson ());
            TesteGeorreferenciamento.conferir ("talhao sem localizacao -> 400 no campo localizacao",
                                               semLocal.getStatus () == 400 && TesteGeorreferenciamento.campo (semLocal, "localizacao") != null);

            Resposta fora = TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Fora", norte, TesteGeorreferenciamento.posicao (-22.9, -47.059)).toJson ());
            TesteGeorreferenciamento.conferir ("posicao fora do poligono -> 400 no campo localizacao",
                                               fora.getStatus () == 400 && TesteGeorreferenciamento.campo (fora, "localizacao").contains ("fora"));

            Resposta posTexto = TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Texto", norte, null).append ("localizacao", "perto da sede").toJson ());
            TesteGeorreferenciamento.conferir ("posicao que nao e {lat, lng} -> 400 no campo localizacao",
                                               posTexto.getStatus () == 400 && TesteGeorreferenciamento.campo (posTexto, "localizacao") != null);

            Resposta dentro = TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Lagarta no meio", norte, TesteGeorreferenciamento.posicao (-22.9061234567, -47.0591)).toJson ());
            Document marcada = TesteGeorreferenciamento.json (dentro);
            String   marcadaId = marcada.getString ("id");
            TesteGeorreferenciamento.conferir ("posicao dentro do poligono -> 201 com {lat, lng} arredondados",
                                               dentro.getStatus () == 201 &&
                                               TesteGeorreferenciamento.perto (marcada.get ("localizacao", Document.class).get ("lat"), -22.906123, 0.0000001));

            String noTalhao  = TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Ferrugem no Sul", sul, null).toJson ())).getString ("id");
            String resolvida = TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Resolvida", norte, null).toJson ())).getString ("id");
            String excluida  = TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias",
                TesteGeorreferenciamento.ocorrencia ("Excluida", norte, TesteGeorreferenciamento.posicao (-22.907, -47.060)).toJson ())).getString ("id");
            TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias", TesteGeorreferenciamento.ocorrencia ("Geral", null, null).toJson ());
            TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias", TesteGeorreferenciamento.ocorrencia ("No Leste", leste, null).toJson ());
            TesteGeorreferenciamento.chamar (roteador, a, "POST", "/api/ocorrencias/" + resolvida + "/timeline",
                                             new Document ("versao", 1).append ("acao", "resolucao").append ("descricao", "Controlada").toJson ());
            TesteGeorreferenciamento.conferir ("ocorrencia marcada pode ser excluida (exclusao logica)",
                                               TesteGeorreferenciamento.status (roteador, a, "DELETE", "/api/ocorrencias/" + excluida,
                                                                                new Document ("versao", 1).toJson ()) == 200);

            Document editar = TesteGeorreferenciamento.ocorrencia ("Lagarta no meio (editada)", norte, marcada.get ("localizacao", Document.class)).append ("versao", 1);
            Resposta editada = TesteGeorreferenciamento.chamar (roteador, a, "PUT", "/api/ocorrencias/" + marcadaId, editar.toJson ());
            TesteGeorreferenciamento.conferir ("edicao mantem a posicao enviada",
                                               editada.getStatus () == 200 &&
                                               TesteGeorreferenciamento.perto (TesteGeorreferenciamento.json (editada).get ("localizacao", Document.class).get ("lng"), -47.0591, 0.0000001));

            List<Document> timeline = TesteGeorreferenciamento.lista (TesteGeorreferenciamento.chamar (roteador, a, "GET", "/api/ocorrencias/" + marcadaId + "/timeline", null));
            boolean        todasComPosicao = timeline.size () == 2;
            for (Document evento : timeline)
                if (!(evento.get ("dados", Document.class).get ("localizacao") instanceof Document))
                    todasComPosicao = false;
            TesteGeorreferenciamento.conferir ("timeline guarda a posicao de cada etapa", todasComPosicao);

            // ---------------------------------------------------------------- mapa
            Resposta respostaMapa = TesteGeorreferenciamento.chamar (roteador, a, "GET", "/api/mapa", null);
            Document mapa         = TesteGeorreferenciamento.json (respostaMapa);
            List<Document> noMapa = mapa.getList ("talhoes", Document.class);
            Document norteMapa    = TesteGeorreferenciamento.item (noMapa, "id", norte);
            Document sulMapa      = TesteGeorreferenciamento.item (noMapa, "id", sul);
            TesteGeorreferenciamento.conferir ("mapa: 200 com Norte e Sul desenhados e Leste em semLocalizacao",
                                               respostaMapa.getStatus () == 200 && noMapa.size () == 2 && norteMapa != null && sulMapa != null &&
                                               mapa.getList ("semLocalizacao", Document.class).size () == 1 &&
                                               leste.equals (mapa.getList ("semLocalizacao", Document.class).get (0).getString ("id")));
            TesteGeorreferenciamento.conferir ("mapa: talhao sem localizacao mostra as ocorrencias abertas",
                                               mapa.getList ("semLocalizacao", Document.class).get (0).getInteger ("ocorrenciasAbertas") == 1);

            List<Document> marcasNorte = norteMapa.getList ("ocorrencias", Document.class);
            Document       propria     = TesteGeorreferenciamento.item (marcasNorte, "id", marcadaId);
            TesteGeorreferenciamento.conferir ("mapa: ocorrencia com posicao propria no Norte (resolvida e excluida fora)",
                                               marcasNorte.size () == 1 && propria != null && "propria".equals (propria.getString ("posicao")) &&
                                               TesteGeorreferenciamento.perto (propria.get ("lat"), -22.906123, 0.0000001));

            Document doSul = TesteGeorreferenciamento.item (sulMapa.getList ("ocorrencias", Document.class), "id", noTalhao);
            TesteGeorreferenciamento.conferir ("mapa: ocorrencia sem posicao fica no centro do talhao",
                                               doSul != null && "talhao".equals (doSul.getString ("posicao")) &&
                                               TesteGeorreferenciamento.perto (doSul.get ("lat"), -22.912346, 0.0000001) &&
                                               TesteGeorreferenciamento.perto (doSul.get ("lng"), -47.0512, 0.0000001));
            TesteGeorreferenciamento.conferir ("mapa: area calculada so no poligono e ocorrencia sem talhao contada",
                                               TesteGeorreferenciamento.perto (norteMapa.get ("areaCalculadaHectares"), 10.2, 0.2) &&
                                               sulMapa.get ("areaCalculadaHectares") == null && mapa.getInteger ("ocorrenciasSemTalhao") == 1);

            Document limites = mapa.get ("limites", Document.class);
            TesteGeorreferenciamento.conferir ("mapa: limites cobrem o poligono e o ponto",
                                               TesteGeorreferenciamento.perto (limites.get ("latMin"), -22.912346, 0.0000001) &&
                                               TesteGeorreferenciamento.perto (limites.get ("latMax"), -22.905, 0.0000001) &&
                                               TesteGeorreferenciamento.perto (limites.get ("lngMin"), -47.061, 0.0000001) &&
                                               TesteGeorreferenciamento.perto (limites.get ("lngMax"), -47.0512, 0.0000001));

            Document todas = TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, a, "GET", "/api/mapa?ocorrencias=todas", null));
            List<Document> marcasTodas = TesteGeorreferenciamento.item (todas.getList ("talhoes", Document.class), "id", norte).getList ("ocorrencias", Document.class);
            TesteGeorreferenciamento.conferir ("mapa ocorrencias=todas inclui a resolvida e nunca a excluida",
                                               marcasTodas.size () == 2 && TesteGeorreferenciamento.item (marcasTodas, "id", resolvida) != null &&
                                               TesteGeorreferenciamento.item (marcasTodas, "id", excluida) == null);

            Document soMilho = TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, a, "GET", "/api/mapa?cultura=Milho", null));
            TesteGeorreferenciamento.conferir ("mapa filtrado por cultura (Norte virou Milho)",
                                               soMilho.getList ("talhoes", Document.class).size () == 1 &&
                                               soMilho.getList ("semLocalizacao", Document.class).isEmpty ());
            TesteGeorreferenciamento.conferir ("mapa com ocorrencias invalido -> 400",
                                               TesteGeorreferenciamento.status (roteador, a, "GET", "/api/mapa?ocorrencias=algumas", null) == 400);

            // ---------------------------------------------------------------- trocar e remover com ocorrencia marcada
            String menor = TesteGeorreferenciamento.localizacao ("poligono", -22.907, -47.061, -22.907, -47.058, -22.908, -47.058, -22.908, -47.061);
            Resposta encolher = TesteGeorreferenciamento.chamar (roteador, a, "PUT", rotaN, menor);
            TesteGeorreferenciamento.conferir ("trocar o poligono deixando ocorrencia fora -> 409 citando a ocorrencia",
                                               encolher.getStatus () == 409 && marcadaId.equals (TesteGeorreferenciamento.json (encolher).getString ("ocorrenciaId")));
            TesteGeorreferenciamento.conferir ("trocar o poligono por ponto com ocorrencia marcada -> 409",
                                               TesteGeorreferenciamento.status (roteador, a, "PUT", rotaN, TesteGeorreferenciamento.localizacao ("ponto", -22.906, -47.059)) == 409);
            TesteGeorreferenciamento.conferir ("o poligono continua o mesmo depois dos 409",
                                               talhoes.buscarPorId (propriedade, norte).get ("localizacao", Document.class).getList ("pontos", Document.class).get (0).get ("lat") instanceof Number &&
                                               TesteGeorreferenciamento.perto (talhoes.buscarPorId (propriedade, norte).get ("localizacao", Document.class).getList ("pontos", Document.class).get (0).get ("lat"), -22.905, 0));
            String maior = TesteGeorreferenciamento.localizacao ("poligono", -22.904, -47.062, -22.904, -47.057, -22.909, -47.057, -22.909, -47.062);
            TesteGeorreferenciamento.conferir ("trocar por um poligono que ainda contem a ocorrencia -> 200",
                                               TesteGeorreferenciamento.status (roteador, a, "PUT", rotaN, maior) == 200);

            Resposta remover = TesteGeorreferenciamento.chamar (roteador, a, "DELETE", rotaN, null);
            TesteGeorreferenciamento.conferir ("DELETE da localizacao com ocorrencia marcada -> 409",
                                               remover.getStatus () == 409 && TesteGeorreferenciamento.json (remover).getString ("error").contains ("marcadas"));
            TesteGeorreferenciamento.conferir ("DELETE da localizacao sem ocorrencia marcada -> 204 e sai do mapa",
                                               TesteGeorreferenciamento.status (roteador, a, "DELETE", rotaS, null) == 204 &&
                                               talhoes.buscarPorId (propriedade, sul).get ("localizacao") == null &&
                                               TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, a, "GET", "/api/mapa", null))
                                                   .getList ("semLocalizacao", Document.class).size () == 2);

            // ---------------------------------------------------------------- isolamento
            Document mapaB = TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (roteador, b, "GET", "/api/mapa", null));
            TesteGeorreferenciamento.conferir ("isolamento: B nao ve o mapa de A (so o proprio talhao, sem limites)",
                                               mapaB.getList ("talhoes", Document.class).isEmpty () && mapaB.get ("limites") == null &&
                                               mapaB.getList ("semLocalizacao", Document.class).size () == 1 &&
                                               oeste.equals (mapaB.getList ("semLocalizacao", Document.class).get (0).getString ("id")) &&
                                               mapaB.getInteger ("ocorrenciasSemTalhao") == 0);

            // ---------------------------------------------------------------- Mongo: leitura depois de reiniciar
            if (mongo)
            {
                Roteador depois = TesteGeorreferenciamento.roteador (new RepositorioTalhoesMongo (conexao.getBanco ()),
                                                                     new RepositorioAtividadesMongo (conexao),
                                                                     new RepositorioOcorrenciasMongo (conexao), sessoes);
                Document lido   = TesteGeorreferenciamento.json (TesteGeorreferenciamento.chamar (depois, a, "GET", "/api/mapa", null));
                Document norteLido = TesteGeorreferenciamento.item (lido.getList ("talhoes", Document.class), "id", norte);
                TesteGeorreferenciamento.conferir ("Mongo: repositorios novos (reinicio) leem o mesmo poligono e a mesma marca",
                                                   norteLido != null && norteLido.get ("localizacao", Document.class).getList ("pontos", Document.class).size () == 4 &&
                                                   TesteGeorreferenciamento.item (norteLido.getList ("ocorrencias", Document.class), "id", marcadaId) != null &&
                                                   lido.getList ("semLocalizacao", Document.class).size () == 2);
            }
        }
        catch (Exception erro)
        {
            TesteGeorreferenciamento.falhas++;
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

        System.out.println ("TesteGeorreferenciamento: " + (TesteGeorreferenciamento.total - TesteGeorreferenciamento.falhas) + "/" +
                            TesteGeorreferenciamento.total + " verifica\u00e7\u00f5es OK" + (mongo ? " (MongoDB)" : " (mem\u00f3ria)"));

        if (TesteGeorreferenciamento.falhas > 0)
            System.exit (1);
    }
}
