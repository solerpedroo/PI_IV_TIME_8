/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package handlers;

import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import http.Manipulador;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessao;
import seguranca.Sessoes;
import util.Json;
import util.Validacao;

/*
    Safras (ciclos produtivos) da propriedade de quem esta logado.

    GET    /api/safras?etapa=&cultura=&q=  lista
    POST   /api/safras                     cria (sempre comeca em Planejamento)
    GET    /api/safras/{id}                consulta, com a area somada dos talhoes
    PUT    /api/safras/{id}                altera nome, cultura, talhoes e periodo
    DELETE /api/safras/{id}                exclui, so em Planejamento
    POST   /api/safras/{id}/avancar        passa para a proxima etapa do ciclo
    GET    /api/safras/{id}/historico      eventos dos talhoes da safra dentro do periodo

    Ciclo: Planejamento -> Preparacao -> Plantio -> Manejo -> Colheita -> Finalizacao.
    Avanca uma etapa por vez e nao volta. Safra finalizada fica so para consulta.
    Um talhao nao pode estar em duas safras com periodos que se cruzam.
*/
public class SafrasHandler implements Manipulador
{
    public static final List<String> ETAPAS = List.of ("Planejamento", "Prepara\u00e7\u00e3o", "Plantio",
                                                       "Manejo", "Colheita", "Finaliza\u00e7\u00e3o");

    private static final String PLANEJAMENTO = "Planejamento";
    private static final String FINALIZACAO  = "Finaliza\u00e7\u00e3o";
    private static final int    MAXIMO_TALHOES = 200;

    // datas dos eventos sao comparadas no fuso da operacao, nao em UTC
    private static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");

    private final RepositorioSafras  safras;
    private final RepositorioTalhoes talhoes;
    private final Sessoes            sessoes;

    public SafrasHandler (RepositorioSafras safras, RepositorioTalhoes talhoes, Sessoes sessoes) throws Exception
    {
        if (safras == null)
            throw new Exception ("Repositorio de safras ausente");

        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        this.safras  = safras;
        this.talhoes = talhoes;
        this.sessoes = sessoes;
    }

    // todas as rotas caem em tratar, que escolhe a acao pelo metodo e pelo caminho
    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/safras",                this);
        roteador.adicionar ("POST",   "/api/safras",                this);
        roteador.adicionar ("GET",    "/api/safras/{id}",           this);
        roteador.adicionar ("PUT",    "/api/safras/{id}",           this);
        roteador.adicionar ("DELETE", "/api/safras/{id}",           this);
        roteador.adicionar ("POST",   "/api/safras/{id}/avancar",   this);
        roteador.adicionar ("GET",    "/api/safras/{id}/historico", this);
    }

    @Override
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   metodo = requisicao.getMetodo ().equals ("HEAD") ? "GET" : requisicao.getMetodo ();
        int      partes = requisicao.getCaminho ().substring (1).split ("/", -1).length; // api/safras/{id}/acao

        if (partes == 2)
        {
            if (metodo.equals ("GET"))
                ret = this.listar (requisicao);
            else
                ret = this.criar (requisicao);
        }
        else if (partes == 4)
        {
            if (metodo.equals ("GET"))
                ret = this.historico (requisicao);
            else
                ret = this.avancar (requisicao);
        }
        else if (metodo.equals ("GET"))
            ret = this.consultar (requisicao);
        else if (metodo.equals ("PUT"))
            ret = this.atualizar (requisicao);
        else
            ret = this.excluir (requisicao);

        return ret;
    }

    // ------------------------------------------------------------------ auxiliares

    private static String id (Document documento)
    {
        Object valor = documento.get ("_id");

        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    private static double hectares (Document talhao)
    {
        Object valor = talhao.get ("hectares");

        return valor instanceof Number ? ((Number) valor).doubleValue () : 0;
    }

    // talhoes da propriedade por id, para validar vinculos e somar a area
    private LinkedHashMap<String, Document> mapaDeTalhoes (String propriedadeId) throws Exception
    {
        LinkedHashMap<String, Document> ret = new LinkedHashMap<String, Document> ();

        for (Document talhao : this.talhoes.listar (propriedadeId))
            ret.put (SafrasHandler.id (talhao), talhao);

        return ret;
    }

    // area da safra = soma dos hectares atuais dos talhoes associados
    private static double area (Document safra, LinkedHashMap<String, Document> talhoes)
    {
        double ret = 0;

        for (String talhaoId : safra.getList ("talhoes", String.class))
        {
            Document talhao = talhoes.get (talhaoId);
            if (talhao != null)
                ret += SafrasHandler.hectares (talhao);
        }

        return ret;
    }

    private static Document publico (Document safra, LinkedHashMap<String, Document> talhoes)
    {
        return Json.publico (safra, "propriedadeId", "nomeNormalizado")
                   .append ("areaHectares", SafrasHandler.area (safra, talhoes));
    }

    // os dois periodos [inicio, fim] se cruzam? (datas AAAA-MM-DD comparam como texto)
    private static boolean cruzam (Document a, Document b)
    {
        return a.getString ("dataInicio").compareTo (b.getString ("dataFim")) <= 0 &&
               b.getString ("dataInicio").compareTo (a.getString ("dataFim")) <= 0;
    }

    // valida o corpo de POST e PUT; devolve os campos prontos para gravar ou null (erros na validacao)
    private static Document lerSafra (Document corpo, Validacao validacao, LinkedHashMap<String, Document> talhoes)
    {
        String nome    = validacao.texto ("nome",       "Informe o nome da safra.",          80);
        String cultura = validacao.texto ("cultura",    "Selecione uma cultura.",            40);
        String inicio  = validacao.data  ("dataInicio", "Informe a data de in\u00edcio.");
        String fim     = validacao.data  ("dataFim",    "Informe a data de t\u00e9rmino.");

        if (cultura != null && !TalhoesHandler.CULTURAS.contains (cultura))
            validacao.erro ("cultura", "Selecione uma cultura v\u00e1lida.");

        if (inicio != null && fim != null && fim.compareTo (inicio) < 0)
            validacao.erro ("dataFim", "A data de t\u00e9rmino deve ser igual ou posterior \u00e0 de in\u00edcio.");

        ArrayList<String> escolhidos = new ArrayList<String> ();
        Object            valor      = corpo.get ("talhoes");

        if (!(valor instanceof List) || ((List<?>) valor).isEmpty ())
            validacao.erro ("talhoes", "Selecione ao menos um talh\u00e3o.");
        else if (((List<?>) valor).size () > SafrasHandler.MAXIMO_TALHOES)
            validacao.erro ("talhoes", "Selecione no m\u00e1ximo " + SafrasHandler.MAXIMO_TALHOES + " talh\u00f5es.");
        else
        {
            for (Object item : (List<?>) valor)
            {
                if (!(item instanceof String) || !talhoes.containsKey (item))
                    validacao.erro ("talhoes", "Selecione apenas talh\u00f5es da propriedade.");
                else if (escolhidos.contains (item))
                    validacao.erro ("talhoes", "Talh\u00e3o repetido na lista.");
                else
                    escolhidos.add ((String) item);
            }
        }

        if (validacao.temErros ())
            return null;

        return new Document ("nome",            nome)
                   .append  ("nomeNormalizado", nome.toLowerCase (Locale.ROOT))
                   .append  ("cultura",         cultura)
                   .append  ("talhoes",         escolhidos)
                   .append  ("dataInicio",      inicio)
                   .append  ("dataFim",         fim)
                   .append  ("atualizadoEm",    Instant.now ().toString ());
    }

    // 409 se algum talhao ja estiver em outra safra com periodo que se cruza; null se nao houver conflito
    private Resposta conflitoDeTalhao (String propriedadeId, String idIgnorado, Document campos,
                                       LinkedHashMap<String, Document> talhoes) throws Exception
    {
        Resposta ret = null;

        for (Document outra : this.safras.listar (propriedadeId))
        {
            if (ret != null || SafrasHandler.id (outra).equals (idIgnorado) || !SafrasHandler.cruzam (campos, outra))
                continue;

            for (String talhaoId : campos.getList ("talhoes", String.class))
            {
                if (ret == null && outra.getList ("talhoes", String.class).contains (talhaoId))
                {
                    String mensagem = "O talh\u00e3o " + talhoes.get (talhaoId).getString ("nome") +
                                      " j\u00e1 est\u00e1 na safra " + outra.getString ("nome") +
                                      " nesse per\u00edodo.";
                    ret = Resposta.json (409, new Document ("error", "Plot already in another crop season")
                                                  .append ("fields", new Document ("talhoes", mensagem)));
                }
            }
        }

        return ret;
    }

    private static Resposta nomeRepetido ()
    {
        return Resposta.json (409, new Document ("error", "Crop season name already exists")
                                       .append ("fields", new Document ("nome", "J\u00e1 existe uma safra com esse nome.")));
    }

    private static Resposta finalizada ()
    {
        return Resposta.json (409, new Document ("error", "Safra finalizada n\u00e3o pode ser alterada."));
    }

    // a escrita usa a propriedade da sessao; se ela mudou no meio (outra aba selecionou outra), recusa
    private Resposta contextoMudou (Requisicao requisicao)
    {
        Resposta ret   = null;
        Sessao   atual = this.sessoes.buscar (requisicao.getToken ());

        if (atual == null)
            ret = Resposta.erro (401, "Unauthorized");
        else if (!atual.getPropriedadeId ().equals (requisicao.getSessao ().getPropriedadeId ()))
            ret = Resposta.erro (409, "Property context changed; reload before saving");

        return ret;
    }

    private static Document etapa (String nome)
    {
        return new Document ("etapa", nome).append ("em", Instant.now ().toString ());
    }

    // ------------------------------------------------------------------ rotas

    // GET /api/safras?etapa=Plantio&cultura=Soja&q=verao
    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String etapa         = requisicao.getConsulta ("etapa");
        String cultura       = requisicao.getConsulta ("cultura");
        String busca         = requisicao.getConsulta ("q");

        if (etapa != null && (etapa.isBlank () || etapa.equalsIgnoreCase ("todas")))
            etapa = null;

        if (cultura != null && (cultura.isBlank () || cultura.equalsIgnoreCase ("todos")))
            cultura = null;

        if (busca != null)
            busca = busca.trim ().toLowerCase (Locale.ROOT);

        LinkedHashMap<String, Document> talhoes = this.mapaDeTalhoes (propriedadeId);
        ArrayList<Document>             ret     = new ArrayList<Document> ();

        for (Document safra : this.safras.listar (propriedadeId))
        {
            if (etapa != null && !etapa.trim ().equalsIgnoreCase (safra.getString ("etapa")))
                continue;

            if (cultura != null && !cultura.trim ().equalsIgnoreCase (safra.getString ("cultura")))
                continue;

            if (busca != null && !safra.getString ("nomeNormalizado").contains (busca))
                continue;

            ret.add (SafrasHandler.publico (safra, talhoes));
        }

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/safras/{id}
    private Resposta consultar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        Document safra         = this.safras.buscarPorId (propriedadeId, requisicao.getParametro ("id"));

        if (safra == null)
            ret = Resposta.erro (404, "Not found");
        else
            ret = Resposta.json (200, SafrasHandler.publico (safra, this.mapaDeTalhoes (propriedadeId)));

        return ret;
    }

    // POST /api/safras
    private Resposta criar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String                          propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            Document                        corpo         = requisicao.getCorpoJson ();
            Validacao                       validacao     = new Validacao (corpo);
            LinkedHashMap<String, Document> talhoes       = this.mapaDeTalhoes (propriedadeId);
            Document                        safra         = SafrasHandler.lerSafra (corpo, validacao, talhoes);

            if (safra == null)
                return validacao.resposta ();

            ret = this.conflitoDeTalhao (propriedadeId, null, safra, talhoes);
            if (ret != null)
                return ret;

            ArrayList<Document> historico = new ArrayList<Document> ();
            historico.add (SafrasHandler.etapa (SafrasHandler.PLANEJAMENTO));

            safra.append ("etapa",           SafrasHandler.PLANEJAMENTO)
                 .append ("historicoEtapas", historico)
                 .append ("propriedadeId",   propriedadeId)
                 .append ("criadoEm",        safra.getString ("atualizadoEm"));

            if (!this.safras.criar (safra))
                return SafrasHandler.nomeRepetido ();

            return Resposta.json (201, SafrasHandler.publico (safra, talhoes));
        }
    }

    // PUT /api/safras/{id}  (troca nome, cultura, talhoes e periodo; a etapa so muda por /avancar)
    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document atual         = this.safras.buscarPorId (propriedadeId, id);

            if (atual == null)
                return Resposta.erro (404, "Not found");

            if (atual.getString ("etapa").equals (SafrasHandler.FINALIZACAO))
                return SafrasHandler.finalizada ();

            Document                        corpo     = requisicao.getCorpoJson ();
            Validacao                       validacao = new Validacao (corpo);
            LinkedHashMap<String, Document> talhoes   = this.mapaDeTalhoes (propriedadeId);
            Document                        campos    = SafrasHandler.lerSafra (corpo, validacao, talhoes);

            if (campos == null)
                return validacao.resposta ();

            ret = this.conflitoDeTalhao (propriedadeId, id, campos, talhoes);
            if (ret != null)
                return ret;

            if (!this.safras.atualizar (propriedadeId, id, campos))
                return SafrasHandler.nomeRepetido ();

            return Resposta.json (200, SafrasHandler.publico (this.safras.buscarPorId (propriedadeId, id), talhoes));
        }
    }

    // DELETE /api/safras/{id}  (so em Planejamento: depois disso a safra ja tem historico)
    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document safra         = this.safras.buscarPorId (propriedadeId, id);

            if (safra == null)
                ret = Resposta.erro (404, "Not found");
            else if (!safra.getString ("etapa").equals (SafrasHandler.PLANEJAMENTO))
                ret = Resposta.json (409, new Document ("error",
                          "S\u00f3 \u00e9 poss\u00edvel excluir uma safra em planejamento."));
            else if (!this.safras.excluir (propriedadeId, id))
                ret = Resposta.erro (404, "Not found");
            else
                ret = Resposta.semConteudo ();

            return ret;
        }
    }

    // POST /api/safras/{id}/avancar  (uma etapa por vez; Finalizacao e o fim do ciclo)
    private Resposta avancar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document safra         = this.safras.buscarPorId (propriedadeId, id);

            if (safra == null)
                return Resposta.erro (404, "Not found");

            int posicao = SafrasHandler.ETAPAS.indexOf (safra.getString ("etapa"));
            if (posicao == SafrasHandler.ETAPAS.size () - 1)
                return Resposta.json (409, new Document ("error", "A safra j\u00e1 est\u00e1 finalizada."));

            String              proxima   = SafrasHandler.ETAPAS.get (posicao + 1);
            ArrayList<Document> historico = new ArrayList<Document> (safra.getList ("historicoEtapas", Document.class));
            historico.add (SafrasHandler.etapa (proxima));

            Document campos = new Document ("etapa",           proxima)
                                  .append  ("historicoEtapas", historico)
                                  .append  ("atualizadoEm",    Instant.now ().toString ());

            if (proxima.equals (SafrasHandler.FINALIZACAO))
                campos.append ("finalizadaEm", campos.getString ("atualizadoEm"));

            this.safras.atualizar (propriedadeId, id, campos);

            return Resposta.json (200, SafrasHandler.publico (this.safras.buscarPorId (propriedadeId, id),
                                                              this.mapaDeTalhoes (propriedadeId)));
        }
    }

    // data local (AAAA-MM-DD) de um instante gravado em criadoEm; null se nao der para ler
    private static String dataLocal (Object instante)
    {
        String ret = null;

        try
        {
            ret = Instant.parse ((String) instante).atZone (SafrasHandler.FUSO).toLocalDate ().toString ();
        }
        catch (Exception erro)
        {} // evento sem data valida fica fora do periodo

        return ret;
    }

    // GET /api/safras/{id}/historico  (atividades, ocorrencias e custos dos talhoes, dentro do periodo)
    private Resposta historico (Requisicao requisicao) throws Exception
    {
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        Document safra         = this.safras.buscarPorId (propriedadeId, requisicao.getParametro ("id"));

        if (safra == null)
            return Resposta.erro (404, "Not found");

        LinkedHashMap<String, Document> talhoes = this.mapaDeTalhoes (propriedadeId);
        ArrayList<Document>             ret     = new ArrayList<Document> ();

        for (String talhaoId : safra.getList ("talhoes", String.class))
        {
            Document talhao = talhoes.get (talhaoId);
            if (talhao == null)
                continue;

            for (Document evento : this.talhoes.listarHistorico (propriedadeId, talhaoId))
            {
                String dia = SafrasHandler.dataLocal (evento.get ("criadoEm"));

                if (dia == null || dia.compareTo (safra.getString ("dataInicio")) < 0 ||
                                   dia.compareTo (safra.getString ("dataFim"))    > 0)
                    continue;

                Document item = Json.publico (evento, "propriedadeId", "talhaoId")
                                    .append ("talhaoId",   talhaoId)
                                    .append ("talhaoNome", talhao.getString ("nome"));

                // insercao ordenada: mais recente primeiro
                int posicao = 0;
                while (posicao < ret.size () &&
                       ret.get (posicao).getString ("criadoEm").compareTo (item.getString ("criadoEm")) >= 0)
                    posicao++;
                ret.add (posicao, item);
            }
        }

        return Resposta.json (200, Json.escrever (ret));
    }
}
