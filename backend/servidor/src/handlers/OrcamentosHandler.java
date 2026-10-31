/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package handlers;

import dados.RepositorioCustos;
import dados.RepositorioOrcamentos;
import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import http.Manipulador;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessao;
import seguranca.Sessoes;
import util.Json;
import util.Validacao;

/*
    Orcamento planejado versus realizado por safra (controle gerencial, nao contabil).

    GET    /api/orcamentos              planejado x realizado de todas as safras da propriedade
    GET    /api/orcamentos/{safraId}    o mesmo, de uma safra
    PUT    /api/orcamentos/{safraId}    define ou altera o planejado {valor, alerta}
    DELETE /api/orcamentos/{safraId}    remove o planejado da safra

    Realizado = soma de todos os custos efetivos lancados na safra (manuais e de consumo de
    estoque), o mesmo conjunto de GET /api/custos?safraId=. Desvio = realizado - planejado.
    "alerta" e o percentual do planejado que liga o aviso de proximidade (padrao 80).
    Situacao: sem-orcamento | dentro | proximo (usou alerta% ou mais) | acima (passou do planejado).
    Safra sem orcamento nao ganha meta inventada: planejado, desvio e percentuais ficam null.
    Orcamento de safra finalizada fica so para consulta (409 em PUT e DELETE).
*/
public class OrcamentosHandler implements Manipulador
{
    public static final int ALERTA_PADRAO = 80;
    public static final int ALERTA_MINIMO = 50;
    public static final int ALERTA_MAXIMO = 100;

    public static final String SEM_ORCAMENTO = "sem-orcamento";
    public static final String DENTRO        = "dentro";
    public static final String PROXIMO       = "proximo";
    public static final String ACIMA         = "acima";

    private static final double VALOR_MAXIMO = 1000000000;
    private static final String FINALIZACAO  = "Finaliza\u00e7\u00e3o";

    private final RepositorioOrcamentos orcamentos;
    private final RepositorioSafras     safras;
    private final RepositorioTalhoes    talhoes;
    private final RepositorioCustos     custos;
    private final Sessoes               sessoes;

    public OrcamentosHandler (RepositorioOrcamentos orcamentos, RepositorioSafras safras, RepositorioTalhoes talhoes,
                              RepositorioCustos custos, Sessoes sessoes) throws Exception
    {
        if (orcamentos == null)
            throw new Exception ("Repositorio de orcamentos ausente");

        if (safras == null)
            throw new Exception ("Repositorio de safras ausente");

        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        if (custos == null)
            throw new Exception ("Repositorio de custos ausente");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        this.orcamentos = orcamentos;
        this.safras     = safras;
        this.talhoes    = talhoes;
        this.custos     = custos;
        this.sessoes    = sessoes;
    }

    // todas as rotas caem em tratar, que escolhe a acao pelo metodo e pelo caminho
    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/orcamentos",           this);
        roteador.adicionar ("GET",    "/api/orcamentos/{safraId}", this);
        roteador.adicionar ("PUT",    "/api/orcamentos/{safraId}", this);
        roteador.adicionar ("DELETE", "/api/orcamentos/{safraId}", this);
    }

    @Override
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   metodo = requisicao.getMetodo ().equals ("HEAD") ? "GET" : requisicao.getMetodo ();

        if (requisicao.getParametro ("safraId") == null)
            ret = this.listar (requisicao);
        else if (metodo.equals ("GET"))
            ret = this.consultar (requisicao);
        else if (metodo.equals ("PUT"))
            ret = this.salvar (requisicao);
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

    private LinkedHashMap<String, Document> mapaDeTalhoes (String propriedadeId) throws Exception
    {
        LinkedHashMap<String, Document> ret = new LinkedHashMap<String, Document> ();

        for (Document talhao : this.talhoes.listar (propriedadeId))
            ret.put (OrcamentosHandler.id (talhao), talhao);

        return ret;
    }

    // dinheiro em centavos (long) para nao acumular erro de ponto flutuante
    private static long centavos (Object valor)
    {
        return valor instanceof Number ? Math.round (((Number) valor).doubleValue () * 100) : 0;
    }

    private static double reais (long centavos)
    {
        return centavos / 100.0;
    }

    // percentual com uma casa decimal
    private static double percentual (long parte, long total)
    {
        return Math.round (parte * 1000.0 / total) / 10.0;
    }

    // realizado por safra: [0] centavos somados, [1] quantidade de lancamentos
    private HashMap<String, long[]> realizados (String propriedadeId) throws Exception
    {
        HashMap<String, long[]> ret = new HashMap<String, long[]> ();

        for (Document custo : this.custos.listar (propriedadeId))
        {
            String safraId = custo.getString ("safraId");
            if (safraId == null)
                continue;

            long[] soma = ret.get (safraId);
            if (soma == null)
            {
                soma = new long[2];
                ret.put (safraId, soma);
            }

            soma[0] += OrcamentosHandler.centavos (custo.get ("value"));
            soma[1]++;
        }

        return ret;
    }

    // situacao do realizado frente ao planejado e ao percentual de alerta
    public static String situacao (long planejado, long realizado, int alerta)
    {
        String ret;

        if (planejado <= 0)
            ret = OrcamentosHandler.SEM_ORCAMENTO;
        else if (realizado > planejado)
            ret = OrcamentosHandler.ACIMA;
        else if (realizado * 100 >= planejado * alerta)
            ret = OrcamentosHandler.PROXIMO;
        else
            ret = OrcamentosHandler.DENTRO;

        return ret;
    }

    // planejado x realizado de uma safra; orcamento pode ser null (safra sem planejado)
    private static Document item (Document safra, Document orcamento, long[] realizado,
                                  LinkedHashMap<String, Document> talhoes)
    {
        long    gasto      = realizado == null ? 0 : realizado[0];
        long    planejado  = orcamento == null ? 0 : OrcamentosHandler.centavos (orcamento.get ("valor"));
        Integer alerta     = orcamento == null ? null : orcamento.getInteger ("alerta");
        double  area       = SafrasHandler.area (safra, talhoes);

        Document ret = new Document ("safraId",      OrcamentosHandler.id (safra))
                           .append  ("safraNome",    safra.getString ("nome"))
                           .append  ("cultura",      safra.getString ("cultura"))
                           .append  ("etapa",        safra.getString ("etapa"))
                           .append  ("finalizada",   OrcamentosHandler.FINALIZACAO.equals (safra.getString ("etapa")))
                           .append  ("areaHectares", area)
                           .append  ("realizado",    OrcamentosHandler.reais (gasto))
                           .append  ("lancamentos",  realizado == null ? 0 : realizado[1]);

        if (orcamento == null)
        {
            ret.append ("planejado",        null)
               .append ("alerta",           null)
               .append ("saldo",            null)
               .append ("desvio",           null)
               .append ("desvioPercentual", null)
               .append ("percentualUsado",  null)
               .append ("situacao",         OrcamentosHandler.SEM_ORCAMENTO)
               .append ("atualizadoEm",     null);
        }
        else
        {
            ret.append ("planejado",        OrcamentosHandler.reais (planejado))
               .append ("alerta",           alerta)
               .append ("saldo",            OrcamentosHandler.reais (planejado - gasto))
               .append ("desvio",           OrcamentosHandler.reais (gasto - planejado))
               .append ("desvioPercentual", OrcamentosHandler.percentual (gasto - planejado, planejado))
               .append ("percentualUsado",  OrcamentosHandler.percentual (gasto, planejado))
               .append ("situacao",         OrcamentosHandler.situacao (planejado, gasto, alerta.intValue ()))
               .append ("atualizadoEm",     orcamento.getString ("atualizadoEm"));
        }

        // planejado e realizado por hectare so existem com area conhecida
        ret.append ("planejadoPorHectare", orcamento == null || area <= 0 ? null
                                           : Double.valueOf (Math.round (planejado / area) / 100.0))
           .append ("realizadoPorHectare", area <= 0 ? null : Double.valueOf (Math.round (gasto / area) / 100.0));

        return ret;
    }

    // valida o corpo do PUT; devolve {valor, alerta} ou null (erros na validacao)
    private static Document lerOrcamento (Document corpo, Validacao validacao, Document atual)
    {
        Object valor  = corpo.get ("valor");
        Object alerta = corpo.get ("alerta");
        int    limite = atual == null ? OrcamentosHandler.ALERTA_PADRAO : atual.getInteger ("alerta").intValue ();

        if (!(valor instanceof Number) ||
            !(((Number) valor).doubleValue () >= 0.01) ||                     // tambem barra NaN
            ((Number) valor).doubleValue () > OrcamentosHandler.VALOR_MAXIMO)
            validacao.erro ("valor", "Informe um valor maior que zero.");

        if (alerta != null)
        {
            if (!(alerta instanceof Number) || ((Number) alerta).doubleValue () != Math.floor (((Number) alerta).doubleValue ()) ||
                ((Number) alerta).intValue () < OrcamentosHandler.ALERTA_MINIMO ||
                ((Number) alerta).intValue () > OrcamentosHandler.ALERTA_MAXIMO)
                validacao.erro ("alerta", "Use um alerta entre " + OrcamentosHandler.ALERTA_MINIMO + "% e " +
                                          OrcamentosHandler.ALERTA_MAXIMO + "% do planejado.");
            else
                limite = ((Number) alerta).intValue ();
        }

        if (validacao.temErros ())
            return null;

        return new Document ("valor",  Math.round (((Number) valor).doubleValue () * 100) / 100.0)
                   .append  ("alerta", limite);
    }

    private static Resposta finalizada ()
    {
        return Resposta.json (409, new Document ("error", "Or\u00e7amento de safra finalizada n\u00e3o pode ser alterado."));
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

    // planejado x realizado da safra informada na rota, ja montado; null se a safra nao for da propriedade
    private Document consultarItem (String propriedadeId, String safraId) throws Exception
    {
        Document ret   = null;
        Document safra = this.safras.buscarPorId (propriedadeId, safraId);

        if (safra != null)
            ret = OrcamentosHandler.item (safra,
                                          this.orcamentos.buscarPorSafra (propriedadeId, safraId),
                                          this.realizados (propriedadeId).get (safraId),
                                          this.mapaDeTalhoes (propriedadeId));

        return ret;
    }

    // ------------------------------------------------------------------ rotas

    // GET /api/orcamentos  (todas as safras, com ou sem planejado, na ordem de cadastro das safras)
    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String                          propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        LinkedHashMap<String, Document> talhoes       = this.mapaDeTalhoes (propriedadeId);
        HashMap<String, long[]>         realizados    = this.realizados (propriedadeId);
        HashMap<String, Document>       porSafra      = new HashMap<String, Document> ();
        ArrayList<Document>             ret           = new ArrayList<Document> ();

        // orcamento de safra que ja nao existe nao aparece: so as safras atuais entram na lista
        for (Document orcamento : this.orcamentos.listar (propriedadeId))
            porSafra.put (orcamento.getString ("safraId"), orcamento);

        for (Document safra : this.safras.listar (propriedadeId))
        {
            String safraId = OrcamentosHandler.id (safra);
            ret.add (OrcamentosHandler.item (safra, porSafra.get (safraId), realizados.get (safraId), talhoes));
        }

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/orcamentos/{safraId}
    private Resposta consultar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        Document item = this.consultarItem (requisicao.getSessao ().getPropriedadeId (), requisicao.getParametro ("safraId"));

        if (item == null)
            ret = Resposta.erro (404, "Not found");
        else
            ret = Resposta.json (200, item);

        return ret;
    }

    // PUT /api/orcamentos/{safraId}  (201 na primeira definicao, 200 nas alteracoes)
    private Resposta salvar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   safraId       = requisicao.getParametro ("safraId");
            Document safra         = this.safras.buscarPorId (propriedadeId, safraId);

            if (safra == null)
                return Resposta.erro (404, "Not found");

            if (OrcamentosHandler.FINALIZACAO.equals (safra.getString ("etapa")))
                return OrcamentosHandler.finalizada ();

            Document  corpo     = requisicao.getCorpoJson ();
            Validacao validacao = new Validacao (corpo);
            Document  atual     = this.orcamentos.buscarPorSafra (propriedadeId, safraId);
            Document  campos    = OrcamentosHandler.lerOrcamento (corpo, validacao, atual);

            if (campos == null)
                return validacao.resposta ();

            String agora = Instant.now ().toString ();
            campos.append ("propriedadeId", propriedadeId)
                  .append ("safraId",       safraId)
                  .append ("atualizadoEm",  agora)
                  .append ("criadoEm",      agora);

            boolean criado = this.orcamentos.salvar (campos);

            return Resposta.json (criado ? 201 : 200, this.consultarItem (propriedadeId, safraId));
        }
    }

    // DELETE /api/orcamentos/{safraId}
    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   safraId       = requisicao.getParametro ("safraId");
            Document safra         = this.safras.buscarPorId (propriedadeId, safraId);

            if (safra == null)
                ret = Resposta.erro (404, "Not found");
            else if (OrcamentosHandler.FINALIZACAO.equals (safra.getString ("etapa")))
                ret = OrcamentosHandler.finalizada ();
            else if (!this.orcamentos.excluir (propriedadeId, safraId))
                ret = Resposta.erro (404, "Not found");
            else
                ret = Resposta.semConteudo ();

            return ret;
        }
    }
}
