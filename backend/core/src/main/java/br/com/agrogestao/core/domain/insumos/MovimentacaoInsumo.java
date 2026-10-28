package br.com.agrogestao.core.domain.insumos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record MovimentacaoInsumo(ObjectId id, EscopoPropriedade escopo, ObjectId insumoId, Tipo tipo,
        BigDecimal quantidade, String unidade, ObjectId atividadeId, Instant ocorridaEm, String motivo) {
    public enum Tipo { ENTRADA, SAIDA, AJUSTE }

    public MovimentacaoInsumo {
        obrigatorio(escopo, "escopo");
        obrigatorio(insumoId, "insumoId");
        obrigatorio(tipo, "tipo");
        obrigatorio(quantidade, "quantidade");
        if (quantidade.signum() == 0 || (tipo != Tipo.AJUSTE && quantidade.signum() < 0)) {
            throw new IllegalArgumentException("quantidade deve ser positiva; ajuste admite diferença negativa");
        }
        new Decimal128(quantidade);
        unidade = texto(unidade, "unidade").toLowerCase(Locale.ROOT);
        if (atividadeId != null && tipo != Tipo.SAIDA) {
            throw new IllegalArgumentException("atividade consumidora só pode acompanhar uma saída");
        }
        ocorridaEm = obrigatorio(ocorridaEm, "ocorridaEm").truncatedTo(ChronoUnit.MILLIS);
        motivo = texto(motivo, "motivo");
    }

    public BigDecimal variacao() { return tipo == Tipo.SAIDA ? quantidade.negate() : quantidade; }

    public Document paraDocumento() {
        Document d = escopo.documento().append("insumoId", insumoId).append("tipo", tipo.name())
                .append("quantidade", new Decimal128(quantidade)).append("unidade", unidade)
                .append("atividadeId", atividadeId).append("ocorridaEm", Date.from(ocorridaEm)).append("motivo", motivo);
        if (id != null) d.put("_id", id);
        return d;
    }

    public static MovimentacaoInsumo doDocumento(Document d) {
        return new MovimentacaoInsumo(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getObjectId("insumoId"),
                Tipo.valueOf(d.getString("tipo")), d.get("quantidade", Decimal128.class).bigDecimalValue(),
                d.getString("unidade"), d.getObjectId("atividadeId"), d.getDate("ocorridaEm").toInstant(), d.getString("motivo"));
    }
}
