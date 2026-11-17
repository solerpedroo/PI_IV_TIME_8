// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Miguel — RA: 25003110
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa java.time.* para utilizar os tipos deste módulo nas operações abaixo.
import java.time.*;
// Importa br.com.agrogestao.core.domain.custos.Custo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.custos.Custo;
// Importa a identificação de falhas de escrita, inclusive chaves duplicadas.
import com.mongodb.MongoWriteException;
// Importa java.util.concurrent.* para utilizar os tipos deste módulo nas operações abaixo.
import java.util.concurrent.*;
// Importa br.com.agrogestao.core.domain.ocorrencias.Ocorrencia para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.ocorrencias.Ocorrencia;
// Importa br.com.agrogestao.core.domain.insumos.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.insumos.*;
// Importa br.com.agrogestao.core.domain.atividades.Atividade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.atividades.Atividade;
// Importa br.com.agrogestao.core.domain.operacao.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.*;
// Importa as listas de vínculos, eventos e resultados.
import java.util.List;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.cultivo.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.cultivo.*;
// Importa br.com.agrogestao.core.domain.operacao.EscopoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

// Agrupa as verificações de CicloDadosAtlas nesta classe.
public final class CicloDadosAtlasTest {
    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) throws Exception {
        // Transforma os argumentos em lista para validar as opções da demonstração.
        List<String> opcoes = List.of(args);
        // Recusa qualquer opção diferente de demonstrar e pausar.
        if (opcoes.stream().anyMatch(a -> !a.equals("--demonstrar") && !a.equals("--pausar"))
                // Impede pedir pausa fora do modo de demonstração.
                || (opcoes.contains("--pausar") && !opcoes.contains("--demonstrar"))) {
            // Rejeita os dados inválidos: Uso: CicloDadosAtlasTest [--demonstrar [--pausar]].
            throw new IllegalArgumentException("Uso: CicloDadosAtlasTest [--demonstrar [--pausar]]");
        }
        // Abre o recurso para esta operação e garante seu fechamento ao sair do bloco, inclusive em caso de falha.
        try (CenarioDadosAtlas c = new CenarioDadosAtlas()) {
            // Prepara os dados fictícios exclusivos desta execução antes de testar os módulos.
            c.preparar();
            // Executa as verificações de cultivo usando o mesmo cenário persistido.
            validarCultivo(c);
            // Executa as verificações de atividade usando o mesmo cenário persistido.
            validarAtividade(c);
            // Executa as verificações de insumos usando o mesmo cenário persistido.
            validarInsumos(c);
            // Executa as verificações de ocorrencias usando o mesmo cenário persistido.
            validarOcorrencias(c);
            // Executa as verificações de custos usando o mesmo cenário persistido.
            validarCustos(c);
            // Executa as verificações de vinculos da safra usando o mesmo cenário persistido.
            validarVinculosDaSafra(c);
            // Quando solicitado, inicia a conferência somente de leitura em outra JVM.
            if (opcoes.contains("--demonstrar")) demonstrarEmOutroProcesso(c);
            // No modo de pausa, mantém os dados fictícios disponíveis até o usuário continuar.
            if (opcoes.contains("--pausar")) {
                // Exibe o resultado desta etapa no console: Dados fictícios disponíveis para inspeção no Atlas. Filtro:.
                System.out.println("Dados fictícios disponíveis para inspeção no Atlas. Filtro:");
                // Exibe o resultado desta etapa no console: { propriedadeId: ObjectId(\".
                System.out.println("{ propriedadeId: ObjectId(\"" + c.propriedadeA + "\") }");
                // Exibe o resultado desta etapa no console: Pressione ENTER para remover os dados fictícios e encerrar.
                System.out.println("Pressione ENTER para remover os dados fictícios e encerrar.");
                // Aguarda ENTER antes de sair do cenário e executar sua limpeza automática.
                new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
            }
        }
        // Exibe o resultado desta etapa no console: CicloDadosAtlasTest: OK (#101–#105; dois ciclos persistentes).
        System.out.println("CicloDadosAtlasTest: OK (#101–#105; dois ciclos persistentes)");
        // Informa que a limpeza terminou depois do fechamento do cenário de demonstração.
        if (opcoes.contains("--demonstrar")) System.out.println("Limpeza conferida no Atlas: nenhum registro fictício desta execução permaneceu.");
    }

    // Inicia outra JVM para ler o cenário já persistido e verificar os dados independentemente da conexão criadora.
    private static void demonstrarEmOutroProcesso(CenarioDadosAtlas c) throws Exception {
        // Exibe o resultado desta etapa no console: Casos válidos e inválidos das #101–#105 conferidos no processo Java .
        System.out.println("Casos válidos e inválidos das #101–#105 conferidos no processo Java " + ProcessHandle.current().pid());
        // Monta os argumentos do leitor como lista, sem interpretar um comando por meio do shell.
        List<String> comando = new java.util.ArrayList<>();
        // Seleciona o executável java da mesma instalação que está rodando esta suíte.
        comando.add(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString());
        // Acrescenta a opção que permite repassar o classpath ao leitor.
        comando.add("-cp");
        // Reutiliza o classpath atual para disponibilizar as classes e o driver MongoDB ao outro processo.
        comando.add(System.getProperty("java.class.path"));
        // Seleciona a classe que confere o cenário somente por leitura.
        comando.add(ConferirCenarioAtlas.class.getName());
        // Reúne os identificadores de produtores e propriedades que delimitam o cenário.
        for (ObjectId id : List.of(c.produtorA, c.propriedadeA, c.produtorB, c.propriedadeB,
                // Inclui os ids dos registros operacionais que o leitor deverá conferir.
                c.talhaoA, c.safraA, c.atividadeA, c.insumoA, c.consumoA, c.ocorrenciaA)) {
            // Passa cada identificador em hexadecimal, sem transmitir credenciais por argumento.
            comando.add(id.toHexString());
        }
        // Inicia outra JVM e compartilha sua saída com o console da demonstração.
        Process leitor = new ProcessBuilder(comando).inheritIO().start();
        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Limita a sessenta segundos a espera pela conclusão do leitor independente.
            if (!leitor.waitFor(60, TimeUnit.SECONDS)) {
                // Interrompe o processo leitor se ele exceder o tempo permitido.
                leitor.destroyForcibly();
                // Faz o teste falhar: o processo leitor não concluiu a conferência no prazo.
                throw new AssertionError("o processo leitor não concluiu a conferência no prazo");
            }
            // Confere a expectativa do teste: a leitura independente do Atlas deve passar.
            verificar(leitor.exitValue() == 0, "a leitura independente do Atlas deve passar");
        // Encaminha esta falha ao tratamento definido abaixo sem continuar o caminho de sucesso.
        } catch (InterruptedException e) {
            // Interrompe o leitor também quando a thread que aguarda for interrompida.
            leitor.destroyForcibly();
            // Restaura a marca de interrupção antes de propagar a falha ao chamador.
            Thread.currentThread().interrupt();
            // Propaga a falha ao chamador para que a execução não prossiga como se tivesse funcionado.
            throw e;
        }
    }

    // Testa centavos exatos, origem dos gastos, duplicidade de consumo, persistência e isolamento das consultas.
    private static void validarCustos(CenarioDadosAtlas c) {
        // Cria o repositório CustoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        CustoRepositorioMongo r = new CustoRepositorioMongo(c.conexao.banco());
        // Garante os índices de r antes das gravações ou consultas do cenário.
        r.criarIndices();
        // Define o escopo e os vínculos de talhão e safra que serão usados neste cenário de teste.
        ContextoOperacao contexto = new ContextoOperacao(c.a, c.talhaoA, c.safraA);
        // Prepara custo de R$ 250,00 rastreável ao consumo da atividade e à cultura da safra.
        Custo consumo = custo(contexto, c.culturaA, c.atividadeA, c.consumoA, "250.00");
        // Grava o custo direto e guarda seu id para as consultas posteriores.
        ObjectId custoId = r.inserir(consumo);
        // Adiciona gasto geral de R$ 37,50, sem talhão nem safra.
        r.inserir(custo(new ContextoOperacao(c.a, null, null), null, null, null, "37.50"));
        // Adiciona gasto geral de R$ 12,50 para completar o total conhecido de R$ 300,00.
        r.inserir(custo(new ContextoOperacao(c.a, null, null), null, null, null, "12.50"));
        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Repete o consumo já contabilizado para comprovar a unicidade do custo direto.
            r.inserir(consumo);
            // Faz o teste falhar: mesmo consumo não deve gerar dois custos diretos.
            throw new AssertionError("mesmo consumo não deve gerar dois custos diretos");
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (MongoWriteException esperado) { verificar(esperado.getCode() == 11000, "índice deve rejeitar duplicidade"); }
        // Confere que uma cultura de outro produtor não pode ser vinculada ao registro deste escopo.
        invalido(() -> r.inserir(custo(contexto, c.culturaB, c.atividadeA, null, "1")));
        // Cria outra cultura na mesma propriedade para testar incompatibilidade com a cultura da safra.
        ObjectId outraCultura = new CultivoRepositorioMongo(c.conexao.banco()).inserir(new Cultura(null, c.a, "Milho"));
        // Confere que o gasto não aceita cultura diferente da cultura da safra, mesmo dentro da propriedade.
        invalido(() -> r.inserir(custo(contexto, outraCultura, c.atividadeA, null, "1")));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(custo(contexto, new ObjectId(), c.atividadeA, null, "1")));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(custo(contexto, c.culturaA, new ObjectId(), null, "1")));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(custo(contexto, c.culturaA, c.atividadeA, new ObjectId(), "1")));
        // Consulta o histórico do insumo para obter uma movimentação de entrada.
        ObjectId entradaId = new InsumoRepositorioMongo(c.conexao.banco()).listarMovimentacoes(c.a, c.insumoA).stream()
                // Seleciona a primeira entrada e guarda seu id para o teste que não a aceita como consumo.
                .filter(m -> m.tipo() == MovimentacaoInsumo.Tipo.ENTRADA).findFirst().orElseThrow().id();
        // Confere que uma entrada de estoque não pode ser tratada como consumo de uma atividade.
        invalido(() -> r.inserir(custo(contexto, c.culturaA, c.atividadeA, entradaId, "1")));
        // Cria outra atividade válida para testar um custo que tenta usar o consumo de atividade diferente.
        ObjectId outraAtividade = new AtividadeRepositorioMongo(c.conexao.banco()).inserir(atividade(contexto));
        // Confere que o custo não pode usar um consumo pertencente a outra atividade.
        invalido(() -> r.inserir(custo(contexto, c.culturaA, outraAtividade, c.consumoA, "1")));
        // Confere que o caso inválido de custo é rejeitado pela validação.
        invalido(() -> r.inserir(custo(new ContextoOperacao(c.a, c.talhaoA, null), null, c.atividadeA, null, "1")));
        // Abre outra conexão para que as próximas verificações comprovem dados persistidos no banco.
        c.reabrir();
        // Cria o repositório CustoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        CustoRepositorioMongo persistido = new CustoRepositorioMongo(c.conexao.banco());
        // Relê o custo gravado pela nova conexão e exige que ele ainda exista.
        Custo lido = persistido.buscar(c.a, custoId).orElseThrow();
        // Confere a expectativa do teste: origem do custo rastreável.
        verificar(lido.consumoId().equals(c.consumoA) && lido.atividadeId().equals(c.atividadeA), "origem do custo rastreável");
        // Confere a expectativa do teste: valor em BRL preservado exatamente.
        verificar(lido.valor().equals(new BigDecimal("250.00")), "valor em BRL preservado exatamente");
        // Confere a expectativa do teste: custo da safra separado de gastos gerais.
        verificar(persistido.listarDoContexto(contexto).size() == 1, "custo da safra separado de gastos gerais");
        // Confere a expectativa do teste: consulta por cultura.
        verificar(persistido.listarDaCultura(c.a, c.culturaA).size() == 1, "consulta por cultura");
        // Confere a expectativa do teste: total conhecido da propriedade após reconexão.
        verificar(persistido.listarDaPropriedade(c.a).stream().map(Custo::valor).reduce(BigDecimal.ZERO, BigDecimal::add)
                // Compara o total decimal com R$ 300,00 depois da reconexão, incluindo os dois gastos gerais.
                .compareTo(new BigDecimal("300.00")) == 0, "total conhecido da propriedade após reconexão");
        // Confere a expectativa do teste: custo isolado entre produtores.
        verificar(persistido.buscar(c.b, custoId).isEmpty(), "custo isolado entre produtores");
        // Confere a expectativa do teste: custo isolado entre propriedades.
        verificar(persistido.buscar(c.extra, custoId).isEmpty(), "custo isolado entre propriedades");
        // Confere a expectativa do teste: consulta vazia do outro produtor.
        verificar(persistido.listarDaPropriedade(c.b).isEmpty(), "consulta vazia do outro produtor");
        // Confere a expectativa do teste: ciclo de consumo/custo mantém atividade concluída.
        verificar(new AtividadeRepositorioMongo(c.conexao.banco()).statusAtual(c.a, c.atividadeA).orElseThrow()
                // Exige que a atividade continue concluída depois de gravar e reler seus custos.
                == Atividade.Status.CONCLUIDA, "ciclo de consumo/custo mantém atividade concluída");
        // Confere a expectativa do teste: segundo ciclo mantém ocorrência resolvida.
        verificar(new OcorrenciaRepositorioMongo(c.conexao.banco()).statusAtual(c.a, c.ocorrenciaA).orElseThrow()
                // Exige que a ocorrência continue resolvida depois de exercitar o segundo ciclo persistido.
                == Ocorrencia.Status.RESOLVIDA, "segundo ciclo mantém ocorrência resolvida");
    }

    // Constrói um custo fictício para verificar centavos e vínculos com safra, atividade e consumo.
    private static Custo custo(ContextoOperacao contexto, ObjectId cultura, ObjectId atividade, ObjectId consumo, String valor) {
        // Reconstrói Custo com os identificadores e valores recebidos para este cenário.
        return new Custo(null, contexto, cultura, atividade, consumo, Custo.Categoria.INSUMOS,
                // Completa o custo fictício com descrição, data conhecida e valor decimal exato.
                "Gasto fictício da operação", LocalDate.of(2026, 10, 23), new BigDecimal(valor));
    }

    // Constrói uma ocorrência fictícia no contexto recebido para os testes de persistência.
    static Ocorrencia ocorrencia(ContextoOperacao contexto) {
        // Reconstrói Ocorrencia com os identificadores e valores recebidos para este cenário.
        return new Ocorrencia(null, contexto, "Praga no Talhão A", "Praga", LocalDate.of(2026, 10, 23),
                // Define a ocorrência inicial com prioridade alta e instante fixo para testar a cronologia dos eventos.
                Prioridade.ALTA, "Inspeção necessária", null, Instant.parse("2026-10-23T12:00:00Z"));
    }

    // Testa vínculos, ordem temporal, disputa por sequência, resolução e isolamento da linha do tempo.
    private static void validarOcorrencias(CenarioDadosAtlas c) {
        // Cria o repositório OcorrenciaRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        OcorrenciaRepositorioMongo r = new OcorrenciaRepositorioMongo(c.conexao.banco());
        // Garante os índices de r antes das gravações ou consultas do cenário.
        r.criarIndices();
        // Define o escopo e os vínculos de talhão e safra que serão usados neste cenário de teste.
        ContextoOperacao contexto = new ContextoOperacao(c.a, c.talhaoA, c.safraA);
        // Grava a ocorrência da safra e guarda seu id para testar a linha do tempo.
        c.ocorrenciaA = r.inserir(ocorrencia(contexto));
        // Grava também ocorrência geral da propriedade para verificar que o talhão pode ser omitido.
        ObjectId ocorrenciaGeral = r.inserir(ocorrencia(new ContextoOperacao(c.a, null, null)));
        // Confere que um talhão de outro produtor não pode ser usado neste contexto.
        invalido(() -> r.inserir(ocorrencia(new ContextoOperacao(c.a, c.talhaoB, c.safraA))));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(ocorrencia(new ContextoOperacao(c.a, c.talhaoA, new ObjectId()))));
        // Confere que o caso inválido de histórico é rejeitado pela validação.
        invalido(() -> r.registrarAtualizacao(c.b, c.ocorrenciaA,
                // Tenta inserir evento usando o escopo de outro produtor para confirmar a proteção da ocorrência.
                new EventoHistorico(1, Instant.parse("2026-10-23T13:00:00Z"), "EM_ACOMPANHAMENTO", "Alheio")));
        // Registra o primeiro acompanhamento autorizado da ocorrência.
        r.registrarAtualizacao(c.a, c.ocorrenciaA,
                // Usa sequência um e data posterior à criação para representar a inspeção realizada.
                new EventoHistorico(1, Instant.parse("2026-10-23T13:00:00Z"), "EM_ACOMPANHAMENTO", "Inspeção realizada"));
        // Dois clientes usam a mesma versão. Exatamente um evento deve ser aceito.
        // Prepara uma barreira que liberará simultaneamente duas tentativas da mesma sequência.
        CountDownLatch largada = new CountDownLatch(1);
        // Abre o recurso para esta operação e garante seu fechamento ao sair do bloco, inclusive em caso de falha.
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // Define a operação concorrente que informa se conseguiu gravar o evento.
            Callable<Boolean> gravar = () -> {
                // Mantém cada escritor esperando até a liberação conjunta da barreira.
                largada.await();
                // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
                try {
                    // Tenta gravar o mesmo próximo evento a partir de dois escritores concorrentes.
                    r.registrarAtualizacao(c.a, c.ocorrenciaA,
                            // Usa a sequência dois em ambas as tentativas para provocar conflito controlado pelo índice único.
                            new EventoHistorico(2, Instant.parse("2026-10-23T14:00:00Z"), "EM_ACOMPANHAMENTO", "Medida de controle"));
                    // Marca sucesso somente quando a inserção do evento terminou sem conflito.
                    return true;
                // Encaminha esta falha ao tratamento definido abaixo sem continuar o caminho de sucesso.
                } catch (IllegalArgumentException conflitoEsperado) { return false; }
            };
            // Submete a primeira tentativa de escrita ao executor.
            Future<Boolean> primeira = executor.submit(gravar);
            // Submete a segunda tentativa com os mesmos dados e a mesma sequência.
            Future<Boolean> segunda = executor.submit(gravar);
            // Libera os dois escritores para disputar a posição do histórico.
            largada.countDown();
            // Confere a expectativa do teste: uma única atualização concorrente deve ser persistida.
            verificar(primeira.get(15, TimeUnit.SECONDS) ^ segunda.get(15, TimeUnit.SECONDS),
                    // Identifica a expectativa deste caso: uma única atualização concorrente deve ser persistida.
                    "uma única atualização concorrente deve ser persistida");
        // Encaminha esta falha ao tratamento definido abaixo sem continuar o caminho de sucesso.
        } catch (Exception e) { throw new AssertionError("falha na validação concorrente do histórico", e); }
        // Confere que o caso inválido de histórico é rejeitado pela validação.
        invalido(() -> r.registrarAtualizacao(c.a, c.ocorrenciaA,
                // Informa data anterior ao último evento para testar a rejeição de regressão temporal.
                new EventoHistorico(3, Instant.parse("2026-10-23T12:00:00Z"), "RESOLVIDA", "Data regressiva")));
        // Resolve a ocorrência depois de completar os acompanhamentos válidos.
        r.registrarAtualizacao(c.a, c.ocorrenciaA,
                // Registra a resolução na sequência três e em instante posterior aos acompanhamentos.
                new EventoHistorico(3, Instant.parse("2026-10-23T15:00:00Z"), "RESOLVIDA", "Problema resolvido"));
        // Confere que o caso inválido de histórico é rejeitado pela validação.
        invalido(() -> r.registrarAtualizacao(c.a, c.ocorrenciaA,
                // Tenta reabrir ocorrência resolvida para confirmar a preservação do desfecho.
                new EventoHistorico(4, Instant.parse("2026-10-23T16:00:00Z"), "EM_ACOMPANHAMENTO", "Reabertura inválida")));
        // Abre outra conexão para que as próximas verificações comprovem dados persistidos no banco.
        c.reabrir();
        // Cria o repositório OcorrenciaRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        OcorrenciaRepositorioMongo persistido = new OcorrenciaRepositorioMongo(c.conexao.banco());
        // Confere a expectativa do teste: ocorrência geral da propriedade preservada sem talhão.
        verificar(persistido.buscar(c.a, ocorrenciaGeral).orElseThrow().contexto().talhaoId() == null,
                // Identifica a expectativa deste caso: ocorrência geral da propriedade preservada sem talhão.
                "ocorrência geral da propriedade preservada sem talhão");
        // Confere a expectativa do teste: resolução persistida.
        verificar(persistido.statusAtual(c.a, c.ocorrenciaA).orElseThrow() == Ocorrencia.Status.RESOLVIDA, "resolução persistida");
        // Confere a expectativa do teste: timeline completa após resolução e reconexão.
        verificar(persistido.listarTimeline(c.a, c.ocorrenciaA).size() == 4, "timeline completa após resolução e reconexão");
        // Confere a expectativa do teste: acompanhamento preservado.
        verificar(persistido.listarTimeline(c.a, c.ocorrenciaA).get(2).descricao().equals("Medida de controle"), "acompanhamento preservado");
        // Confere a expectativa do teste: ocorrência ligada ao ciclo da safra.
        verificar(persistido.listarDoContexto(contexto).size() == 1, "ocorrência ligada ao ciclo da safra");
        // Confere a expectativa do teste: ocorrência isolada.
        verificar(persistido.buscar(c.b, c.ocorrenciaA).isEmpty(), "ocorrência isolada");
        // Confere a expectativa do teste: timeline isolada.
        verificar(persistido.listarTimeline(c.b, c.ocorrenciaA).isEmpty(), "timeline isolada");
        // Confere a expectativa do teste: resolução isolada entre propriedades.
        verificar(persistido.statusAtual(c.extra, c.ocorrenciaA).isEmpty(), "resolução isolada entre propriedades");
    }

    // Testa catálogo, entradas e saídas, unidade de medida, atividade consumidora e persistência do histórico.
    private static void validarInsumos(CenarioDadosAtlas c) {
        // Cria o repositório InsumoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        InsumoRepositorioMongo r = new InsumoRepositorioMongo(c.conexao.banco());
        // Garante os índices de r antes das gravações ou consultas do cenário.
        r.criarIndices();
        // Cria o insumo de A com mínimo de cem unidades para os testes de persistência.
        c.insumoA = r.inserir(new Insumo(null, c.a, "NPK", "Fertilizante", "kg", new BigDecimal("100")));
        // Cria insumo de B para testar o bloqueio de movimentações entre produtores.
        ObjectId insumoB = r.inserir(new Insumo(null, c.b, "NPK", "Fertilizante", "kg", BigDecimal.TEN));
        // Cria uma atividade no escopo de B para os testes de referência consumidora inválida.
        ObjectId atividadeB = new AtividadeRepositorioMongo(c.conexao.banco()).inserir(
                // Usa o talhão de B e nenhum vínculo de safra nessa atividade auxiliar.
                atividade(new ContextoOperacao(c.b, c.talhaoB, null)));
        // Registra entrada de 500 kg no histórico do insumo de A.
        r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.ENTRADA, "500", "kg", null));
        // Registra saída de 50 kg vinculada à atividade de A e guarda seu id como consumo.
        c.consumoA = r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.SAIDA, "50", "kg", c.atividadeA));
        // Confere que o insumo de outro produtor não pode receber movimentação neste escopo.
        invalido(() -> r.inserir(movimento(c.a, insumoB, MovimentacaoInsumo.Tipo.ENTRADA, "1", "kg", null)));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(movimento(c.a, new ObjectId(), MovimentacaoInsumo.Tipo.ENTRADA, "1", "kg", null)));
        // Confere que o consumo não pode apontar para a atividade de outro produtor.
        invalido(() -> r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.SAIDA, "1", "kg", atividadeB)));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.SAIDA, "1", "kg", new ObjectId())));
        // Confere que uma movimentação com unidade diferente da unidade do catálogo é rejeitada.
        invalido(() -> r.inserir(movimento(c.a, c.insumoA, MovimentacaoInsumo.Tipo.ENTRADA, "1", "litro", null)));
        // Abre outra conexão para que as próximas verificações comprovem dados persistidos no banco.
        c.reabrir();
        // Cria o repositório InsumoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        InsumoRepositorioMongo persistido = new InsumoRepositorioMongo(c.conexao.banco());
        // Relê as movimentações usando a conexão reaberta, sem usar o histórico em memória anterior.
        var historico = persistido.listarMovimentacoes(c.a, c.insumoA);
        // Confere a expectativa do teste: entrada e saída preservadas.
        verificar(historico.size() == 2, "entrada e saída preservadas");
        // Confere a expectativa do teste: histórico representa 500 kg menos 50 kg após reconectar.
        verificar(historico.stream().map(MovimentacaoInsumo::variacao).reduce(BigDecimal.ZERO, BigDecimal::add)
                // Compara a soma das movimentações com 450 kg, resultado exato da entrada de 500 e da saída de 50.
                .compareTo(new BigDecimal("450")) == 0, "histórico representa 500 kg menos 50 kg após reconectar");
        // Confere a expectativa do teste: mínimo pertence ao catálogo da propriedade.
        verificar(persistido.buscar(c.a, c.insumoA).orElseThrow().estoqueMinimo().compareTo(new BigDecimal("100")) == 0,
                // Identifica a expectativa deste caso: mínimo pertence ao catálogo da propriedade.
                "mínimo pertence ao catálogo da propriedade");
        // Confere a expectativa do teste: consumo rastreável.
        verificar(persistido.listarConsumosDaAtividade(c.a, c.atividadeA).getFirst().id().equals(c.consumoA), "consumo rastreável");
        // Confere a expectativa do teste: catálogo isolado.
        verificar(persistido.buscar(c.b, c.insumoA).isEmpty(), "catálogo isolado");
        // Confere a expectativa do teste: histórico de estoque isolado.
        verificar(persistido.listarMovimentacoes(c.b, c.insumoA).isEmpty(), "histórico de estoque isolado");
        // Confere a expectativa do teste: consumo isolado entre propriedades.
        verificar(persistido.buscarMovimentacao(c.extra, c.consumoA).isEmpty(), "consumo isolado entre propriedades");
    }

    // Constrói uma movimentação fictícia com quantidade decimal e referências escolhidas pelo teste.
    static MovimentacaoInsumo movimento(EscopoPropriedade escopo, ObjectId insumoId,
            // Recebe o tipo, a quantidade, a unidade e a atividade que cada caso quer validar.
            MovimentacaoInsumo.Tipo tipo, String quantidade, String unidade, ObjectId atividadeId) {
        // Reconstrói MovimentacaoInsumo com os identificadores e valores recebidos para este cenário.
        return new MovimentacaoInsumo(null, escopo, insumoId, tipo, new BigDecimal(quantidade), unidade,
                // Completa a movimentação com instante fixo e justificativa fictícia.
                atividadeId, Instant.parse("2026-10-23T11:30:00Z"), "Movimentação fictícia");
    }

    // Constrói uma atividade fictícia no contexto recebido para os testes de persistência.
    static Atividade atividade(ContextoOperacao contexto) {
        // Reconstrói Atividade com os identificadores e valores recebidos para este cenário.
        return new Atividade(null, contexto, "Aplicação de fertilizante", "Adubação", "Produtor fictício",
                // Define horário e instante conhecidos para comparar cronologicamente os eventos da atividade.
                LocalDate.of(2026, 10, 23), LocalTime.of(8, 0), Prioridade.MEDIA, Instant.parse("2026-10-23T10:00:00Z"));
    }

    // Testa transições da atividade, preservação dos eventos e isolamento após reabrir a conexão.
    private static void validarAtividade(CenarioDadosAtlas c) {
        // Cria o repositório AtividadeRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        AtividadeRepositorioMongo r = new AtividadeRepositorioMongo(c.conexao.banco());
        // Garante os índices de r antes das gravações ou consultas do cenário.
        r.criarIndices();
        // Define o escopo e os vínculos de talhão e safra que serão usados neste cenário de teste.
        ContextoOperacao contexto = new ContextoOperacao(c.a, c.talhaoA, c.safraA);
        // Grava a atividade válida de A e guarda seu id para as verificações do histórico.
        c.atividadeA = r.inserir(atividade(contexto));
        // Confere que um talhão de outro produtor não pode ser usado neste contexto.
        invalido(() -> r.inserir(atividade(new ContextoOperacao(c.a, c.talhaoB, c.safraA))));
        // Cria talhão da mesma propriedade que não integra a safra em teste.
        ObjectId outroTalhao = new CultivoRepositorioMongo(c.conexao.banco()).inserir(
                // Mantém esse talhão ativo para que a rejeição dependa da associação com a safra.
                new Talhao(null, c.a, "Talhão fora da safra", BigDecimal.ONE, Talhao.Status.ATIVO));
        // Confere que o caso inválido de dados do domínio é rejeitado pela validação.
        invalido(() -> r.inserir(atividade(new ContextoOperacao(c.a, outroTalhao, c.safraA))));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(atividade(new ContextoOperacao(c.a, c.talhaoA, new ObjectId()))));
        // Confere que o caso inválido de histórico é rejeitado pela validação.
        invalido(() -> r.registrarEvento(c.b, c.atividadeA,
                // Tenta registrar evento com outro produtor para comprovar o isolamento do histórico.
                new EventoHistorico(1, Instant.parse("2026-10-23T11:00:00Z"), "EM_ANDAMENTO", "Alheio")));
        // Confere que o caso inválido de histórico é rejeitado pela validação.
        invalido(() -> r.registrarEvento(c.a, c.atividadeA,
                // Usa instante anterior à criação da atividade para testar a ordem temporal.
                new EventoHistorico(1, Instant.parse("2026-10-23T09:00:00Z"), "EM_ANDAMENTO", "Anterior à criação")));
        // Registra o início válido da execução da atividade.
        r.registrarEvento(c.a, c.atividadeA,
                // Define o primeiro evento posterior à criação, com estado EM_ANDAMENTO.
                new EventoHistorico(1, Instant.parse("2026-10-23T11:00:00Z"), "EM_ANDAMENTO", "Aplicação iniciada"));
        // Confere que o caso inválido de histórico é rejeitado pela validação.
        invalido(() -> r.registrarEvento(c.a, c.atividadeA,
                // Repete a sequência um já utilizada para testar a rejeição de histórico desatualizado.
                new EventoHistorico(1, Instant.parse("2026-10-23T12:00:00Z"), "CONCLUIDA", "Ordem repetida")));
        // Registra a conclusão depois do início de execução.
        r.registrarEvento(c.a, c.atividadeA,
                // Usa sequência dois e instante posterior para manter o histórico ordenado.
                new EventoHistorico(2, Instant.parse("2026-10-23T12:00:00Z"), "CONCLUIDA", "Aplicação finalizada"));
        // Confere que o caso inválido de histórico é rejeitado pela validação.
        invalido(() -> r.registrarEvento(c.a, c.atividadeA,
                // Tenta voltar à execução depois da conclusão para comprovar o bloqueio de reabertura.
                new EventoHistorico(3, Instant.parse("2026-10-23T13:00:00Z"), "EM_ANDAMENTO", "Reabertura inválida")));
        // Abre outra conexão para que as próximas verificações comprovem dados persistidos no banco.
        c.reabrir();
        // Cria o repositório AtividadeRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        AtividadeRepositorioMongo persistido = new AtividadeRepositorioMongo(c.conexao.banco());
        // Confere a expectativa do teste: conclusão persistida.
        verificar(persistido.statusAtual(c.a, c.atividadeA).orElseThrow() == Atividade.Status.CONCLUIDA, "conclusão persistida");
        // Confere a expectativa do teste: histórico permanece após conclusão.
        verificar(persistido.listarHistorico(c.a, c.atividadeA).size() == 3, "histórico permanece após conclusão");
        // Confere a expectativa do teste: consulta pelo ciclo da safra.
        verificar(persistido.listarDoContexto(contexto).size() == 1, "consulta pelo ciclo da safra");
        // Confere a expectativa do teste: atividade isolada.
        verificar(persistido.buscar(c.b, c.atividadeA).isEmpty(), "atividade isolada");
        // Confere a expectativa do teste: histórico isolado.
        verificar(persistido.listarHistorico(c.b, c.atividadeA).isEmpty(), "histórico isolado");
        // Confere a expectativa do teste: status isolado entre propriedades.
        verificar(persistido.statusAtual(c.extra, c.atividadeA).isEmpty(), "status isolado entre propriedades");
    }

    // Testa referências entre propriedade, cultura, talhão e safra, incluindo leitura após reconexão.
    private static void validarCultivo(CenarioDadosAtlas c) {
        // Cria o repositório CultivoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        CultivoRepositorioMongo r = new CultivoRepositorioMongo(c.conexao.banco());
        // Confere que uma cultura de outro produtor não pode ser vinculada ao registro deste escopo.
        invalido(() -> r.inserir(c.safra(c.a, c.culturaB, List.of(c.talhaoA))));
        // Confere que um talhão de outro produtor não pode ser usado neste contexto.
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(c.talhaoB))));
        // Confere que um talhão de outra propriedade do mesmo produtor também é rejeitado.
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(c.talhaoExtra))));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(c.safra(c.a, new ObjectId(), List.of(c.talhaoA))));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(new ObjectId()))));
        // Confere que o caso inválido de dados do domínio é rejeitado pela validação.
        invalido(() -> r.inserir(new Cultura(null, new EscopoPropriedade(c.produtorB, c.propriedadeA), "Alheia")));
        // Confere a rejeição de talhão com campo obrigatório ou referência inválida neste cenário.
        invalido(() -> r.inserir(new Talhao(null, new EscopoPropriedade(c.produtorA, new ObjectId()),
                // Completa o talhão de teste cujo escopo aponta para uma propriedade inexistente.
                "Sem propriedade", BigDecimal.ONE, Talhao.Status.ATIVO)));
        // Abre outra conexão para que as próximas verificações comprovem dados persistidos no banco.
        c.reabrir();
        // Cria o repositório CultivoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        CultivoRepositorioMongo persistido = new CultivoRepositorioMongo(c.conexao.banco());
        // Confere a expectativa do teste: área deve permanecer exata após reconectar.
        verificar(persistido.buscarTalhao(c.a, c.talhaoA).orElseThrow().areaHectares().compareTo(new BigDecimal("12.75")) == 0,
                // Identifica a expectativa deste caso: área deve permanecer exata após reconectar.
                "área deve permanecer exata após reconectar");
        // Confere a expectativa do teste: cultura persistida.
        verificar(persistido.buscarCultura(c.a, c.culturaA).orElseThrow().nome().equals("Soja"), "cultura persistida");
        // Confere a expectativa do teste: consulta do ciclo por talhão.
        verificar(persistido.listarSafrasDoTalhao(c.a, c.talhaoA).size() == 1, "consulta do ciclo por talhão");
        // Confere a expectativa do teste: safra não pode vazar entre produtores.
        verificar(persistido.buscarSafra(c.b, c.safraA).isEmpty(), "safra não pode vazar entre produtores");
        // Confere a expectativa do teste: talhão não pode vazar entre propriedades.
        verificar(persistido.buscarTalhao(c.extra, c.talhaoA).isEmpty(), "talhão não pode vazar entre propriedades");
        // Confere a expectativa do teste: cultura não pode vazar entre produtores.
        verificar(persistido.buscarCultura(c.b, c.culturaA).isEmpty(), "cultura não pode vazar entre produtores");
    }

    // Conclui o ciclo e verifica que histórico e registros associados permanecem consultáveis e protegidos.
    private static void validarVinculosDaSafra(CenarioDadosAtlas c) {
        // Cria o repositório CultivoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        CultivoRepositorioMongo persistido = new CultivoRepositorioMongo(c.conexao.banco());
        // Relê o ciclo antes de avançar todas as etapas até a finalização.
        Safra ciclo = persistido.buscarSafra(c.a, c.safraA).orElseThrow();
        // Percorre as etapas seguintes na ordem oficial, sem pular nenhuma fase.
        for (Safra.Status etapa : new Safra.Status[] { Safra.Status.PREPARACAO, Safra.Status.PLANTIO,
                // Inclui manejo, colheita e finalização para completar o ciclo iniciado em planejamento.
                Safra.Status.MANEJO, Safra.Status.COLHEITA, Safra.Status.FINALIZADA }) {
            // Persiste o próximo estado e mantém o objeto retornado com o histórico atualizado.
            ciclo = persistido.avancarSafra(c.a, c.safraA, etapa);
        }
        // Guarda o ciclo finalizado para os testes que devem rejeitar alterações posteriores.
        Safra finalizada = ciclo;
        // Confere a expectativa do teste: fases devem avançar em ordem até a finalização.
        verificar(finalizada.status() == Safra.Status.FINALIZADA,
                // Identifica a expectativa deste caso: fases devem avançar em ordem até a finalização.
                "fases devem avançar em ordem até a finalização");
        // Confere a expectativa do teste: histórico de etapas permanece no documento finalizado.
        verificar(finalizada.historicoEtapas().size() == 6,
                // Identifica a expectativa deste caso: histórico de etapas permanece no documento finalizado.
                "histórico de etapas permanece no documento finalizado");
        // Confere que não se pode repetir ou pular a etapa prevista no avanço da safra.
        invalido(() -> persistido.avancarSafra(c.a, c.safraA, Safra.Status.FINALIZADA));
        // Confere que o ciclo finalizado rejeita alterações de seus dados.
        invalido(() -> persistido.atualizarSafra(finalizada));
        // Confere que a safra finalizada ou com vínculos operacionais não pode ser removida.
        invalido(() -> persistido.excluirSafra(c.a, c.safraA));
        // Confere que uma cultura com safra vinculada permanece disponível para o histórico.
        invalido(() -> persistido.excluirCultura(c.a, c.culturaA));
        // Mesmo talhão + período cruzado deve ser rejeitado (regra alinhada ao servidor HTTP).
        // Confere que o caso inválido de safra é rejeitado pela validação.
        invalido(() -> persistido.inserir(Safra.nova(null, c.a, "Conflito período", c.culturaA,
                // Usa o mesmo talhão e um período que se cruza com o da safra existente.
                List.of(c.talhaoA), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31),
                // Completa a área positiva do exemplo para que o conflito seja especificamente de período.
                new BigDecimal("1.0"))));
        // Abre outra conexão para que as próximas verificações comprovem dados persistidos no banco.
        c.reabrir();
        // Cria o repositório CultivoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
        CultivoRepositorioMongo reaberto = new CultivoRepositorioMongo(c.conexao.banco());
        // Relê a safra finalizada depois de abrir outro cliente para comprovar sua persistência.
        Safra lida = reaberto.buscarSafra(c.a, c.safraA).orElseThrow();
        // Confere a expectativa do teste: finalização permanece consultável após reconectar.
        verificar(lida.status() == Safra.Status.FINALIZADA,
                // Identifica a expectativa deste caso: finalização permanece consultável após reconectar.
                "finalização permanece consultável após reconectar");
        // Confere a expectativa do teste: trilha de etapas sobrevive à reconexão.
        verificar(lida.historicoEtapas().size() == 6,
                // Identifica a expectativa deste caso: trilha de etapas sobrevive à reconexão.
                "trilha de etapas sobrevive à reconexão");
        // Confere a expectativa do teste: consulta da propriedade inclui histórico finalizado.
        verificar(reaberto.listarSafras(c.a).stream().anyMatch(s -> s.id().equals(c.safraA)),
                // Identifica a expectativa deste caso: consulta da propriedade inclui histórico finalizado.
                "consulta da propriedade inclui histórico finalizado");
        // Confere a expectativa do teste: atividades continuam consultáveis pela safra finalizada.
        verificar(new AtividadeRepositorioMongo(c.conexao.banco()).listarDaSafra(c.a, c.safraA).size() == 2,
                // Identifica a expectativa deste caso: atividades continuam consultáveis pela safra finalizada.
                "atividades continuam consultáveis pela safra finalizada");
        // Confere a expectativa do teste: ocorrências continuam consultáveis pela safra finalizada.
        verificar(new OcorrenciaRepositorioMongo(c.conexao.banco()).listarDaSafra(c.a, c.safraA).size() == 1,
                // Identifica a expectativa deste caso: ocorrências continuam consultáveis pela safra finalizada.
                "ocorrências continuam consultáveis pela safra finalizada");
        // Confere a expectativa do teste: custos continuam consultáveis pela safra finalizada.
        verificar(new CustoRepositorioMongo(c.conexao.banco()).listarDaSafra(c.a, c.safraA).size() == 1,
                // Identifica a expectativa deste caso: custos continuam consultáveis pela safra finalizada.
                "custos continuam consultáveis pela safra finalizada");
        // Confere que outro produtor não consegue consultar os registros desta safra.
        invalido(() -> new AtividadeRepositorioMongo(c.conexao.banco()).listarDaSafra(c.b, c.safraA));
    }
}
