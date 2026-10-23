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
    MongoConexao conexao = MongoConexao.peloAmbiente();
    final ObjectId produtorA = new ObjectId();
    final ObjectId produtorB = new ObjectId();
    final ObjectId propriedadeA = new ObjectId();
    final ObjectId propriedadeB = new ObjectId();
    final ObjectId propriedadeExtra = new ObjectId();
    final EscopoPropriedade a = new EscopoPropriedade(produtorA, propriedadeA);
    final EscopoPropriedade b = new EscopoPropriedade(produtorB, propriedadeB);
    final EscopoPropriedade extra = new EscopoPropriedade(produtorA, propriedadeExtra);
    ObjectId talhaoA, talhaoB, talhaoExtra, culturaA, culturaB, safraA, atividadeA, insumoA, consumoA;

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
        return new Safra(null, escopo, "Safra 2026", cultura, talhoes, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), new BigDecimal("12.75"), Safra.Status.MANEJO);
    }

    void reabrir() {
        conexao.close();
        conexao = MongoConexao.peloAmbiente();
        conexao.verificar();
    }

    @Override public void close() {
        try {
            for (String colecao : List.of("movimentacoes_insumos", "insumos", "atividades_historico", "atividades", "safras", "culturas", "talhoes")) {
                for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                    conexao.banco().getCollection(colecao).deleteMany(escopo.documento());
                }
            }
            for (EscopoPropriedade escopo : List.of(a, b, extra)) {
                conexao.banco().getCollection("propriedades").deleteOne(new Document("_id", escopo.propriedadeId())
                        .append("proprietarioId", escopo.proprietarioId()));
            }
            for (ObjectId id : List.of(produtorA, produtorB)) {
                conexao.banco().getCollection("usuarios").deleteOne(new Document("_id", id));
            }
        } finally { conexao.close(); }
    }
}
