/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers;

import dados.RepositorioTalhoes;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import util.Json;
import util.Validacao;

/*
    Talhoes (areas de cultivo) da propriedade de quem esta logado.
    GET /api/talhoes?cultura=&q=, GET /api/talhoes/resumo, POST /api/talhoes, PUT /api/talhoes/{id}.
    Sem DELETE nesta entrega.
*/
public class TalhoesHandler
{
    public static final List<String> CULTURAS = List.of ("Soja", "Milho", "Trigo", "Café", "Algodão");
    public static final List<String> STATUS   = List.of ("Ativo", "Irrigação pendente", "Em preparo");
    public static final List<String> SAFRAS    = List.of ("2024/25", "2025/26", "2026/27");

    private final RepositorioTalhoes talhoes;
    private final seguranca.Sessoes sessoes;

    public TalhoesHandler (RepositorioTalhoes talhoes, seguranca.Sessoes sessoes) throws Exception
    {
        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        this.talhoes = talhoes;
        if (sessoes == null) throw new Exception ("Sessoes ausentes");
        this.sessoes = sessoes;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        // /resumo antes de /{id} para nao ser confundido com um id
        roteador.adicionar ("GET",  "/api/talhoes/resumo", this::resumo);
        roteador.adicionar ("GET",  "/api/talhoes/{id}/historico", this::historico);
        roteador.adicionar ("POST", "/api/talhoes/{id}/historico", this::registrarHistorico);
        roteador.adicionar ("GET",  "/api/talhoes",        this::listar);
        roteador.adicionar ("POST", "/api/talhoes",        this::criar);
        roteador.adicionar ("PUT",  "/api/talhoes/{id}",   this::atualizar);
    }

    private static Document publico (Document talhao)
    {
        return Json.publico (talhao, "propriedadeId", "nomeNormalizado");
    }

    // GET /api/talhoes?cultura=Soja&q=norte
    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String cultura = requisicao.getConsulta ("cultura");
        String busca   = requisicao.getConsulta ("q");

        if (cultura != null && (cultura.isBlank () || cultura.equalsIgnoreCase ("todos")))
            cultura = null;

        if (busca != null)
            busca = busca.trim ().toLowerCase (Locale.ROOT);

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document talhao : this.talhoes.listar (requisicao.getSessao ().getPropriedadeId ()))
        {
            if (cultura != null && !cultura.trim ().equalsIgnoreCase (talhao.getString ("cultura")))
                continue;

            if (busca != null && !talhao.getString ("nomeNormalizado").contains (busca))
                continue;

            ret.add (TalhoesHandler.publico (talhao));
        }

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/talhoes/resumo
    private Resposta resumo (Requisicao requisicao) throws Exception
    {
        double hectares = 0;
        int    total    = 0;
        int    ativos   = 0;

        for (Document talhao : this.talhoes.listar (requisicao.getSessao ().getPropriedadeId ()))
        {
            hectares += talhao.getDouble ("hectares");
            total++;

            if (talhao.getString ("status").equals ("Ativo"))
                ativos++;
        }

        return Resposta.json (200, new Document ("totalHectares",    hectares)
                                       .append ("quantidadeTotal",  total)
                                       .append ("quantidadeAtivos", ativos));
    }

    // valida o corpo de POST e PUT; devolve os campos prontos para gravar ou null (erros na validacao)
    private static Document lerTalhao (Document corpo, Validacao validacao)
    {
        String nome    = validacao.texto ("nome", "Informe o nome do talhão.", 80);
        String cultura = validacao.texto ("cultura", "Selecione uma cultura.", 40);
        Object valorSafra = corpo.get ("safra");
        String safra = valorSafra == null ? "2025/26" : (valorSafra instanceof String ? ((String) valorSafra).trim () : null);
        if (safra == null || safra.isEmpty ())
            validacao.erro ("safra", "Selecione uma safra.");

        if (cultura != null && !TalhoesHandler.CULTURAS.contains (cultura))
            validacao.erro ("cultura", "Selecione uma cultura válida.");
        if (!TalhoesHandler.SAFRAS.contains (safra))
            validacao.erro ("safra", "Selecione uma safra válida.");

        Object hectares = corpo.get ("hectares");
        if (!(hectares instanceof Number) ||
            !(((Number) hectares).doubleValue () > 0) ||      // tambem barra NaN
            ((Number) hectares).doubleValue () > 1000000)
            validacao.erro ("hectares", "Informe um valor de hectares maior que zero.");

        String status = "Ativo";
        Object valor  = corpo.get ("status");
        if (valor != null && !"".equals (valor))
        {
            if (!(valor instanceof String) || !TalhoesHandler.STATUS.contains (valor))
                validacao.erro ("status", "Selecione um status válido.");
            else
                status = (String) valor;
        }

        if (validacao.temErros ())
            return null;

        return new Document ("nome",            nome)
                   .append  ("nomeNormalizado", nome.toLowerCase (Locale.ROOT))
                   .append  ("cultura",         cultura)
                   .append  ("safra",           safra)
                   .append  ("hectares",        ((Number) hectares).doubleValue ())
                   .append  ("status",          status)
                   .append  ("atualizadoEm",    Instant.now ().toString ());
    }

    private static Resposta nomeRepetido ()
    {
        return Resposta.json (409, new Document ("error", "Plot name already exists")
                                       .append ("fields", new Document ("nome", "Já existe um talhão com esse nome.")));
    }

    // POST /api/talhoes
    private Resposta criar (Requisicao requisicao) throws Exception
    {
        // Serializa com seleção/exclusão: uma requisição que capturou o contexto
        // anterior não pode criar o primeiro talhão depois de excluir a fazenda.
        synchronized (this.sessoes)
        {
            seguranca.Sessao atual = this.sessoes.buscar (requisicao.getToken ());
            if (atual == null) return Resposta.erro (401, "Unauthorized");
            if (!atual.getPropriedadeId ().equals (requisicao.getSessao ().getPropriedadeId ()))
                return Resposta.erro (409, "Property context changed; reload before saving");
            return this.criarProtegido (requisicao);
        }
    }

    private Resposta criarProtegido (Requisicao requisicao) throws Exception
    {
        Document  corpo     = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        Document  talhao    = TalhoesHandler.lerTalhao (corpo, validacao);

        if (talhao == null)
            return validacao.resposta ();

        talhao.append ("propriedadeId", requisicao.getSessao ().getPropriedadeId ())
              .append ("criadoEm",      talhao.getString ("atualizadoEm"));

        if (!this.talhoes.criar (talhao))
            return TalhoesHandler.nomeRepetido ();

        return Resposta.json (201, TalhoesHandler.publico (talhao));
    }

    // PUT /api/talhoes/{id}  (troca nome, cultura, hectares e status)
    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        if (this.talhoes.buscarPorId (propriedadeId, id) == null)
            return Resposta.erro (404, "Not found");

        Document  corpo     = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        Document  campos    = TalhoesHandler.lerTalhao (corpo, validacao);

        if (campos == null)
            return validacao.resposta ();

        if (!this.talhoes.atualizar (propriedadeId, id, campos))
            return TalhoesHandler.nomeRepetido ();

        return Resposta.json (200, TalhoesHandler.publico (this.talhoes.buscarPorId (propriedadeId, id)));
    }

    // GET retorna somente eventos da propriedade do usuario autenticado.
    private Resposta historico (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id = requisicao.getParametro ("id");
        if (this.talhoes.buscarPorId (propriedadeId, id) == null) return Resposta.erro (404, "Not found");
        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document evento : this.talhoes.listarHistorico (propriedadeId, id))
            ret.add (Json.publico (evento, "propriedadeId", "talhaoId"));
        return Resposta.json (200, Json.escrever (ret));
    }

    // POST registra a origem da operacao sem permitir que o cliente altere os vinculos internos.
    private Resposta registrarHistorico (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id = requisicao.getParametro ("id");
        if (this.talhoes.buscarPorId (propriedadeId, id) == null) return Resposta.erro (404, "Not found");
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        String tipo = validacao.texto ("tipo", "Informe o tipo.", 20);
        String descricao = validacao.texto ("descricao", "Informe a descricao.", 240);
        if (tipo != null && !List.of ("atividade", "ocorrencia", "custo").contains (tipo)) validacao.erro ("tipo", "Tipo invalido.");
        if (validacao.temErros ()) return validacao.resposta ();
        Document evento = new Document ("tipo", tipo).append ("descricao", descricao).append ("criadoEm", Instant.now ().toString ());
        this.talhoes.criarHistorico (propriedadeId, id, evento);
        return Resposta.json (201, evento);
    }
}
