package br.com.agrogestao.core.domain.operacao;

import org.bson.Document;
import org.bson.types.ObjectId;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio;

/** O talhão e a safra são opcionais para registros gerais da propriedade. */
public record ContextoOperacao(EscopoPropriedade escopo, ObjectId talhaoId, ObjectId safraId) {
    public ContextoOperacao {
        obrigatorio(escopo, "escopo");
        if (safraId != null && talhaoId == null) throw new IllegalArgumentException("safra exige talhaoId");
    }

    public Document documento() {
        return escopo.documento().append("talhaoId", talhaoId).append("safraId", safraId);
    }

    public static ContextoOperacao doDocumento(Document d) {
        return new ContextoOperacao(EscopoPropriedade.doDocumento(d), d.getObjectId("talhaoId"), d.getObjectId("safraId"));
    }
}
