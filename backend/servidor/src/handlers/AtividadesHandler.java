/* Miguel — RA: 25003110 */
package handlers;

import dados.RepositorioAtividades;
import dados.RepositorioTalhoes;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import seguranca.Sessao;
import seguranca.Sessoes;
import util.Json;
import util.Validacao;

/**
 * Manipulador HTTP das rotas de Atividades operacionais e linha do tempo de histórico.
 *
 * Permite planejar, consultar, editar, concluir, cancelar e auditar a timeline
 * de eventos de cada atividade. Apenas atividades não terminais (Pendente, Agendada,
 * Em andamento) podem ter status alterado; atividades concluídas ou canceladas
 * permanecem preservadas para auditoria e histórico persistente.
 */
public class AtividadesHandler
{
    public static final List<String> STATUS_VALIDOS = List.of ("Pendente", "Agendada", "Em andamento", "Concluída", "Cancelada");
    public static final List<String> TIPOS_VALIDOS  = List.of ("Aplicação", "Irrigação", "Colheita", "Manutenção", "Manejo");

    private final RepositorioAtividades atividades;
    private final RepositorioTalhoes    talhoes;
    private final Sessoes               sessoes;

    public AtividadesHandler (RepositorioAtividades atividades, RepositorioTalhoes talhoes, Sessoes sessoes) throws Exception
    {
        if (atividades == null)
            throw new Exception ("Repositório de atividades ausente");
        if (talhoes == null)
            throw new Exception ("Repositório de talhões ausente");
        if (sessoes == null)
            throw new Exception ("Sessões ausentes");

        this.atividades = atividades;
        this.talhoes    = talhoes;
        this.sessoes    = sessoes;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",  "/api/atividades",                  this::listar);
        roteador.adicionar ("POST", "/api/atividades",                  this::criar);
        roteador.adicionar ("GET",  "/api/atividades/{id}",             this::consultar);
        roteador.adicionar ("PUT",  "/api/atividades/{id}",             this::atualizar);
        roteador.adicionar ("POST", "/api/atividades/{id}/concluir",    this::concluir);
        roteador.adicionar ("POST", "/api/atividades/{id}/cancelar",    this::cancelar);
        roteador.adicionar ("POST", "/api/atividades/{id}/status",      this::alterarStatus);
        roteador.adicionar ("GET",  "/api/atividades/{id}/historico",   this::historico);
    }

    private static Document publico (Document atividade)
    {
        return Json.publico (atividade, "propriedadeId", "usuarioId");
    }

    private static boolean statusEquivalente (String s1, String s2)
    {
        if (s1 == null || s2 == null)
            return false;
        String n1 = java.text.Normalizer.normalize (s1, java.text.Normalizer.Form.NFD).replaceAll ("\\p{M}", "").toLowerCase (Locale.ROOT);
        String n2 = java.text.Normalizer.normalize (s2, java.text.Normalizer.Form.NFD).replaceAll ("\\p{M}", "").toLowerCase (Locale.ROOT);
        return n1.equals (n2);
    }

    // O formulário trabalha com o nome do talhão, mas o vínculo só é válido
    // quando o talhão pertence ao contexto de propriedade da sessão atual.
    private boolean talhaoDaPropriedade (String propriedadeId, String nome) throws Exception
    {
        for (Document talhao : this.talhoes.listar (propriedadeId))
            if (nome.equalsIgnoreCase (talhao.getString ("nome")))
                return true;

        return false;
    }

    // GET /api/atividades?status=&tipo=&talhao=&q=
    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String status = requisicao.getConsulta ("status");
        String tipo   = requisicao.getConsulta ("tipo");
        String talhao = requisicao.getConsulta ("talhao");
        String busca  = requisicao.getConsulta ("q");

        if (status != null && (status.isBlank () || status.equalsIgnoreCase ("todos")))
            status = null;
        if (tipo != null && (tipo.isBlank () || tipo.equalsIgnoreCase ("todos")))
            tipo = null;
        if (talhao != null && (talhao.isBlank () || talhao.equalsIgnoreCase ("todos")))
            talhao = null;

        if (busca != null)
            busca = busca.trim ().toLowerCase (Locale.ROOT);

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        ArrayList<Document> ret = new ArrayList<Document> ();

        for (Document a : this.atividades.listar (propriedadeId))
        {
            if (status != null && !statusEquivalente (status, a.getString ("status")))
                continue;

            if (tipo != null && !tipo.equalsIgnoreCase (a.getString ("tipo")))
                continue;

            if (talhao != null && !talhao.equalsIgnoreCase (a.getString ("talhao")))
                continue;

            if (busca != null)
            {
                String nome = (a.getString ("nome") != null ? a.getString ("nome") : "").toLowerCase (Locale.ROOT);
                String t = (a.getString ("talhao") != null ? a.getString ("talhao") : "").toLowerCase (Locale.ROOT);
                if (!nome.contains (busca) && !t.contains (busca))
                    continue;
            }

            ret.add (AtividadesHandler.publico (a));
        }

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/atividades/{id}
    private Resposta consultar (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();

        Document a = this.atividades.buscarPorId (propriedadeId, id);
        if (a == null)
            return Resposta.json (404, new Document ("error", "Atividade não encontrada."));

        return Resposta.json (200, AtividadesHandler.publico (a));
    }

    // POST /api/atividades
    private Resposta criar (Requisicao requisicao) throws Exception
    {
        Document corpo = requisicao.getCorpoJson ();
        Validacao v = new Validacao (corpo);

        String nome   = v.texto ("nome",   "Informe o nome da atividade.", 100);
        String tipo   = v.texto ("tipo",   "Selecione o tipo.",            50);
        String talhao = v.texto ("talhao", "Selecione o talhão.",          60);
        String data   = v.texto ("data",   "Informe a data.",              30);

        String status      = corpo.getString ("status");
        String responsavel = corpo.getString ("responsavel");
        String prioridade  = corpo.getString ("prioridade");
        String horario     = corpo.getString ("horario");

        if (status == null || status.isBlank ())
            status = "Pendente";
        else if (!STATUS_VALIDOS.contains (status))
            v.erro ("status", "Status inválido.");

        if (tipo != null && !TIPOS_VALIDOS.contains (tipo))
            v.erro ("tipo", "Selecione um tipo válido.");

        if (talhao != null && !this.talhaoDaPropriedade (requisicao.getSessao ().getPropriedadeId (), talhao))
            v.erro ("talhao", "Selecione um talhão da propriedade ativa.");

        if (v.temErros ())
            return v.resposta ();

        Sessao sessao = requisicao.getSessao ();
        Document nova = new Document ();
        String idCorpo = corpo.getString ("id");
        if (idCorpo != null && !idCorpo.isBlank ())
            nova.put ("id", idCorpo.trim ());
        nova.put ("usuarioId", sessao.getUsuarioId ());
        nova.put ("propriedadeId", sessao.getPropriedadeId ());
        nova.put ("nome", nome);
        nova.put ("tipo", tipo);
        nova.put ("talhao", talhao);
        nova.put ("data", data);
        nova.put ("status", status);
        if (responsavel != null && !responsavel.isBlank ())
            nova.put ("responsavel", responsavel.trim ());
        if (prioridade != null && !prioridade.isBlank ())
            nova.put ("prioridade", prioridade.trim ());
        if (horario != null && !horario.isBlank ())
            nova.put ("horario", horario.trim ());

        this.atividades.criar (nova);

        return Resposta.json (201, AtividadesHandler.publico (nova));
    }

    // PUT /api/atividades/{id}
    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();

        Document atual = this.atividades.buscarPorId (propriedadeId, id);
        if (atual == null)
            return Resposta.json (404, new Document ("error", "Atividade não encontrada."));

        String statusAtual = atual.getString ("status");
        if ("Concluída".equalsIgnoreCase (statusAtual) || "Cancelada".equalsIgnoreCase (statusAtual))
            return Resposta.json (409, new Document ("error", "Atividades concluídas ou canceladas não podem ser alteradas."));

        Document corpo = requisicao.getCorpoJson ();
        Validacao v = new Validacao (corpo);

        String nome   = v.texto ("nome",   "Informe o nome da atividade.", 100);
        String tipo   = v.texto ("tipo",   "Selecione o tipo.",            50);
        String talhao = v.texto ("talhao", "Selecione o talhão.",          60);
        String data   = v.texto ("data",   "Informe a data.",              30);

        if (tipo != null && !TIPOS_VALIDOS.contains (tipo))
            v.erro ("tipo", "Selecione um tipo válido.");
        if (talhao != null && !this.talhaoDaPropriedade (propriedadeId, talhao))
            v.erro ("talhao", "Selecione um talhão da propriedade ativa.");

        if (v.temErros ())
            return v.resposta ();

        Document campos = new Document ();
        campos.put ("nome", nome);
        campos.put ("tipo", tipo);
        campos.put ("talhao", talhao);
        campos.put ("data", data);
        if (corpo.containsKey ("responsavel"))
            campos.put ("responsavel", corpo.getString ("responsavel"));
        if (corpo.containsKey ("prioridade"))
            campos.put ("prioridade", corpo.getString ("prioridade"));
        if (corpo.containsKey ("horario"))
            campos.put ("horario", corpo.getString ("horario"));

        this.atividades.atualizar (propriedadeId, id, campos);
        Document atualizado = this.atividades.buscarPorId (propriedadeId, id);

        return Resposta.json (200, AtividadesHandler.publico (atualizado));
    }

    // POST /api/atividades/{id}/concluir
    private Resposta concluir (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();

        Document atual = this.atividades.buscarPorId (propriedadeId, id);
        if (atual == null)
            return Resposta.json (404, new Document ("error", "Atividade não encontrada."));

        String statusAtual = atual.getString ("status");
        if ("Concluída".equalsIgnoreCase (statusAtual))
            return Resposta.json (409, new Document ("error", "Esta atividade já está concluída."));
        if ("Cancelada".equalsIgnoreCase (statusAtual))
            return Resposta.json (409, new Document ("error", "Atividade cancelada não pode ser concluída."));

        Document corpo = null;
        try { corpo = requisicao.getCorpoJson (); } catch (Exception ignora) {}
        String desc = corpo != null ? corpo.getString ("descricao") : null;
        if (desc == null || desc.isBlank ())
            desc = "Atividade concluída com sucesso";

        this.atividades.mudarStatus (propriedadeId, id, "Concluída", "conclusao", desc);
        Document atualizado = this.atividades.buscarPorId (propriedadeId, id);

        return Resposta.json (200, AtividadesHandler.publico (atualizado));
    }

    // POST /api/atividades/{id}/cancelar
    private Resposta cancelar (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();

        Document atual = this.atividades.buscarPorId (propriedadeId, id);
        if (atual == null)
            return Resposta.json (404, new Document ("error", "Atividade não encontrada."));

        String statusAtual = atual.getString ("status");
        if ("Concluída".equalsIgnoreCase (statusAtual))
            return Resposta.json (409, new Document ("error", "Atividade já concluída não pode ser cancelada."));
        if ("Cancelada".equalsIgnoreCase (statusAtual))
            return Resposta.json (409, new Document ("error", "Esta atividade já está cancelada."));

        Document corpo = null;
        try { corpo = requisicao.getCorpoJson (); } catch (Exception ignora) {}
        String motivo = corpo != null ? corpo.getString ("motivo") : null;
        if (motivo == null || motivo.isBlank ())
            motivo = corpo != null ? corpo.getString ("descricao") : null;
        if (motivo == null || motivo.isBlank ())
            motivo = "Atividade cancelada operacionalmente";

        this.atividades.mudarStatus (propriedadeId, id, "Cancelada", "cancelamento", motivo);
        Document atualizado = this.atividades.buscarPorId (propriedadeId, id);

        return Resposta.json (200, AtividadesHandler.publico (atualizado));
    }

    // POST /api/atividades/{id}/status
    private Resposta alterarStatus (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();

        Document atual = this.atividades.buscarPorId (propriedadeId, id);
        if (atual == null)
            return Resposta.json (404, new Document ("error", "Atividade não encontrada."));

        String statusAtual = atual.getString ("status");
        if ("Concluída".equalsIgnoreCase (statusAtual) || "Cancelada".equalsIgnoreCase (statusAtual))
            return Resposta.json (409, new Document ("error", "Atividades finalizadas não permitem alteração de status."));

        Document corpo = requisicao.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        String novoStatus = v.texto ("status", "Selecione o status.", 30);

        if (!STATUS_VALIDOS.contains (novoStatus))
            v.erro ("status", "Status inválido.");

        if (v.temErros ())
            return v.resposta ();

        String acao = "mudanca_status";
        String desc = corpo.getString ("descricao");

        // A rota unica de status preserva os eventos de negocio dos atalhos
        // antigos: concluir e cancelar continuam auditaveis na mesma timeline.
        if ("Concluída".equals (novoStatus))
        {
            acao = "conclusao";
            if (desc == null || desc.isBlank ()) desc = "Atividade concluída com sucesso";
        }
        else if ("Cancelada".equals (novoStatus))
        {
            acao = "cancelamento";
            String motivo = corpo.getString ("motivo");
            if (motivo != null && !motivo.isBlank ()) desc = motivo;
            if (desc == null || desc.isBlank ()) desc = "Atividade cancelada operacionalmente";
        }
        else if (desc == null || desc.isBlank ())
            desc = "Status alterado para " + novoStatus;

        this.atividades.mudarStatus (propriedadeId, id, novoStatus, acao, desc);
        Document atualizado = this.atividades.buscarPorId (propriedadeId, id);

        return Resposta.json (200, AtividadesHandler.publico (atualizado));
    }

    // GET /api/atividades/{id}/historico
    private Resposta historico (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();

        List<Document> eventos = this.atividades.listarHistorico (propriedadeId, id);
        if (eventos == null)
            return Resposta.json (404, new Document ("error", "Atividade não encontrada."));

        return Resposta.json (200, Json.escrever (eventos));
    }
}
