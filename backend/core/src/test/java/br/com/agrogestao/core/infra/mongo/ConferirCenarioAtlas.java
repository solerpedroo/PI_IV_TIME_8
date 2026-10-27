package br.com.agrogestao.core.infra.mongo;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.atividades.Atividade;
import br.com.agrogestao.core.domain.custos.Custo;
import br.com.agrogestao.core.domain.insumos.MovimentacaoInsumo;
import br.com.agrogestao.core.domain.ocorrencias.Ocorrencia;
import br.com.agrogestao.core.domain.operacao.*;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.verificar;

/** Processo independente e somente de leitura. Recebe apenas IDs dos dados fictícios, nunca credenciais. */
public final class ConferirCenarioAtlas {
    public static void main(String[] args) {
        if (args.length != 10) throw new IllegalArgumentException("esperados os dez IDs do cenário fictício");
        List<ObjectId> ids = java.util.Arrays.stream(args).map(ObjectId::new).toList();
        EscopoPropriedade a = new EscopoPropriedade(ids.get(0), ids.get(1));
        EscopoPropriedade b = new EscopoPropriedade(ids.get(2), ids.get(3));
        ObjectId talhaoId = ids.get(4), safraId = ids.get(5), atividadeId = ids.get(6),
                insumoId = ids.get(7), consumoId = ids.get(8), ocorrenciaId = ids.get(9);

        try (MongoConexao conexao = MongoConexao.paraTestes()) {
            conexao.verificar();
            CultivoRepositorioMongo cultivo = new CultivoRepositorioMongo(conexao.banco());
            AtividadeRepositorioMongo atividades = new AtividadeRepositorioMongo(conexao.banco());
            InsumoRepositorioMongo insumos = new InsumoRepositorioMongo(conexao.banco());
            OcorrenciaRepositorioMongo ocorrencias = new OcorrenciaRepositorioMongo(conexao.banco());
            CustoRepositorioMongo custos = new CustoRepositorioMongo(conexao.banco());
            var talhao = cultivo.buscarTalhao(a, talhaoId).orElseThrow();
            var safra = cultivo.buscarSafra(a, safraId).orElseThrow();
            var cultura = cultivo.buscarCultura(a, safra.culturaId()).orElseThrow();
            verificar(safra.talhaoIds().contains(talhaoId), "safra deve referenciar o talhão");
            var atividade = atividades.buscar(a, atividadeId).orElseThrow();
            verificar(atividade.contexto().safraId().equals(safraId), "atividade deve referenciar a safra");
            verificar(atividades.statusAtual(a, atividadeId).orElseThrow() == Atividade.Status.CONCLUIDA, "atividade concluída persistida");
            var historico = atividades.listarHistorico(a, atividadeId);
            verificar(historico.size() == 3, "três eventos da atividade preservados");
            var insumo = insumos.buscar(a, insumoId).orElseThrow();
            var movimentos = insumos.listarMovimentacoes(a, insumoId);
            BigDecimal variacao = movimentos.stream().map(MovimentacaoInsumo::variacao).reduce(BigDecimal.ZERO, BigDecimal::add);
            verificar(movimentos.size() == 2 && variacao.compareTo(new BigDecimal("450")) == 0, "histórico representa 500 - 50 kg");
            var consumo = insumos.buscarMovimentacao(a, consumoId).orElseThrow();
            verificar(consumo.atividadeId().equals(atividadeId), "consumo referencia a atividade");
            verificar(ocorrencias.buscar(a, ocorrenciaId).orElseThrow().contexto().safraId().equals(safraId), "ocorrência vinculada");
            verificar(ocorrencias.statusAtual(a, ocorrenciaId).orElseThrow() == Ocorrencia.Status.RESOLVIDA, "resolução persistida");
            var timeline = ocorrencias.listarTimeline(a, ocorrenciaId);
            verificar(timeline.size() == 4, "quatro eventos da ocorrência preservados");
            var custosDoCiclo = custos.listarDoContexto(new ContextoOperacao(a, talhaoId, safraId));
            verificar(custosDoCiclo.size() == 1, "um custo direto no ciclo");
            Custo custo = custosDoCiclo.getFirst();
            verificar(consumoId.equals(custo.consumoId()) && atividadeId.equals(custo.atividadeId()), "origem do gasto preservada");
            verificar(custo.valor().compareTo(new BigDecimal("250.00")) == 0, "custo direto exato");
            BigDecimal total = custos.listarDaPropriedade(a).stream().map(Custo::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            verificar(total.compareTo(new BigDecimal("300.00")) == 0, "total da propriedade exato");
            verificar(cultivo.buscarTalhao(b, talhaoId).isEmpty() && cultivo.buscarSafra(b, safraId).isEmpty()
                    && atividades.buscar(b, atividadeId).isEmpty() && atividades.listarHistorico(b, atividadeId).isEmpty()
                    && insumos.buscar(b, insumoId).isEmpty() && insumos.buscarMovimentacao(b, consumoId).isEmpty()
                    && ocorrencias.buscar(b, ocorrenciaId).isEmpty() && ocorrencias.listarTimeline(b, ocorrenciaId).isEmpty()
                    && custos.buscar(b, custo.id()).isEmpty(), "outro produtor não pode consultar registros do cenário");

            System.out.println("Leitura independente no processo Java " + ProcessHandle.current().pid()
                    + " — banco " + conexao.banco().getName());
            System.out.println("#101: " + talhao.nome() + " / " + cultura.nome() + " / " + safra.nome()
                    + " / " + talhao.areaHectares().toPlainString() + " ha / " + safra.inicio() + " até " + safra.fim());
            System.out.println("#102: " + atividade.titulo() + " / " + estados(historico));
            System.out.println("#103: " + insumo.nome() + " / entrada 500 kg / consumo 50 kg / variação líquida do histórico "
                    + variacao.toPlainString() + " kg / mínimo " + insumo.estoqueMinimo().toPlainString() + " kg");
            System.out.println("#104: " + estados(timeline) + " / " + timeline.size() + " eventos preservados");
            System.out.println("#105: custo do consumo R$ " + custo.valor().toPlainString() + " / total da propriedade R$ " + total.toPlainString());
            System.out.println("Isolamento: consultas do outro produtor retornaram vazio em todos os módulos.");
            Document documento = conexao.banco().getCollection("custos").find(a.documento().append("_id", custo.id())).first();
            verificar(documento != null, "documento bruto do custo deve existir no Atlas");
            System.out.println("Documento real de custo lido do Atlas (somente dados fictícios):");
            System.out.println(documento.toJson());
            System.out.println("ConferirCenarioAtlas: OK (leitura em outro processo, sem regravar os dados)");
        }
    }

    private static String estados(List<EventoHistorico> eventos) {
        return eventos.stream().map(EventoHistorico::status).collect(Collectors.joining(" → "));
    }
}
