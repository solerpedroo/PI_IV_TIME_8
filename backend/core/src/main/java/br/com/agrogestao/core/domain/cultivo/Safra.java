package br.com.agrogestao.core.domain.cultivo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record Safra(ObjectId id, EscopoPropriedade escopo, String nome, ObjectId culturaId,
        List<ObjectId> talhaoIds, LocalDate inicio, LocalDate fim, BigDecimal areaHectares, Status status) {
    public enum Status { PLANEJAMENTO, PREPARACAO, PLANTIO, MANEJO, COLHEITA, FINALIZADA }

    public Safra {
        obrigatorio(escopo, "escopo");
        nome = texto(nome, "nome");
        obrigatorio(culturaId, "culturaId");
        obrigatorio(talhaoIds, "talhaoIds");
        if (talhaoIds.isEmpty() || talhaoIds.stream().anyMatch(java.util.Objects::isNull) || new HashSet<>(talhaoIds).size() != talhaoIds.size()) {
            throw new IllegalArgumentException("talhaoIds deve conter talhões únicos e não vazios");
        }
        talhaoIds = List.copyOf(talhaoIds);
        obrigatorio(inicio, "inicio");
        obrigatorio(fim, "fim");
        if (fim.isBefore(inicio)) throw new IllegalArgumentException("fim não pode anteceder inicio");
        decimal(areaHectares, "areaHectares", false);
        obrigatorio(status, "status");
    }

    public Document paraDocumento() {
        Document d = escopo.documento().append("nome", nome).append("culturaId", culturaId)
                .append("talhaoIds", talhaoIds).append("inicio", inicio.toString()).append("fim", fim.toString())
                .append("areaHectares", new Decimal128(areaHectares)).append("status", status.name());
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Safra doDocumento(Document d) {
        return new Safra(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"),
                d.getObjectId("culturaId"), d.getList("talhaoIds", ObjectId.class),
                LocalDate.parse(d.getString("inicio")), LocalDate.parse(d.getString("fim")),
                d.get("areaHectares", Decimal128.class).bigDecimalValue(), Status.valueOf(d.getString("status")));
    }
}
