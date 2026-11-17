// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core;

// Importa a combinação do host e da porta de escuta.
import java.net.InetSocketAddress;
// Importa a codificação UTF-8 explícita das respostas HTTP.
import java.nio.charset.StandardCharsets;
// Importa a criação de pools de threads com tamanho controlado.
import java.util.concurrent.Executors;

// Importa a leitura e escrita de uma troca HTTP.
import com.sun.net.httpserver.HttpExchange;
// Importa o servidor HTTP disponibilizado pelo JDK.
import com.sun.net.httpserver.HttpServer;

// Importa br.com.agrogestao.core.infra.mongo.MongoConexao para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.MongoConexao;
// Importa br.com.agrogestao.core.infra.mongo.CultivoRepositorioMongo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.CultivoRepositorioMongo;
// Importa br.com.agrogestao.core.infra.mongo.AtividadeRepositorioMongo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.AtividadeRepositorioMongo;
// Importa br.com.agrogestao.core.infra.mongo.InsumoRepositorioMongo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.InsumoRepositorioMongo;
// Importa br.com.agrogestao.core.infra.mongo.OcorrenciaRepositorioMongo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.OcorrenciaRepositorioMongo;
// Importa br.com.agrogestao.core.infra.mongo.CustoRepositorioMongo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.CustoRepositorioMongo;
// Importa br.com.agrogestao.core.infra.mongo.PropriedadeRepositorioMongo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.PropriedadeRepositorioMongo;
// Importa br.com.agrogestao.core.infra.mongo.UsuarioRepositorioMongo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.infra.mongo.UsuarioRepositorioMongo;

/**
 * Fundação do modelo de dados (#100–#105): ping Atlas, índices e /health|/ready.
 * O CRUD HTTP autenticado de safras fica no {@code backend/api} ({@code SafrasHandler}),
 * não aqui — evita superfície sem sessão e schema paralelo na mesma coleção.
 */
// Agrupa as operações de CoreApplication nesta classe.
public final class CoreApplication {
    // Impede criar instâncias desta classe, pois suas operações são estáticas.
    private CoreApplication() {}

    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Declara a conexão que será usada tanto na inicialização quanto no encerramento do processo.
        MongoConexao conexao;
        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Carrega a configuração de banco e constrói o cliente MongoDB.
            conexao = MongoConexao.peloAmbiente();
        // Trata erros de configuração ou construção da conexão antes de iniciar o servidor.
        } catch (Exception erro) {
            // Indica as variáveis de conexão a conferir sem revelar seus valores.
            System.err.println("Não foi possível criar a conexão. Verifique MONGODB_URI e MONGODB_DATABASE.");
            // Termina o processo com falha, pois não há conexão utilizável.
            System.exit(1);
            // Impede continuar a inicialização depois da tentativa de encerrar o processo.
            return;
        }

        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Faz um ping real antes de publicar a prontidão do core.
            conexao.verificar();
            // Garante os índices de UsuarioRepositorioMongo(conexao.banco()) antes das gravações ou consultas do cenário.
            new UsuarioRepositorioMongo(conexao.banco()).criarIndices();
            // Garante os índices de PropriedadeRepositorioMongo(conexao.banco()) antes das gravações ou consultas do cenário.
            new PropriedadeRepositorioMongo(conexao.banco()).criarIndices();
            // Garante os índices de CultivoRepositorioMongo(conexao.banco()) antes das gravações ou consultas do cenário.
            new CultivoRepositorioMongo(conexao.banco()).criarIndices();
            // Garante os índices de AtividadeRepositorioMongo(conexao.banco()) antes das gravações ou consultas do cenário.
            new AtividadeRepositorioMongo(conexao.banco()).criarIndices();
            // Garante os índices de InsumoRepositorioMongo(conexao.banco()) antes das gravações ou consultas do cenário.
            new InsumoRepositorioMongo(conexao.banco()).criarIndices();
            // Garante os índices de OcorrenciaRepositorioMongo(conexao.banco()) antes das gravações ou consultas do cenário.
            new OcorrenciaRepositorioMongo(conexao.banco()).criarIndices();
            // Garante os índices de CustoRepositorioMongo(conexao.banco()) antes das gravações ou consultas do cenário.
            new CustoRepositorioMongo(conexao.banco()).criarIndices();

            // Define o endereço de escuta, usando loopback como padrão.
            String host = ambiente("JAVA_HOST", "127.0.0.1");
            // Obtém e valida a porta TCP, com 8081 como valor padrão.
            int porta = porta(ambiente("JAVA_PORT", "8081"));
            // Cria o servidor HTTP no endereço escolhido; zero deixa o backlog a cargo da implementação.
            HttpServer servidor = HttpServer.create(new InetSocketAddress(host, porta), 0);

            // Expõe a verificação de processo ativo sem executar outra consulta ao banco.
            servidor.createContext("/health", troca -> responder(troca, 200, "{\"status\":\"UP\"}"));
            // Registra a rota de prontidão que também verifica o acesso atual ao MongoDB.
            servidor.createContext("/ready", troca -> {
                // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
                try {
                    // Confere novamente o banco a cada consulta de prontidão.
                    conexao.verificar();
                    // Responde UP quando o ping do banco funciona.
                    responder(troca, 200, "{\"status\":\"UP\"}");
                // Trata a indisponibilidade do banco durante a consulta de prontidão.
                } catch (Exception erro) {
                    // Retorna HTTP 503 e DOWN para que o chamador saiba que o core não está pronto.
                    responder(troca, 503, "{\"status\":\"DOWN\"}");
                }
            });
            // Limita o processamento HTTP a um pool fixo de dez threads.
            servidor.setExecutor(Executors.newFixedThreadPool(10));
            // Passa a aceitar requisições depois da validação do banco e da criação dos índices.
            servidor.start();

            // Registra a rotina executada quando a JVM encerrar normalmente.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // Interrompe a escuta HTTP sem período adicional de espera.
                servidor.stop(0);
                // Fecha o cliente MongoDB durante o encerramento do processo.
                conexao.close();
            }));

            // Exibe apenas endereço e porta da instância que acabou de iniciar.
            System.out.println("Core pronto em http://" + host + ":" + porta);
        // Trata falhas posteriores à criação da conexão, incluindo índices ou abertura da porta.
        } catch (Exception erro) {
            // Libera a conexão se a inicialização não puder terminar.
            conexao.close();
            // Orienta a conferir acesso e configuração do Atlas sem imprimir a URI ou a senha.
            System.err.println("Não foi possível iniciar o core. Verifique MONGODB_URI, usuário, senha e IP autorizado no Atlas.");
            // Sinaliza a falha de inicialização ao sistema operacional.
            System.exit(1);
        }
    }

    // Consulta uma variável de ambiente e aplica o padrão quando ela não possui conteúdo.
    static String ambiente(String nome, String padrao) {
        // Consulta a variável de ambiente solicitada pelo chamador.
        String valor = System.getenv(nome);
        // Aplica o padrão se a variável estiver ausente ou em branco; caso contrário devolve o valor aparado.
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }

    // Converte e valida uma porta dentro do intervalo TCP permitido.
    static int porta(String texto) {
        // Converte o texto em inteiro e deixa a conversão rejeitar valores não numéricos.
        int valor = Integer.parseInt(texto);
        // Restringe a porta ao intervalo TCP utilizável de 1 a 65535.
        if (valor < 1 || valor > 65535) {
            // Rejeita a configuração que ficaria fora do intervalo permitido.
            throw new IllegalArgumentException("JAVA_PORT deve ser uma porta TCP válida");
        }
        // Retorna a porta já validada para a criação do endereço de escuta.
        return valor;
    }

    // Envia uma resposta HTTP em JSON codificado como UTF-8 e encerra a troca.
    private static void responder(HttpExchange troca, int status, String corpo) throws java.io.IOException {
        // Codifica o corpo como UTF-8 para calcular o tamanho real enviado pela rede.
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        // Declara explicitamente o formato JSON e a codificação do conteúdo.
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        // Envia o status HTTP e o comprimento do corpo em bytes.
        troca.sendResponseHeaders(status, bytes.length);
        // Escreve o corpo já codificado no fluxo da resposta.
        troca.getResponseBody().write(bytes);
        // Fecha a troca HTTP depois de enviar os dados ao cliente.
        troca.close();
    }
}
