// Miguel — RA: 25003110
package br.com.agrogestao.core.infra.mongo;

import java.math.BigDecimal;
import java.time.*;
import br.com.agrogestao.core.domain.custos.Custo;
import com.mongodb.MongoWriteException;
import java.util.concurrent.*;
import br.com.agrogestao.core.domain.ocorrencias.Ocorrencia;
import br.com.agrogestao.core.domain.insumos.*;
import br.com.agrogestao.core.domain.atividades.Atividade;
import br.com.agrogestao.core.domain.operacao.*;
import java.util.List;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.cultivo.*;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

public final class CicloDadosAtlasTest {
    public static void main(String[] args) throws Exception {
        List<String> opcoes = List.of(args);
        if (opcoes.stream().anyMatch(a -> !a.equals("--demonstrar") && !a.equals("--pausar"))
                || (opcoes.contains("--pausar") && !opcoes.contains("--demonstrar"))) {
            throw new IllegalArgumentException("Uso: CicloDadosAtlasTest [--demonstrar [--pausar]]");
        }
        try (CenarioDadosAtlas c = new CenarioDadosAtlas()) {
            c.preparar();
            validarCultivo(c);
            validarAtividade(c);
            validarInsumos(c);
            validarOcorrencias(c);
            validarCustos(c);
            validarVinculosDaSafra(c);
            if (opcoes.contains("--demonstrar")) demonstrarEmOutroProcesso(c);
            if (opcoes.contains("--pausar")) {
                System.out.println("Dados fictícios disponíveis para inspeção no Atlas. Filtro:");
                System.out.println("{ propriedadeId: ObjectId(\"" + c.propriedadeA + "\") }");
                System.out.println("Pressione ENTER para remover os dados fictícios e encerrar.");
                new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
            }
        }
        System.out.println("CicloDadosAtlasTest: OK (#101–#105; dois ciclos persistentes)");
        if (opcoes.contains("--demonstrar")) System.out.println("Limpeza conferida no Atlas: nenhum registro fictício desta execução permaneceu.");
    }

    private static void demonstrarEmOutroProcesso(CenarioDadosAtlas c) throws Exception {
        System.out.println("Casos válidos e inválidos das #101–#105 conferidos no processo Java " + ProcessHandle.current().pid());
        List<String> comando = new java.util.ArrayList<>();
        comando.add(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString());
        comando.add("-cp");
        comando.add(System.getProperty("java.class.path"));
        comando.add(ConferirCenarioAtlas.class.getName());
        for (ObjectId id : List.of(c.produtorA, c.propriedadeA, c.produtorB, c.propriedadeB,
                c.talhaoA, c.safraA, c.atividadeA, c.insumoA, c.consumoA, c.ocorrenciaA)) {
            comando.add(id.toHexString());
        }
        Process leitor = new ProcessBuilder(comando).inheritIO().start();
        try {
            if (!leitor.waitFor(60, TimeUnit.SECONDS)) {
                leitor.destroyForcibly();
                throw new AssertionError("o processo leitor não concluiu a conferência no prazo");
            }
            verificar(leitor.exitValue() == 0, "a leitura independente do Atlas deve passar");
        } catch (InterruptedException e) {
            leitor.destroyForcibly();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private static void validarCustos(CenarioDadosAtlas c) {
        CustoRepositorioMongo r = new CustoRepositorioMongo(c.conexao.banco());
        r.criarIndices();
        ContextoOperacao contexto = new ContextoOperacao(c.a, c.talhaoA, c.safraA);
        Custo consumo = custo(contexto, c.culturaA, c.atividadeA, c.consumoA, "250.00");
        ObjectId custoId = r.inserir(consumo);
        r.inserir(custo(new ContextoOperacao(c.a, null, null), null, null, null, "37.50"));
        r.inserir(custo(new ContextoOperacao(c.a, null, null), null, null, null, "12.50"));
        try {
            r.inserir(consumo);
            throw new AssertionError("mesmo consumo não deve gerar dois custos diretos");
        } catch (MongoWriteException esperado) { verificar(esperado.getCode() == 11000, "índice deve rejeitar duplicidade"); }
        invalido(() -> r.inserir(custo(contexto, c.culturaB, c.atividadeA, null, "1")));
        ObjectId outraCultura = new CultivoRepositorioMongo(c.conexao.banco()).inserir(new Cultura(null, c.a, "Milho"));
        invalido(() -> r.inserir(custo(contexto, outraCultura, c.atividadeA, null, "1")));
        invalido(() -> r.inserir(custo(contexto, new ObjectId(), c.atividadeA, null, "1")));
        invalido(() -> r.inserir(custo(contexto, c.culturaA, new ObjectId(), null, "1")));
        invalido(() -> r.inserir(custo(contexto, c.culturaA, c.atividadeA, new ObjectId(), "1")));
        ObjectId entradaId = new InsumoRepositorioMongo(c.conexao.banco()).listarMovimentacoes(c.a, c.insumoA).stream()
                .filter(m -> m.tipo() == MovimentacaoInsumo.Tipo.ENTRADA).findFirst().orElseThrow().id();
        invalido(() -> r.inserir(custo(contexto, c.culturaA, c.atividadeA, entradaId, "1")));
        ObjectId outraAtividade = new AtividadeRepositorioMongo(c.conexao.banco()).inserir(atividade(contexto));
        invalido(() -> r.inserir(custo(contexto, c.culturaA, outraAtividade, c.consumoA, "1")));
        invalido(() -> r.inserir(custo(new ContextoOperacao(c.a, c.talhaoA, null), null, c.atividadeA, null, "1")));
        c.reabrir();
        CustoRepositorioMongo persistido = new CustoRepositorioMongo(c.conexao.banco());
        Custo lido = persistido.buscar(c.a, custoId).orElseThrow();
        verificar(lido.consumoId().equals(c.consumoA) && lido.atividadeId().equals(c.atividadeA), "origem do custo rastreável");
        verificar(lido.valor().equals(new BigDecimal("250.00")), "valor em BRL preservado exatamente");
        verificar(persistido.listarDoContexto(contexto).size() == 1, "custo da safra separado de gastos gerais");
        verificar(persistido.listarDaCultura(c.a, c.culturaA).size() == 1, "consulta por cultura");
        verificar(persistido.listarDaPropriedade(c.a).stream().map(Custo::valor).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("300.00")) == 0, "total conhecido da propriedade após reconexão");
        verificar(persistido.buscar(c.b, custoId).isEmpty(), "custo isolado entre produtores");
        verificar(persistido.buscar(c.extra, custoId).isEmpty(), "custo isolado entre propriedades");
        verificar(persistido.listarDaPropriedade(c.b).isEmpty(), "consulta vazia do outro produtor");
        verificar(new AtividadeRepositorioMongo(c.conexao.banco()).statusAtual(c.a, c.atividadeA).orElseThrow()
                == Atividade.Status.CONCLUIDA, "ciclo de consumo/custo mantém atividade concluída");
        verificar(new OcorrenciaRepositorioMongo(c.conexao.banco()).statusAtual(c.a, c.ocorrenciaA).orElseThrow()
                == Ocorrencia.Status.RESOLVIDA, "segundo ciclo mantém ocorrência resolvida");
    }

    private static Custo custo(ContextoOperacao contexto, ObjectId cultura, ObjectId atividade, ObjectId consumo, String valor) {
        return new Custo(null, contexto, cultura, atividade, consumo, Custo.Categoria.INSUMOS,
                "Gasto fictício da operação", LocalDate.of(2026, 10, 23), new BigDecimal(valor));
    }

    static Ocorrencia ocorrencia(ContextoOperacao contexto) {
        return new Ocorrencia(null, contexto, "Praga no Talhão A", "Praga", LocalDate.of(2026, 10, 23),
                Prioridade.ALTA, "Inspeção necessária", null, Instant.parse("2026-10-23T12:00:00Z"));
    }

    private static void validarOcorrencias(CenarioDadosAtlas c) {
        OcorrenciaRepositorioMongo r = new OcorrenciaRepositorioMongo(c.conexao.banco());
        r.criarIndices();
        ContextoOperacao contexto = new ContextoOperacao(c.a, c.talhaoA, c.safraA);
        c.ocorrenciaA = r.inserir(ocorrencia(contexto));
        ObjectId ocorrenciaGeral = r.inserir(ocorrencia(new ContextoOperacao(c.a, null, null)));
        invalido(() -> r.inserir(ocorrencia(new ContextoOperacao(c.a, c.talhaoB, c.safraA))));
        invalido(() -> r.inserir(ocorrencia(new ContextoOperacao(c.a, c.talhaoA, new ObjectId()))));
        invalido(() -> r.registrarAtualizacao(c.b, c.ocorrenciaA,
                new EventoHistorico(1, Instant.parse("2026-10-23T13:00:00Z"), "EM_ACOMPANHAMENTO", "Alheio")));
        r.registrarAtualizacao(c.a, c.ocorrenciaA,
                new EventoHistorico(1, Instant.parse("2026-10-23T13:00:00Z"), "EM_ACOMPANHAMENTO", "Inspeção realizada"));
        // Dois clientes usam a mesma versão. Exatamente um evento deve ser aceito.
        CountDownLatch largada = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> gravar = () -> {
                largada.await();
                try {
                    r.registrarAtualizacao(c.a, c.ocorrenciaA,
                            new EventoHistorico(2, Instant.parse("2026-10-23T14:00:00Z"), "EM_ACOMPANHAMENTO", "Medida de controle"));
                    return true;
                } catch (IllegalArgumentException conflitoEsperado) { return false; }
            };
            Future<Boolean> primeira = executor.submit(gravar);
            Future<Boolean> segunda = executor.submit(gravar);
            largada.countDown();
            verificar(primeira.get(15, TimeUnit.SECONDS) ^ segunda.get(15, TimeUnit.SECONDS),
                    "uma única atualização concorrente deve ser persistida");
        } catch (Exception e) { throw new AssertionError("falha na validação concorrente do histórico", e); }
        invalido(() -> r.registrarAtualizacao(c.a, c.ocorrenciaA,
                new EventoHistorico(3, Instant.parse("2026-10-23T12:00:00Z"), "RESOLVIDA", "Data regressiva")));
        r.registrarAtualizacao(c.a, c.ocorrenciaA,
                new EventoHistorico(3, Instant.parse("2026-10-23T15:00:00Z"), "RESOLVIDA", "Problema resolvido"));
        invalido(() -> r.registrarAtualizacao(c.a, c.ocorrenciaA,
                new EventoHistorico(4, Instant.parse("2026-10-23T16:00:00Z"), "EM_ACOMPANHAMENTO", "Reabertura inválida")));
        c.reabrir();
        OcorrenciaRepositorioMongo persistido = new OcorrenciaRepositorioMongo(c.conexao.banco());
        verificar(persistido.buscar(c.a, ocorrenciaGeral).orElseThrow().contexto().talhaoId() == null,
                "ocorrência geral da propriedade preservada sem talhão");
        verificar(persistido.statusAtual(c.a, c.ocorrenciaA).orElseThrow() == Ocorrencia.Status.RESOLVIDA, "resolução persistida");
        verificar(persistido.listarTimeline(c.a, c.ocorrenciaA).size() == 4, "timeline completa após resolução e reconexão");
        verificar(persistido.listarTimeline(c.a, c.ocorrenciaA).get(2).descricao().equals("Medida de controle"), "acompanhamento preservado");
        verificar(persistido.listarDoContexto(contexto).size() == 1, "ocorrência ligada ao ciclo da safra");
        verificar(persistido.buscar(c.b, c.ocorrenciaA).isEmpty(), "ocorrência isolada");
        verificar(persistido.listarTimeline(c.b, c.ocorrenciaA).isEmpty(), "timeline isolada");
        verificar(persistido.statusAtual(c.extra, c.ocorrenciaA).isEmpty(), "resolução isolada entre propriedades");
    }

    private static void validarInsumos(CenarioDadosAtlas c) {
        InsumoRepositorioMongo r = new InsumoRepositorioMongo(c.conexao.banco());
        r.criarIndices();
        c.insumoA = r.inserir(new Insumo(null, c.a, "NPK", "Fertilizante", "kg", new BigDecimal("100")));
        ObjectId insumoB = r.inserir(new Insumo(null, c.b, "NPK", "Fertilizante", "kg", BigDecimal.TEN));
        ObjectId atividadeB = new AtividadeRepositorioMongo(c.conexao.banco()).inserir(
                atividade(new ContextoOperacao(c.b, c.talhaoB, null)));
        r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.ENTRADA, "500", "kg", null));
        c.consumoA = r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.SAIDA, "50", "kg", c.atividadeA));
        invalido(() -> r.inserir(movimento(c.a, insumoB, MovimentacaoInsumo.Tipo.ENTRADA, "1", "kg", null)));
        invalido(() -> r.inserir(movimento(c.a, new ObjectId(), MovimentacaoInsumo.Tipo.ENTRADA, "1", "kg", null)));
        invalido(() -> r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.SAIDA, "1", "kg", atividadeB)));
        invalido(() -> r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.SAIDA, "1", "kg", new ObjectId())));
        invalido(() -> r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.ENTRADA, "1", "litro", null)));
        c.reabrir();
        InsumoRepositorioMongo persistido = new InsumoRepositorioMongo(c.conexao.banco());
        var historico = persistido.listarMovimentacoes(c.a, c.insumoA);
        verificar(historico.size() == 2, "entrada e saída preservadas");
        verificar(historico.stream().map(MovimentacaoInsumo::variacao).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("450")) == 0, "histórico representa 500 kg menos 50 kg após reconectar");
        verificar(persistido.buscar(c.a, c.insumoA).orElseThrow().estoqueMinimo().compareTo(new BigDecimal("100")) == 0,
                "mínimo pertence ao catálogo da propriedade");
        verificar(persistido.listarConsumosDaAtividade(c.a, c.atividadeA).getFirst().id().equals(c.consumoA), "consumo rastreável");
        verificar(persistido.buscar(c.b, c.insumoA).isEmpty(), "catálogo isolado");
        verificar(persistido.listarMovimentacoes(c.b, c.insumoA).isEmpty(), "histórico de estoque isolado");
        verificar(persistido.buscarMovimentacao(c.extra, c.consumoA).isEmpty(), "consumo isolado entre propriedades");
    }

    static MovimentacaoInsumo movimento(EscopoPropriedade escopo, ObjectId insumoId,
            MovimentacaoInsumo.Tipo tipo, String quantidade, String unidade, ObjectId atividadeId) {
        return new MovimentacaoInsumo(null, escopo, insumoId, tipo, new BigDecimal(quantidade), unidade,
                atividadeId, Instant.parse("2026-10-23T11:30:00Z"), "Movimentação fictícia");
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

    private static void validarVinculosDaSafra(CenarioDadosAtlas c) {
        CultivoRepositorioMongo persistido = new CultivoRepositorioMongo(c.conexao.banco());
        Safra colheita = persistido.avancarSafra(c.a, c.safraA, Safra.Status.COLHEITA);
        Safra finalizada = persistido.avancarSafra(c.a, c.safraA, Safra.Status.FINALIZADA);
        verificar(finalizada.status() == Safra.Status.FINALIZADA && colheita.status() == Safra.Status.COLHEITA,
                "fases devem avançar em ordem até a finalização");
        invalido(() -> persistido.atualizarSafra(finalizada));
        invalido(() -> persistido.excluirSafra(c.a, c.safraA));
        invalido(() -> persistido.excluirCultura(c.a, c.culturaA));
        c.reabrir();
        CultivoRepositorioMongo reaberto = new CultivoRepositorioMongo(c.conexao.banco());
        verificar(reaberto.buscarSafra(c.a, c.safraA).orElseThrow().status() == Safra.Status.FINALIZADA,
                "finalização permanece consultável após reconectar");
        verificar(reaberto.listarSafras(c.a).stream().anyMatch(s -> s.id().equals(c.safraA)),
                "consulta da propriedade inclui histórico finalizado");
        verificar(new AtividadeRepositorioMongo(c.conexao.banco()).listarDaSafra(c.a, c.safraA).size() == 2,
                "atividades continuam consultáveis pela safra finalizada");
        verificar(new OcorrenciaRepositorioMongo(c.conexao.banco()).listarDaSafra(c.a, c.safraA).size() == 1,
                "ocorrências continuam consultáveis pela safra finalizada");
        verificar(new CustoRepositorioMongo(c.conexao.banco()).listarDaSafra(c.a, c.safraA).size() == 1,
                "custos continuam consultáveis pela safra finalizada");
        invalido(() -> new AtividadeRepositorioMongo(c.conexao.banco()).listarDaSafra(c.b, c.safraA));
    }
}
