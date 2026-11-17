// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Filters.and, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Filters.and;
// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Filters.eq, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Filters.eq;
// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Indexes.ascending, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Indexes.ascending;

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

// Importa br.com.agrogestao.core.domain.propriedades.Propriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.propriedades.Propriedade;
// Importa br.com.agrogestao.core.domain.propriedades.PropriedadeRepository para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.propriedades.PropriedadeRepository;

// Agrupa as operações de PropriedadeRepositorioMongo nesta classe.
public final class PropriedadeRepositorioMongo implements PropriedadeRepository {
    // Mantém coleção de propriedades como dependência ou dado imutável.
    private final MongoCollection<Document> propriedades;
    // Mantém coleção de usuários como dependência ou dado imutável.
    private final MongoCollection<Document> usuarios;

    // Inicializa PropriedadeRepositorioMongo com os valores recebidos e as validações detalhadas a seguir.
    public PropriedadeRepositorioMongo(MongoDatabase banco) {
        // Guarda coleção de propriedades na instância para reutilização nas próximas operações.
        this.propriedades = banco.getCollection("propriedades");
        // Guarda coleção de usuários na instância para reutilização nas próximas operações.
        this.usuarios = banco.getCollection("usuarios");
    }

    // Cria os índices usados nas consultas e nas restrições de unicidade desta coleção.
    public void criarIndices() {
        // Cria o índice pelos campos  para as consultas e restrições desta coleção.
        propriedades.createIndex(
                // Combina titular e nome para permitir nomes iguais entre produtores diferentes.
                ascending("proprietarioId", "nome"),
                // Impede chaves duplicadas neste índice e identifica-o como proprietario_nome_unico.
                new IndexOptions().unique(true).name("proprietario_nome_unico"));
    }

    // Valida os vínculos, grava o documento e devolve o identificador persistido.
    public ObjectId inserir(Propriedade propriedade) {
        // Impede inserir propriedade cujo titular não existe na coleção de usuários.
        if (usuarios.find(eq("_id", propriedade.getProprietarioId())).first() == null) {
            // Rejeita os dados inválidos: proprietarioId não referencia um usuário existente.
            throw new IllegalArgumentException("proprietarioId não referencia um usuário existente");
        }
        // Converte os dados validados em BSON antes de gravá-los no MongoDB.
        Document documento = propriedade.paraDocumento();
        // Insere o documento validado na coleção; o driver pode completar seu _id quando ele estiver ausente.
        propriedades.insertOne(documento);
        // Devolve o _id do documento após a inserção para permitir referenciar o registro persistido.
        return documento.getObjectId("_id");
    }

    // Indica que este método implementa o contrato herdado pela classe.
    @Override
    // Aplica o id da propriedade e o do titular na mesma consulta de autorização.
    public Optional<Propriedade> buscarDoProprietario(ObjectId id, ObjectId proprietarioId) {
        // Aplica os filtros de id e titular na mesma consulta para não revelar propriedades de outro produtor.
        Document documento = propriedades.find(and(eq("_id", id), eq("proprietarioId", proprietarioId))).first();
        // Converte o documento encontrado em objeto de domínio; ausência no filtro resulta em Optional vazio.
        return Optional.ofNullable(documento).map(Propriedade::doDocumento);
    }

    // Lista exclusivamente as propriedades vinculadas ao titular informado.
    public List<Propriedade> listarDoProprietario(ObjectId proprietarioId) {
        // Prepara a lista que receberá as propriedades encontradas.
        List<Propriedade> resultado = new ArrayList<>();
        // Filtra a coleção exclusivamente pelo proprietário solicitado.
        propriedades.find(eq("proprietarioId", proprietarioId))
                // Converte os documentos retornados em objetos de domínio.
                .map(Propriedade::doDocumento)
                // Reúne os objetos já convertidos na lista de resultado.
                .into(resultado);
        // Devolve somente as propriedades que passaram pelo filtro do titular.
        return resultado;
    }
}
