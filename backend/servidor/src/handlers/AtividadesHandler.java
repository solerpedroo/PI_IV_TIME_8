/* Miguel — RA: 25003110 */
package handlers;

import dados.RepositorioAtividades;
import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.ObjectId;
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
    public static final List<String> PRIORIDADES_VALIDAS = List.of ("Baixa", "Média", "Alta");

    private final RepositorioAtividades atividades;
    private final RepositorioSafras      safras;
    private final RepositorioTalhoes    talhoes;
    private final Sessoes               sessoes;

    public AtividadesHandler (RepositorioAtividades atividades, RepositorioTalhoes talhoes, RepositorioSafras safras,
                              Sessoes sessoes) throws Exception
    {
        if (atividades == null)
            throw new Exception ("Repositório de atividades ausente");
        if (talhoes == null)
            throw new Exception ("Repositório de talhões ausente");
        if (safras == null)
            throw new Exception ("Repositório de safras ausente");
        if (sessoes == null)
            throw new Exception ("Sessões ausentes");

        this.atividades = atividades;
        this.talhoes    = talhoes;
        this.safras     = safras;
        this.sessoes    = sessoes;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",  "/api/atividades",                  this::listar);
        roteador.adicionar ("POST", "/api/atividades",                  r -> { synchronized (this.sessoes) { return this.criar (r); } });
        roteador.adicionar ("GET",  "/api/atividades/{id}",             this::consultar);
        roteador.adicionar ("PUT",  "/api/atividades/{id}",             r -> { synchronized (this.sessoes) { return this.atualizar (r); } });
        roteador.adicionar ("POST", "/api/atividades/{id}/concluir",    r -> { synchronized (this.sessoes) { return this.concluir (r); } });
        roteador.adicionar ("POST", "/api/atividades/{id}/cancelar",    r -> { synchronized (this.sessoes) { return this.cancelar (r); } });
        roteador.adicionar ("POST", "/api/atividades/{id}/status",      r -> { synchronized (this.sessoes) { return this.alterarStatus (r); } });
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

    // O projeto define a sequência de execução; Pendente é um estado legado
    // da interface e deve ser agendado antes do início da execução.
    private static boolean transicaoPermitida (String atual, String destino)
    {
        return ("Pendente".equals (atual) && ("Agendada".equals (destino) || "Cancelada".equals (destino))) ||
               ("Agendada".equals (atual) && ("Em andamento".equals (destino) || "Cancelada".equals (destino))) ||
               ("Em andamento".equals (atual) && ("Concluída".equals (destino) || "Cancelada".equals (destino)));
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

    private Document talhaoDaPropriedadeComNome (String propriedadeId, String nome) throws Exception
    {
        for (Document talhao : this.talhoes.listar (propriedadeId))
            if (nome.equalsIgnoreCase (talhao.getString ("nome")))
                return talhao;

        return null;
    }

    private static String idDoDocumento (Document documento)
    {
        Object id = documento.get ("_id");
        return id instanceof ObjectId ? ((ObjectId) id).toHexString () : String.valueOf (id);
    }

    // Uma safra só pode ser associada se incluir o talhão escolhido na atividade.
    // Assim, cultura e ciclo são derivados de uma única referência consistente.
    private Document safraDoTalhao (String propriedadeId, String safraId, String talhao, Validacao validacao) throws Exception
    {
        if (safraId == null || safraId.isBlank ())
            return null;

        Document safra = this.safras.buscarPorId (propriedadeId, safraId);
        if (safra == null)
        {
            validacao.erro ("safraId", "Selecione uma safra da propriedade ativa.");
            return null;
        }

        Document registroTalhao = this.talhaoDaPropriedadeComNome (propriedadeId, talhao);
        if (registroTalhao == null || !safra.getList ("talhoes", String.class).contains (AtividadesHandler.idDoDocumento (registroTalhao)))
        {
            validacao.erro ("safraId", "A safra selecionada não contém o talhão informado.");
            return null;
        }

        return safra;
    }

    // Campos de planejamento são opcionais, mas nunca aceitam tipos inesperados
    // nem texto além do limite que a interface também apresenta ao usuário.
    private static String textoOpcional (Document corpo, Validacao validacao, String campo, int maximo)
    {
        if (!corpo.containsKey (campo))
            return null;

        Object valor = corpo.get (campo);
        if (!(valor instanceof String))
        {
            validacao.erro (campo, "Valor inválido.");
            return null;
        }

        String texto = ((String) valor).trim ();
        if (texto.length () > maximo)
        {
            validacao.erro (campo, "Use no máximo " + maximo + " caracteres.");
            return null;
        }

        return texto;
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

        String status      = AtividadesHandler.textoOpcional (corpo, v, "status", 30);
        String responsavel = AtividadesHandler.textoOpcional (corpo, v, "responsavel", 100);
        String prioridade  = AtividadesHandler.textoOpcional (corpo, v, "prioridade", 20);
        String horario     = AtividadesHandler.textoOpcional (corpo, v, "horario", 5);
        String safraId     = AtividadesHandler.textoOpcional (corpo, v, "safraId", 60);

        if (status == null || status.isBlank ())
            status = "Pendente";
        else if (!"Pendente".equals (status) && !"Agendada".equals (status))
            v.erro ("status", "A atividade deve começar pendente ou agendada.");

        if (tipo != null && !TIPOS_VALIDOS.contains (tipo))
            v.erro ("tipo", "Selecione um tipo válido.");
        if (prioridade != null && !prioridade.isBlank () && !PRIORIDADES_VALIDAS.contains (prioridade))
            v.erro ("prioridade", "Selecione uma prioridade válida.");
        if (horario != null && !horario.isBlank () && !horario.matches ("([01]\\d|2[0-3]):[0-5]\\d"))
            v.erro ("horario", "Informe um horário válido.");

        if (talhao != null && !this.talhaoDaPropriedade (requisicao.getSessao ().getPropriedadeId (), talhao))
            v.erro ("talhao", "Selecione um talhão da propriedade ativa.");

        Document safra = talhao == null ? null : this.safraDoTalhao (requisicao.getSessao ().getPropriedadeId (), safraId, talhao, v);

        if (v.temErros ())
            return v.resposta ();

        Sessao sessao = requisicao.getSessao ();
        Document nova = new Document ();
        // A identidade é sempre gerada pelo servidor, nunca escolhida pelo cliente.
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
        if (safra != null)
        {
            nova.put ("safraId", safraId);
            nova.put ("safra", safra.getString ("nome"));
            nova.put ("cultura", safra.getString ("cultura"));
        }

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
        String responsavel = AtividadesHandler.textoOpcional (corpo, v, "responsavel", 100);
        String prioridade  = AtividadesHandler.textoOpcional (corpo, v, "prioridade", 20);
        String horario     = AtividadesHandler.textoOpcional (corpo, v, "horario", 5);
        String safraId     = AtividadesHandler.textoOpcional (corpo, v, "safraId", 60);

        if (tipo != null && !TIPOS_VALIDOS.contains (tipo))
            v.erro ("tipo", "Selecione um tipo válido.");
        if (prioridade != null && !prioridade.isBlank () && !PRIORIDADES_VALIDAS.contains (prioridade))
            v.erro ("prioridade", "Selecione uma prioridade válida.");
        if (horario != null && !horario.isBlank () && !horario.matches ("([01]\\d|2[0-3]):[0-5]\\d"))
            v.erro ("horario", "Informe um horário válido.");
        if (talhao != null && !this.talhaoDaPropriedade (propriedadeId, talhao))
            v.erro ("talhao", "Selecione um talhão da propriedade ativa.");

        // Ao omitir a safra na edição, preservamos e validamos o vínculo já
        // existente contra o talhão novo, sem permitir uma referência inválida.
        if (!corpo.containsKey ("safraId"))
            safraId = atual.getString ("safraId");
        Document safra = talhao == null ? null : this.safraDoTalhao (propriedadeId, safraId, talhao, v);

        if (v.temErros ())
            return v.resposta ();

        Document campos = new Document ();
        campos.put ("nome", nome);
        campos.put ("tipo", tipo);
        campos.put ("talhao", talhao);
        campos.put ("data", data);
        if (corpo.containsKey ("responsavel"))
            campos.put ("responsavel", responsavel);
        if (corpo.containsKey ("prioridade"))
            campos.put ("prioridade", prioridade);
        if (corpo.containsKey ("horario"))
            campos.put ("horario", horario);
        if (corpo.containsKey ("safraId"))
        {
            campos.put ("safraId", safra == null ? "" : safraId);
            campos.put ("safra", safra == null ? "" : safra.getString ("nome"));
            campos.put ("cultura", safra == null ? "" : safra.getString ("cultura"));
        }

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
        if (!AtividadesHandler.transicaoPermitida (statusAtual, "Concluída"))
            return Resposta.json (409, new Document ("error", "Inicie a atividade antes de concluí-la."));

        Document corpo = null;
        try { corpo = requisicao.getCorpoJson (); } catch (Exception ignora) {}
        String desc = corpo != null ? corpo.getString ("descricao") : null;
        if (desc == null || desc.isBlank ())
            desc = "Atividade concluída com sucesso";

        try { this.atividades.mudarStatus (propriedadeId, id, statusAtual, "Concluída", "conclusao", desc); }
        catch (IllegalStateException conflito) { return Resposta.json (409, new Document ("error", "Estado alterado; consulte novamente.")); }
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

        try { this.atividades.mudarStatus (propriedadeId, id, statusAtual, "Cancelada", "cancelamento", motivo); }
        catch (IllegalStateException conflito) { return Resposta.json (409, new Document ("error", "Estado alterado; consulte novamente.")); }
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

        if (novoStatus != null && !STATUS_VALIDOS.contains (novoStatus))
            v.erro ("status", "Status inválido.");

        String desc = AtividadesHandler.textoOpcional (corpo, v, "descricao", 500);
        String motivo = AtividadesHandler.textoOpcional (corpo, v, "motivo", 500);

        if (v.temErros ())
            return v.resposta ();

        if (!AtividadesHandler.transicaoPermitida (statusAtual, novoStatus))
            return Resposta.json (409, new Document ("error", "Transição de status não permitida."));

        String acao = "mudanca_status";

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
            if (motivo != null && !motivo.isBlank ()) desc = motivo;
            if (desc == null || desc.isBlank ()) desc = "Atividade cancelada operacionalmente";
        }
        else if (desc == null || desc.isBlank ())
            desc = "Status alterado para " + novoStatus;

        try { this.atividades.mudarStatus (propriedadeId, id, statusAtual, novoStatus, acao, desc); }
        catch (IllegalStateException conflito) { return Resposta.json (409, new Document ("error", "Estado alterado; consulte novamente.")); }
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
