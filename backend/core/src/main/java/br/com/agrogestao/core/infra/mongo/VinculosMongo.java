package br.com.agrogestao.core.infra.mongo;

import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoDatabase;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import br.com.agrogestao.core.domain.operacao.ContextoOperacao;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio;

/** Validação contextual: IDs existentes em outra propriedade também são referências inválidas. */
final class VinculosMongo {
    private final MongoDatabase banco;

    VinculosMongo(MongoDatabase banco) { this.banco = banco; }

    void exigirPropriedade(EscopoPropriedade escopo) {
        obrigatorio(escopo, "escopo");
        if (new PropriedadeRepositorioMongo(banco)
                .buscarDoProprietario(escopo.propriedadeId(), escopo.proprietarioId()).isEmpty()) {
            throw new IllegalArgumentException("propriedade não encontrada para o titular");
        }
    }

    void exigirContexto(ContextoOperacao contexto) {
        exigirPropriedade(contexto.escopo());
        if (contexto.talhaoId() != null) exigir("talhoes", contexto.escopo(), contexto.talhaoId());
        if (contexto.safraId() != null) {
            Document safra = exigir("safras", contexto.escopo(), contexto.safraId());
            if (!safra.getList("talhaoIds", ObjectId.class).contains(contexto.talhaoId())) {
                throw new IllegalArgumentException("talhão não pertence à safra informada");
            }
        }
    }

    Document filtro(EscopoPropriedade escopo, ObjectId id) {
        return escopo.documento().append("_id", obrigatorio(id, "id"));
    }

    Document exigir(String colecao, EscopoPropriedade escopo, ObjectId id) {
        Document d = banco.getCollection(colecao).find(filtro(escopo, id)).first();
        if (d == null) throw new IllegalArgumentException("referência não encontrada no escopo: " + colecao);
        return d;
    }
}
