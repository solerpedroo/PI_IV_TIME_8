package br.com.agrogestao.core.domain.ocorrencias;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.*;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record Ocorrencia(ObjectId id, ContextoOperacao contexto, String titulo, String tipo,
        LocalDate data, Prioridade prioridade, String descricao, String fotoUrl, Instant criadaEm) {
    public enum Status { ABERTA, EM_ACOMPANHAMENTO, RESOLVIDA }

    public Ocorrencia {
        obrigatorio(contexto, "contexto");
        obrigatorio(contexto.talhaoId(), "talhaoId");
        titulo = texto(titulo, "titulo");
        tipo = texto(tipo, "tipo");
        obrigatorio(data, "data");
        obrigatorio(prioridade, "prioridade");
        descricao = texto(descricao, "descricao");
        fotoUrl = fotoUrl == null || fotoUrl.isBlank() ? null : fotoUrl.trim();
        criadaEm = obrigatorio(criadaEm, "criadaEm").truncatedTo(ChronoUnit.MILLIS);
    }

    public static boolean permiteTransicao(String anterior, String proximo) {
        Status de = Status.valueOf(anterior);
        Status para = Status.valueOf(proximo);
        return de != Status.RESOLVIDA && (para == Status.EM_ACOMPANHAMENTO || para == Status.RESOLVIDA);
    }

    public Document paraDocumento() {
        Document d = contexto.documento().append("titulo", titulo).append("tipo", tipo).append("data", data.toString())
                .append("prioridade", prioridade.name()).append("descricao", descricao).append("fotoUrl", fotoUrl)
                .append("criadaEm", Date.from(criadaEm)).append("statusInicial", Status.ABERTA.name());
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Ocorrencia doDocumento(Document d) {
        return new Ocorrencia(d.getObjectId("_id"), ContextoOperacao.doDocumento(d), d.getString("titulo"),
                d.getString("tipo"), LocalDate.parse(d.getString("data")), Prioridade.valueOf(d.getString("prioridade")),
                d.getString("descricao"), d.getString("fotoUrl"), d.getDate("criadaEm").toInstant());
    }
}
