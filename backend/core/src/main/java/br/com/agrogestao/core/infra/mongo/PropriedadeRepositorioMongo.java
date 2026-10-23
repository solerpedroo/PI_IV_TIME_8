// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.infra.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Indexes.ascending;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

import br.com.agrogestao.core.domain.propriedades.Propriedade;
import br.com.agrogestao.core.domain.propriedades.PropriedadeRepository;

public final class PropriedadeRepositorioMongo implements PropriedadeRepository {
    private final MongoCollection<Document> propriedades;
    private final MongoCollection<Document> usuarios;

    public PropriedadeRepositorioMongo(MongoDatabase banco) {
        this.propriedades = banco.getCollection("propriedades");
        this.usuarios = banco.getCollection("usuarios");
    }

    public void criarIndices() {
        propriedades.createIndex(
                ascending("proprietarioId", "nome"),
                new IndexOptions().unique(true).name("proprietario_nome_unico"));
    }

    public ObjectId inserir(Propriedade propriedade) {
        if (usuarios.find(eq("_id", propriedade.getProprietarioId())).first() == null) {
            throw new IllegalArgumentException("proprietarioId não referencia um usuário existente");
        }
        Document documento = propriedade.paraDocumento();
        propriedades.insertOne(documento);
        return documento.getObjectId("_id");
    }

    @Override
    public Optional<Propriedade> buscarDoProprietario(ObjectId id, ObjectId proprietarioId) {
        Document documento = propriedades.find(and(eq("_id", id), eq("proprietarioId", proprietarioId))).first();
        return Optional.ofNullable(documento).map(Propriedade::doDocumento);
    }

    public List<Propriedade> listarDoProprietario(ObjectId proprietarioId) {
        List<Propriedade> resultado = new ArrayList<>();
        propriedades.find(eq("proprietarioId", proprietarioId))
                .map(Propriedade::doDocumento)
                .into(resultado);
        return resultado;
    }
}
