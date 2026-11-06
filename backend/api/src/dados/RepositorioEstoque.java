/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados;

import java.util.List;
import org.bson.Document;

/*
    Catálogo e movimentações de insumos. Todos os métodos recebem a propriedade
    para impedir que uma sessão consulte ou altere o estoque de outra fazenda.
*/
public interface RepositorioEstoque
{
    List<Document> listar (String propriedadeId) throws Exception;
    Document buscarPorId (String propriedadeId, String id) throws Exception;
    boolean criar (Document insumo) throws Exception;
    boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;
    boolean excluir (String propriedadeId, String id) throws Exception;

    // Devolve null quando a saída não cabe no saldo atual. A movimentação e o
    // novo saldo são gravados juntos para não haver saldo sem histórico.
    Document movimentar (String propriedadeId, String insumoId, Document movimentacao) throws Exception;
    List<Document> listarMovimentacoes (String propriedadeId, String insumoId) throws Exception;

    // Somente leitura: o estoque referencia atividades existentes, sem criar outro módulo.
    List<Document> listarAtividades (String usuarioId, String propriedadeId) throws Exception;
    Document buscarAtividade (String usuarioId, String propriedadeId, String id) throws Exception;
}
