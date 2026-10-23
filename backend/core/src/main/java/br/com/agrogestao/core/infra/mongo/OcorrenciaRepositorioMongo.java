package br.com.agrogestao.core.infra.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import br.com.agrogestao.core.domain.ocorrencias.Ocorrencia;
import br.com.agrogestao.core.domain.operacao.*;
import static com.mongodb.client.model.Indexes.ascending;

public final class OcorrenciaRepositorioMongo {
    private final MongoCollection<Document> ocorrencias;
    private final VinculosMongo vinculos;
    private final HistoricoMongo historico;

    public OcorrenciaRepositorioMongo(MongoDatabase banco) {
        ocorrencias = banco.getCollection("ocorrencias");
        vinculos = new VinculosMongo(banco);
        historico = new HistoricoMongo(banco, "ocorrencias_timeline", "ocorrenciaId");
    }

    public void criarIndices() {
        ocorrencias.createIndex(ascending("proprietarioId", "propriedadeId", "talhaoId", "safraId", "data"));
        historico.criarIndices();
    }

    public ObjectId inserir(Ocorrencia ocorrencia) {
        vinculos.exigirContexto(ocorrencia.contexto());
        Document d = ocorrencia.paraDocumento();
        ocorrencias.insertOne(d);
        return d.getObjectId("_id");
    }

    public Optional<Ocorrencia> buscar(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(ocorrencias.find(vinculos.filtro(escopo, id)).first()).map(Ocorrencia::doDocumento);
    }

    public List<Ocorrencia> listarDoContexto(ContextoOperacao contexto) {
        vinculos.exigirContexto(contexto);
        return ocorrencias.find(contexto.documento()).map(Ocorrencia::doDocumento).into(new ArrayList<>());
    }

    public List<EventoHistorico> listarTimeline(EscopoPropriedade escopo, ObjectId id) {
        return buscar(escopo, id).map(o -> historico.listar(escopo, id, o.paraDocumento())).orElse(List.of());
    }

    public Optional<Ocorrencia.Status> statusAtual(EscopoPropriedade escopo, ObjectId id) {
        return buscar(escopo, id).map(o -> Ocorrencia.Status.valueOf(historico.ultimo(escopo, id, o.paraDocumento()).status()));
    }

    public void registrarAtualizacao(EscopoPropriedade escopo, ObjectId id, EventoHistorico evento) {
        Document original = vinculos.exigir("ocorrencias", escopo, id);
        historico.adicionar(escopo, id, original, evento, Ocorrencia::permiteTransicao);
    }
}
