package br.com.agrogestao.core.domain.operacao;

import java.time.*;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.atividades.Atividade;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

public final class DadosAtividadeTest {
    public static void main(String[] args) {
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        ContextoOperacao contexto = new ContextoOperacao(escopo, new ObjectId(), new ObjectId());
        Atividade a = new Atividade(new ObjectId(), contexto, "Aplicar NPK", "Adubação", "João fictício",
                LocalDate.of(2026, 10, 23), LocalTime.of(8, 30), Prioridade.ALTA, Instant.now());
        verificar(Atividade.doDocumento(a.paraDocumento()).equals(a), "atividade deve preservar campos e vínculos no BSON");
        verificar(Atividade.permiteTransicao("AGENDADA", "EM_ANDAMENTO"), "início permitido");
        verificar(Atividade.permiteTransicao("EM_ANDAMENTO", "CONCLUIDA"), "conclusão permitida");
        verificar(!Atividade.permiteTransicao("CONCLUIDA", "EM_ANDAMENTO"), "histórico concluído deve permanecer finalizado");
        verificar(!Atividade.permiteTransicao("AGENDADA", "CONCLUIDA"), "conclusão exige execução");
        EventoHistorico e = new EventoHistorico(1, Instant.now(), "EM_ANDAMENTO", "Aplicação iniciada");
        verificar(EventoHistorico.doDocumento(e.paraDocumento()).equals(e), "evento deve preservar data e sequência");
        invalido(() -> new ContextoOperacao(escopo, null, new ObjectId()));
        invalido(() -> new EventoHistorico(-1, Instant.now(), "CONCLUIDA", "Inválido"));
        invalido(() -> new EventoHistorico(1, Instant.now(), "CONCLUIDA", " "));
        System.out.println("DadosAtividadeTest: OK");
    }
}
