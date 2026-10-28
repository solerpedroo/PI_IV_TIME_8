// Miguel — RA: 25003110
package br.com.agrogestao.core.domain.cultivo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record Safra(ObjectId id, EscopoPropriedade escopo, String nome, ObjectId culturaId,
        List<ObjectId> talhaoIds, LocalDate inicio, LocalDate fim, BigDecimal areaHectares, Status status,
        List<EtapaHistorico> historicoEtapas) {
    public enum Status { PLANEJAMENTO, PREPARACAO, PLANTIO, MANEJO, COLHEITA, FINALIZADA }

    /** Marca de cada avanço do ciclo; a safra finalizada conserva a trilha completa. */
    public record EtapaHistorico(Status etapa, Instant em) {
        public EtapaHistorico {
            obrigatorio(etapa, "etapa");
            em = obrigatorio(em, "em").truncatedTo(ChronoUnit.MILLIS);
        }

        public Document paraDocumento() {
            return new Document("etapa", etapa.name()).append("em", Date.from(em));
        }

        public static EtapaHistorico doDocumento(Document d) {
            return new EtapaHistorico(Status.valueOf(d.getString("etapa")), d.getDate("em").toInstant());
        }
    }

    public Safra {
        obrigatorio(escopo, "escopo");
        nome = texto(nome, "nome");
        obrigatorio(culturaId, "culturaId");
        obrigatorio(talhaoIds, "talhaoIds");
        if (talhaoIds.isEmpty() || talhaoIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(talhaoIds).size() != talhaoIds.size()) {
            throw new IllegalArgumentException("talhaoIds deve conter talhões únicos e não vazios");
        }
        talhaoIds = List.copyOf(talhaoIds);
        obrigatorio(inicio, "inicio");
        obrigatorio(fim, "fim");
        if (fim.isBefore(inicio)) throw new IllegalArgumentException("fim não pode anteceder inicio");
        decimal(areaHectares, "areaHectares", false);
        obrigatorio(status, "status");
        // Sem trilha explícita: registra a etapa atual (cadastro novo = planejamento).
        if (historicoEtapas == null || historicoEtapas.isEmpty()) {
            historicoEtapas = List.of(new EtapaHistorico(status, Instant.now()));
        } else {
            historicoEtapas = List.copyOf(historicoEtapas);
        }
    }

    /** Atalho para cadastro: sempre começa em planejamento com a primeira marca de histórico. */
    public static Safra nova(ObjectId id, EscopoPropriedade escopo, String nome, ObjectId culturaId,
            List<ObjectId> talhaoIds, LocalDate inicio, LocalDate fim, BigDecimal areaHectares) {
        return new Safra(id, escopo, nome, culturaId, talhaoIds, inicio, fim, areaHectares,
                Status.PLANEJAMENTO, List.of(new EtapaHistorico(Status.PLANEJAMENTO, Instant.now())));
    }

    public Document paraDocumento() {
        Document d = escopo.documento().append("nome", nome).append("culturaId", culturaId)
                .append("talhaoIds", talhaoIds).append("inicio", inicio.toString()).append("fim", fim.toString())
                .append("areaHectares", new Decimal128(areaHectares)).append("status", status.name())
                .append("historicoEtapas", historicoEtapas.stream().map(EtapaHistorico::paraDocumento).toList());
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Safra doDocumento(Document d) {
        List<Document> bruto = d.getList("historicoEtapas", Document.class);
        List<EtapaHistorico> historico = bruto == null || bruto.isEmpty()
                ? List.of()
                : bruto.stream().map(EtapaHistorico::doDocumento).toList();
        return new Safra(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"),
                d.getObjectId("culturaId"), d.getList("talhaoIds", ObjectId.class),
                LocalDate.parse(d.getString("inicio")), LocalDate.parse(d.getString("fim")),
                d.get("areaHectares", Decimal128.class).bigDecimalValue(), Status.valueOf(d.getString("status")),
                historico);
    }

    /** Mantém o avanço sequencial e acrescenta a marca no histórico do mesmo documento. */
    public Safra avancar(Status proximo) {
        obrigatorio(proximo, "status");
        if (status.ordinal() + 1 != proximo.ordinal()) {
            throw new IllegalArgumentException("status deve avançar para a próxima etapa do ciclo");
        }
        List<EtapaHistorico> trilha = new ArrayList<>(historicoEtapas);
        trilha.add(new EtapaHistorico(proximo, Instant.now()));
        return new Safra(id, escopo, nome, culturaId, talhaoIds, inicio, fim, areaHectares, proximo, trilha);
    }
}
