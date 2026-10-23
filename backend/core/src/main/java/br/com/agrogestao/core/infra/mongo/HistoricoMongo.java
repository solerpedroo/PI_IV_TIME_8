package br.com.agrogestao.core.infra.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import br.com.agrogestao.core.domain.operacao.*;

/** Histórico é a fonte do estado atual; evita gravar status e histórico em operações separadas. */
final class HistoricoMongo {
    private final MongoCollection<Document> eventos;
    private final String campoRegistro;

    HistoricoMongo(MongoDatabase banco, String colecao, String campoRegistro) {
        this.eventos = banco.getCollection(colecao);
        this.campoRegistro = campoRegistro;
    }

    void criarIndices() {
        eventos.createIndex(com.mongodb.client.model.Indexes.ascending("proprietarioId", "propriedadeId", campoRegistro, "ordem"),
                new IndexOptions().unique(true).name("registro_ordem_unica"));
    }

    List<EventoHistorico> listar(EscopoPropriedade escopo, ObjectId id, Document original) {
        List<EventoHistorico> resultado = new ArrayList<>();
        resultado.add(new EventoHistorico(0, original.getDate("criadaEm").toInstant(),
                original.getString("statusInicial"), "Registro criado"));
        eventos.find(escopo.documento().append(campoRegistro, id)).sort(com.mongodb.client.model.Sorts.ascending("ordem"))
                .map(EventoHistorico::doDocumento).into(resultado);
        return List.copyOf(resultado);
    }

    void adicionar(EscopoPropriedade escopo, ObjectId id, Document original, EventoHistorico evento,
            BiPredicate<String, String> permiteTransicao) {
        EventoHistorico ultimo = listar(escopo, id, original).getLast();
        if (evento.ordem() != ultimo.ordem() + 1) {
            throw new IllegalArgumentException("ordem desatualizada; consulte o histórico antes de gravar");
        }
        if (evento.registradoEm().isBefore(ultimo.registradoEm())) {
            throw new IllegalArgumentException("evento não pode anteceder o histórico existente");
        }
        if (!permiteTransicao.test(ultimo.status(), evento.status())) {
            throw new IllegalArgumentException("transição de status inválida");
        }
        Document d = escopo.documento().append(campoRegistro, id);
        d.putAll(evento.paraDocumento());
        try {
            eventos.insertOne(d);
        } catch (MongoWriteException e) {
            if (e.getCode() == 11000) throw new IllegalArgumentException("histórico alterado por outra gravação; consulte novamente");
            throw e;
        }
    }
}
