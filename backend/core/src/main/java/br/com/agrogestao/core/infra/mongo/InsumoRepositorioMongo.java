// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa as listas mutáveis usadas ao reunir resultados das consultas.
import java.util.ArrayList;
// Importa as listas de vínculos, eventos e resultados.
import java.util.List;
// Importa a representação explícita de uma busca sem resultado.
import java.util.Optional;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa o acesso tipado às coleções de documentos.
import com.mongodb.client.MongoCollection;
// Importa a seleção das coleções no banco compartilhado.
import com.mongodb.client.MongoDatabase;
// Importa br.com.agrogestao.core.domain.insumos.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.insumos.*;
// Importa br.com.agrogestao.core.domain.operacao.EscopoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Indexes.ascending, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Indexes.ascending;

// Agrupa as operações de InsumoRepositorioMongo nesta classe.
public final class InsumoRepositorioMongo {
    // Mantém catálogo de insumos como dependência ou dado imutável.
    private final MongoCollection<Document> insumos;
    // Mantém histórico de movimentações de insumos como dependência ou dado imutável.
    private final MongoCollection<Document> movimentacoes;
    // Mantém validador das referências no escopo da propriedade como dependência ou dado imutável.
    private final VinculosMongo vinculos;

    // Inicializa InsumoRepositorioMongo com os valores recebidos e as validações detalhadas a seguir.
    public InsumoRepositorioMongo(MongoDatabase banco) {
        // Seleciona a coleção insumos no banco recebido, sem copiar seus documentos.
        insumos = banco.getCollection("insumos");
        // Seleciona a coleção movimentacoes_insumos no banco recebido, sem copiar seus documentos.
        movimentacoes = banco.getCollection("movimentacoes_insumos");
        // Prepara o validador que confere a existência e a titularidade das referências.
        vinculos = new VinculosMongo(banco);
    }

    // Cria os índices usados nas consultas e nas restrições de unicidade desta coleção.
    public void criarIndices() {
        // Nome único por propriedade evita dois "NPK" no mesmo catálogo.
        // Se existir índice antigo não único com as mesmas chaves, remova-o no Atlas antes.
        // Cria o índice pelos campos proprietarioId, propriedadeId, nome para as consultas e restrições desta coleção.
        insumos.createIndex(ascending("proprietarioId", "propriedadeId", "nome"),
                // Impede chaves duplicadas neste índice e identifica-o como insumo_nome_unico.
                new com.mongodb.client.model.IndexOptions().unique(true).name("insumo_nome_unico"));
        // Cria o índice pelos campos proprietarioId, propriedadeId, insumoId, ocorridaEm, _id para as consultas e restrições desta coleção.
        movimentacoes.createIndex(ascending("proprietarioId", "propriedadeId", "insumoId", "ocorridaEm", "_id"));
        // Cria o índice pelos campos proprietarioId, propriedadeId, atividadeId para as consultas e restrições desta coleção.
        movimentacoes.createIndex(ascending("proprietarioId", "propriedadeId", "atividadeId"));
    }

    // Valida os vínculos, grava o documento e devolve o identificador persistido.
    public ObjectId inserir(Insumo insumo) {
        // Confere que a propriedade pertence ao titular informado antes da operação.
        vinculos.exigirPropriedade(insumo.escopo());
        // Converte os dados validados em BSON antes de gravá-los no MongoDB.
        Document d = insumo.paraDocumento();
        // Insere o documento validado na coleção; o driver pode completar seu _id quando ele estiver ausente.
        insumos.insertOne(d);
        // Devolve o _id do documento após a inserção para permitir referenciar o registro persistido.
        return d.getObjectId("_id");
    }

    /**
     * Só registra o histórico da movimentação.
     * Não calcula saldo nem bloqueia saída maior que o estoque: isso fica para #142/#160.
     * Até lá, nenhuma rota HTTP deve tratar este método como baixa segura de estoque.
     */
    // Valida os vínculos, grava o documento e devolve o identificador persistido.
    public ObjectId inserir(MovimentacaoInsumo movimento) {
        // Confere que a propriedade pertence ao titular informado antes da operação.
        vinculos.exigirPropriedade(movimento.escopo());
        // Exige a referência na coleção insumos dentro do mesmo titular e propriedade.
        Document insumo = vinculos.exigir("insumos", movimento.escopo(), movimento.insumoId());
        // Compara a unidade da movimentação com a unidade cadastrada para o insumo.
        if (!insumo.getString("unidade").equals(movimento.unidade())) {
            // Rejeita os dados inválidos: unidade difere do catálogo; conversão não é automática.
            throw new IllegalArgumentException("unidade difere do catálogo; conversão não é automática");
        }
        // Quando existe atividade consumidora, exige que ela também pertença à mesma propriedade e titular.
        if (movimento.atividadeId() != null) vinculos.exigir("atividades", movimento.escopo(), movimento.atividadeId());
        // Converte os dados validados em BSON antes de gravá-los no MongoDB.
        Document d = movimento.paraDocumento();
        // Insere o documento validado na coleção; o driver pode completar seu _id quando ele estiver ausente.
        movimentacoes.insertOne(d);
        // Devolve o _id do documento após a inserção para permitir referenciar o registro persistido.
        return d.getObjectId("_id");
    }

    // Consulta um registro pelo identificador dentro do escopo autorizado e representa a ausência com Optional.
    public Optional<Insumo> buscar(EscopoPropriedade escopo, ObjectId id) {
        // Converte o documento encontrado em objeto de domínio; ausência no filtro resulta em Optional vazio.
        return Optional.ofNullable(insumos.find(vinculos.filtro(escopo, id)).first()).map(Insumo::doDocumento);
    }

    // Busca a movimentação de insumo pelo id com o filtro de titular e propriedade.
    public Optional<MovimentacaoInsumo> buscarMovimentacao(EscopoPropriedade escopo, ObjectId id) {
        // Converte o documento encontrado em objeto de domínio; ausência no filtro resulta em Optional vazio.
        return Optional.ofNullable(movimentacoes.find(vinculos.filtro(escopo, id)).first()).map(MovimentacaoInsumo::doDocumento);
    }

    // Lista as movimentações do insumo no escopo em ordem temporal estável.
    public List<MovimentacaoInsumo> listarMovimentacoes(EscopoPropriedade escopo, ObjectId insumoId) {
        // Retorna lista vazia quando o insumo não existe no escopo, impedindo consultar movimentações alheias.
        if (buscar(escopo, insumoId).isEmpty()) return List.of();
        // Executa a busca com os filtros de escopo e referência indicados e prepara os resultados para conversão.
        return movimentacoes.find(escopo.documento().append("insumoId", insumoId))
                // Ordena os resultados pelos campos ocorridaEm, _id, mantendo uma sequência estável.
                .sort(com.mongodb.client.model.Sorts.ascending("ocorridaEm", "_id"))
                // Converte os documentos retornados em objetos de domínio e os reúne em uma lista.
                .map(MovimentacaoInsumo::doDocumento).into(new ArrayList<>());
    }

    // Valida a atividade e lista somente as saídas de insumo associadas a ela.
    public List<MovimentacaoInsumo> listarConsumosDaAtividade(EscopoPropriedade escopo, ObjectId atividadeId) {
        // Exige a referência na coleção atividades dentro do mesmo titular e propriedade.
        vinculos.exigir("atividades", escopo, atividadeId);
        // Executa a busca com os filtros de escopo e referência indicados e prepara os resultados para conversão.
        return movimentacoes.find(escopo.documento().append("atividadeId", atividadeId).append("tipo", "SAIDA"))
                // Ordena os resultados pelos campos ocorridaEm, _id, mantendo uma sequência estável.
                .sort(com.mongodb.client.model.Sorts.ascending("ocorridaEm", "_id"))
                // Converte os documentos retornados em objetos de domínio e os reúne em uma lista.
                .map(MovimentacaoInsumo::doDocumento).into(new ArrayList<>());
    }
}
