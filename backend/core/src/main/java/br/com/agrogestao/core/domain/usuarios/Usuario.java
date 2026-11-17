// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.usuarios, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.usuarios;

// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa a ponte entre Instant e o tipo de data aceito pelo driver BSON.
import java.util.Date;
// Importa a normalização textual independente do idioma do sistema.
import java.util.Locale;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;

/**
 * Conta de acesso ao AgroGestão. A senha nunca é persistida: somente seu hash.
 */
// Agrupa as operações de Usuario nesta classe.
public final class Usuario {
    // Mantém identificador do registro como dependência ou dado imutável.
    private final ObjectId id;
    // Mantém email normalizado como dependência ou dado imutável.
    private final String email;
    // Mantém nome de exibição como dependência ou dado imutável.
    private final String nome;
    // Mantém hash da senha, sem guardar a senha original como dependência ou dado imutável.
    private final String senhaHash;
    // Mantém instante de criação como dependência ou dado imutável.
    private final Instant criadoEm;

    // Inicializa Usuario com os valores recebidos e as validações detalhadas a seguir.
    public Usuario(ObjectId id, String email, String nome, String senhaHash, Instant criadoEm) {
        // Guarda identificador do registro na instância para reutilização nas próximas operações.
        this.id = id;
        // Valida e normaliza o email antes de guardar a conta em memória.
        this.email = normalizarEmail(email);
        // Guarda nome de exibição na instância para reutilização nas próximas operações.
        this.nome = obrigatorio(nome, "nome");
        // Exige o hash já calculado pela autenticação; este objeto não calcula nem recebe a senha original.
        this.senhaHash = obrigatorio(senhaHash, "senhaHash");
        // Preserva a data recebida ou registra o instante atual quando ela não foi informada.
        this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    }

    // Inicializa Usuario com os valores recebidos e as validações detalhadas a seguir.
    public Usuario(String email, String nome, String senhaHash) {
        // Reutiliza o construtor completo com id ainda ausente e instante atual para uma nova conta.
        this(null, email, nome, senhaHash, Instant.now());
    }

    // Devolve identificador do registro sem modificar o objeto.
    public ObjectId getId() { return id; }
    // Devolve email normalizado sem modificar o objeto.
    public String getEmail() { return email; }
    // Devolve nome de exibição sem modificar o objeto.
    public String getNome() { return nome; }
    // Devolve hash da senha, sem guardar a senha original sem modificar o objeto.
    public String getSenhaHash() { return senhaHash; }
    // Devolve instante de criação sem modificar o objeto.
    public Instant getCriadoEm() { return criadoEm; }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos email.
        Document documento = new Document("email", email)
                // Preenche no BSON os campos nome.
                .append("nome", nome)
                // Preenche no BSON os campos senhaHash.
                .append("senhaHash", senhaHash)
                // Preenche no BSON os campos criadoEm e converte o instante para a data BSON.
                .append("criadoEm", Date.from(criadoEm));
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) documento.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return documento;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Usuario doDocumento(Document documento) {
        // Reconstrói Usuario com os identificadores e valores recebidos para este cenário.
        return new Usuario(
                // Lê _id do BSON.
                documento.getObjectId("_id"),
                // Lê email do BSON.
                documento.getString("email"),
                // Lê nome do BSON.
                documento.getString("nome"),
                // Lê senhaHash do BSON.
                documento.getString("senhaHash"),
                // Lê criadoEm do BSON; restaura o instante.
                documento.getDate("criadoEm").toInstant());
    }

    // Aplica a validação mínima de formato e normaliza o endereço usado no cadastro.
    private static String normalizarEmail(String email) {
        // Exige um email não vazio, remove espaços e converte para minúsculas sem depender do idioma do sistema.
        String normalizado = obrigatorio(email, "email").toLowerCase(Locale.ROOT);
        // Checagem simples: parte local, @ e domínio com ponto. Hash/KDF ficam na auth.
        // Localiza o primeiro separador entre a parte local e o domínio do email.
        int arroba = normalizado.indexOf('@');
        // Exige conteúdo antes do @ e impede mais de um separador no endereço.
        if (arroba < 1 || arroba != normalizado.lastIndexOf('@')) {
            // Sinaliza que o endereço não atende ao formato mínimo adotado pelo modelo.
            throw new IllegalArgumentException("email deve ser válido");
        }
        // Extrai o domínio depois do @ para validar sua estrutura básica.
        String dominio = normalizado.substring(arroba + 1);
        // Exige ponto no domínio e impede que ele apareça na primeira ou na última posição.
        if (!dominio.contains(".") || dominio.startsWith(".") || dominio.endsWith(".")) {
            // Rejeita o domínio inválido com o mesmo erro de formato do email.
            throw new IllegalArgumentException("email deve ser válido");
        }
        // Retorna o email normalizado para gravação e busca consistentes.
        return normalizado;
    }

    // Rejeita valores ausentes e devolve o dado validado para o construtor.
    private static String obrigatorio(String valor, String campo) {
        // Rejeita null, texto vazio e texto composto apenas por espaços no campo obrigatório.
        if (valor == null || valor.isBlank()) {
            // Identifica o campo que não pode ficar sem valor.
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        // Devolve o texto obrigatório sem espaços nas extremidades.
        return valor.trim();
    }
}
