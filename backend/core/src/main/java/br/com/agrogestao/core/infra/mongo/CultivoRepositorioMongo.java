// Miguel — RA: 25003110
package br.com.agrogestao.core.infra.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.MongoDatabase;
import br.com.agrogestao.core.domain.cultivo.*;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import br.com.agrogestao.core.domain.operacao.RecursoNaoEncontradoException;
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
        inserirReferenciasValidas(safra);
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

    public List<Cultura> listarCulturas(EscopoPropriedade escopo) {
        vinculos.exigirPropriedade(escopo);
        return banco.getCollection("culturas").find(escopo.documento()).map(Cultura::doDocumento).into(new ArrayList<>());
    }

    public Cultura atualizarCultura(Cultura cultura) {
        if (cultura.id() == null) throw new IllegalArgumentException("id é obrigatório");
        vinculos.exigirPropriedade(cultura.escopo());
        if (banco.getCollection("culturas").replaceOne(vinculos.filtro(cultura.escopo(), cultura.id()), cultura.paraDocumento()).getMatchedCount() == 0) {
            throw new RecursoNaoEncontradoException("cultura não encontrada no escopo");
        }
        return cultura;
    }

    /** Uma cultura com safra não é apagada para não quebrar o histórico produtivo. */
    public void excluirCultura(EscopoPropriedade escopo, ObjectId id) {
        buscarCultura(escopo, id).orElseThrow(() -> new RecursoNaoEncontradoException("cultura não encontrada no escopo"));
        if (referenciada(escopo, "safras", "culturaId", id) || referenciada(escopo, "custos", "culturaId", id)) {
            throw new IllegalArgumentException("cultura possui safras e deve permanecer no histórico");
        }
        banco.getCollection("culturas").deleteOne(vinculos.filtro(escopo, id));
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

    /** Consulta inclusive ciclos finalizados para preservar o histórico da propriedade. */
    public List<Safra> listarSafras(EscopoPropriedade escopo) {
        vinculos.exigirPropriedade(escopo);
        return banco.getCollection("safras").find(escopo.documento()).map(Safra::doDocumento).into(new ArrayList<>());
    }

    public Safra atualizarSafra(Safra safra) {
        if (safra.id() == null) throw new IllegalArgumentException("id é obrigatório");
        Safra atual = buscarSafra(safra.escopo(), safra.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("safra não encontrada no escopo"));
        if (atual.status() == Safra.Status.FINALIZADA) {
            throw new IllegalArgumentException("safra finalizada é somente para consulta histórica");
        }
        if (atual.status() != safra.status()) {
            throw new IllegalArgumentException("status só pode mudar pelo avanço sequencial do ciclo");
        }
        inserirReferenciasValidas(safra);
        if (banco.getCollection("safras").replaceOne(vinculos.filtro(safra.escopo(), safra.id()), safra.paraDocumento()).getMatchedCount() == 0) {
            throw new RecursoNaoEncontradoException("safra não encontrada no escopo");
        }
        return safra;
    }

    public Safra avancarSafra(EscopoPropriedade escopo, ObjectId id, Safra.Status proximo) {
        Safra atual = buscarSafra(escopo, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("safra não encontrada no escopo"));
        Safra avancada = atual.avancar(proximo);
        Document filtro = vinculos.filtro(escopo, id).append("status", atual.status().name());
        if (banco.getCollection("safras").replaceOne(filtro, avancada.paraDocumento()).getMatchedCount() == 0) {
            throw new IllegalArgumentException("safra alterada por outra operação; consulte novamente");
        }
        return avancada;
    }

    public void excluirSafra(EscopoPropriedade escopo, ObjectId id) {
        Safra safra = buscarSafra(escopo, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("safra não encontrada no escopo"));
        if (safra.status() != Safra.Status.PLANEJAMENTO) {
            throw new IllegalArgumentException("somente safras em planejamento podem ser excluídas");
        }
        if (referenciada(escopo, "atividades", "safraId", id) || referenciada(escopo, "ocorrencias", "safraId", id)
                || referenciada(escopo, "custos", "safraId", id)) {
            throw new IllegalArgumentException("safra possui registros operacionais e deve permanecer no histórico");
        }
        banco.getCollection("safras").deleteOne(vinculos.filtro(escopo, id));
    }

    private void inserirReferenciasValidas(Safra safra) {
        vinculos.exigirPropriedade(safra.escopo());
        vinculos.exigir("culturas", safra.escopo(), safra.culturaId());
        for (ObjectId talhaoId : safra.talhaoIds()) vinculos.exigir("talhoes", safra.escopo(), talhaoId);
        exigirSemSobreposicao(safra);
    }

    /**
     * Um talhão não pode estar em duas safras cujos períodos se cruzam (mesma regra do servidor HTTP).
     * Intervalos inclusivos em data de calendário: overlap se inicioA ≤ fimB e inicioB ≤ fimA.
     */
    private void exigirSemSobreposicao(Safra safra) {
        for (Safra outra : listarSafras(safra.escopo())) {
            if (safra.id() != null && safra.id().equals(outra.id())) continue;
            boolean talhaoCompartilhado = outra.talhaoIds().stream().anyMatch(safra.talhaoIds()::contains);
            if (!talhaoCompartilhado) continue;
            boolean overlap = !safra.inicio().isAfter(outra.fim()) && !outra.inicio().isAfter(safra.fim());
            if (overlap) {
                throw new IllegalArgumentException(
                        "talhão já vinculado a outra safra no mesmo período");
            }
        }
    }

    private boolean referenciada(EscopoPropriedade escopo, String colecao, String campo, ObjectId id) {
        return banco.getCollection(colecao).find(escopo.documento().append(campo, id)).first() != null;
    }

    private ObjectId inserir(String colecao, Document documento) {
        banco.getCollection(colecao).insertOne(documento);
        return documento.getObjectId("_id");
    }
}
