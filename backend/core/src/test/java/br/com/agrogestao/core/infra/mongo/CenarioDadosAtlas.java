package br.com.agrogestao.core.infra.mongo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.cultivo.*;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import br.com.agrogestao.core.domain.propriedades.Propriedade;
import br.com.agrogestao.core.domain.usuarios.Usuario;

/** IDs exclusivos são conhecidos antes da primeira escrita para permitir limpeza mesmo em caso de falha. */
final class CenarioDadosAtlas implements AutoCloseable {
    // Suíte Atlas só grava em banco cujo nome termina em _teste (ver MongoConexao.paraTestes).
    MongoConexao conexao = MongoConexao.paraTestes();
    final ObjectId produtorA = new ObjectId();
    final ObjectId produtorB = new ObjectId();
    final ObjectId propriedadeA = new ObjectId();
    final ObjectId propriedadeB = new ObjectId();
    final ObjectId propriedadeExtra = new ObjectId();
    final EscopoPropriedade a = new EscopoPropriedade(produtorA, propriedadeA);
    final EscopoPropriedade b = new EscopoPropriedade(produtorB, propriedadeB);
    final EscopoPropriedade extra = new EscopoPropriedade(produtorA, propriedadeExtra);
    ObjectId talhaoA, talhaoB, talhaoExtra, culturaA, culturaB, safraA, atividadeA, insumoA, consumoA, ocorrenciaA;

    void preparar() {
        conexao.verificar();
        UsuarioRepositorioMongo usuarios = new UsuarioRepositorioMongo(conexao.banco());
        usuarios.criarIndices();
        for (ObjectId id : List.of(produtorA, produtorB)) {
            usuarios.inserir(new Usuario(id, "dados-" + id + "@teste.invalid", "Produtor fictício", "hash-ficticio", Instant.now()));
        }
        PropriedadeRepositorioMongo propriedades = new PropriedadeRepositorioMongo(conexao.banco());
        propriedades.criarIndices();
        for (EscopoPropriedade escopo : List.of(a, b, extra)) {
            propriedades.inserir(new Propriedade(escopo.propriedadeId(), escopo.proprietarioId(),
                    "Teste dados " + escopo.propriedadeId(), Instant.now()));
        }
        CultivoRepositorioMongo cultivo = new CultivoRepositorioMongo(conexao.banco());
        cultivo.criarIndices();
        talhaoA = cultivo.inserir(new Talhao(null, a, "Talhão A", new BigDecimal("12.75"), Talhao.Status.ATIVO));
        talhaoB = cultivo.inserir(new Talhao(null, b, "Talhão B", BigDecimal.TEN, Talhao.Status.ATIVO));
        talhaoExtra = cultivo.inserir(new Talhao(null, extra, "Talhão extra", BigDecimal.ONE, Talhao.Status.ATIVO));
        culturaA = cultivo.inserir(new Cultura(null, a, "Soja"));
        culturaB = cultivo.inserir(new Cultura(null, b, "Milho"));
        safraA = cultivo.inserir(safra(a, culturaA, List.of(talhaoA)));
    }

    Safra safra(EscopoPropriedade escopo, ObjectId cultura, List<ObjectId> talhoes) {
        return Safra.nova(null, escopo, "Safra 2026", cultura, talhoes, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), new BigDecimal("12.75"));
    }

    void reabrir() {
        conexao.close();
        conexao = MongoConexao.paraTestes();
        conexao.verificar();
    }

    @Override public void close() {
        // Conexão própria: a limpeza funciona mesmo se uma tentativa de reconexão do teste falhar.
        try (MongoConexao limpeza = MongoConexao.paraTestes()) {
            for (String colecao : List.of("custos", "ocorrencias_timeline", "ocorrencias", "movimentacoes_insumos", "insumos", "atividades_historico", "atividades", "safras", "culturas", "talhoes")) {
                for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                    limpeza.banco().getCollection(colecao).deleteMany(escopo.documento());
                    if (limpeza.banco().getCollection(colecao).countDocuments(escopo.documento()) != 0) {
                        throw new AssertionError("registro fictício não removido: " + colecao);
                    }
                }
            }
            for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                limpeza.banco().getCollection("propriedades").deleteOne(new Document("_id", escopo.propriedadeId())
                        .append("proprietarioId", escopo.proprietarioId()));
            }
            for (ObjectId id : List.of(produtorA, produtorB)) {
                limpeza.banco().getCollection("usuarios").deleteOne(new Document("_id", id));
            }
            for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                if (limpeza.banco().getCollection("propriedades").countDocuments(new Document("_id", escopo.propriedadeId())) != 0) {
                    throw new AssertionError("propriedade fictícia não removida");
                }
            }
            for (ObjectId id : List.of(produtorA, produtorB)) {
                if (limpeza.banco().getCollection("usuarios").countDocuments(new Document("_id", id)) != 0) {
                    throw new AssertionError("usuário fictício não removido");
                }
            }
        } finally { conexao.close(); }
    }
}
