package br.com.agrogestao.core.infra.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import br.com.agrogestao.core.domain.atividades.Atividade;
import br.com.agrogestao.core.domain.operacao.*;
import static com.mongodb.client.model.Indexes.ascending;

public final class AtividadeRepositorioMongo {
    private final MongoCollection<Document> atividades;
    private final VinculosMongo vinculos;
    private final HistoricoMongo historico;

    public AtividadeRepositorioMongo(MongoDatabase banco) {
        atividades = banco.getCollection("atividades");
        vinculos = new VinculosMongo(banco);
        historico = new HistoricoMongo(banco, "atividades_historico", "atividadeId");
    }

    public void criarIndices() {
        atividades.createIndex(ascending("proprietarioId", "propriedadeId", "talhaoId", "safraId", "data"));
        historico.criarIndices();
    }

    public ObjectId inserir(Atividade atividade) {
        vinculos.exigirContexto(atividade.contexto());
        Document d = atividade.paraDocumento();
        atividades.insertOne(d);
        return d.getObjectId("_id");
    }

    public Optional<Atividade> buscar(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(atividades.find(vinculos.filtro(escopo, id)).first()).map(Atividade::doDocumento);
    }

    public List<Atividade> listarDoContexto(ContextoOperacao contexto) {
        vinculos.exigirContexto(contexto);
        return atividades.find(contexto.documento()).map(Atividade::doDocumento).into(new ArrayList<>());
    }

    public List<EventoHistorico> listarHistorico(EscopoPropriedade escopo, ObjectId id) {
        return buscar(escopo, id).map(a -> historico.listar(escopo, id, a.paraDocumento())).orElse(List.of());
    }

    public Optional<Atividade.Status> statusAtual(EscopoPropriedade escopo, ObjectId id) {
        return buscar(escopo, id).map(a -> Atividade.Status.valueOf(historico.ultimo(escopo, id, a.paraDocumento()).status()));
    }

    public void registrarEvento(EscopoPropriedade escopo, ObjectId id, EventoHistorico evento) {
        Document original = vinculos.exigir("atividades", escopo, id);
        historico.adicionar(escopo, id, original, evento, Atividade::permiteTransicao);
    }
}
