package br.com.agrogestao.core.domain.atividades;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.operacao.*;
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

public record Atividade(ObjectId id, ContextoOperacao contexto, String titulo, String tipo,
        String responsavel, LocalDate data, LocalTime horario, Prioridade prioridade, Instant criadaEm) {
    public enum Status { AGENDADA, EM_ANDAMENTO, CONCLUIDA, CANCELADA }

    public Atividade {
        obrigatorio(contexto, "contexto");
        titulo = texto(titulo, "titulo");
        tipo = texto(tipo, "tipo");
        responsavel = texto(responsavel, "responsavel");
        obrigatorio(data, "data");
        obrigatorio(horario, "horario");
        obrigatorio(prioridade, "prioridade");
        criadaEm = obrigatorio(criadaEm, "criadaEm").truncatedTo(ChronoUnit.MILLIS);
    }

    public static boolean permiteTransicao(String anterior, String proximo) {
        Status de = Status.valueOf(anterior);
        Status para = Status.valueOf(proximo);
        return (de == Status.AGENDADA && (para == Status.EM_ANDAMENTO || para == Status.CANCELADA))
                || (de == Status.EM_ANDAMENTO && (para == Status.CONCLUIDA || para == Status.CANCELADA));
    }

    public Document paraDocumento() {
        Document d = contexto.documento().append("titulo", titulo).append("tipo", tipo)
                .append("responsavel", responsavel).append("data", data.toString()).append("horario", horario.toString())
                .append("prioridade", prioridade.name()).append("criadaEm", Date.from(criadaEm))
                .append("statusInicial", Status.AGENDADA.name());
        if (id != null) d.put("_id", id);
        return d;
    }

    public static Atividade doDocumento(Document d) {
        return new Atividade(d.getObjectId("_id"), ContextoOperacao.doDocumento(d), d.getString("titulo"),
                d.getString("tipo"), d.getString("responsavel"), LocalDate.parse(d.getString("data")),
                LocalTime.parse(d.getString("horario")), Prioridade.valueOf(d.getString("prioridade")),
                d.getDate("criadaEm").toInstant());
    }
}
