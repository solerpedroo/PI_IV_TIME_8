// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.domain.propriedades;

import java.time.Instant;
import java.util.Date;

import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * Unidade produtiva. Todo dado operacional futuro deve apontar para uma propriedade.
 */
public final class Propriedade {
    private final ObjectId id;
    private final ObjectId proprietarioId;
    private final String nome;
    private final Instant criadaEm;

    public Propriedade(ObjectId id, ObjectId proprietarioId, String nome, Instant criadaEm) {
        this.id = id;
        this.proprietarioId = obrigatorio(proprietarioId, "proprietarioId");
        this.nome = obrigatorio(nome, "nome");
        this.criadaEm = criadaEm == null ? Instant.now() : criadaEm;
    }

    public Propriedade(ObjectId proprietarioId, String nome) {
        this(null, proprietarioId, nome, Instant.now());
    }

    public ObjectId getId() { return id; }
    public ObjectId getProprietarioId() { return proprietarioId; }
    public String getNome() { return nome; }
    public Instant getCriadaEm() { return criadaEm; }

    public Document paraDocumento() {
        Document documento = new Document("proprietarioId", proprietarioId)
                .append("nome", nome)
                .append("criadaEm", Date.from(criadaEm));
        if (id != null) documento.put("_id", id);
        return documento;
    }

    public static Propriedade doDocumento(Document documento) {
        return new Propriedade(
                documento.getObjectId("_id"),
                documento.getObjectId("proprietarioId"),
                documento.getString("nome"),
                documento.getDate("criadaEm").toInstant());
    }

    private static String obrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return valor.trim();
    }

    private static ObjectId obrigatorio(ObjectId valor, String campo) {
        if (valor == null) throw new IllegalArgumentException(campo + " é obrigatório");
        return valor;
    }
}
