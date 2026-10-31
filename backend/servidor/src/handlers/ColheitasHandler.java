/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package handlers;

import dados.RepositorioColheitas;
import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import http.Manipulador;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessao;
import seguranca.Sessoes;
import util.Json;
import util.Validacao;

/*
    Colheita por safra e talhao e produtividade por hectare (dominio gerencial, sem previsao).

    GET    /api/colheitas?safraId=&talhaoId=&cultura=&de=&ate=   lista, da data mais recente para a mais antiga
    GET    /api/colheitas/resumo?(mesmos filtros)                producao e produtividade do recorte
    GET    /api/colheitas/comparativo?cultura=                   um item por ciclo (safra), em ordem de inicio
    POST   /api/colheitas                                        registra
    GET    /api/colheitas/{id}                                   consulta
    PUT    /api/colheitas/{id}                                   altera
    DELETE /api/colheitas/{id}                                   exclui

    Unidades: kg, t (1.000 kg) e sc (saca de 60 kg). A quantidade fica como foi informada e
    tambem convertida em quantidadeKg; as somas sao feitas em gramas (long).
    Produtividade = kg colhidos / area real do recorte. A area de cada ciclo e a soma dos
    hectares dos talhoes da safra ou, com filtro de talhao, os hectares desse talhao.
    Sem area ou sem colheita, a produtividade fica null (nao inventa area nem producao).
    Colheita so e registrada a partir do Plantio; safra finalizada fica so para consulta (409).
    A data nao pode ser anterior ao inicio da safra nem estar no futuro.
*/
public class ColheitasHandler implements Manipulador
{
    public static final List<String> UNIDADES    = List.of ("kg", "t", "sc");
    public static final double       KG_POR_SACA = 60;

    private static final double QUANTIDADE_MAXIMA = 1000000000;
    private static final int    PLANTIO           = 2; // posicao em SafrasHandler.ETAPAS
    private static final String FINALIZACAO       = "Finaliza\u00e7\u00e3o";
    private static final ZoneId FUSO              = ZoneId.of ("America/Sao_Paulo");

    private final RepositorioColheitas colheitas;
    private final RepositorioSafras    safras;
    private final RepositorioTalhoes   talhoes;
    private final Sessoes              sessoes;

    public ColheitasHandler (RepositorioColheitas colheitas, RepositorioSafras safras, RepositorioTalhoes talhoes,
                             Sessoes sessoes) throws Exception
    {
        if (colheitas == null)
            throw new Exception ("Repositorio de colheitas ausente");

        if (safras == null)
            throw new Exception ("Repositorio de safras ausente");

        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        this.colheitas = colheitas;
        this.safras    = safras;
        this.talhoes   = talhoes;
        this.sessoes   = sessoes;
    }

    // todas as rotas caem em tratar, que escolhe a acao pelo metodo e pelo caminho
    public void registrar (Roteador roteador) throws Exception
    {
        // /resumo e /comparativo antes de /{id} para nao serem confundidos com id
        roteador.adicionar ("GET",    "/api/colheitas/resumo",      this);
        roteador.adicionar ("GET",    "/api/colheitas/comparativo", this);
        roteador.adicionar ("GET",    "/api/colheitas",             this);
        roteador.adicionar ("POST",   "/api/colheitas",             this);
        roteador.adicionar ("GET",    "/api/colheitas/{id}",        this);
        roteador.adicionar ("PUT",    "/api/colheitas/{id}",        this);
        roteador.adicionar ("DELETE", "/api/colheitas/{id}",        this);
    }

    @Override
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   metodo  = requisicao.getMetodo ().equals ("HEAD") ? "GET" : requisicao.getMetodo ();
        String   caminho = requisicao.getCaminho ();
        int      partes  = caminho.substring (1).split ("/", -1).length; // api/colheitas/{id}

        if (partes == 2)
        {
            if (metodo.equals ("GET"))
                ret = this.consultarLista (requisicao, false);
            else
                ret = this.criar (requisicao);
        }
        else if (requisicao.getParametro ("id") == null)
        {
            if (caminho.endsWith ("/comparativo"))
                ret = this.comparativo (requisicao);
            else
                ret = this.consultarLista (requisicao, true);
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

    private static LinkedHashMap<String, Document> porId (List<Document> documentos)
    {
        LinkedHashMap<String, Document> ret = new LinkedHashMap<String, Document> ();

        for (Document documento : documentos)
            ret.put (ColheitasHandler.id (documento), documento);

        return ret;
    }

    // kg de uma unidade
    public static double fator (String unidade)
    {
        double ret = 1;

        if ("t".equals (unidade))
            ret = 1000;
        else if ("sc".equals (unidade))
            ret = ColheitasHandler.KG_POR_SACA;

        return ret;
    }

    // massa da colheita em gramas, para somar sem erro de ponto flutuante
    private static long gramas (Document colheita)
    {
        Object valor = colheita.get ("quantidadeKg");

        return valor instanceof Number ? Math.round (((Number) valor).doubleValue () * 1000) : 0;
    }

    private static double kg (long gramas)
    {
        return Math.round (gramas / 10.0) / 100.0; // duas casas
    }

    private static double sacas (long gramas)
    {
        return Math.round (gramas / ColheitasHandler.KG_POR_SACA / 10.0) / 100.0;
    }

    // kg/ha com uma casa; null sem area ou sem colheita (nao inventa produtividade)
    private static Double kgPorHectare (long gramas, double area)
    {
        Double ret = null;

        if (area > 0 && gramas > 0)
            ret = Double.valueOf (Math.round (gramas / area / 100.0) / 10.0);

        return ret;
    }

    private static Double scPorHectare (long gramas, double area)
    {
        Double ret = null;

        if (area > 0 && gramas > 0)
            ret = Double.valueOf (Math.round (gramas / area / ColheitasHandler.KG_POR_SACA / 100.0) / 10.0);

        return ret;
    }

    private static boolean finalizada (Document safra)
    {
        return safra != null && ColheitasHandler.FINALIZACAO.equals (safra.getString ("etapa"));
    }

    private static Document publico (Document colheita, Map<String, Document> talhoes, Map<String, Document> safras)
    {
        Document talhao = talhoes.get (colheita.getString ("talhaoId"));
        Document safra  = safras.get  (colheita.getString ("safraId"));

        return Json.publico (colheita, "propriedadeId")
                   .append ("talhaoNome", talhao == null ? null : talhao.getString ("nome"))
                   .append ("safraNome",  safra  == null ? null : safra.getString ("nome"))
                   .append ("cultura",    safra  == null ? null : safra.getString ("cultura"))
                   .append ("finalizada", ColheitasHandler.finalizada (safra));
    }

    // texto opcional do corpo: null se ausente ou vazio; "" se nao for texto (o chamador trata como invalido)
    private static String opcional (Document corpo, String campo)
    {
        String ret   = null;
        Object valor = corpo.get (campo);

        if (valor instanceof String)
            ret = ((String) valor).trim ().isEmpty () ? null : ((String) valor).trim ();
        else if (valor != null)
            ret = "";

        return ret;
    }

    // valida o corpo de POST e PUT; devolve os campos prontos para gravar ou null (erros na validacao)
    private static Document lerColheita (Document corpo, Validacao validacao,
                                         Map<String, Document> talhoes, Map<String, Document> safras)
    {
        String safraId    = validacao.texto ("safraId",  "Selecione a safra.",   40);
        String talhaoId   = validacao.texto ("talhaoId", "Selecione o talh\u00e3o.", 40);
        String unidade    = validacao.texto ("unidade",  "Selecione a unidade.", 10);
        String data       = validacao.data  ("data",     "Informe a data da colheita.");
        String observacao = ColheitasHandler.opcional (corpo, "observacao");
        Object quantidade = corpo.get ("quantidade");
        Document safra    = safraId == null ? null : safras.get (safraId);

        if (safraId != null && safra == null)
            validacao.erro ("safraId", "Selecione uma safra da propriedade.");

        if (talhaoId != null && !talhoes.containsKey (talhaoId))
            validacao.erro ("talhaoId", "Selecione um talh\u00e3o da propriedade.");
        else if (talhaoId != null && safra != null && !safra.getList ("talhoes", String.class).contains (talhaoId))
            validacao.erro ("talhaoId", "O talh\u00e3o n\u00e3o faz parte da safra escolhida.");

        if (unidade != null && !ColheitasHandler.UNIDADES.contains (unidade))
            validacao.erro ("unidade", "Use kg, t ou sc (saca de 60 kg).");

        if (!(quantidade instanceof Number) || !(((Number) quantidade).doubleValue () > 0)) // tambem barra NaN
            validacao.erro ("quantidade", "Informe uma quantidade maior que zero.");
        else if (((Number) quantidade).doubleValue () > ColheitasHandler.QUANTIDADE_MAXIMA)
            validacao.erro ("quantidade", "Quantidade acima do permitido.");

        if (data != null && data.compareTo (LocalDate.now (ColheitasHandler.FUSO).toString ()) > 0)
            validacao.erro ("data", "A data da colheita n\u00e3o pode estar no futuro.");
        else if (data != null && safra != null && data.compareTo (safra.getString ("dataInicio")) < 0)
            validacao.erro ("data", "A colheita n\u00e3o pode ser anterior ao in\u00edcio da safra.");

        if ("".equals (observacao))
            validacao.erro ("observacao", "Valor inv\u00e1lido.");
        else if (observacao != null && observacao.length () > 200)
            validacao.erro ("observacao", "Use no m\u00e1ximo 200 caracteres.");

        if (validacao.temErros ())
            return null;

        double valor = Math.round (((Number) quantidade).doubleValue () * 1000) / 1000.0; // tres casas

        return new Document ("safraId",      safraId)
                   .append  ("talhaoId",     talhaoId)
                   .append  ("quantidade",   valor)
                   .append  ("unidade",      unidade)
                   .append  ("quantidadeKg", Math.round (valor * ColheitasHandler.fator (unidade) * 1000) / 1000.0)
                   .append  ("data",         data)
                   .append  ("observacao",   observacao)
                   .append  ("atualizadoEm", Instant.now ().toString ());
    }

    // 409 se a safra ainda nao chegou ao plantio ou ja foi finalizada; null se pode registrar
    private static Resposta etapaFechada (Document safra)
    {
        Resposta ret     = null;
        int      posicao = SafrasHandler.ETAPAS.indexOf (safra.getString ("etapa"));

        if (ColheitasHandler.finalizada (safra))
            ret = ColheitasHandler.safraFechada ();
        else if (posicao < ColheitasHandler.PLANTIO)
            ret = Resposta.json (409, new Document ("error", "A colheita s\u00f3 pode ser registrada a partir do plantio.")
                                          .append ("fields", new Document ("safraId",
                                                   "A safra ainda est\u00e1 em " + safra.getString ("etapa") + ".")));

        return ret;
    }

    private static Resposta safraFechada ()
    {
        return Resposta.json (409, new Document ("error", "Colheitas de safra finalizada n\u00e3o podem ser alteradas."));
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

    // filtro de texto da consulta: null quando ausente, vazio ou "todos"
    private static String filtro (Requisicao requisicao, String nome)
    {
        String ret = requisicao.getConsulta (nome);

        if (ret != null)
        {
            ret = ret.trim ();
            if (ret.isEmpty () || ret.equalsIgnoreCase ("todos") || ret.equalsIgnoreCase ("todas"))
                ret = null;
        }

        return ret;
    }

    // a colheita passa pelos filtros da consulta?
    private static boolean passa (Document colheita, Map<String, Document> safras, String safraId, String talhaoId,
                                  String cultura, String de, String ate)
    {
        boolean  ret   = true;
        Document safra = safras.get (colheita.getString ("safraId"));

        if (safraId != null && !safraId.equals (colheita.getString ("safraId")))
            ret = false;

        if (talhaoId != null && !talhaoId.equals (colheita.getString ("talhaoId")))
            ret = false;

        if (cultura != null && (safra == null || !cultura.equalsIgnoreCase (safra.getString ("cultura"))))
            ret = false;

        if (de != null && colheita.getString ("data").compareTo (de) < 0)
            ret = false;

        if (ate != null && colheita.getString ("data").compareTo (ate) > 0)
            ret = false;

        return ret;
    }

    // insercao ordenada: data mais recente primeiro; na mesma data, a cadastrada por ultimo primeiro
    private static void inserirOrdenado (ArrayList<Document> lista, Document colheita)
    {
        String chave   = colheita.getString ("data") + ColheitasHandler.id (colheita);
        int    posicao = 0;

        while (posicao < lista.size () &&
               (lista.get (posicao).getString ("data") + ColheitasHandler.id (lista.get (posicao))).compareTo (chave) > 0)
            posicao++;

        lista.add (posicao, colheita);
    }

    private static void somar (LinkedHashMap<String, long[]> mapa, String chave, long gramas)
    {
        long[] soma = mapa.get (chave);

        if (soma == null)
        {
            soma = new long[2];
            mapa.put (chave, soma);
        }

        soma[0] += gramas;
        soma[1]++;
    }

    // producao e produtividade das colheitas ja filtradas
    private static Document resumo (List<Document> lista, Map<String, Document> talhoes, Map<String, Document> safras,
                                    String talhaoId)
    {
        long                          total     = 0;
        double                        area      = 0;
        LinkedHashMap<String, long[]> porSafra  = new LinkedHashMap<String, long[]> ();
        LinkedHashMap<String, long[]> porTalhao = new LinkedHashMap<String, long[]> ();
        LinkedHashMap<String, String> ciclos    = new LinkedHashMap<String, String> (); // talhao+safra distintos

        for (Document colheita : lista)
        {
            long gramas = ColheitasHandler.gramas (colheita);

            total += gramas;
            ColheitasHandler.somar (porSafra,  colheita.getString ("safraId"),  gramas);
            ColheitasHandler.somar (porTalhao, colheita.getString ("talhaoId"), gramas);
            ciclos.put (colheita.getString ("talhaoId") + "/" + colheita.getString ("safraId"), colheita.getString ("talhaoId"));
        }

        // area de cada ciclo: a safra inteira ou, com filtro de talhao, so esse talhao
        ArrayList<Document> listaSafras = new ArrayList<Document> ();
        for (Map.Entry<String, long[]> entrada : porSafra.entrySet ())
        {
            Document safra       = safras.get (entrada.getKey ());
            double   areaDoCiclo = 0;

            if (talhaoId != null && talhoes.containsKey (talhaoId))
                areaDoCiclo = SafrasHandler.hectares (talhoes.get (talhaoId));
            else if (safra != null)
                areaDoCiclo = SafrasHandler.area (safra, talhoes);

            area += areaDoCiclo;
            listaSafras.add (new Document ("safraId",           entrada.getKey ())
                                 .append  ("nome",              safra == null ? null : safra.getString ("nome"))
                                 .append  ("cultura",           safra == null ? null : safra.getString ("cultura"))
                                 .append  ("registros",         entrada.getValue ()[1])
                                 .append  ("totalKg",           ColheitasHandler.kg (entrada.getValue ()[0]))
                                 .append  ("areaHectares",      areaDoCiclo)
                                 .append  ("produtividadeKgHa", ColheitasHandler.kgPorHectare (entrada.getValue ()[0], areaDoCiclo))
                                 .append  ("produtividadeScHa", ColheitasHandler.scPorHectare (entrada.getValue ()[0], areaDoCiclo)));
        }

        // talhao em varios ciclos: a area conta uma vez por ciclo
        ArrayList<Document> listaTalhoes = new ArrayList<Document> ();
        for (Map.Entry<String, long[]> entrada : porTalhao.entrySet ())
        {
            Document talhao   = talhoes.get (entrada.getKey ());
            int      vezes    = 0;
            double   hectares = talhao == null ? 0 : SafrasHandler.hectares (talhao);

            for (String dono : ciclos.values ())
                if (dono.equals (entrada.getKey ()))
                    vezes++;

            listaTalhoes.add (new Document ("talhaoId",          entrada.getKey ())
                                  .append  ("nome",              talhao == null ? null : talhao.getString ("nome"))
                                  .append  ("hectares",          hectares)
                                  .append  ("ciclos",            vezes)
                                  .append  ("registros",         entrada.getValue ()[1])
                                  .append  ("totalKg",           ColheitasHandler.kg (entrada.getValue ()[0]))
                                  .append  ("produtividadeKgHa", ColheitasHandler.kgPorHectare (entrada.getValue ()[0], hectares * vezes))
                                  .append  ("produtividadeScHa", ColheitasHandler.scPorHectare (entrada.getValue ()[0], hectares * vezes)));
        }

        return new Document ("registros",         lista.size ())
                   .append  ("totalKg",           ColheitasHandler.kg (total))
                   .append  ("totalSacas",        ColheitasHandler.sacas (total))
                   .append  ("areaHectares",      area)
                   .append  ("produtividadeKgHa", ColheitasHandler.kgPorHectare (total, area))
                   .append  ("produtividadeScHa", ColheitasHandler.scPorHectare (total, area))
                   .append  ("porSafra",          listaSafras)
                   .append  ("porTalhao",         listaTalhoes);
    }

    // ------------------------------------------------------------------ rotas

    // GET /api/colheitas e GET /api/colheitas/resumo, com os mesmos filtros
    private Resposta consultarLista (Requisicao requisicao, boolean resumir) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String safraId       = ColheitasHandler.filtro (requisicao, "safraId");
        String talhaoId      = ColheitasHandler.filtro (requisicao, "talhaoId");
        String cultura       = ColheitasHandler.filtro (requisicao, "cultura");

        // o periodo usa a mesma validacao de data dos formularios
        Document periodo = new Document ();
        if (ColheitasHandler.filtro (requisicao, "de") != null)
            periodo.append ("de", requisicao.getConsulta ("de").trim ());
        if (ColheitasHandler.filtro (requisicao, "ate") != null)
            periodo.append ("ate", requisicao.getConsulta ("ate").trim ());

        Validacao validacao = new Validacao (periodo);
        String    de        = periodo.containsKey ("de")  ? validacao.data ("de",  "Informe a data inicial.") : null;
        String    ate       = periodo.containsKey ("ate") ? validacao.data ("ate", "Informe a data final.")   : null;

        if (de != null && ate != null && ate.compareTo (de) < 0)
            validacao.erro ("ate", "A data final deve ser igual ou posterior \u00e0 inicial.");

        if (validacao.temErros ())
            return validacao.resposta ();

        LinkedHashMap<String, Document> talhoes   = ColheitasHandler.porId (this.talhoes.listar (propriedadeId));
        LinkedHashMap<String, Document> safras    = ColheitasHandler.porId (this.safras.listar  (propriedadeId));
        ArrayList<Document>             filtradas = new ArrayList<Document> ();

        for (Document colheita : this.colheitas.listar (propriedadeId))
            if (ColheitasHandler.passa (colheita, safras, safraId, talhaoId, cultura, de, ate))
                ColheitasHandler.inserirOrdenado (filtradas, colheita);

        if (resumir)
            return Resposta.json (200, ColheitasHandler.resumo (filtradas, talhoes, safras, talhaoId));

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document colheita : filtradas)
            ret.add (ColheitasHandler.publico (colheita, talhoes, safras));

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/colheitas/comparativo?cultura=  (todos os ciclos, com ou sem colheita, do mais antigo ao mais novo)
    private Resposta comparativo (Requisicao requisicao) throws Exception
    {
        String                          propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String                          cultura       = ColheitasHandler.filtro (requisicao, "cultura");
        LinkedHashMap<String, Document> talhoes       = ColheitasHandler.porId (this.talhoes.listar (propriedadeId));
        LinkedHashMap<String, long[]>   producao      = new LinkedHashMap<String, long[]> ();
        LinkedHashMap<String, String>   colhidos      = new LinkedHashMap<String, String> ();
        LinkedHashMap<String, String>   ultima        = new LinkedHashMap<String, String> ();
        ArrayList<Document>             ret           = new ArrayList<Document> ();

        for (Document colheita : this.colheitas.listar (propriedadeId))
        {
            String safraId = colheita.getString ("safraId");
            String data    = colheita.getString ("data");

            ColheitasHandler.somar (producao, safraId, ColheitasHandler.gramas (colheita));
            colhidos.put (safraId + "/" + colheita.getString ("talhaoId"), safraId);
            if (ultima.get (safraId) == null || ultima.get (safraId).compareTo (data) < 0)
                ultima.put (safraId, data);
        }

        // insercao ordenada por inicio do ciclo (e nome, no empate)
        for (Document safra : this.safras.listar (propriedadeId))
        {
            if (cultura != null && !cultura.equalsIgnoreCase (safra.getString ("cultura")))
                continue;

            String safraId   = ColheitasHandler.id (safra);
            long[] soma      = producao.get (safraId);
            long   gramas    = soma == null ? 0 : soma[0];
            double area      = SafrasHandler.area (safra, talhoes);
            int    talhoesOk = 0;

            for (String dono : colhidos.values ())
                if (dono.equals (safraId))
                    talhoesOk++;

            Document item = new Document ("safraId",           safraId)
                                .append  ("safraNome",         safra.getString ("nome"))
                                .append  ("cultura",           safra.getString ("cultura"))
                                .append  ("etapa",             safra.getString ("etapa"))
                                .append  ("finalizada",        ColheitasHandler.finalizada (safra))
                                .append  ("dataInicio",        safra.getString ("dataInicio"))
                                .append  ("dataFim",           safra.getString ("dataFim"))
                                .append  ("areaHectares",      area)
                                .append  ("talhoesNaSafra",    safra.getList ("talhoes", String.class).size ())
                                .append  ("talhoesColhidos",   talhoesOk)
                                .append  ("registros",         soma == null ? 0 : soma[1])
                                .append  ("totalKg",           ColheitasHandler.kg (gramas))
                                .append  ("totalSacas",        ColheitasHandler.sacas (gramas))
                                .append  ("produtividadeKgHa", ColheitasHandler.kgPorHectare (gramas, area))
                                .append  ("produtividadeScHa", ColheitasHandler.scPorHectare (gramas, area))
                                .append  ("ultimaColheita",    ultima.get (safraId));

            String chave   = safra.getString ("dataInicio") + safra.getString ("nomeNormalizado");
            int    posicao = 0;
            while (posicao < ret.size () &&
                   (ret.get (posicao).getString ("dataInicio") + ret.get (posicao).getString ("safraNome").toLowerCase ()).compareTo (chave) <= 0)
                posicao++;
            ret.add (posicao, item);
        }

        // variacao da produtividade frente ao ciclo anterior da mesma cultura que tem produtividade
        LinkedHashMap<String, Double> anterior = new LinkedHashMap<String, Double> ();
        for (Document item : ret)
        {
            Double atual = (Double) item.get ("produtividadeKgHa");
            Double antes = anterior.get (item.getString ("cultura"));
            Double variacao = null;

            if (atual != null && antes != null && antes.doubleValue () > 0)
                variacao = Double.valueOf (Math.round ((atual.doubleValue () - antes.doubleValue ()) * 1000.0 / antes.doubleValue ()) / 10.0);

            item.append ("variacaoPercentual", variacao);
            if (atual != null)
                anterior.put (item.getString ("cultura"), atual);
        }

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/colheitas/{id}
    private Resposta consultar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        Document colheita      = this.colheitas.buscarPorId (propriedadeId, requisicao.getParametro ("id"));

        if (colheita == null)
            ret = Resposta.erro (404, "Not found");
        else
            ret = Resposta.json (200, ColheitasHandler.publico (colheita,
                                                                ColheitasHandler.porId (this.talhoes.listar (propriedadeId)),
                                                                ColheitasHandler.porId (this.safras.listar  (propriedadeId))));

        return ret;
    }

    // POST /api/colheitas
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
            LinkedHashMap<String, Document> talhoes       = ColheitasHandler.porId (this.talhoes.listar (propriedadeId));
            LinkedHashMap<String, Document> safras        = ColheitasHandler.porId (this.safras.listar  (propriedadeId));
            Document                        colheita      = ColheitasHandler.lerColheita (corpo, validacao, talhoes, safras);

            if (colheita == null)
                return validacao.resposta ();

            ret = ColheitasHandler.etapaFechada (safras.get (colheita.getString ("safraId")));
            if (ret != null)
                return ret;

            colheita.append ("propriedadeId", propriedadeId)
                    .append ("criadoEm",      colheita.getString ("atualizadoEm"));

            this.colheitas.criar (colheita);

            return Resposta.json (201, ColheitasHandler.publico (colheita, talhoes, safras));
        }
    }

    // PUT /api/colheitas/{id}
    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document atual         = this.colheitas.buscarPorId (propriedadeId, id);

            if (atual == null)
                return Resposta.erro (404, "Not found");

            Document                        corpo     = requisicao.getCorpoJson ();
            Validacao                       validacao = new Validacao (corpo);
            LinkedHashMap<String, Document> talhoes   = ColheitasHandler.porId (this.talhoes.listar (propriedadeId));
            LinkedHashMap<String, Document> safras    = ColheitasHandler.porId (this.safras.listar  (propriedadeId));

            // sair de uma safra finalizada tambem mexeria no historico dela
            if (ColheitasHandler.finalizada (safras.get (atual.getString ("safraId"))))
                return ColheitasHandler.safraFechada ();

            Document campos = ColheitasHandler.lerColheita (corpo, validacao, talhoes, safras);

            if (campos == null)
                return validacao.resposta ();

            ret = ColheitasHandler.etapaFechada (safras.get (campos.getString ("safraId")));
            if (ret != null)
                return ret;

            if (!this.colheitas.atualizar (propriedadeId, id, campos))
                return Resposta.erro (404, "Not found");

            return Resposta.json (200, ColheitasHandler.publico (this.colheitas.buscarPorId (propriedadeId, id), talhoes, safras));
        }
    }

    // DELETE /api/colheitas/{id}
    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document colheita      = this.colheitas.buscarPorId (propriedadeId, id);

            if (colheita == null)
                ret = Resposta.erro (404, "Not found");
            else if (ColheitasHandler.finalizada (this.safras.buscarPorId (propriedadeId, colheita.getString ("safraId"))))
                ret = ColheitasHandler.safraFechada ();
            else if (!this.colheitas.excluir (propriedadeId, id))
                ret = Resposta.erro (404, "Not found");
            else
                ret = Resposta.semConteudo ();

            return ret;
        }
    }
}
