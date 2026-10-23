package br.com.agrogestao.core.infra.mongo;

import java.math.BigDecimal;
import java.time.*;
import br.com.agrogestao.core.domain.atividades.Atividade;
import br.com.agrogestao.core.domain.operacao.*;
import java.util.List;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.cultivo.*;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

public final class CicloDadosAtlasTest {
    public static void main(String[] args) {
        try (CenarioDadosAtlas c = new CenarioDadosAtlas()) {
            c.preparar();
            validarCultivo(c);
            validarAtividade(c);
        }
        System.out.println("CicloDadosAtlasTest: OK (#101–#102)");
    }

    static Atividade atividade(ContextoOperacao contexto) {
        return new Atividade(null, contexto, "Aplicação de fertilizante", "Adubação", "Produtor fictício",
                LocalDate.of(2026, 10, 23), LocalTime.of(8, 0), Prioridade.MEDIA, Instant.parse("2026-10-23T10:00:00Z"));
    }

    private static void validarAtividade(CenarioDadosAtlas c) {
        AtividadeRepositorioMongo r = new AtividadeRepositorioMongo(c.conexao.banco());
        r.criarIndices();
        ContextoOperacao contexto = new ContextoOperacao(c.a, c.talhaoA, c.safraA);
        c.atividadeA = r.inserir(atividade(contexto));
        invalido(() -> r.inserir(atividade(new ContextoOperacao(c.a, c.talhaoB, c.safraA))));
        ObjectId outroTalhao = new CultivoRepositorioMongo(c.conexao.banco()).inserir(
                new Talhao(null, c.a, "Talhão fora da safra", BigDecimal.ONE, Talhao.Status.ATIVO));
        invalido(() -> r.inserir(atividade(new ContextoOperacao(c.a, outroTalhao, c.safraA))));
        invalido(() -> r.inserir(atividade(new ContextoOperacao(c.a, c.talhaoA, new ObjectId()))));
        invalido(() -> r.registrarEvento(c.b, c.atividadeA,
                new EventoHistorico(1, Instant.parse("2026-10-23T11:00:00Z"), "EM_ANDAMENTO", "Alheio")));
        invalido(() -> r.registrarEvento(c.a, c.atividadeA,
                new EventoHistorico(1, Instant.parse("2026-10-23T09:00:00Z"), "EM_ANDAMENTO", "Anterior à criação")));
        r.registrarEvento(c.a, c.atividadeA,
                new EventoHistorico(1, Instant.parse("2026-10-23T11:00:00Z"), "EM_ANDAMENTO", "Aplicação iniciada"));
        invalido(() -> r.registrarEvento(c.a, c.atividadeA,
                new EventoHistorico(1, Instant.parse("2026-10-23T12:00:00Z"), "CONCLUIDA", "Ordem repetida")));
        r.registrarEvento(c.a, c.atividadeA,
                new EventoHistorico(2, Instant.parse("2026-10-23T12:00:00Z"), "CONCLUIDA", "Aplicação finalizada"));
        invalido(() -> r.registrarEvento(c.a, c.atividadeA,
                new EventoHistorico(3, Instant.parse("2026-10-23T13:00:00Z"), "EM_ANDAMENTO", "Reabertura inválida")));
        c.reabrir();
        AtividadeRepositorioMongo persistido = new AtividadeRepositorioMongo(c.conexao.banco());
        verificar(persistido.statusAtual(c.a, c.atividadeA).orElseThrow() == Atividade.Status.CONCLUIDA, "conclusão persistida");
        verificar(persistido.listarHistorico(c.a, c.atividadeA).size() == 3, "histórico permanece após conclusão");
        verificar(persistido.listarDoContexto(contexto).size() == 1, "consulta pelo ciclo da safra");
        verificar(persistido.buscar(c.b, c.atividadeA).isEmpty(), "atividade isolada");
        verificar(persistido.listarHistorico(c.b, c.atividadeA).isEmpty(), "histórico isolado");
        verificar(persistido.statusAtual(c.extra, c.atividadeA).isEmpty(), "status isolado entre propriedades");
    }

    private static void validarCultivo(CenarioDadosAtlas c) {
        CultivoRepositorioMongo r = new CultivoRepositorioMongo(c.conexao.banco());
        invalido(() -> r.inserir(c.safra(c.a, c.culturaB, List.of(c.talhaoA))));
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(c.talhaoB))));
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(c.talhaoExtra))));
        invalido(() -> r.inserir(c.safra(c.a, new ObjectId(), List.of(c.talhaoA))));
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(new ObjectId()))));
        invalido(() -> r.inserir(new Cultura(null, new EscopoPropriedade(c.produtorB, c.propriedadeA), "Alheia")));
        invalido(() -> r.inserir(new Talhao(null, new EscopoPropriedade(c.produtorA, new ObjectId()),
                "Sem propriedade", BigDecimal.ONE, Talhao.Status.ATIVO)));
        c.reabrir();
        CultivoRepositorioMongo persistido = new CultivoRepositorioMongo(c.conexao.banco());
        verificar(persistido.buscarTalhao(c.a, c.talhaoA).orElseThrow().areaHectares().compareTo(new BigDecimal("12.75")) == 0,
                "área deve permanecer exata após reconectar");
        verificar(persistido.buscarCultura(c.a, c.culturaA).orElseThrow().nome().equals("Soja"), "cultura persistida");
        verificar(persistido.listarSafrasDoTalhao(c.a, c.talhaoA).size() == 1, "consulta do ciclo por talhão");
        verificar(persistido.buscarSafra(c.b, c.safraA).isEmpty(), "safra não pode vazar entre produtores");
        verificar(persistido.buscarTalhao(c.extra, c.talhaoA).isEmpty(), "talhão não pode vazar entre propriedades");
        verificar(persistido.buscarCultura(c.b, c.culturaA).isEmpty(), "cultura não pode vazar entre produtores");
    }
}
