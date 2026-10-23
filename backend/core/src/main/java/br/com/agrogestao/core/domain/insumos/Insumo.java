package br.com.agrogestao.core.domain.insumos;

import java.math.BigDecimal;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record Insumo(ObjectId id, EscopoPropriedade escopo, String nome, String categoria,
        String unidade, BigDecimal estoqueMinimo) {
    public Insumo {
        obrigatorio(escopo, "escopo");
        nome = texto(nome, "nome");
        categoria = texto(categoria, "categoria");
        unidade = texto(unidade, "unidade").toLowerCase(Locale.ROOT);
        decimal(estoqueMinimo, "estoqueMinimo", true);
    }

    public Document paraDocumento() {
        Document d = escopo.documento().append("nome", nome).append("categoria", categoria)
                .append("unidade", unidade).append("estoqueMinimo", new Decimal128(estoqueMinimo));
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Insumo doDocumento(Document d) {
        return new Insumo(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"),
                d.getString("categoria"), d.getString("unidade"), d.get("estoqueMinimo", Decimal128.class).bigDecimalValue());
    }
}
