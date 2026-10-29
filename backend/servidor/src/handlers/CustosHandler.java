/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package handlers;

import dados.RepositorioCustos;
import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import http.Manipulador;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessao;
import seguranca.Sessoes;
import servicos.CustosDeConsumo;
import util.Json;
import util.Validacao;

/*
    Lancamentos de custo da propriedade de quem esta logado (controle gerencial, nao contabil).

    GET    /api/custos?category=&talhaoId=&safraId=&de=&ate=&q=  lista, do mais recente ao mais antigo
    GET    /api/custos/resumo?(mesmos filtros)                    indicadores sobre os lancamentos filtrados
    POST   /api/custos                                            cria
    GET    /api/custos/{id}                                       consulta
    PUT    /api/custos/{id}                                       altera
    DELETE /api/custos/{id}                                       exclui

    O lancamento pode ficar na propriedade toda (sem talhao) ou num talhao, e pode
    pertencer a uma safra. Com talhao e safra juntos, o talhao precisa ser da safra.
    Custo de safra finalizada nao e criado, alterado nem excluido (o historico fica fechado).
    Custo gerado por consumo de estoque (campo origem) so muda pela movimentacao do insumo:
    PUT e DELETE devolvem 409, e as consultas criam antes os custos de consumo que faltarem.
    Os nomes dos campos sao os "name" do formulario de custos.html.
*/
public class CustosHandler implements Manipulador
{
    public static final List<String> CATEGORIAS = List.of ("Insumos", "M\u00e3o de Obra", "Maquin\u00e1rio", "Combust\u00edvel", "Outros");

    // valores especiais dos filtros talhaoId e safraId: lancamentos sem talhao / sem safra
    public static final String SEM_TALHAO = "propriedade";
    public static final String SEM_SAFRA  = "nenhuma";

    private static final double VALOR_MAXIMO = 1000000000;

    private final RepositorioCustos  custos;
    private final RepositorioSafras  safras;
    private final RepositorioTalhoes talhoes;
    private final Sessoes            sessoes;
    private       CustosDeConsumo    consumo;

    public CustosHandler (RepositorioCustos custos, RepositorioSafras safras, RepositorioTalhoes talhoes,
                          Sessoes sessoes) throws Exception
    {
        if (custos == null)
            throw new Exception ("Repositorio de custos ausente");

        if (safras == null)
            throw new Exception ("Repositorio de safras ausente");

        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        this.custos  = custos;
        this.safras  = safras;
        this.talhoes = talhoes;
        this.sessoes = sessoes;
    }

    // custos gerados pelas saidas de estoque com atividade (#156); sem ele, so lancamentos manuais
    public void setConsumo (CustosDeConsumo consumo)
    {
        this.consumo = consumo;
    }

    // todas as rotas caem em tratar, que escolhe a acao pelo metodo e pelo caminho
    public void registrar (Roteador roteador) throws Exception
    {
        // /resumo antes de /{id} para nao ser confundido com um id
        roteador.adicionar ("GET",    "/api/custos/resumo", this);
        roteador.adicionar ("GET",    "/api/custos",        this);
        roteador.adicionar ("POST",   "/api/custos",        this);
        roteador.adicionar ("GET",    "/api/custos/{id}",   this);
        roteador.adicionar ("PUT",    "/api/custos/{id}",   this);
        roteador.adicionar ("DELETE", "/api/custos/{id}",   this);
    }

    @Override
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   metodo = requisicao.getMetodo ().equals ("HEAD") ? "GET" : requisicao.getMetodo ();
        int      partes = requisicao.getCaminho ().substring (1).split ("/", -1).length; // api/custos/{id}

        if (partes == 2)
        {
            if (metodo.equals ("GET"))
                ret = this.consultarLista (requisicao, false);
            else
                ret = this.criar (requisicao);
        }
        else if (requisicao.getParametro ("id") == null)
            ret = this.consultarLista (requisicao, true); // /api/custos/resumo
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
            ret.put (CustosHandler.id (documento), documento);

        return ret;
    }

    // dinheiro e somado em centavos (long) para nao acumular erro de ponto flutuante
    private static long centavos (Document custo)
    {
        return Math.round (((Number) custo.get ("value")).doubleValue () * 100);
    }

    private static double reais (long centavos)
    {
        return centavos / 100.0;
    }

    // cultura do lancamento: a da safra; sem safra, a do talhao; sem os dois, nenhuma
    private static String cultura (Document custo, Map<String, Document> talhoes, Map<String, Document> safras)
    {
        String   ret    = null;
        Document safra  = safras.get  (custo.getString ("safraId"));
        Document talhao = talhoes.get (custo.getString ("talhaoId"));

        if (safra != null)
            ret = safra.getString ("cultura");
        else if (talhao != null)
            ret = talhao.getString ("cultura");

        return ret;
    }

    private static Document publico (Document custo, Map<String, Document> talhoes, Map<String, Document> safras)
    {
        Document talhao = talhoes.get (custo.getString ("talhaoId"));
        Document safra  = safras.get  (custo.getString ("safraId"));

        return Json.publico (custo, "propriedadeId")
                   .append ("talhaoNome", talhao == null ? null : talhao.getString ("nome"))
                   .append ("safraNome",  safra  == null ? null : safra.getString ("nome"))
                   .append ("cultura",    CustosHandler.cultura (custo, talhoes, safras));
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
    private static Document lerCusto (Document corpo, Validacao validacao,
                                      Map<String, Document> talhoes, Map<String, Document> safras)
    {
        String description = validacao.texto ("description", "Informe a descri\u00e7\u00e3o.", 120);
        String category    = validacao.texto ("category",    "Selecione a categoria.",                40);
        String date        = validacao.data  ("date",        "Selecione a data.");
        String talhaoId    = CustosHandler.opcional (corpo, "talhaoId");
        String safraId     = CustosHandler.opcional (corpo, "safraId");
        Object value       = corpo.get ("value");

        if (category != null && !CustosHandler.CATEGORIAS.contains (category))
            validacao.erro ("category", "Selecione uma categoria v\u00e1lida.");

        if (!(value instanceof Number) ||
            !(((Number) value).doubleValue () >= 0.01) ||                     // tambem barra NaN
            ((Number) value).doubleValue () > CustosHandler.VALOR_MAXIMO)
            validacao.erro ("value", "Informe um valor maior que zero.");

        if (talhaoId != null && !talhoes.containsKey (talhaoId))
            validacao.erro ("talhaoId", "Selecione um talh\u00e3o da propriedade.");

        if (safraId != null && !safras.containsKey (safraId))
            validacao.erro ("safraId", "Selecione uma safra da propriedade.");

        if (talhoes.containsKey (talhaoId) && safras.containsKey (safraId) &&
            !safras.get (safraId).getList ("talhoes", String.class).contains (talhaoId))
            validacao.erro ("talhaoId", "O talh\u00e3o n\u00e3o faz parte da safra escolhida.");

        if (validacao.temErros ())
            return null;

        return new Document ("description",  description)
                   .append  ("category",     category)
                   .append  ("value",        Math.round (((Number) value).doubleValue () * 100) / 100.0)
                   .append  ("date",         date)
                   .append  ("talhaoId",     talhaoId)
                   .append  ("safraId",      safraId)
                   .append  ("atualizadoEm", Instant.now ().toString ());
    }

    // true se o lancamento esta (ou iria para) uma safra ja finalizada
    private static boolean emSafraFinalizada (String safraId, Map<String, Document> safras)
    {
        Document safra = safraId == null ? null : safras.get (safraId);

        return safra != null && "Finaliza\u00e7\u00e3o".equals (safra.getString ("etapa"));
    }

    private static Resposta deConsumo ()
    {
        return Resposta.json (409, new Document ("error", "Custo gerado pelo consumo de estoque. Corrija pela movimenta\u00e7\u00e3o do insumo."));
    }

    private static Resposta safraFechada ()
    {
        return Resposta.json (409, new Document ("error", "Custos de safra finalizada n\u00e3o podem ser alterados."));
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

    // o lancamento passa pelos filtros da consulta?
    private static boolean passa (Document custo, String category, String talhaoId, String safraId,
                                  String de, String ate, String busca)
    {
        boolean ret = true;

        if (category != null && !category.equalsIgnoreCase (custo.getString ("category")))
            ret = false;

        if (talhaoId != null)
        {
            String valor = custo.getString ("talhaoId");
            if (talhaoId.equalsIgnoreCase (CustosHandler.SEM_TALHAO) ? valor != null : !talhaoId.equals (valor))
                ret = false;
        }

        if (safraId != null)
        {
            String valor = custo.getString ("safraId");
            if (safraId.equalsIgnoreCase (CustosHandler.SEM_SAFRA) ? valor != null : !safraId.equals (valor))
                ret = false;
        }

        if (de != null && custo.getString ("date").compareTo (de) < 0)
            ret = false;

        if (ate != null && custo.getString ("date").compareTo (ate) > 0)
            ret = false;

        if (busca != null && !custo.getString ("description").toLowerCase (Locale.ROOT).contains (busca))
            ret = false;

        return ret;
    }

    // insercao ordenada: data mais recente primeiro; na mesma data, o cadastrado por ultimo primeiro
    private static void inserirOrdenado (ArrayList<Document> lista, Document custo)
    {
        String chave   = custo.getString ("date") + CustosHandler.id (custo);
        int    posicao = 0;

        while (posicao < lista.size () &&
               (lista.get (posicao).getString ("date") + CustosHandler.id (lista.get (posicao))).compareTo (chave) > 0)
            posicao++;

        lista.add (posicao, custo);
    }

    private static Document item (String chave, Object valor, long centavos)
    {
        return new Document (chave, valor).append ("total", CustosHandler.reais (centavos));
    }

    private static void somar (LinkedHashMap<String, Long> mapa, String chave, long centavos)
    {
        Long atual = mapa.get (chave);

        mapa.put (chave, (atual == null ? 0 : atual.longValue ()) + centavos);
    }

    // indicadores sobre os lancamentos ja filtrados
    private static Document resumo (List<Document> lista, Map<String, Document> talhoes, Map<String, Document> safras,
                                    String talhaoId, String safraId)
    {
        long                        total      = 0;
        LinkedHashMap<String, Long> categorias = new LinkedHashMap<String, Long> ();
        LinkedHashMap<String, Long> porTalhao  = new LinkedHashMap<String, Long> ();
        LinkedHashMap<String, Long> porSafra   = new LinkedHashMap<String, Long> ();
        LinkedHashMap<String, Long> porCultura = new LinkedHashMap<String, Long> ();
        TreeMap<String, Long>       porMes     = new TreeMap<String, Long> ();

        for (String categoria : CustosHandler.CATEGORIAS)
            categorias.put (categoria, Long.valueOf (0));

        for (Document custo : lista)
        {
            long   valor   = CustosHandler.centavos (custo);
            String cultura = CustosHandler.cultura (custo, talhoes, safras);
            String mes     = custo.getString ("date").substring (0, 7);
            Long   noMes   = porMes.get (mes);

            total += valor;
            CustosHandler.somar (categorias, custo.getString ("category"), valor);
            CustosHandler.somar (porTalhao,  String.valueOf (custo.getString ("talhaoId")), valor);
            CustosHandler.somar (porSafra,   String.valueOf (custo.getString ("safraId")),  valor);
            CustosHandler.somar (porCultura, String.valueOf (cultura),                      valor);
            porMes.put (mes, (noMes == null ? 0 : noMes.longValue ()) + valor);
        }

        ArrayList<Document> listaCategorias = new ArrayList<Document> ();
        for (Map.Entry<String, Long> entrada : categorias.entrySet ())
            listaCategorias.add (CustosHandler.item ("categoria", entrada.getKey (), entrada.getValue ()));

        ArrayList<Document> listaTalhoes = new ArrayList<Document> ();
        for (Map.Entry<String, Long> entrada : porTalhao.entrySet ())
        {
            Document talhao = talhoes.get (entrada.getKey ());
            listaTalhoes.add (CustosHandler.item ("talhaoId", talhao == null ? null : entrada.getKey (), entrada.getValue ())
                                  .append ("nome", talhao == null ? "Propriedade" : talhao.getString ("nome")));
        }

        ArrayList<Document> listaSafras = new ArrayList<Document> ();
        for (Map.Entry<String, Long> entrada : porSafra.entrySet ())
        {
            Document safra = safras.get (entrada.getKey ());
            listaSafras.add (CustosHandler.item ("safraId", safra == null ? null : entrada.getKey (), entrada.getValue ())
                                 .append ("nome", safra == null ? "Sem safra" : safra.getString ("nome")));
        }

        ArrayList<Document> listaCulturas = new ArrayList<Document> ();
        for (Map.Entry<String, Long> entrada : porCultura.entrySet ())
            listaCulturas.add (CustosHandler.item ("cultura",
                                                   entrada.getKey ().equals ("null") ? null : entrada.getKey (),
                                                   entrada.getValue ()));

        ArrayList<Document> evolucao = new ArrayList<Document> ();
        for (Map.Entry<String, Long> entrada : porMes.entrySet ())
            evolucao.add (CustosHandler.item ("mes", entrada.getKey (), entrada.getValue ()));

        // area do recorte: a safra filtrada, o talhao filtrado ou todos os talhoes da propriedade
        double area = 0;
        if (safraId != null && safras.containsKey (safraId))
            area = SafrasHandler.area (safras.get (safraId), talhoes);
        else if (talhaoId != null && talhoes.containsKey (talhaoId))
            area = SafrasHandler.hectares (talhoes.get (talhaoId));
        else if (safraId == null && talhaoId == null)
            for (Document talhao : talhoes.values ())
                area += SafrasHandler.hectares (talhao);

        Double porHectare = null;
        if (area > 0)
            porHectare = Double.valueOf (Math.round (total / area) / 100.0);

        return new Document ("total",           CustosHandler.reais (total))
                   .append  ("quantidade",      lista.size ())
                   .append  ("areaHectares",    area)
                   .append  ("custoPorHectare", porHectare)
                   .append  ("porCategoria",    listaCategorias)
                   .append  ("porTalhao",       listaTalhoes)
                   .append  ("porSafra",        listaSafras)
                   .append  ("porCultura",      listaCulturas)
                   .append  ("evolucao",        evolucao);
    }

    // ------------------------------------------------------------------ rotas

    // GET /api/custos e GET /api/custos/resumo, com os mesmos filtros
    private Resposta consultarLista (Requisicao requisicao, boolean resumir) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String category      = CustosHandler.filtro (requisicao, "category");
        String talhaoId      = CustosHandler.filtro (requisicao, "talhaoId");
        String safraId       = CustosHandler.filtro (requisicao, "safraId");
        String busca         = CustosHandler.filtro (requisicao, "q");

        // o periodo usa a mesma validacao de data dos formularios
        Document periodo = new Document ();
        if (CustosHandler.filtro (requisicao, "de") != null)
            periodo.append ("de", requisicao.getConsulta ("de").trim ());
        if (CustosHandler.filtro (requisicao, "ate") != null)
            periodo.append ("ate", requisicao.getConsulta ("ate").trim ());

        Validacao validacao = new Validacao (periodo);
        String    de        = periodo.containsKey ("de")  ? validacao.data ("de",  "Informe a data inicial.") : null;
        String    ate       = periodo.containsKey ("ate") ? validacao.data ("ate", "Informe a data final.")   : null;

        if (de != null && ate != null && ate.compareTo (de) < 0)
            validacao.erro ("ate", "A data final deve ser igual ou posterior \u00e0 inicial.");

        if (validacao.temErros ())
            return validacao.resposta ();

        if (busca != null)
            busca = busca.toLowerCase (Locale.ROOT);

        // saida de estoque cujo custo nao chegou a ser gravado entra antes da consulta
        if (this.consumo != null)
            synchronized (this.sessoes)
            {
                this.consumo.sincronizar (requisicao.getSessao ().getUsuarioId (), propriedadeId);
            }

        LinkedHashMap<String, Document> talhoes   = CustosHandler.porId (this.talhoes.listar (propriedadeId));
        LinkedHashMap<String, Document> safras    = CustosHandler.porId (this.safras.listar  (propriedadeId));
        ArrayList<Document>             filtrados = new ArrayList<Document> ();

        for (Document custo : this.custos.listar (propriedadeId))
            if (CustosHandler.passa (custo, category, talhaoId, safraId, de, ate, busca))
                CustosHandler.inserirOrdenado (filtrados, custo);

        if (resumir)
            return Resposta.json (200, CustosHandler.resumo (filtrados, talhoes, safras, talhaoId, safraId));

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document custo : filtrados)
            ret.add (CustosHandler.publico (custo, talhoes, safras));

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/custos/{id}
    private Resposta consultar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        Document custo         = this.custos.buscarPorId (propriedadeId, requisicao.getParametro ("id"));

        if (custo == null)
            ret = Resposta.erro (404, "Not found");
        else
            ret = Resposta.json (200, CustosHandler.publico (custo,
                                                             CustosHandler.porId (this.talhoes.listar (propriedadeId)),
                                                             CustosHandler.porId (this.safras.listar  (propriedadeId))));

        return ret;
    }

    // POST /api/custos
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
            LinkedHashMap<String, Document> talhoes       = CustosHandler.porId (this.talhoes.listar (propriedadeId));
            LinkedHashMap<String, Document> safras        = CustosHandler.porId (this.safras.listar  (propriedadeId));
            Document                        custo         = CustosHandler.lerCusto (corpo, validacao, talhoes, safras);

            if (custo == null)
                return validacao.resposta ();

            if (CustosHandler.emSafraFinalizada (custo.getString ("safraId"), safras))
                return CustosHandler.safraFechada ();

            custo.append ("propriedadeId", propriedadeId)
                 .append ("criadoEm",      custo.getString ("atualizadoEm"));

            this.custos.criar (custo);

            return Resposta.json (201, CustosHandler.publico (custo, talhoes, safras));
        }
    }

    // PUT /api/custos/{id}
    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document atual         = this.custos.buscarPorId (propriedadeId, id);

            if (atual == null)
                return Resposta.erro (404, "Not found");

            if (atual.get ("origem") != null)
                return CustosHandler.deConsumo ();

            Document                        corpo     = requisicao.getCorpoJson ();
            Validacao                       validacao = new Validacao (corpo);
            LinkedHashMap<String, Document> talhoes   = CustosHandler.porId (this.talhoes.listar (propriedadeId));
            LinkedHashMap<String, Document> safras    = CustosHandler.porId (this.safras.listar  (propriedadeId));

            // sair de uma safra finalizada tambem mexeria no historico dela
            if (CustosHandler.emSafraFinalizada (atual.getString ("safraId"), safras))
                return CustosHandler.safraFechada ();

            Document campos = CustosHandler.lerCusto (corpo, validacao, talhoes, safras);

            if (campos == null)
                return validacao.resposta ();

            if (CustosHandler.emSafraFinalizada (campos.getString ("safraId"), safras))
                return CustosHandler.safraFechada ();

            if (!this.custos.atualizar (propriedadeId, id, campos))
                return Resposta.erro (404, "Not found");

            return Resposta.json (200, CustosHandler.publico (this.custos.buscarPorId (propriedadeId, id), talhoes, safras));
        }
    }

    // DELETE /api/custos/{id}
    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document custo         = this.custos.buscarPorId (propriedadeId, id);

            if (custo == null)
                ret = Resposta.erro (404, "Not found");
            else if (custo.get ("origem") != null)
                ret = CustosHandler.deConsumo ();
            else if (CustosHandler.emSafraFinalizada (custo.getString ("safraId"),
                                                      CustosHandler.porId (this.safras.listar (propriedadeId))))
                ret = CustosHandler.safraFechada ();
            else if (!this.custos.excluir (propriedadeId, id))
                ret = Resposta.erro (404, "Not found");
            else
                ret = Resposta.semConteudo ();

            return ret;
        }
    }
}
