package br.com.agrogestao.core.infra.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import br.com.agrogestao.core.domain.custos.Custo;
import br.com.agrogestao.core.domain.operacao.*;
import static com.mongodb.client.model.Indexes.ascending;

public final class CustoRepositorioMongo {
    private final MongoCollection<Document> custos;
    private final VinculosMongo vinculos;

    public CustoRepositorioMongo(MongoDatabase banco) {
        custos = banco.getCollection("custos");
        vinculos = new VinculosMongo(banco);
    }

    public void criarIndices() {
        custos.createIndex(ascending("proprietarioId", "propriedadeId", "talhaoId", "safraId", "data"));
        custos.createIndex(ascending("proprietarioId", "propriedadeId", "culturaId", "data"));
        custos.createIndex(ascending("proprietarioId", "propriedadeId", "consumoId"),
                new IndexOptions().unique(true).name("consumo_custo_unico")
                        .partialFilterExpression(new Document("consumoId", new Document("$type", "objectId"))));
    }

    public ObjectId inserir(Custo custo) {
        EscopoPropriedade escopo = custo.contexto().escopo();
        vinculos.exigirContexto(custo.contexto());
        if (custo.culturaId() != null) vinculos.exigir("culturas", escopo, custo.culturaId());
        if (custo.contexto().safraId() != null) {
            Document safra = vinculos.exigir("safras", escopo, custo.contexto().safraId());
            if (!safra.getObjectId("culturaId").equals(custo.culturaId())) {
                throw new IllegalArgumentException("cultura difere da cultura da safra");
            }
        }
        if (custo.atividadeId() != null) {
            Document atividade = vinculos.exigir("atividades", escopo, custo.atividadeId());
            if (!ContextoOperacao.doDocumento(atividade).equals(custo.contexto())) {
                throw new IllegalArgumentException("contexto do custo difere da atividade");
            }
        }
        if (custo.consumoId() != null) {
            Document consumo = vinculos.exigir("movimentacoes_insumos", escopo, custo.consumoId());
            if (!"SAIDA".equals(consumo.getString("tipo")) || !custo.atividadeId().equals(consumo.getObjectId("atividadeId"))) {
                throw new IllegalArgumentException("consumo não é uma saída da atividade informada");
            }
        }
        Document d = custo.paraDocumento();
        custos.insertOne(d);
        return d.getObjectId("_id");
    }

    public Optional<Custo> buscar(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(custos.find(vinculos.filtro(escopo, id)).first()).map(Custo::doDocumento);
    }

    public List<Custo> listarDaPropriedade(EscopoPropriedade escopo) {
        vinculos.exigirPropriedade(escopo);
        return consultar(escopo.documento());
    }

    public List<Custo> listarDoContexto(ContextoOperacao contexto) {
        vinculos.exigirContexto(contexto);
        return consultar(contexto.documento());
    }

    public List<Custo> listarDaCultura(EscopoPropriedade escopo, ObjectId culturaId) {
        vinculos.exigir("culturas", escopo, culturaId);
        return consultar(escopo.documento().append("culturaId", culturaId));
    }

    private List<Custo> consultar(Document filtro) {
        return custos.find(filtro).sort(com.mongodb.client.model.Sorts.ascending("data", "_id"))
                .map(Custo::doDocumento).into(new ArrayList<>());
    }
}
