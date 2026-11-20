/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
/* Autor secundario: Tomas de Paula Michelon Toniato - RA: 25004211 */
package dados.estoque;

import dados.operacao.RepositorioAtividades;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Estoque em memória, para testes e para o servidor no modo --memoria.
    Não há coleção Mongo nem transação de banco: o synchronized do método
    cobre leitura, validação do saldo e gravação juntas, então duas baixas
    simultâneas não enxergam o mesmo saldo.

    O isolamento é o campo propriedadeId de cada documento. Listas e buscas
    devolvem cópia, para o chamador não alterar o mapa interno por acidente.
*/
public class RepositorioEstoqueMemoria implements RepositorioEstoque
{
    private final LinkedHashMap<String, Document> insumos = new LinkedHashMap<> ();
    private final ArrayList<Document> movimentos = new ArrayList<> ();
    private final LinkedHashMap<String, Document> atividades = new LinkedHashMap<> ();

    /*
        No modo --memoria o estoque enxerga as atividades reais do servidor, como no
        MongoDB (mesma coleção); sem elas, só as adicionadas pelos testes.
    */
    private final RepositorioAtividades atividadesReais;

    /* Sem repositório de atividades: os testes injetam documentos com adicionarAtividadeParaTeste. */
    public RepositorioEstoqueMemoria ()
    {
        this (null);
    }

    /* Liga as atividades já gravadas no modo memória, no lugar da coleção atividades do Mongo. */
    public RepositorioEstoqueMemoria (RepositorioAtividades atividadesReais)
    {
        this.atividadesReais = atividadesReais;
    }

    /* Só os insumos desta propriedade que ainda não foram excluídos. */
    public synchronized List<Document> listar (String propriedadeId)
    {
        ArrayList<Document> lista = new ArrayList<> ();
        for (Document item : this.insumos.values ())
            if (propriedadeId.equals (item.getString ("propriedadeId")) && !Boolean.TRUE.equals (item.getBoolean ("excluido")))
                lista.add (new Document (item));
        return lista;
    }

    /* Inclui os arquivados para permitir consultar históricos antigos. Id de outra fazenda devolve null. */
    public synchronized Document buscarPorId (String propriedadeId, String id)
    {
        Document item = this.insumos.get (id);
        return item != null && propriedadeId.equals (item.getString ("propriedadeId")) ? new Document (item) : null;
    }

    /* Grava o insumo e, se houver saldo, a movimentação de saldo inicial no mesmo bloqueio. */
    public synchronized boolean criar (Document insumo)
    {
        insumo.put ("_id", new ObjectId ());
        this.insumos.put (insumo.getObjectId ("_id").toHexString (), new Document (insumo));
        if (CalculosEstoque.numero (insumo, "saldo") > 0) this.movimentos.add (CalculosEstoque.saldoInicial (insumo));
        return true;
    }

    /* Substitui campos do cadastro. Insumo de outra propriedade ou já excluído não é alterado. */
    public synchronized boolean atualizar (String propriedadeId, String id, Document campos)
    {
        Document item = this.buscarPorId (propriedadeId, id);
        if (item == null || Boolean.TRUE.equals (item.getBoolean ("excluido"))) return false;
        item.putAll (campos);
        this.insumos.put (id, item);
        return true;
    }

    /* Exclusão lógica: retira do catálogo sem apagar o rastro das movimentações. Saldo diferente de zero impede. */
    public synchronized boolean excluir (String propriedadeId, String id)
    {
        Document item = this.buscarPorId (propriedadeId, id);
        if (item == null || Boolean.TRUE.equals (item.getBoolean ("excluido")) || CalculosEstoque.numero (item, "saldo") != 0) return false;
        this.insumos.get (id).put ("excluido", true);
        return true;
    }

    /*
        Calcula o novo saldo e só então grava produto e movimentação.
        Se a saída não couber, nada é escrito e o retorno é null.
    */
    public synchronized Document movimentar (String propriedadeId, String id, Document movimento)
    {
        Document item = this.buscarPorId (propriedadeId, id);
        if (item == null || Boolean.TRUE.equals (item.getBoolean ("excluido"))) return null;
        Document atualizado = CalculosEstoque.movimentar (item, movimento);
        if (atualizado == null) return null;
        movimento.append ("_id", new ObjectId ()).append ("propriedadeId", propriedadeId).append ("insumoId", id);
        this.insumos.put (id, atualizado);
        this.movimentos.add (new Document (movimento));
        return new Document (atualizado);
    }

    /* id null consulta o histórico inteiro da propriedade, inclusive itens arquivados. A ordem é da mais nova para a mais antiga. */
    public synchronized List<Document> listarMovimentacoes (String propriedadeId, String id)
    {
        ArrayList<Document> lista = new ArrayList<> ();
        for (int i = this.movimentos.size () - 1; i >= 0; i--)
        {
            Document item = this.movimentos.get (i);
            if (propriedadeId.equals (item.getString ("propriedadeId")) && (id == null || id.equals (item.getString ("insumoId"))))
                lista.add (new Document (item));
        }
        return lista;
    }

    /* Preparação de dados de teste; não é exposta como rota de criação de atividades. */
    public synchronized void adicionarAtividadeParaTeste (Document atividade)
    {
        this.atividades.put (atividade.getObjectId ("_id").toHexString (), new Document (atividade));
    }

    /* Junta atividades de teste e, se houver, as do repositório real, sempre do mesmo usuário e fazenda. */
    public synchronized List<Document> listarAtividades (String usuarioId, String propriedadeId) throws Exception
    {
        ArrayList<Document> lista = new ArrayList<> ();
        for (Document atividade : this.atividades.values ())
            if (usuarioId.equals (atividade.getString ("usuarioId")) && propriedadeId.equals (atividade.getString ("propriedadeId")))
                lista.add (new Document (atividade));
        if (this.atividadesReais != null)
            for (Document atividade : this.atividadesReais.listar (propriedadeId))
                if (usuarioId.equals (atividade.getString ("usuarioId")) && atividade.get ("_id") instanceof String &&
                    ObjectId.isValid (atividade.getString ("_id")))
                    lista.add (new Document (atividade).append ("_id", new ObjectId (atividade.getString ("_id"))));
        return lista;
    }

    /* Procura uma atividade já filtrada por usuário e propriedade. */
    public synchronized Document buscarAtividade (String usuarioId, String propriedadeId, String id) throws Exception
    {
        for (Document atividade : this.listarAtividades (usuarioId, propriedadeId))
            if (atividade.getObjectId ("_id").toHexString ().equals (id)) return atividade;
        return null;
    }
}
