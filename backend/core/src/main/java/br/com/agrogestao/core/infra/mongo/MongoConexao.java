// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;

// Importa o cliente que mantém os recursos de conexão do driver.
import com.mongodb.client.MongoClient;
// Importa a fábrica de clientes MongoDB a partir da URI.
import com.mongodb.client.MongoClients;
// Importa a seleção das coleções no banco compartilhado.
import com.mongodb.client.MongoDatabase;

// Agrupa as operações de MongoConexao nesta classe.
public final class MongoConexao implements AutoCloseable {
    // Mantém cliente do driver MongoDB como dependência ou dado imutável.
    private final MongoClient cliente;
    // Mantém banco compartilhado pelos repositórios como dependência ou dado imutável.
    private final MongoDatabase banco;

    // Inicializa MongoConexao com os valores recebidos e as validações detalhadas a seguir.
    public MongoConexao(String uri, String nomeBanco) {
        // Exige uma URI não vazia antes de criar o cliente MongoDB.
        if (uri == null || uri.isBlank()) {
            // Interrompe a construção quando a configuração de conexão não foi fornecida.
            throw new IllegalArgumentException("MONGODB_URI não foi configurada");
        }
        // Exige explicitamente o nome do banco selecionado para esta conexão.
        if (nomeBanco == null || nomeBanco.isBlank()) {
            // Informa que falta o nome do banco em vez de assumir uma seleção implícita.
            throw new IllegalArgumentException("MONGODB_DATABASE não foi configurada");
        }

        // Cria o cliente compartilhável do driver usando a URI sem espaços nas extremidades.
        this.cliente = MongoClients.create(uri.trim());
        // Seleciona o banco pelo nome recebido, independentemente do banco eventualmente presente na URI.
        this.banco = cliente.getDatabase(nomeBanco.trim());
    }

    // Constrói a conexão da aplicação usando a URI e o banco configurados no ambiente.
    public static MongoConexao peloAmbiente() {
        // Centraliza a criação da conexão de aplicação com os valores do ambiente.
        return new MongoConexao(
                // Obtém a URI do ambiente sem incluí-la em mensagens de diagnóstico.
                System.getenv("MONGODB_URI"),
                // Usa o banco configurado ou agrogestao_dev quando MONGODB_DATABASE não estiver definido.
                valorAmbiente("MONGODB_DATABASE", "agrogestao_dev"));
    }

    /**
     * Conexão só para suítes Atlas. O nome do banco precisa terminar em {@code _teste}
     * para não gravar dados fictícios em {@code agrogestao_dev}.
     */
    // Restringe a conexão da suíte a um banco explicitamente identificado com o sufixo _teste.
    public static MongoConexao paraTestes() {
        // Lê o banco de teste sem aplicar um nome de produção como alternativa.
        String nomeBanco = valorAmbiente("MONGODB_DATABASE", "");
        // Só permite a suíte quando o nome foi definido e termina em _teste.
        if (nomeBanco.isBlank() || !nomeBanco.endsWith("_teste")) {
            // Aborta antes de criar o cliente se o banco não atende à proteção da suíte.
            throw new IllegalArgumentException(
                    // Explica como selecionar um banco próprio para os dados fictícios dos testes.
                    "Para testes Atlas, defina MONGODB_DATABASE com nome terminado em _teste");
        }
        // Abre a conexão de teste depois de validar o nome do banco.
        return new MongoConexao(System.getenv("MONGODB_URI"), nomeBanco);
    }

    // Disponibiliza o banco selecionado para os repositórios sem criar um novo cliente.
    public MongoDatabase banco() {
        // Expõe o objeto do banco para os repositórios que compartilham este cliente.
        return banco;
    }

    /** Nome do banco atual — útil para os testes confirmarem o sufixo _teste. */
    // Permite conferir o banco efetivamente selecionado nesta conexão.
    public String nomeBanco() {
        // Informa o nome efetivo do banco, permitindo conferir o ambiente de execução.
        return banco.getName();
    }

    // Verifica a disponibilidade do banco selecionado com um comando ping.
    public void verificar() {
        // Executa ping no banco para verificar a disponibilidade real da conexão.
        banco.runCommand(new Document("ping", 1));
    }

    // Indica que este método implementa o contrato herdado pela classe.
    @Override
    // Libera os recursos mantidos por esta instância ao terminar seu uso.
    public void close() {
        // Fecha o cliente e libera seus recursos e conexões de rede.
        cliente.close();
    }

    // Lê uma variável de ambiente e aplica o valor padrão quando ela não contém informação.
    private static String valorAmbiente(String nome, String padrao) {
        // Consulta a variável de ambiente pelo nome recebido.
        String valor = System.getenv(nome);
        // Usa o padrão quando a variável não existe ou está vazia; nos demais casos remove espaços externos.
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
