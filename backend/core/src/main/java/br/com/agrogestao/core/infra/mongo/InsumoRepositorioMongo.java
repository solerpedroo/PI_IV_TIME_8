package br.com.agrogestao.core.infra.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import br.com.agrogestao.core.domain.insumos.*;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static com.mongodb.client.model.Indexes.ascending;

public final class InsumoRepositorioMongo {
    private final MongoCollection<Document> insumos;
    private final MongoCollection<Document> movimentacoes;
    private final VinculosMongo vinculos;

    public InsumoRepositorioMongo(MongoDatabase banco) {
        insumos = banco.getCollection("insumos");
        movimentacoes = banco.getCollection("movimentacoes_insumos");
        vinculos = new VinculosMongo(banco);
    }

    public void criarIndices() {
        insumos.createIndex(ascending("proprietarioId", "propriedadeId", "nome"));
        movimentacoes.createIndex(ascending("proprietarioId", "propriedadeId", "insumoId", "ocorridaEm", "_id"));
        movimentacoes.createIndex(ascending("proprietarioId", "propriedadeId", "atividadeId"));
    }

    public ObjectId inserir(Insumo insumo) {
        vinculos.exigirPropriedade(insumo.escopo());
        Document d = insumo.paraDocumento();
        insumos.insertOne(d);
        return d.getObjectId("_id");
    }

    /** Registra o documento; a baixa transacional e a validação concorrente de saldo pertencem à #142/#160. */
    public ObjectId inserir(MovimentacaoInsumo movimento) {
        vinculos.exigirPropriedade(movimento.escopo());
        Document insumo = vinculos.exigir("insumos", movimento.escopo(), movimento.insumoId());
        if (!insumo.getString("unidade").equals(movimento.unidade())) {
            throw new IllegalArgumentException("unidade difere do catálogo; conversão não é automática");
        }
        if (movimento.atividadeId() != null) vinculos.exigir("atividades", movimento.escopo(), movimento.atividadeId());
        Document d = movimento.paraDocumento();
        movimentacoes.insertOne(d);
        return d.getObjectId("_id");
    }

    public Optional<Insumo> buscar(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(insumos.find(vinculos.filtro(escopo, id)).first()).map(Insumo::doDocumento);
    }

    public Optional<MovimentacaoInsumo> buscarMovimentacao(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(movimentacoes.find(vinculos.filtro(escopo, id)).first()).map(MovimentacaoInsumo::doDocumento);
    }

    public List<MovimentacaoInsumo> listarMovimentacoes(EscopoPropriedade escopo, ObjectId insumoId) {
        if (buscar(escopo, insumoId).isEmpty()) return List.of();
        return movimentacoes.find(escopo.documento().append("insumoId", insumoId))
                .sort(com.mongodb.client.model.Sorts.ascending("ocorridaEm", "_id"))
                .map(MovimentacaoInsumo::doDocumento).into(new ArrayList<>());
    }

    public List<MovimentacaoInsumo> listarConsumosDaAtividade(EscopoPropriedade escopo, ObjectId atividadeId) {
        vinculos.exigir("atividades", escopo, atividadeId);
        return movimentacoes.find(escopo.documento().append("atividadeId", atividadeId).append("tipo", "SAIDA"))
                .sort(com.mongodb.client.model.Sorts.ascending("ocorridaEm", "_id"))
                .map(MovimentacaoInsumo::doDocumento).into(new ArrayList<>());
    }
}
