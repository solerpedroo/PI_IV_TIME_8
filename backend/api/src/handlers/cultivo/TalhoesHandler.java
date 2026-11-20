/* Miguel — RA: 25003110 */
/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers.cultivo;

import dados.operacao.RepositorioAtividades;
import dados.cultivo.RepositorioCulturas;
import dados.cultivo.RepositorioTalhoes;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import servicos.integracao.ClienteDeAlgoritmos;
import util.comum.Json;
import util.comum.Validacao;

/*
    Talhões (áreas de cultivo) da propriedade de quem está logado.

    Quem chama: a tela de talhões e o histórico do talhão. O id da
    propriedade não vem no corpo: tudo usa a propriedade ativa da sessão.
    CulturasHandler é consultado antes de gravar, para o nome da cultura
    existir no catálogo.

    Rotas autenticadas (sem token o roteador responde 401):
      GET  /api/talhoes?cultura=&q=
      GET  /api/talhoes/resumo
      GET  /api/talhoes/{id}/historico
      POST /api/talhoes/{id}/historico
      POST /api/talhoes
      PUT  /api/talhoes/{id}
    Não há DELETE nesta entrega. /resumo e /{id}/historico são registrados
    como caminhos próprios, para "resumo" não ser lido como um id.

    O que persiste: o documento do talhão (nome, cultura, hectares, status,
    safra) na propriedade, e os eventos do histórico (tipo e descrição).
    Renomear é recusado com 409 se alguma atividade ainda cita o nome antigo.
*/
public class TalhoesHandler
{
    public static final List<String> STATUS = List.of ("Ativo", "Irrigação pendente", "Em preparo");

    private final RepositorioTalhoes    talhoes;
    private final RepositorioAtividades atividades;
    private final RepositorioCulturas   culturas;
    private final seguranca.Sessoes     sessoes;

    /*
        Talhões, atividades, culturas e sessões são obrigatórios.
        Atividades entram para impedir renomear talhão que já tem lançamento.
        Sessões entram na trava de criar e de atualizar.
    */
    public TalhoesHandler (RepositorioTalhoes talhoes, RepositorioAtividades atividades,
                         RepositorioCulturas culturas, seguranca.Sessoes sessoes) throws Exception
    {
        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        this.talhoes = talhoes;
        if (atividades == null) throw new Exception ("Repositorio de atividades ausente");
        this.atividades = atividades;
        if (culturas == null) throw new Exception ("Repositorio de culturas ausente");
        this.culturas = culturas;
        if (sessoes == null) throw new Exception ("Sessoes ausentes");
        this.sessoes = sessoes;
    }

    /*
        /resumo e /{id}/historico vêm antes de um eventual /{id} solto.
        O casamento do roteador é por segmento, então "resumo" não cai num id.
    */
    public void registrar (Roteador roteador) throws Exception
    {
        // /resumo antes de /{id} para não ser confundido com um id
        roteador.adicionar ("GET",  "/api/talhoes/resumo", this::resumo);
        roteador.adicionar ("GET",  "/api/talhoes/{id}/historico", this::historico);
        roteador.adicionar ("POST", "/api/talhoes/{id}/historico", this::registrarHistorico);
        roteador.adicionar ("GET",  "/api/talhoes",        this::listar);
        roteador.adicionar ("POST", "/api/talhoes",        this::criar);
        roteador.adicionar ("PUT",  "/api/talhoes/{id}",   this::atualizar);
    }

    /*
        JSON do talhão sem propriedadeId nem o nome já normalizado para busca.
        Esses campos são internos.
    */
    private static Document publico (Document talhao)
    {
        return Json.publico (talhao, "propriedadeId", "nomeNormalizado");
    }

    /*
        GET /api/talhoes?cultura=Soja&q=norte
        Lista os talhões da propriedade ativa.
        cultura em branco ou "todos" não filtra. q é comparado, em minúsculas,
        com nomeNormalizado.
        200 com o array filtrado, possivelmente vazio. Não há 400 de query.
    */
    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String cultura = requisicao.getConsulta ("cultura");
        String busca   = requisicao.getConsulta ("q");

        // "todos" e vazio significam a lista inteira, não um nome de cultura.
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

    /*
        GET /api/talhoes/resumo
        Manda a lista da propriedade ao ServerSocket (CalculadoraDeTalhoes)
        e devolve o JSON que ele calculou. O status sai de lá.
        Esta API não recalcula hectares nem contagens.
    */
    private Resposta resumo (Requisicao requisicao) throws Exception
    {
        return ClienteDeAlgoritmos.resumoTalhoes (this.talhoes.listar (requisicao.getSessao ().getPropriedadeId ()));
    }

    /*
        Valida POST e PUT no ServerSocket (ValidadorDeTalhao).
        Devolve null se o corpo foi recusado; o chamador então pede o 400.
        Unicidade do nome não é daqui: fica no repositório, como 409.
    */
    private static Document lerTalhao (Document corpo) throws Exception
    {
        ClienteDeAlgoritmos.ResultadoTalhao remoto = ClienteDeAlgoritmos.validarTalhao (corpo);

        if (remoto.respostaErro != null)
            return null;

        return new Document (remoto.campos).append ("atualizadoEm", Instant.now ().toString ());
    }

    /*
        Repete a validação remota para obter o 400 com os campos.
        Se essa segunda chamada não trouxer o detalhe, responde 400 genérico.
    */
    private static Resposta respostaTalhaoInvalido (Document corpo) throws Exception
    {
        ClienteDeAlgoritmos.ResultadoTalhao remoto = ClienteDeAlgoritmos.validarTalhao (corpo);
        return remoto.respostaErro != null ? remoto.respostaErro : Resposta.erro (400, "Dados inválidos.");
    }

    /*
        409 usado em criar e em atualizar quando o nome normalizado já existe
        em outro talhão da mesma propriedade.
    */
    private static Resposta nomeRepetido ()
    {
        return Resposta.json (409, new Document ("error", "Já existe um talhão com esse nome.")
                                       .append ("fields", new Document ("nome", "Já existe um talhão com esse nome.")));
    }

    /*
        POST /api/talhoes
        Cria o talhão na propriedade que a sessão ainda aponta.
        synchronized (sessoes): a mesma trava de selecionar e de excluir fazenda.
        401: o token sumiu enquanto esta chamada esperava a trava.
        409: a propriedade ativa mudou depois que a requisição foi lida.
        Os outros status estão em criarProtegido.
    */
    private Resposta criar (Requisicao requisicao) throws Exception
    {
        // Serializa com seleção/exclusão: uma requisição que capturou o contexto
        // anterior não pode criar o primeiro talhão depois de excluir a fazenda.
        synchronized (this.sessoes)
        {
            seguranca.Sessao atual = this.sessoes.buscar (requisicao.getToken ());
            if (atual == null) return Resposta.erro (401, "Não autorizado.");
            // 409: outra aba trocou a fazenda; o corpo foi montado para a anterior.
            if (!atual.getPropriedadeId ().equals (requisicao.getSessao ().getPropriedadeId ()))
                return Resposta.erro (409, "A propriedade ativa mudou. Recarregue antes de salvar.");
            return this.criarProtegido (requisicao);
        }
    }

    /*
        Corpo do POST, já com a sessão conferida.
        400: validador remoto recusou o corpo, ou a cultura não está no catálogo.
        409: já existe talhão com esse nome na propriedade.
        201: talhão público, com propriedadeId e nomeNormalizado ocultos.
    */
    private Resposta criarProtegido (Requisicao requisicao) throws Exception
    {
        Document corpo  = requisicao.getCorpoJson ();
        Document talhao = TalhoesHandler.lerTalhao (corpo);

        if (talhao == null)
            return TalhoesHandler.respostaTalhaoInvalido (corpo);

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String titular       = requisicao.getSessao ().getUsuarioId ();
        if (!this.culturas.existeNome (propriedadeId, talhao.getString ("cultura")))
            return CulturasHandler.erroCulturaNaoCadastrada ();

        /*
            O validador remoto já devolveu nome, cultura, hectares, status e atualizadoEm.
            Estes dois append completam o documento da coleção talhoes:

            {
              "nome", "cultura", "hectares", "status", "atualizadoEm",
              "propriedadeId": fazenda da sessão,   // o cliente não escolhe
              "criadoEm": o mesmo instante de atualizadoEm
            }

            O repositório ainda gera _id e nomeNormalizado (nome em minúsculas,
            para o índice único por fazenda). A resposta 201 esconde esses dois.
        */
        talhao.append ("propriedadeId", propriedadeId)
              .append ("criadoEm",      talhao.getString ("atualizadoEm"));

        if (!this.talhoes.criar (talhao))
            return TalhoesHandler.nomeRepetido ();

        return Resposta.json (201, TalhoesHandler.publico (talhao));
    }

    /*
        PUT /api/talhoes/{id}
        Troca nome, cultura, hectares e status do talhão desta propriedade.
        synchronized (sessoes): não grava em paralelo com login, seleção ou
        exclusão da fazenda, que usam o mesmo monitor.
        404: o id não existe nesta propriedade.
        400: corpo recusado pelo validador, ou cultura fora do catálogo.
        409: o nome mudou e alguma atividade ainda cita o nome antigo,
        ou o nome novo já pertence a outro talhão.
        200: documento relido.
    */
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

        if (!this.culturas.existeNome (propriedadeId, campos.getString ("cultura")))
            return CulturasHandler.erroCulturaNaoCadastrada ();

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

    /*
        GET /api/talhoes/{id}/historico
        Eventos deste talhão, só da propriedade da sessão.
        404: o talhão não existe nessa propriedade.
        200: lista sem propriedadeId e sem talhaoId.
    */
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

    /*
        POST /api/talhoes/{id}/historico
        Grava um evento. O cliente informa tipo e descrição; propriedade e
        talhão vêm da sessão e da URL, não do corpo.
        404: talhão inexistente nesta propriedade.
        400: tipo ou descrição ausentes, longos demais, ou tipo fora de
        atividade, ocorrencia e custo.
        201: o evento gravado, com criadoEm preenchido aqui.
    */
    private Resposta registrarHistorico (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id = requisicao.getParametro ("id");
        if (this.talhoes.buscarPorId (propriedadeId, id) == null) return Resposta.erro (404, "Não encontrado.");
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        String tipo = validacao.texto ("tipo", "Informe o tipo.", 20);
        String descricao = validacao.texto ("descricao", "Informe a descricao.", 240);
        // Lista fechada: outro texto, mesmo curto, é 400 no campo tipo.
        if (tipo != null && !List.of ("atividade", "ocorrencia", "custo").contains (tipo)) validacao.erro ("tipo", "Tipo invalido.");
        if (validacao.temErros ()) return validacao.resposta ();
        Document evento = new Document ("tipo", tipo).append ("descricao", descricao).append ("criadoEm", Instant.now ().toString ());
        this.talhoes.criarHistorico (propriedadeId, id, evento);
        return Resposta.json (201, evento);
    }
}
