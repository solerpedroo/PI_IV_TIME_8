/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import java.util.List;
import org.bson.Document;

/*
    Lancamentos de custo, sempre filtrados pela propriedade (isolamento entre usuarios).
    Implementacoes: RepositorioCustosMongo e RepositorioCustosMemoria (testes).
*/
public interface RepositorioCustos
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    // custo de consumo pela movimentacao de estoque; null se ainda nao existir
    public Document buscarPorMovimentacao (String propriedadeId, String movimentacaoId) throws Exception;

    // grava e preenche o _id
    public void criar (Document custo) throws Exception;

    // custo gerado por uma operacao (origem.movimentacaoId): grava so se ainda nao
    // existe um da mesma origem na propriedade; false se ja existia (sem duplicidade)
    public boolean criarSeAusente (Document custo) throws Exception;

    // $set dos campos; false se o lancamento nao existir na propriedade
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    // false se o lancamento nao existir na propriedade
    public boolean excluir (String propriedadeId, String id) throws Exception;
}
