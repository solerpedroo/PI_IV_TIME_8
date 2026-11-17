// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.propriedades, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.propriedades;

// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa a ponte entre Instant e o tipo de data aceito pelo driver BSON.
import java.util.Date;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;

/**
 * Unidade produtiva. Todo dado operacional futuro deve apontar para uma propriedade.
 */
// Agrupa as operações de Propriedade nesta classe.
public final class Propriedade {
    // Mantém identificador do registro como dependência ou dado imutável.
    private final ObjectId id;
    // Mantém identificador do titular como dependência ou dado imutável.
    private final ObjectId proprietarioId;
    // Mantém nome de exibição como dependência ou dado imutável.
    private final String nome;
    // Mantém instante de criação como dependência ou dado imutável.
    private final Instant criadaEm;

    // Inicializa Propriedade com os valores recebidos e as validações detalhadas a seguir.
    public Propriedade(ObjectId id, ObjectId proprietarioId, String nome, Instant criadaEm) {
        // Guarda identificador do registro na instância para reutilização nas próximas operações.
        this.id = id;
        // Exige o identificador do titular para que a propriedade sempre pertença a um usuário.
        this.proprietarioId = obrigatorio(proprietarioId, "proprietarioId");
        // Valida o nome e remove espaços nas extremidades antes de armazená-lo.
        this.nome = obrigatorio(nome, "nome");
        // Usa a data recebida ou o instante atual quando a criação ainda não tem data registrada.
        this.criadaEm = criadaEm == null ? Instant.now() : criadaEm;
    }

    // Inicializa Propriedade com os valores recebidos e as validações detalhadas a seguir.
    public Propriedade(ObjectId proprietarioId, String nome) {
        // Reutiliza o construtor completo com id ainda ausente e criação no instante atual.
        this(null, proprietarioId, nome, Instant.now());
    }

    // Devolve identificador do registro sem modificar o objeto.
    public ObjectId getId() { return id; }
    // Devolve identificador do titular sem modificar o objeto.
    public ObjectId getProprietarioId() { return proprietarioId; }
    // Devolve nome de exibição sem modificar o objeto.
    public String getNome() { return nome; }
    // Devolve instante de criação sem modificar o objeto.
    public Instant getCriadaEm() { return criadaEm; }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos proprietarioId.
        Document documento = new Document("proprietarioId", proprietarioId)
                // Preenche no BSON os campos nome.
                .append("nome", nome)
                // Preenche no BSON os campos criadaEm e converte o instante para a data BSON.
                .append("criadaEm", Date.from(criadaEm));
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) documento.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return documento;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Propriedade doDocumento(Document documento) {
        // Reconstrói Propriedade com os identificadores e valores recebidos para este cenário.
        return new Propriedade(
                // Lê _id do BSON.
                documento.getObjectId("_id"),
                // Lê proprietarioId do BSON.
                documento.getObjectId("proprietarioId"),
                // Lê nome do BSON.
                documento.getString("nome"),
                // Lê criadaEm do BSON; restaura o instante.
                documento.getDate("criadaEm").toInstant());
    }

    // Rejeita valores ausentes e devolve o dado validado para o construtor.
    private static String obrigatorio(String valor, String campo) {
        // Rejeita nome ausente, vazio ou composto somente por espaços.
        if (valor == null || valor.isBlank()) {
            // Informa qual campo textual obrigatório não foi preenchido.
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        // Retorna o texto validado e aparado para manter o cadastro consistente.
        return valor.trim();
    }

    // Rejeita valores ausentes e devolve o dado validado para o construtor.
    private static ObjectId obrigatorio(ObjectId valor, String campo) {
        // Recusa um identificador ausente antes de permitir a criação da propriedade.
        if (valor == null) throw new IllegalArgumentException(campo + " é obrigatório");
        // Devolve o ObjectId obrigatório já validado, sem criar outro identificador.
        return valor;
    }
}
