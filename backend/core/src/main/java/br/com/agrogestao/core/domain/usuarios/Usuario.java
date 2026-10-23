// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.domain.usuarios;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * Conta de acesso ao AgroGestão. A senha nunca é persistida: somente seu hash.
 */
public final class Usuario {
    private final ObjectId id;
    private final String email;
    private final String nome;
    private final String senhaHash;
    private final Instant criadoEm;

    public Usuario(ObjectId id, String email, String nome, String senhaHash, Instant criadoEm) {
        this.id = id;
        this.email = normalizarEmail(email);
        this.nome = obrigatorio(nome, "nome");
        this.senhaHash = obrigatorio(senhaHash, "senhaHash");
        this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    }

    public Usuario(String email, String nome, String senhaHash) {
        this(null, email, nome, senhaHash, Instant.now());
    }

    public ObjectId getId() { return id; }
    public String getEmail() { return email; }
    public String getNome() { return nome; }
    public String getSenhaHash() { return senhaHash; }
    public Instant getCriadoEm() { return criadoEm; }

    public Document paraDocumento() {
        Document documento = new Document("email", email)
                .append("nome", nome)
                .append("senhaHash", senhaHash)
                .append("criadoEm", Date.from(criadoEm));
        if (id != null) documento.put("_id", id);
        return documento;
    }

    public static Usuario doDocumento(Document documento) {
        return new Usuario(
                documento.getObjectId("_id"),
                documento.getString("email"),
                documento.getString("nome"),
                documento.getString("senhaHash"),
                documento.getDate("criadoEm").toInstant());
    }

    private static String normalizarEmail(String email) {
        String normalizado = obrigatorio(email, "email").toLowerCase(Locale.ROOT);
        if (!normalizado.contains("@")) {
            throw new IllegalArgumentException("email deve ser válido");
        }
        return normalizado;
    }

    private static String obrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return valor.trim();
    }
}
