// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.infra.mongo;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Indexes.ascending;

import java.util.Optional;
import java.util.Locale;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

import br.com.agrogestao.core.domain.usuarios.Usuario;

public final class UsuarioRepositorioMongo {
    private final MongoCollection<Document> usuarios;

    public UsuarioRepositorioMongo(MongoDatabase banco) {
        this.usuarios = banco.getCollection("usuarios");
    }

    public void criarIndices() {
        usuarios.createIndex(ascending("email"), new IndexOptions().unique(true).name("email_unico"));
    }

    public ObjectId inserir(Usuario usuario) {
        Document documento = usuario.paraDocumento();
        usuarios.insertOne(documento);
        return documento.getObjectId("_id");
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email é obrigatório");
        }
        Document documento = usuarios.find(eq("email", email.trim().toLowerCase(Locale.ROOT))).first();
        return Optional.ofNullable(documento).map(Usuario::doDocumento);
    }
}
