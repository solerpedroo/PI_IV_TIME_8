// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.com.agrogestao.core.infra.mongo.MongoConexao;
import br.com.agrogestao.core.infra.mongo.CultivoRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.AtividadeRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.InsumoRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.OcorrenciaRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.CustoRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.PropriedadeRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.UsuarioRepositorioMongo;

public final class CoreApplication {
    private CoreApplication() {}

    public static void main(String[] args) {
        MongoConexao conexao;
        try {
            conexao = MongoConexao.peloAmbiente();
        } catch (Exception erro) {
            System.err.println("Não foi possível criar a conexão. Verifique MONGODB_URI e MONGODB_DATABASE.");
            System.exit(1);
            return;
        }

        try {
            conexao.verificar();
            new UsuarioRepositorioMongo(conexao.banco()).criarIndices();
            new PropriedadeRepositorioMongo(conexao.banco()).criarIndices();
            new CultivoRepositorioMongo(conexao.banco()).criarIndices();
            new AtividadeRepositorioMongo(conexao.banco()).criarIndices();
            new InsumoRepositorioMongo(conexao.banco()).criarIndices();
            new OcorrenciaRepositorioMongo(conexao.banco()).criarIndices();
            new CustoRepositorioMongo(conexao.banco()).criarIndices();

            String host = ambiente("JAVA_HOST", "127.0.0.1");
            int porta = porta(ambiente("JAVA_PORT", "8081"));
            HttpServer servidor = HttpServer.create(new InetSocketAddress(host, porta), 0);

            servidor.createContext("/health", troca -> responder(troca, 200, "{\"status\":\"UP\"}"));
            servidor.createContext("/ready", troca -> {
                try {
                    conexao.verificar();
                    responder(troca, 200, "{\"status\":\"UP\"}");
                } catch (Exception erro) {
                    responder(troca, 503, "{\"status\":\"DOWN\"}");
                }
            });
            servidor.setExecutor(Executors.newFixedThreadPool(10));
            servidor.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                servidor.stop(0);
                conexao.close();
            }));

            System.out.println("Core pronto em http://" + host + ":" + porta);
        } catch (Exception erro) {
            conexao.close();
            System.err.println("Não foi possível iniciar o core. Verifique MONGODB_URI, usuário, senha e IP autorizado no Atlas.");
            System.exit(1);
        }
    }

    static String ambiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }

    static int porta(String texto) {
        int valor = Integer.parseInt(texto);
        if (valor < 1 || valor > 65535) {
            throw new IllegalArgumentException("JAVA_PORT deve ser uma porta TCP válida");
        }
        return valor;
    }

    private static void responder(HttpExchange troca, int status, String corpo) throws java.io.IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }
}
