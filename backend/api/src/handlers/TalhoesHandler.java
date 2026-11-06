/* Miguel — RA: 25003110 */
/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers;

import dados.RepositorioTalhoes;
import dados.RepositorioAtividades;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import servicos.ClienteDeAlgoritmos;
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
    private final RepositorioAtividades atividades;

    public TalhoesHandler (RepositorioTalhoes talhoes, RepositorioAtividades atividades, seguranca.Sessoes sessoes) throws Exception
    {
        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        this.talhoes = talhoes;
        if (atividades == null) throw new Exception ("Repositorio de atividades ausente");
        this.atividades = atividades;
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

    // GET /api/talhoes/resumo — cálculo no ServerSocket (CalculadoraDeTalhoes)
    private Resposta resumo (Requisicao requisicao) throws Exception
    {
        return ClienteDeAlgoritmos.resumoTalhoes (this.talhoes.listar (requisicao.getSessao ().getPropriedadeId ()));
    }

    // valida POST/PUT via ServerSocket (ValidadorDeTalhao); unicidade de nome continua no repositório
    private static Document lerTalhao (Document corpo) throws Exception
    {
        ClienteDeAlgoritmos.ResultadoTalhao remoto = ClienteDeAlgoritmos.validarTalhao (corpo);

        if (remoto.respostaErro != null)
            return null;

        return new Document (remoto.campos).append ("atualizadoEm", Instant.now ().toString ());
    }

    private static Resposta respostaTalhaoInvalido (Document corpo) throws Exception
    {
        ClienteDeAlgoritmos.ResultadoTalhao remoto = ClienteDeAlgoritmos.validarTalhao (corpo);
        return remoto.respostaErro != null ? remoto.respostaErro : Resposta.erro (400, "Dados inválidos.");
    }

    private static Resposta nomeRepetido ()
    {
        return Resposta.json (409, new Document ("error", "Já existe um talhão com esse nome.")
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
            if (atual == null) return Resposta.erro (401, "Não autorizado.");
            if (!atual.getPropriedadeId ().equals (requisicao.getSessao ().getPropriedadeId ()))
                return Resposta.erro (409, "A propriedade ativa mudou. Recarregue antes de salvar.");
            return this.criarProtegido (requisicao);
        }
    }

    private Resposta criarProtegido (Requisicao requisicao) throws Exception
    {
        Document corpo  = requisicao.getCorpoJson ();
        Document talhao = TalhoesHandler.lerTalhao (corpo);

        if (talhao == null)
            return TalhoesHandler.respostaTalhaoInvalido (corpo);

        talhao.append ("propriedadeId", requisicao.getSessao ().getPropriedadeId ())
              .append ("criadoEm",      talhao.getString ("atualizadoEm"));

        if (!this.talhoes.criar (talhao))
            return TalhoesHandler.nomeRepetido ();

        return Resposta.json (201, TalhoesHandler.publico (talhao));
    }

    // PUT /api/talhoes/{id}  (troca nome, cultura, hectares e status)
    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        Document anterior = this.talhoes.buscarPorId (propriedadeId, id);
        if (anterior == null)
            return Resposta.erro (404, "Não encontrado.");

        Document corpo  = requisicao.getCorpoJson ();
        Document campos = TalhoesHandler.lerTalhao (corpo);

        if (campos == null)
            return TalhoesHandler.respostaTalhaoInvalido (corpo);

        // Atividades antigas guardam o nome do talhão; impedir a troca mantém
        // a referência legível e válida também no histórico existente.
        if (!anterior.getString ("nome").equals (campos.getString ("nome")))
            for (Document atividade : this.atividades.listar (propriedadeId))
                if (anterior.getString ("nome").equalsIgnoreCase (atividade.getString ("talhao")))
                    return Resposta.json (409, new Document ("error", "Talhão possui atividades vinculadas e não pode ser renomeado."));

        if (!this.talhoes.atualizar (propriedadeId, id, campos))
            return TalhoesHandler.nomeRepetido ();

        return Resposta.json (200, TalhoesHandler.publico (this.talhoes.buscarPorId (propriedadeId, id)));
        }
    }

    // GET retorna somente eventos da propriedade do usuario autenticado.
    private Resposta historico (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id = requisicao.getParametro ("id");
        if (this.talhoes.buscarPorId (propriedadeId, id) == null) return Resposta.erro (404, "Não encontrado.");
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
        if (this.talhoes.buscarPorId (propriedadeId, id) == null) return Resposta.erro (404, "Não encontrado.");
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
