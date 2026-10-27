package br.com.agrogestao.core.domain.cultivo;

import java.math.BigDecimal;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record Talhao(ObjectId id, EscopoPropriedade escopo, String nome, BigDecimal areaHectares, Status status) {
    public enum Status { ATIVO, INATIVO }

    public Talhao {
        obrigatorio(escopo, "escopo");
        nome = texto(nome, "nome");
        decimal(areaHectares, "areaHectares", false);
        obrigatorio(status, "status");
    }

    public Document paraDocumento() {
        Document d = escopo.documento().append("nome", nome)
                .append("areaHectares", new Decimal128(areaHectares)).append("status", status.name());
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Talhao doDocumento(Document d) {
        return new Talhao(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"),
                d.get("areaHectares", Decimal128.class).bigDecimalValue(), Status.valueOf(d.getString("status")));
    }
}
