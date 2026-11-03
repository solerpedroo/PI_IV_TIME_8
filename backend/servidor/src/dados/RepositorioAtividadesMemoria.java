/* Miguel — RA: 25003110 */
package dados;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * Implementação em memória do repositório de atividades para testes e execução sem banco.
 *
 * Mantém atividades e seus respectivos históricos isolados por propriedade.
 * Todos os métodos são sincronizados para proteger a concorrência e trabalham
 * com cópias defensivas de documentos BSON.
 */
public class RepositorioAtividadesMemoria implements RepositorioAtividades
{
    private final LinkedHashMap<String, Document> atividades = new LinkedHashMap<String, Document> ();
    private final LinkedHashMap<String, ArrayList<Document>> historicos = new LinkedHashMap<String, ArrayList<Document>> ();

    @Override
    public synchronized List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document atividade : this.atividades.values ())
        {
            if (propriedadeId.equals (atividade.getString ("propriedadeId")))
                ret.add (new Document (atividade));
        }

        return ret;
    }

    @Override
    public synchronized List<Document> listarParaCalendario (String propriedadeId, String de, String ate) throws Exception
    {
        if (propriedadeId == null || de == null || ate == null)
            throw new Exception ("Parâmetros do calendário ausentes");

        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document atividade : this.atividades.values ())
        {
            if (!propriedadeId.equals (atividade.getString ("propriedadeId")))
                continue;
            if (!RepositorioAtividadesMemoria.candidataCalendario (atividade.getString ("data"), de, ate))
                continue;
            ret.add (new Document (atividade));
        }

        return ret;
    }

    // ISO no intervalo, protótipo "12 ago" ou texto que não é ISO (semData); ISO fora fica de fora.
    static boolean candidataCalendario (String data, String de, String ate)
    {
        if (data == null)
            return true;

        String texto = data.trim ();
        if (texto.matches ("\\d{4}-\\d{2}-\\d{2}"))
            return texto.compareTo (de) >= 0 && texto.compareTo (ate) <= 0;

        return true;
    }

    @Override
    public synchronized Document buscarPorId (String propriedadeId, String id) throws Exception
    {
        if (propriedadeId == null || id == null)
            throw new Exception ("Parâmetros obrigatórios ausentes");

        Document atividade = this.atividades.get (id);
        if (atividade == null || !propriedadeId.equals (atividade.getString ("propriedadeId")))
            return null;

        return new Document (atividade);
    }

    @Override
    public synchronized boolean criar (Document atividade) throws Exception
    {
        if (atividade == null)
            throw new Exception ("Atividade ausente");

        String propriedadeId = atividade.getString ("propriedadeId");
        if (propriedadeId == null || propriedadeId.isBlank ())
            throw new Exception ("Propriedade ausente na atividade");

        String idStr = atividade.getString ("id");
        if (idStr == null || idStr.isBlank ())
        {
            ObjectId id = new ObjectId ();
            idStr = id.toHexString ();
        }
        atividade.put ("_id", idStr);
        atividade.put ("id", idStr);

        if (atividade.getString ("status") == null || atividade.getString ("status").isBlank ())
            atividade.put ("status", "Pendente");

        Instant agora = Instant.now ();
        atividade.put ("criadaEm", agora.toString ());

        this.atividades.put (idStr, new Document (atividade));

        // Evento inicial 0 na linha do tempo
        Document eventoZero = new Document ();
        eventoZero.put ("atividadeId", idStr);
        eventoZero.put ("propriedadeId", propriedadeId);
        eventoZero.put ("ordem", 0);
        eventoZero.put ("registradoEm", agora.toString ());
        eventoZero.put ("status", atividade.getString ("status"));
        eventoZero.put ("acao", "criacao");
        eventoZero.put ("descricao", "Registro inicial da atividade");

        ArrayList<Document> listaEventos = new ArrayList<Document> ();
        listaEventos.add (eventoZero);
        this.historicos.put (idStr, listaEventos);

        return true;
    }

    @Override
    public synchronized boolean atualizar (String propriedadeId, String id, Document campos) throws Exception
    {
        Document atual = this.buscarPorId (propriedadeId, id);
        if (atual == null)
            return false;

        String statusAtual = atual.getString ("status");
        if ("Concluída".equalsIgnoreCase (statusAtual) || "Cancelada".equalsIgnoreCase (statusAtual))
            throw new IllegalStateException ("Atividades concluídas ou canceladas não podem ser alteradas.");

        Document atividade = this.atividades.get (id);
        if (campos.containsKey ("nome"))
            atividade.put ("nome", campos.getString ("nome"));
        if (campos.containsKey ("tipo"))
            atividade.put ("tipo", campos.getString ("tipo"));
        if (campos.containsKey ("talhao"))
            atividade.put ("talhao", campos.getString ("talhao"));
        if (campos.containsKey ("data"))
            atividade.put ("data", campos.getString ("data"));
        if (campos.containsKey ("horario"))
            atividade.put ("horario", campos.getString ("horario"));
        if (campos.containsKey ("responsavel"))
            atividade.put ("responsavel", campos.getString ("responsavel"));
        if (campos.containsKey ("prioridade"))
            atividade.put ("prioridade", campos.getString ("prioridade"));
        if (campos.containsKey ("safraId"))
            atividade.put ("safraId", campos.getString ("safraId"));
        if (campos.containsKey ("safra"))
            atividade.put ("safra", campos.getString ("safra"));
        if (campos.containsKey ("cultura"))
            atividade.put ("cultura", campos.getString ("cultura"));

        return true;
    }

    @Override
    public synchronized boolean mudarStatus (String propriedadeId, String id, String statusEsperado, String novoStatus, String acao, String descricao) throws Exception
    {
        Document atual = this.buscarPorId (propriedadeId, id);
        if (atual == null)
            return false;

        String statusAtual = atual.getString ("status");
        if (!statusAtual.equals (statusEsperado))
            throw new IllegalStateException ("Estado alterado por outra operação.");
        if ("Concluída".equalsIgnoreCase (statusAtual) || "Cancelada".equalsIgnoreCase (statusAtual))
            throw new IllegalStateException ("Atividade finalizada (" + statusAtual + ") não permite alteração de status.");

        if (statusAtual.equalsIgnoreCase (novoStatus))
            return true; // Sem alteração real

        Document atividade = this.atividades.get (id);
        atividade.put ("status", novoStatus);

        ArrayList<Document> lista = this.historicos.computeIfAbsent (id, k -> new ArrayList<Document> ());
        Instant agora = Instant.now ();

        Document evento = new Document ();
        evento.put ("atividadeId", id);
        evento.put ("propriedadeId", propriedadeId);
        evento.put ("ordem", lista.size ());
        evento.put ("registradoEm", agora.toString ());
        evento.put ("status", novoStatus);
        evento.put ("acao", acao != null && !acao.isBlank () ? acao : "mudanca_status");
        evento.put ("descricao", descricao != null && !descricao.isBlank () ? descricao : "Status alterado para " + novoStatus);
        if ("cancelamento".equalsIgnoreCase (acao) || "Cancelada".equalsIgnoreCase (novoStatus))
            evento.put ("motivo", descricao != null ? descricao : "");

        lista.add (evento);
        return true;
    }

    @Override
    public synchronized List<Document> listarHistorico (String propriedadeId, String atividadeId) throws Exception
    {
        Document atual = this.buscarPorId (propriedadeId, atividadeId);
        if (atual == null)
            return null;

        ArrayList<Document> ret = new ArrayList<Document> ();
        ArrayList<Document> lista = this.historicos.get (atividadeId);
        if (lista != null)
        {
            for (Document ev : lista)
                ret.add (new Document (ev));
        }

        return ret;
    }
}
