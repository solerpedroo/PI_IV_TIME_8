// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Filters.eq, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Filters.eq;
// Disponibiliza diretamente os utilitários de com.mongodb.client.model.Indexes.ascending, usados nas validações ou consultas abaixo.
import static com.mongodb.client.model.Indexes.ascending;

// Importa a representação explícita de uma busca sem resultado.
import java.util.Optional;
// Importa a normalização textual independente do idioma do sistema.
import java.util.Locale;

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

// Importa br.com.agrogestao.core.domain.usuarios.Usuario para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.usuarios.Usuario;

// Agrupa as operações de UsuarioRepositorioMongo nesta classe.
public final class UsuarioRepositorioMongo {
    // Mantém coleção de usuários como dependência ou dado imutável.
    private final MongoCollection<Document> usuarios;

    // Inicializa UsuarioRepositorioMongo com os valores recebidos e as validações detalhadas a seguir.
    public UsuarioRepositorioMongo(MongoDatabase banco) {
        // Guarda coleção de usuários na instância para reutilização nas próximas operações.
        this.usuarios = banco.getCollection("usuarios");
    }

    // Cria os índices usados nas consultas e nas restrições de unicidade desta coleção.
    public void criarIndices() {
        // Cria o índice pelos campos email, email_unico para as consultas e restrições desta coleção.
        usuarios.createIndex(ascending("email"), new IndexOptions().unique(true).name("email_unico"));
    }

    // Valida os vínculos, grava o documento e devolve o identificador persistido.
    public ObjectId inserir(Usuario usuario) {
        // Converte os dados validados em BSON antes de gravá-los no MongoDB.
        Document documento = usuario.paraDocumento();
        // Insere o documento validado na coleção; o driver pode completar seu _id quando ele estiver ausente.
        usuarios.insertOne(documento);
        // Devolve o _id do documento após a inserção para permitir referenciar o registro persistido.
        return documento.getObjectId("_id");
    }

    // Normaliza o email e consulta a conta correspondente sem distinguir maiúsculas e minúsculas.
    public Optional<Usuario> buscarPorEmail(String email) {
        // Exige email não vazio antes de construir o filtro da conta.
        if (email == null || email.isBlank()) {
            // Rejeita os dados inválidos: email é obrigatório.
            throw new IllegalArgumentException("email é obrigatório");
        }
        // Normaliza espaços e capitalização no filtro para buscar o mesmo email usado no cadastro.
        Document documento = usuarios.find(eq("email", email.trim().toLowerCase(Locale.ROOT))).first();
        // Converte o documento encontrado em objeto de domínio; ausência no filtro resulta em Optional vazio.
        return Optional.ofNullable(documento).map(Usuario::doDocumento);
    }
}
