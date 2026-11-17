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
// Importa br.com.agrogestao.core.domain.atividades.Atividade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.atividades.Atividade;
// Importa br.com.agrogestao.core.domain.operacao.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.*;
// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Indexes.ascending, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Indexes.ascending;

// Agrupa as operações de AtividadeRepositorioMongo nesta classe.
public final class AtividadeRepositorioMongo {
    // Mantém coleção de atividades como dependência ou dado imutável.
    private final MongoCollection<Document> atividades;
    // Mantém validador das referências no escopo da propriedade como dependência ou dado imutável.
    private final VinculosMongo vinculos;
    // Mantém repositório de eventos que determina o estado atual como dependência ou dado imutável.
    private final HistoricoMongo historico;

    // Inicializa AtividadeRepositorioMongo com os valores recebidos e as validações detalhadas a seguir.
    public AtividadeRepositorioMongo(MongoDatabase banco) {
        // Seleciona a coleção atividades no banco recebido, sem copiar seus documentos.
        atividades = banco.getCollection("atividades");
        // Prepara o validador que confere a existência e a titularidade das referências.
        vinculos = new VinculosMongo(banco);
        // Associa o histórico à coleção atividades_historico usando atividadeId como referência ao registro principal.
        historico = new HistoricoMongo(banco, "atividades_historico", "atividadeId");
    }

    // Cria os índices usados nas consultas e nas restrições de unicidade desta coleção.
    public void criarIndices() {
        // Cria o índice pelos campos proprietarioId, propriedadeId, talhaoId, safraId, data para as consultas e restrições desta coleção.
        atividades.createIndex(ascending("proprietarioId", "propriedadeId", "talhaoId", "safraId", "data"));
        // Garante os índices de historico antes das gravações ou consultas do cenário.
        historico.criarIndices();
    }

    // Valida os vínculos, grava o documento e devolve o identificador persistido.
    public ObjectId inserir(Atividade atividade) {
        // Confere titular, propriedade e os vínculos opcionais de talhão e safra antes de acessar os dados.
        vinculos.exigirContexto(atividade.contexto());
        // Converte os dados validados em BSON antes de gravá-los no MongoDB.
        Document d = atividade.paraDocumento();
        // Insere o documento validado na coleção; o driver pode completar seu _id quando ele estiver ausente.
        atividades.insertOne(d);
        // Devolve o _id do documento após a inserção para permitir referenciar o registro persistido.
        return d.getObjectId("_id");
    }

    // Consulta um registro pelo identificador dentro do escopo autorizado e representa a ausência com Optional.
    public Optional<Atividade> buscar(EscopoPropriedade escopo, ObjectId id) {
        // Converte o documento encontrado em objeto de domínio; ausência no filtro resulta em Optional vazio.
        return Optional.ofNullable(atividades.find(vinculos.filtro(escopo, id)).first()).map(Atividade::doDocumento);
    }

    // Valida o contexto e lista somente os registros que correspondem a ele.
    public List<Atividade> listarDoContexto(ContextoOperacao contexto) {
        // Confere titular, propriedade e os vínculos opcionais de talhão e safra antes de acessar os dados.
        vinculos.exigirContexto(contexto);
        // Executa a busca com os filtros de escopo e referência indicados e prepara os resultados para conversão.
        return atividades.find(contexto.documento()).map(Atividade::doDocumento).into(new ArrayList<>());
    }

    /** Consulta todas as atividades da safra, inclusive após a finalização do ciclo. */
    // Valida a safra no escopo e consulta seus registros, inclusive quando o ciclo já terminou.
    public List<Atividade> listarDaSafra(EscopoPropriedade escopo, ObjectId safraId) {
        // Exige a referência na coleção safras dentro do mesmo titular e propriedade.
        vinculos.exigir("safras", escopo, safraId);
        // Executa a busca com os filtros de escopo e referência indicados e prepara os resultados para conversão.
        return atividades.find(escopo.documento().append("safraId", safraId))
                // Converte os documentos retornados em objetos de domínio e os reúne em uma lista.
                .map(Atividade::doDocumento).into(new ArrayList<>());
    }

    // Reconstitui a sequência de eventos somente quando o registro principal existe no escopo.
    public List<EventoHistorico> listarHistorico(EscopoPropriedade escopo, ObjectId id) {
        // Só consulta o histórico de um registro encontrado no escopo; usa lista vazia quando ele não existe ali.
        return buscar(escopo, id).map(a -> historico.listar(escopo, id, a.paraDocumento())).orElse(List.of());
    }

    // Deriva o estado atual do último evento, mantendo o histórico como fonte da situação do registro.
    public Optional<Atividade.Status> statusAtual(EscopoPropriedade escopo, ObjectId id) {
        // Só consulta o histórico de um registro encontrado no escopo; usa Optional vazio quando ele não existe ali.
        return buscar(escopo, id).map(a -> Atividade.Status.valueOf(historico.ultimo(escopo, id, a.paraDocumento()).status()));
    }

    // Valida o registro principal e acrescenta o evento segundo as regras de transição da atividade.
    public void registrarEvento(EscopoPropriedade escopo, ObjectId id, EventoHistorico evento) {
        // Exige a referência na coleção atividades dentro do mesmo titular e propriedade.
        Document original = vinculos.exigir("atividades", escopo, id);
        // Acrescenta o evento ao histórico depois de conferir a sequência, a data e a regra de transição do domínio.
        historico.adicionar(escopo, id, original, evento, Atividade::permiteTransicao);
    }
}
