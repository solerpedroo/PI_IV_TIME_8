package br.com.agrogestao.core.domain.operacao;

import java.time.*;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.ocorrencias.Ocorrencia;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

public final class DadosOcorrenciaTest {
    public static void main(String[] args) {
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        ContextoOperacao contexto = new ContextoOperacao(escopo, new ObjectId(), new ObjectId());
        Ocorrencia o = new Ocorrencia(new ObjectId(), contexto, "Praga no Talhão A", "Praga", LocalDate.of(2026, 10, 23),
                Prioridade.ALTA, "Folhas com sinais de praga", "https://exemplo.test/foto.jpg", Instant.now());
        verificar(Ocorrencia.doDocumento(o.paraDocumento()).equals(o), "ocorrência e foto https preservadas no BSON");
        verificar(Ocorrencia.permiteTransicao("ABERTA", "EM_ACOMPANHAMENTO"), "acompanhamento permitido");
        verificar(Ocorrencia.permiteTransicao("EM_ACOMPANHAMENTO", "EM_ACOMPANHAMENTO"), "novas anotações permitidas");
        verificar(Ocorrencia.permiteTransicao("EM_ACOMPANHAMENTO", "RESOLVIDA"), "resolução permitida");
        verificar(!Ocorrencia.permiteTransicao("RESOLVIDA", "ABERTA"), "resolvida preserva desfecho");
        Ocorrencia geral = new Ocorrencia(null, new ContextoOperacao(escopo, null, null), "Equipamento", "Problema operacional",
                LocalDate.now(), Prioridade.MEDIA, "Problema geral da propriedade", null, Instant.now());
        verificar(Ocorrencia.doDocumento(geral.paraDocumento()).equals(geral), "ocorrência geral pode omitir talhão e safra");
        invalido(() -> new Ocorrencia(null, null, "Praga", "Praga", LocalDate.now(),
                Prioridade.ALTA, "Descrição", null, Instant.now()));
        // Esquema javascript: e caminhos relativos não entram no allowlist http(s).
        invalido(() -> new Ocorrencia(null, contexto, "Praga", "Praga", LocalDate.now(),
                Prioridade.ALTA, "Descrição", "javascript:alert(1)", Instant.now()));
        invalido(() -> new Ocorrencia(null, contexto, "Praga", "Praga", LocalDate.now(),
                Prioridade.ALTA, "Descrição", "/uploads/foto.jpg", Instant.now()));
        System.out.println("DadosOcorrenciaTest: OK");
    }
}
