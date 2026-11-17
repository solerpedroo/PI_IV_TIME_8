// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa as listas de vínculos, eventos e resultados.
import java.util.List;
// Importa a composição dos estados do histórico em texto.
import java.util.stream.Collectors;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.atividades.Atividade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.atividades.Atividade;
// Importa br.com.agrogestao.core.domain.custos.Custo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.custos.Custo;
// Importa br.com.agrogestao.core.domain.insumos.MovimentacaoInsumo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.insumos.MovimentacaoInsumo;
// Importa br.com.agrogestao.core.domain.ocorrencias.Ocorrencia para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.ocorrencias.Ocorrencia;
// Importa br.com.agrogestao.core.domain.operacao.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.*;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.DadosCultivoTest.verificar, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.verificar;

/** Processo independente e somente de leitura. Recebe apenas IDs dos dados fictícios, nunca credenciais. */
// Agrupa as operações de ConferirCenarioAtlas nesta classe.
public final class ConferirCenarioAtlas {
    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Exige exatamente os dez identificadores que descrevem o cenário, sem aceitar argumentos extras.
        if (args.length != 10) throw new IllegalArgumentException("esperados os dez IDs do cenário fictício");
        // Converte cada argumento hexadecimal em ObjectId antes de montar os escopos de consulta.
        List<ObjectId> ids = java.util.Arrays.stream(args).map(ObjectId::new).toList();
        // Define titular e propriedade que serão usados neste cenário de teste.
        EscopoPropriedade a = new EscopoPropriedade(ids.get(0), ids.get(1));
        // Define titular e propriedade que serão usados neste cenário de teste.
        EscopoPropriedade b = new EscopoPropriedade(ids.get(2), ids.get(3));
        // Associa as posições seguintes aos ids de talhão, safra e atividade.
        ObjectId talhaoId = ids.get(4), safraId = ids.get(5), atividadeId = ids.get(6),
                // Associa as últimas posições aos ids de insumo, consumo e ocorrência.
                insumoId = ids.get(7), consumoId = ids.get(8), ocorrenciaId = ids.get(9);

        // Abre o recurso para esta operação e garante seu fechamento ao sair do bloco, inclusive em caso de falha.
        try (MongoConexao conexao = MongoConexao.paraTestes()) {
            // Executa ping no banco antes de continuar o cenário de persistência.
            conexao.verificar();
            // Cria o repositório CultivoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
            CultivoRepositorioMongo cultivo = new CultivoRepositorioMongo(conexao.banco());
            // Cria o repositório AtividadeRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
            AtividadeRepositorioMongo atividades = new AtividadeRepositorioMongo(conexao.banco());
            // Cria o repositório InsumoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
            InsumoRepositorioMongo insumos = new InsumoRepositorioMongo(conexao.banco());
            // Cria o repositório OcorrenciaRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
            OcorrenciaRepositorioMongo ocorrencias = new OcorrenciaRepositorioMongo(conexao.banco());
            // Cria o repositório CustoRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
            CustoRepositorioMongo custos = new CustoRepositorioMongo(conexao.banco());
            // Relê o talhão no escopo autorizado e falha se o registro não estiver persistido.
            var talhao = cultivo.buscarTalhao(a, talhaoId).orElseThrow();
            // Relê a safra do mesmo escopo na conexão independente.
            var safra = cultivo.buscarSafra(a, safraId).orElseThrow();
            // Relê a cultura apontada pela safra para conferir o vínculo produtivo.
            var cultura = cultivo.buscarCultura(a, safra.culturaId()).orElseThrow();
            // Confere a expectativa do teste: safra deve referenciar o talhão.
            verificar(safra.talhaoIds().contains(talhaoId), "safra deve referenciar o talhão");
            // Relê a atividade pelo id conhecido e pelo escopo do produtor A.
            var atividade = atividades.buscar(a, atividadeId).orElseThrow();
            // Confere a expectativa do teste: atividade deve referenciar a safra.
            verificar(atividade.contexto().safraId().equals(safraId), "atividade deve referenciar a safra");
            // Confere a expectativa do teste: atividade concluída persistida.
            verificar(atividades.statusAtual(a, atividadeId).orElseThrow() == Atividade.Status.CONCLUIDA, "atividade concluída persistida");
            // Obtém o histórico completo da atividade para conferir sequência e quantidade de eventos.
            var historico = atividades.listarHistorico(a, atividadeId);
            // Confere a expectativa do teste: três eventos da atividade preservados.
            verificar(historico.size() == 3, "três eventos da atividade preservados");
            // Relê o catálogo do insumo do produtor A.
            var insumo = insumos.buscar(a, insumoId).orElseThrow();
            // Busca as entradas, saídas e ajustes persistidos para esse insumo.
            var movimentos = insumos.listarMovimentacoes(a, insumoId);
            // Soma a variação assinada de cada movimento com BigDecimal, sem erro de ponto flutuante.
            BigDecimal variacao = movimentos.stream().map(MovimentacaoInsumo::variacao).reduce(BigDecimal.ZERO, BigDecimal::add);
            // Confere a expectativa do teste: histórico representa 500 - 50 kg.
            verificar(movimentos.size() == 2 && variacao.compareTo(new BigDecimal("450")) == 0, "histórico representa 500 - 50 kg");
            // Relê o consumo específico para conferir sua referência à atividade.
            var consumo = insumos.buscarMovimentacao(a, consumoId).orElseThrow();
            // Confere a expectativa do teste: consumo referencia a atividade.
            verificar(consumo.atividadeId().equals(atividadeId), "consumo referencia a atividade");
            // Confere a expectativa do teste: ocorrência vinculada.
            verificar(ocorrencias.buscar(a, ocorrenciaId).orElseThrow().contexto().safraId().equals(safraId), "ocorrência vinculada");
            // Confere a expectativa do teste: resolução persistida.
            verificar(ocorrencias.statusAtual(a, ocorrenciaId).orElseThrow() == Ocorrencia.Status.RESOLVIDA, "resolução persistida");
            // Consulta a linha do tempo persistida da ocorrência.
            var timeline = ocorrencias.listarTimeline(a, ocorrenciaId);
            // Confere a expectativa do teste: quatro eventos da ocorrência preservados.
            verificar(timeline.size() == 4, "quatro eventos da ocorrência preservados");
            // Filtra os custos pelo conjunto de propriedade, talhão e safra do cenário.
            var custosDoCiclo = custos.listarDoContexto(new ContextoOperacao(a, talhaoId, safraId));
            // Confere a expectativa do teste: um custo direto no ciclo.
            verificar(custosDoCiclo.size() == 1, "um custo direto no ciclo");
            // Obtém o único custo direto cuja quantidade foi conferida na instrução anterior.
            Custo custo = custosDoCiclo.getFirst();
            // Confere a expectativa do teste: origem do gasto preservada.
            verificar(consumoId.equals(custo.consumoId()) && atividadeId.equals(custo.atividadeId()), "origem do gasto preservada");
            // Confere a expectativa do teste: custo direto exato.
            verificar(custo.valor().compareTo(new BigDecimal("250.00")) == 0, "custo direto exato");
            // Soma os custos de toda a propriedade com aritmética decimal exata.
            BigDecimal total = custos.listarDaPropriedade(a).stream().map(Custo::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            // Confere a expectativa do teste: total da propriedade exato.
            verificar(total.compareTo(new BigDecimal("300.00")) == 0, "total da propriedade exato");
            // Confere a expectativa do teste: outro produtor não pode consultar registros do cenário.
            verificar(cultivo.buscarTalhao(b, talhaoId).isEmpty() && cultivo.buscarSafra(b, safraId).isEmpty()
                    // Confere também que atividade e histórico ficam invisíveis no escopo de B.
                    && atividades.buscar(b, atividadeId).isEmpty() && atividades.listarHistorico(b, atividadeId).isEmpty()
                    // Confere o isolamento do catálogo de insumos e das movimentações.
                    && insumos.buscar(b, insumoId).isEmpty() && insumos.buscarMovimentacao(b, consumoId).isEmpty()
                    // Confere o isolamento da ocorrência e de sua linha do tempo.
                    && ocorrencias.buscar(b, ocorrenciaId).isEmpty() && ocorrencias.listarTimeline(b, ocorrenciaId).isEmpty()
                    // Inclui o custo no teste de isolamento conjunto entre produtores.
                    && custos.buscar(b, custo.id()).isEmpty(), "outro produtor não pode consultar registros do cenário");

            // Exibe o resultado desta etapa no console: Leitura independente no processo Java .
            System.out.println("Leitura independente no processo Java " + ProcessHandle.current().pid()
                    // Acrescenta o nome do banco efetivamente consultado pela JVM independente.
                    + " — banco " + conexao.banco().getName());
            // Exibe o resultado desta etapa no console: #101: .
            System.out.println("#101: " + talhao.nome() + " / " + cultura.nome() + " / " + safra.nome()
                    // Apresenta a área decimal e as datas da safra sem alterar os valores consultados.
                    + " / " + talhao.areaHectares().toPlainString() + " ha / " + safra.inicio() + " até " + safra.fim());
            // Exibe o resultado desta etapa no console: #102: .
            System.out.println("#102: " + atividade.titulo() + " / " + estados(historico));
            // Exibe o resultado desta etapa no console: #103: .
            System.out.println("#103: " + insumo.nome() + " / entrada 500 kg / consumo 50 kg / variação líquida do histórico "
                    // Apresenta a variação líquida do histórico e o estoque mínimo do catálogo.
                    + variacao.toPlainString() + " kg / mínimo " + insumo.estoqueMinimo().toPlainString() + " kg");
            // Exibe o resultado desta etapa no console: #104: .
            System.out.println("#104: " + estados(timeline) + " / " + timeline.size() + " eventos preservados");
            // Exibe o resultado desta etapa no console: #105: custo do consumo R$ .
            System.out.println("#105: custo do consumo R$ " + custo.valor().toPlainString() + " / total da propriedade R$ " + total.toPlainString());
            // Exibe o resultado desta etapa no console: Isolamento: consultas do outro produtor retornaram vazio em todos os módulos.
            System.out.println("Isolamento: consultas do outro produtor retornaram vazio em todos os módulos.");
            // Consulta o documento bruto do custo pelo escopo de A e seu id para comprovar a gravação real no Atlas.
            Document documento = conexao.banco().getCollection("custos").find(a.documento().append("_id", custo.id())).first();
            // Confere a expectativa do teste: documento bruto do custo deve existir no Atlas.
            verificar(documento != null, "documento bruto do custo deve existir no Atlas");
            // Exibe o resultado desta etapa no console: Documento real de custo lido do Atlas (somente dados fictícios):.
            System.out.println("Documento real de custo lido do Atlas (somente dados fictícios):");
            // Exibe o diagnóstico desta etapa no console.
            System.out.println(documento.toJson());
            // Exibe o resultado desta etapa no console: ConferirCenarioAtlas: OK (leitura em outro processo, sem regravar os dados).
            System.out.println("ConferirCenarioAtlas: OK (leitura em outro processo, sem regravar os dados)");
        }
    }

    // Formata os estados do histórico em ordem para apresentar a trilha de evolução no console.
    private static String estados(List<EventoHistorico> eventos) {
        // Extrai os estados na ordem dos eventos e os une com setas para exibir a evolução.
        return eventos.stream().map(EventoHistorico::status).collect(Collectors.joining(" → "));
    }
}
