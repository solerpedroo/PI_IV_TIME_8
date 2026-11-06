import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import protocolo.Protocolo;

/*
    ServerSocket do AgroGestao: servidor de ALGORITMOS da API (validacoes e calculos).
    Nao fala HTTP, nao conhece rotas, sessoes nem banco: a API envia um Pedido e recebe uma Resposta
    pelo protocolo proprio do pacote protocolo.
    Uso: java -cp bin Servidor [porta]

    Variaveis de ambiente (todas com padrao):
    SERVIDOR_HOST  127.0.0.1
    SERVIDOR_PORT  3000   (o argumento da linha de comando tem prioridade)

    Digite "desativar" no terminal para encerrar.
*/
public class Servidor
{
    public static final int MAXIMO_DE_CONEXOES = 100;
    public static final int OCIOSO_EM_MILISSEGUNDOS = 300000; // 5 minutos sem pedido: o servidor fecha a conexao

    // mostra uma linha por pedido atendido; os testes desligam
    public static volatile boolean registrarPedidos = true;

    private final ServerSocket        pedido;
    private final AceitadoraDeConexao aceitadora;

    public Servidor (String host, int porta, int maximoDeConexoes, int ociosoEmMilissegundos) throws Exception
    {
        if (host == null)
            throw new Exception ("Host ausente");

        this.pedido     = new ServerSocket (porta, 50, InetAddress.getByName (host));
        this.aceitadora = new AceitadoraDeConexao (this.pedido, maximoDeConexoes, ociosoEmMilissegundos);

        this.aceitadora.start ();
    }

    // util quando a porta foi 0 (o sistema escolhe uma livre, como nos testes)
    public int getPorta ()
    {
        return this.pedido.getLocalPort ();
    }

    public int getConexoesAtivas ()
    {
        return this.aceitadora.getConexoesAtivas ();
    }

    // para de aceitar, avisa os clientes conectados (esperando o pedido em andamento) e espera as threads
    public void desativar () throws Exception
    {
        this.pedido.close ();
        this.aceitadora.join ();
        this.aceitadora.desligarAtivas ();
    }

    private static String variavel (String nome, String padrao)
    {
        String ret = System.getenv (nome);

        if (ret == null || ret.isBlank ())
            ret = padrao;

        return ret;
    }

    public static int lerPorta (String texto) throws Exception
    {
        int ret;

        try
        {
            ret = Integer.parseInt (texto.trim ());
        }
        catch (Exception erro)
        {
            throw new Exception ("Porta invalida: " + texto);
        }

        if (ret < 1 || ret > 65535)
            throw new Exception ("Porta invalida: " + texto);

        return ret;
    }

    public static void main (String[] args)
    {
        if (args.length > 1)
        {
            System.err.println ("Uso esperado: java Servidor [PORTA]");
            return;
        }

        Servidor servidor;

        try
        {
            String porta = Servidor.variavel ("SERVIDOR_PORT", String.valueOf (Protocolo.PORTA_PADRAO));

            if (args.length == 1)
                porta = args[0];

            String host = Servidor.variavel ("SERVIDOR_HOST", "127.0.0.1");

            servidor = new Servidor (host, Servidor.lerPorta (porta), Servidor.MAXIMO_DE_CONEXOES,
                                     Servidor.OCIOSO_EM_MILISSEGUNDOS);

            System.out.println ("ServerSocket ouvindo em " + host + ":" + servidor.getPorta () +
                                " (protocolo " + Protocolo.VERSAO + ")");
            System.out.println ("Digite \"desativar\" para encerrar.");
        }
        catch (Exception erro)
        {
            System.err.println ("Nao foi possivel iniciar: " + erro.getMessage ());
            System.exit (1);
            return;
        }

        try
        {
            BufferedReader teclado = new BufferedReader (new InputStreamReader (System.in));

            for (;;)
            {
                String comando = teclado.readLine ();

                // fim da entrada (ex.: rodando em segundo plano) -> continua no ar
                if (comando == null)
                {
                    Thread.currentThread ().join ();
                    return;
                }

                if (comando.trim ().equalsIgnoreCase ("desativar"))
                {
                    servidor.desativar ();
                    System.out.println ("O servidor foi desativado!");
                    System.exit (0);
                }

                if (!comando.isBlank ())
                    System.err.println ("Comando invalido!");
            }
        }
        catch (Exception erro)
        {
            System.err.println ("Erro ao desativar: " + erro.getMessage ());
            System.exit (1);
        }
    }
}
