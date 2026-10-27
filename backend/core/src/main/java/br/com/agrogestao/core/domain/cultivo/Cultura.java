package br.com.agrogestao.core.domain.cultivo;

import org.bson.Document;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

/** Catálogo da propriedade; a safra referencia a cultura sem sobrescrever ciclos anteriores. */
public record Cultura(ObjectId id, EscopoPropriedade escopo, String nome) {
    public Cultura {
        obrigatorio(escopo, "escopo");
        nome = texto(nome, "nome");
    }

    public Document paraDocumento() {
        Document d = escopo.documento().append("nome", nome);
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Cultura doDocumento(Document d) {
        return new Cultura(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"));
    }
}
