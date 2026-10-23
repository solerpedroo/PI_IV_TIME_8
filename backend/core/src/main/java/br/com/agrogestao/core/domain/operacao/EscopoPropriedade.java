package br.com.agrogestao.core.domain.operacao;

import org.bson.Document;
import org.bson.types.ObjectId;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio;

/** Titular e propriedade devem acompanhar toda gravação e consulta operacional. */
public record EscopoPropriedade(ObjectId proprietarioId, ObjectId propriedadeId) {
    public EscopoPropriedade {
        obrigatorio(proprietarioId, "proprietarioId");
        obrigatorio(propriedadeId, "propriedadeId");
    }

    public Document documento() {
        return new Document("proprietarioId", proprietarioId).append("propriedadeId", propriedadeId);
    }

    public static EscopoPropriedade doDocumento(Document documento) {
        return new EscopoPropriedade(documento.getObjectId("proprietarioId"), documento.getObjectId("propriedadeId"));
    }
}
