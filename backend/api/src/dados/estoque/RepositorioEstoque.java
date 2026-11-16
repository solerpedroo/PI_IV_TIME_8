/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados.estoque;

import java.util.List;
import org.bson.Document;

/*
    Contrato do estoque de insumos. Há duas implementações com o mesmo
    resultado: memória (testes e modo demonstração) e MongoDB (coleções
    insumos e movimentacoes_insumos).

    O isolamento é por propriedade. Quase todo método recebe propriedadeId
    para uma sessão não consultar nem alterar o estoque de outra fazenda.
    Criar e movimentar gravam saldo e histórico juntos. Na memória isso cabe
    no mesmo synchronized; no Mongo, na mesma transação.
*/
public interface RepositorioEstoque
{
    /* Catálogo ativo da fazenda, sem os insumos excluídos logicamente. */
    List<Document> listar (String propriedadeId) throws Exception;

    /* Um insumo desta propriedade, inclusive arquivado, para o histórico continuar acessível. */
    Document buscarPorId (String propriedadeId, String id) throws Exception;

    /* Insere o produto. Se o saldo inicial for maior que zero, também nasce a movimentação de abertura. */
    boolean criar (Document insumo) throws Exception;

    /* Atualiza cadastro (nome, unidade, mínimo, custo). Não mexe no saldo. */
    boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    /* Exclusão lógica. Só vale com saldo zero, para a quantidade não sumir sem rastreio. */
    boolean excluir (String propriedadeId, String id) throws Exception;

    /*
        Aplica entrada, saída ou ajuste. Devolve null quando a saída não cabe
        no saldo atual. A movimentação e o novo saldo são gravados juntos
        para não haver saldo sem histórico nem histórico sem saldo.
    */
    Document movimentar (String propriedadeId, String insumoId, Document movimentacao) throws Exception;

    /* Histórico da propriedade. insumoId nulo lista todas as movimentações, inclusive de item arquivado. */
    List<Document> listarMovimentacoes (String propriedadeId, String insumoId) throws Exception;

    /*
        Somente leitura: o estoque referencia atividades existentes, sem criar outro módulo.
        O filtro junta usuário e propriedade.
    */
    List<Document> listarAtividades (String usuarioId, String propriedadeId) throws Exception;

    /* Uma atividade do usuário nesta propriedade, usada para validar a saída vinculada. */
    Document buscarAtividade (String usuarioId, String propriedadeId, String id) throws Exception;
}
