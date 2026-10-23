package br.com.agrogestao.core.domain.custos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.ContextoOperacao;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record Custo(ObjectId id, ContextoOperacao contexto, ObjectId culturaId, ObjectId atividadeId,
        ObjectId consumoId, Categoria categoria, String descricao, LocalDate data, BigDecimal valor) {
    public enum Categoria { INSUMOS, COMBUSTIVEL, MANUTENCAO, MAO_DE_OBRA, OUTROS }

    public Custo {
        obrigatorio(contexto, "contexto");
        if (contexto.safraId() != null) obrigatorio(culturaId, "culturaId da safra");
        if (consumoId != null) obrigatorio(atividadeId, "atividadeId do consumo");
        obrigatorio(categoria, "categoria");
        descricao = texto(descricao, "descricao");
        obrigatorio(data, "data");
        decimal(valor, "valor", false);
        try {
            valor = valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("valor em BRL deve representar centavos exatos");
        }
        new Decimal128(valor);
    }

    public Document paraDocumento() {
        Document d = contexto.documento().append("culturaId", culturaId).append("atividadeId", atividadeId)
                .append("consumoId", consumoId).append("categoria", categoria.name()).append("descricao", descricao)
                .append("data", data.toString()).append("valor", new Decimal128(valor)).append("moeda", "BRL");
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Custo doDocumento(Document d) {
        if (!"BRL".equals(d.getString("moeda"))) throw new IllegalArgumentException("moeda não suportada");
        return new Custo(d.getObjectId("_id"), ContextoOperacao.doDocumento(d), d.getObjectId("culturaId"),
                d.getObjectId("atividadeId"), d.getObjectId("consumoId"), Categoria.valueOf(d.getString("categoria")),
                d.getString("descricao"), LocalDate.parse(d.getString("data")), d.get("valor", Decimal128.class).bigDecimalValue());
    }
}
