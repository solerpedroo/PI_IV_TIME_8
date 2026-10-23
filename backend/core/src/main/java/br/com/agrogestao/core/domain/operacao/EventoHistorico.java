package br.com.agrogestao.core.domain.operacao;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bson.Document;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

/** Eventos persistidos começam em 1; o evento 0 é a criação guardada no documento principal. */
public record EventoHistorico(int ordem, Instant registradoEm, String status, String descricao) {
    public EventoHistorico {
        if (ordem < 0) throw new IllegalArgumentException("ordem não pode ser negativa");
        registradoEm = obrigatorio(registradoEm, "registradoEm").truncatedTo(ChronoUnit.MILLIS);
        status = texto(status, "status");
        descricao = texto(descricao, "descricao");
    }

    public Document paraDocumento() {
        return new Document("ordem", ordem).append("registradoEm", Date.from(registradoEm))
                .append("status", status).append("descricao", descricao);
    }

    public static EventoHistorico doDocumento(Document d) {
        return new EventoHistorico(d.getInteger("ordem"), d.getDate("registradoEm").toInstant(),
                d.getString("status"), d.getString("descricao"));
    }
}
