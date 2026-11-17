// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Miguel — RA: 25003110
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
// Importa as opções de unicidade e nome dos índices.
import com.mongodb.client.model.IndexOptions;
// Importa br.com.agrogestao.core.domain.custos.Custo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.custos.Custo;
// Importa br.com.agrogestao.core.domain.operacao.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.*;
// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Indexes.ascending, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Indexes.ascending;

// Agrupa as operações de CustoRepositorioMongo nesta classe.
public final class CustoRepositorioMongo {
    // Mantém coleção de custos como dependência ou dado imutável.
    private final MongoCollection<Document> custos;
    // Mantém validador das referências no escopo da propriedade como dependência ou dado imutável.
    private final VinculosMongo vinculos;

    // Inicializa CustoRepositorioMongo com os valores recebidos e as validações detalhadas a seguir.
    public CustoRepositorioMongo(MongoDatabase banco) {
        // Seleciona a coleção custos no banco recebido, sem copiar seus documentos.
        custos = banco.getCollection("custos");
        // Prepara o validador que confere a existência e a titularidade das referências.
        vinculos = new VinculosMongo(banco);
    }

    // Cria os índices usados nas consultas e nas restrições de unicidade desta coleção.
    public void criarIndices() {
        // Cria o índice pelos campos proprietarioId, propriedadeId, talhaoId, safraId, data para as consultas e restrições desta coleção.
        custos.createIndex(ascending("proprietarioId", "propriedadeId", "talhaoId", "safraId", "data"));
        // Cria o índice pelos campos proprietarioId, propriedadeId, culturaId, data para as consultas e restrições desta coleção.
        custos.createIndex(ascending("proprietarioId", "propriedadeId", "culturaId", "data"));
        // Cria o índice pelos campos proprietarioId, propriedadeId, consumoId para as consultas e restrições desta coleção.
        custos.createIndex(ascending("proprietarioId", "propriedadeId", "consumoId"),
                // Impede chaves duplicadas neste índice e identifica-o como consumo_custo_unico.
                new IndexOptions().unique(true).name("consumo_custo_unico")
                        // Aplica a unicidade somente a documentos cujo consumoId é ObjectId, permitindo custos sem consumo associado.
                        .partialFilterExpression(new Document("consumoId", new Document("$type", "objectId"))));
    }

    // Valida os vínculos, grava o documento e devolve o identificador persistido.
    public ObjectId inserir(Custo custo) {
        // Extrai o titular e a propriedade do contexto do custo para conferir suas demais referências.
        EscopoPropriedade escopo = custo.contexto().escopo();
        // Confere titular, propriedade e os vínculos opcionais de talhão e safra antes de acessar os dados.
        vinculos.exigirContexto(custo.contexto());
        // Quando existe cultura informada, exige que ela pertença ao titular e à propriedade deste custo.
        if (custo.culturaId() != null) vinculos.exigir("culturas", escopo, custo.culturaId());
        // Executa a conferência da cultura da safra somente quando o custo está associado a um ciclo.
        if (custo.contexto().safraId() != null) {
            // Exige a referência na coleção safras dentro do mesmo titular e propriedade.
            Document safra = vinculos.exigir("safras", escopo, custo.contexto().safraId());
            // Compara a cultura da safra persistida com a cultura informada no custo.
            if (!safra.getObjectId("culturaId").equals(custo.culturaId())) {
                // Rejeita os dados inválidos: cultura difere da cultura da safra.
                throw new IllegalArgumentException("cultura difere da cultura da safra");
            }
        }
        // Quando há atividade, confere também se seu contexto corresponde ao contexto do custo.
        if (custo.atividadeId() != null) {
            // Carrega a atividade consumidora somente dentro do escopo autorizado.
            Document atividade = vinculos.exigir("atividades", escopo, custo.atividadeId());
            // Compara todo o contexto da atividade com o do custo, incluindo talhão e safra.
            if (!ContextoOperacao.doDocumento(atividade).equals(custo.contexto())) {
                // Rejeita os dados inválidos: contexto do custo difere da atividade.
                throw new IllegalArgumentException("contexto do custo difere da atividade");
            }
        }
        // Quando há consumo associado, confere seu tipo e a atividade que efetivamente consumiu o insumo.
        if (custo.consumoId() != null) {
            // Exige a referência na coleção movimentacoes_insumos dentro do mesmo titular e propriedade.
            Document consumo = vinculos.exigir("movimentacoes_insumos", escopo, custo.consumoId());
            // Exige que o consumo seja uma SAIDA e que sua atividade corresponda à atividade do custo.
            if (!"SAIDA".equals(consumo.getString("tipo")) || !custo.atividadeId().equals(consumo.getObjectId("atividadeId"))) {
                // Rejeita os dados inválidos: consumo não é uma saída da atividade informada.
                throw new IllegalArgumentException("consumo não é uma saída da atividade informada");
            }
        }
        // Converte os dados validados em BSON antes de gravá-los no MongoDB.
        Document d = custo.paraDocumento();
        // Insere o documento validado na coleção; o driver pode completar seu _id quando ele estiver ausente.
        custos.insertOne(d);
        // Devolve o _id do documento após a inserção para permitir referenciar o registro persistido.
        return d.getObjectId("_id");
    }

    // Consulta um registro pelo identificador dentro do escopo autorizado e representa a ausência com Optional.
    public Optional<Custo> buscar(EscopoPropriedade escopo, ObjectId id) {
        // Converte o documento encontrado em objeto de domínio; ausência no filtro resulta em Optional vazio.
        return Optional.ofNullable(custos.find(vinculos.filtro(escopo, id)).first()).map(Custo::doDocumento);
    }

    // Consulta os custos da propriedade depois de validar o vínculo do titular.
    public List<Custo> listarDaPropriedade(EscopoPropriedade escopo) {
        // Confere que a propriedade pertence ao titular informado antes da operação.
        vinculos.exigirPropriedade(escopo);
        // Reutiliza a consulta de custos com o filtro de titular e propriedade.
        return consultar(escopo.documento());
    }

    // Valida o contexto e lista somente os registros que correspondem a ele.
    public List<Custo> listarDoContexto(ContextoOperacao contexto) {
        // Confere titular, propriedade e os vínculos opcionais de talhão e safra antes de acessar os dados.
        vinculos.exigirContexto(contexto);
        // Reutiliza a consulta de custos com o filtro de contexto operacional.
        return consultar(contexto.documento());
    }

    /** Custos do período são consultados por safra, mesmo quando distribuídos em vários talhões. */
    // Valida a safra no escopo e consulta seus registros, inclusive quando o ciclo já terminou.
    public List<Custo> listarDaSafra(EscopoPropriedade escopo, ObjectId safraId) {
        // Exige a referência na coleção safras dentro do mesmo titular e propriedade.
        vinculos.exigir("safras", escopo, safraId);
        // Reutiliza a consulta de custos com o filtro de titular e propriedade acrescido de safraId.
        return consultar(escopo.documento().append("safraId", safraId));
    }

    // Valida a cultura e consulta os gastos associados a ela no mesmo escopo.
    public List<Custo> listarDaCultura(EscopoPropriedade escopo, ObjectId culturaId) {
        // Exige a referência na coleção culturas dentro do mesmo titular e propriedade.
        vinculos.exigir("culturas", escopo, culturaId);
        // Reutiliza a consulta de custos com o filtro de titular e propriedade acrescido de culturaId.
        return consultar(escopo.documento().append("culturaId", culturaId));
    }

    // Executa o filtro recebido e converte os resultados de custo em ordem de data e id.
    private List<Custo> consultar(Document filtro) {
        // Executa a busca com os filtros de escopo e referência indicados e prepara os resultados para conversão.
        return custos.find(filtro).sort(com.mongodb.client.model.Sorts.ascending("data", "_id"))
                // Converte os documentos retornados em objetos de domínio e os reúne em uma lista.
                .map(Custo::doDocumento).into(new ArrayList<>());
    }
}
