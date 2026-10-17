import dados.ConexaoMongo;
import dados.RepositorioTalhoes;
import dados.RepositorioTalhoesMemoria;
import dados.RepositorioTalhoesMongo;
import dados.RepositorioUsuarios;
import dados.RepositorioUsuariosMemoria;
import dados.RepositorioUsuariosMongo;
import handlers.AuthHandler;
import handlers.EstaticosHandler;
import handlers.SaudeHandler;
import handlers.TalhoesHandler;
import http.Roteador;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import seguranca.Sessoes;

/*
    Servidor HTTP do AgroGestao feito na mao sobre ServerSocket.
    Uso: java -cp "bin;lib/*" Servidor [porta]

    Variaveis de ambiente (todas com padrao):
    SERVIDOR_HOST  127.0.0.1
    SERVIDOR_PORT  8080        (o argumento da linha de comando tem prioridade)
    MONGODB_URI    obrigatoria (ou use o argumento --memoria, sem banco)
    SERVIDOR_RAIZ  ../..       (pasta do front)

    Digite "desativar" no terminal para encerrar.
*/
public class Servidor
{
    public static final int MAXIMO_DE_CONEXOES = 100;

    private final ServerSocket        pedido;
    private final AceitadoraDeConexao aceitadora;
    private final ConexaoMongo        conexao; // null no modo memoria

    public Servidor (String host, int porta, Roteador roteador, ConexaoMongo conexao, int maximoDeConexoes) throws Exception
    {
        if (host == null)
            throw new Exception ("Host ausente");

        if (roteador == null)
            throw new Exception ("Roteador ausente");

        this.pedido     = new ServerSocket (porta, 50, InetAddress.getByName (host));
        this.aceitadora = new AceitadoraDeConexao (this.pedido, roteador, maximoDeConexoes);
        this.conexao    = conexao;

        this.aceitadora.start ();
    }

    public int getPorta ()
    {
        return this.pedido.getLocalPort ();
    }

    // fecha o ServerSocket, espera as conexoes em andamento e fecha o Mongo
    public void desativar () throws Exception
    {
        this.pedido.close ();
        this.aceitadora.join ();
        this.aceitadora.aguardarAtivas ();

        if (this.conexao != null)
            this.conexao.fechar ();
    }

    // conexao pode ser null (modo memoria / testes)
    public static Roteador montarRoteador (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes,
                                           ConexaoMongo conexao, Path raiz) throws Exception
    {
        Sessoes  sessoes  = new Sessoes ();
        Roteador roteador = new Roteador (sessoes);

        new SaudeHandler   (conexao).registrar           (roteador);
        new AuthHandler    (usuarios, sessoes).registrar (roteador);
        new TalhoesHandler (talhoes).registrar           (roteador);

        roteador.setEstaticos (new EstaticosHandler (raiz)::tratar);

        return roteador;
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
        Servidor servidor;

        try
        {
            boolean memoria = false;
            String  porta   = Servidor.variavel ("SERVIDOR_PORT", "8080");

            for (String arg : args)
            {
                if (arg.equals ("--memoria"))
                    memoria = true;
                else
                    porta = arg;
            }

            String host = Servidor.variavel ("SERVIDOR_HOST", "127.0.0.1");
            Path   raiz = Path.of (Servidor.variavel ("SERVIDOR_RAIZ", "../.."));

            ConexaoMongo        conexao  = null;
            RepositorioUsuarios usuarios = new RepositorioUsuariosMemoria ();
            RepositorioTalhoes  talhoes  = new RepositorioTalhoesMemoria  ();

            if (!memoria)
            {
                conexao = new ConexaoMongo (System.getenv ("MONGODB_URI"));

                // criar os indices ja exige o banco no ar; se nao estiver, para aqui
                try
                {
                    usuarios = new RepositorioUsuariosMongo (conexao.getBanco ());
                    talhoes  = new RepositorioTalhoesMongo  (conexao.getBanco ());
                }
                catch (Exception erro)
                {
                    conexao.fechar ();
                    throw new Exception ("MongoDB indisponivel (" + erro.getClass ().getSimpleName () + ")");
                }
            }

            Roteador roteador = Servidor.montarRoteador (usuarios, talhoes, conexao, raiz);
            servidor = new Servidor (host, Servidor.lerPorta (porta), roteador, conexao, Servidor.MAXIMO_DE_CONEXOES);

            System.out.println ("Servidor ouvindo em http://" + host + ":" + servidor.getPorta () +
                                (memoria ? " (modo memoria, sem banco)" : ""));
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
                    break;

                System.out.println ("Comando invalido. Use \"desativar\".");
            }

            System.out.println ("Desativando...");
            servidor.desativar ();
            System.out.println ("Servidor desativado.");
        }
        catch (Exception erro)
        {
            System.err.println ("Erro ao desativar: " + erro.getMessage ());
            System.exit (1);
        }
    }
}
