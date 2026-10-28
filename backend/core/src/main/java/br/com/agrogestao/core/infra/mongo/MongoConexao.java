// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.infra.mongo;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public final class MongoConexao implements AutoCloseable {
    private final MongoClient cliente;
    private final MongoDatabase banco;

    public MongoConexao(String uri, String nomeBanco) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("MONGODB_URI não foi configurada");
        }
        if (nomeBanco == null || nomeBanco.isBlank()) {
            throw new IllegalArgumentException("MONGODB_DATABASE não foi configurada");
        }

        this.cliente = MongoClients.create(uri.trim());
        this.banco = cliente.getDatabase(nomeBanco.trim());
    }

    public static MongoConexao peloAmbiente() {
        return new MongoConexao(
                System.getenv("MONGODB_URI"),
                valorAmbiente("MONGODB_DATABASE", "agrogestao_dev"));
    }

    /**
     * Conexão só para suítes Atlas. O nome do banco precisa terminar em {@code _teste}
     * para não gravar dados fictícios em {@code agrogestao_dev}.
     */
    public static MongoConexao paraTestes() {
        String nomeBanco = valorAmbiente("MONGODB_DATABASE", "");
        if (nomeBanco.isBlank() || !nomeBanco.endsWith("_teste")) {
            throw new IllegalArgumentException(
                    "Para testes Atlas, defina MONGODB_DATABASE com nome terminado em _teste");
        }
        return new MongoConexao(System.getenv("MONGODB_URI"), nomeBanco);
    }

    public MongoDatabase banco() {
        return banco;
    }

    /** Nome do banco atual — útil para os testes confirmarem o sufixo _teste. */
    public String nomeBanco() {
        return banco.getName();
    }

    public void verificar() {
        banco.runCommand(new Document("ping", 1));
    }

    @Override
    public void close() {
        cliente.close();
    }

    private static String valorAmbiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
