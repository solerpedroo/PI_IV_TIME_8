package br.com.agrogestao.core.infra.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoDatabase;
import br.com.agrogestao.core.domain.cultivo.*;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static com.mongodb.client.model.Indexes.ascending;

public final class CultivoRepositorioMongo {
    private final MongoDatabase banco;
    private final VinculosMongo vinculos;

    public CultivoRepositorioMongo(MongoDatabase banco) {
        this.banco = banco;
        this.vinculos = new VinculosMongo(banco);
    }

    public void criarIndices() {
        // Nome único por propriedade: dois talhões/culturas/safras iguais no mesmo escopo são erro.
        // Se o Atlas ainda tiver o índice antigo (não único), apague-o antes de subir o core.
        banco.getCollection("talhoes").createIndex(ascending("proprietarioId", "propriedadeId", "nome"),
                new com.mongodb.client.model.IndexOptions().unique(true).name("talhao_nome_unico"));
        banco.getCollection("culturas").createIndex(ascending("proprietarioId", "propriedadeId", "nome"),
                new com.mongodb.client.model.IndexOptions().unique(true).name("cultura_nome_unico"));
        banco.getCollection("safras").createIndex(ascending("proprietarioId", "propriedadeId", "nome"),
                new com.mongodb.client.model.IndexOptions().unique(true).name("safra_nome_unico"));
        banco.getCollection("safras").createIndex(ascending("proprietarioId", "propriedadeId", "talhaoIds", "inicio"));
    }

    public ObjectId inserir(Talhao talhao) {
        vinculos.exigirPropriedade(talhao.escopo());
        return inserir("talhoes", talhao.paraDocumento());
    }

    public ObjectId inserir(Cultura cultura) {
        vinculos.exigirPropriedade(cultura.escopo());
        return inserir("culturas", cultura.paraDocumento());
    }

    public ObjectId inserir(Safra safra) {
        vinculos.exigirPropriedade(safra.escopo());
        vinculos.exigir("culturas", safra.escopo(), safra.culturaId());
        for (ObjectId talhaoId : safra.talhaoIds()) vinculos.exigir("talhoes", safra.escopo(), talhaoId);
        return inserir("safras", safra.paraDocumento());
    }

    public Optional<Talhao> buscarTalhao(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(banco.getCollection("talhoes").find(vinculos.filtro(escopo, id)).first())
                .map(Talhao::doDocumento);
    }

    public Optional<Cultura> buscarCultura(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(banco.getCollection("culturas").find(vinculos.filtro(escopo, id)).first())
                .map(Cultura::doDocumento);
    }

    public Optional<Safra> buscarSafra(EscopoPropriedade escopo, ObjectId id) {
        return Optional.ofNullable(banco.getCollection("safras").find(vinculos.filtro(escopo, id)).first())
                .map(Safra::doDocumento);
    }

    public List<Safra> listarSafrasDoTalhao(EscopoPropriedade escopo, ObjectId talhaoId) {
        vinculos.exigir("talhoes", escopo, talhaoId);
        return banco.getCollection("safras").find(escopo.documento().append("talhaoIds", talhaoId))
                .map(Safra::doDocumento).into(new ArrayList<>());
    }

    private ObjectId inserir(String colecao, Document documento) {
        banco.getCollection(colecao).insertOne(documento);
        return documento.getObjectId("_id");
    }
}
