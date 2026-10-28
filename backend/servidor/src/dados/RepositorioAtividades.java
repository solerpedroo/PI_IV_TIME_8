/* Miguel — RA: 25003110 */
package dados;

import java.util.List;
import org.bson.Document;

/**
 * Contrato de repositório para persistência de atividades e seu histórico.
 * 
 * Cada atividade pertence exclusivamente a uma propriedade e ao usuário dono da sessão.
 * O histórico registra a evolução cronológica dos estados (criação, início, conclusão
 * e cancelamento), servindo como auditoria confiável e fonte do estado real.
 */
public interface RepositorioAtividades
{
    /** Lista todas as atividades da propriedade ativa, em ordem de criação. */
    public List<Document> listar (String propriedadeId) throws Exception;

    /** Busca uma atividade específica pelo seu identificador na propriedade. */
    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    /** Insere uma nova atividade com seu evento inicial de histórico (ordem 0). */
    public boolean criar (Document atividade) throws Exception;

    /** Atualiza os dados cadastrais da atividade se ela não estiver em estado terminal. */
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    /** Atualiza o status da atividade e anexa o novo evento na linha do tempo. */
    public boolean mudarStatus (String propriedadeId, String id, String novoStatus, String acao, String descricao) throws Exception;

    /** Retorna a timeline completa de eventos registrados para a atividade. */
    public List<Document> listarHistorico (String propriedadeId, String atividadeId) throws Exception;
}
