/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import dados.ConexaoMongo;
import dados.RepositorioAtividades;
import dados.RepositorioAtividadesMemoria;
import dados.RepositorioAtividadesMongo;
import dados.RepositorioOcorrencias;
import dados.RepositorioOcorrenciasMemoria;
import dados.RepositorioOcorrenciasMongo;
import dados.RepositorioCustos;
import dados.RepositorioCustosMemoria;
import dados.RepositorioCustosMongo;
import dados.RepositorioSafras;
import dados.RepositorioSafrasMemoria;
import dados.RepositorioSafrasMongo;
import dados.RepositorioTalhoes;
import dados.RepositorioTalhoesMemoria;
import dados.RepositorioTalhoesMongo;
import dados.RepositorioEstoque;
import dados.RepositorioEstoqueMemoria;
import dados.RepositorioEstoqueMongo;
import dados.RepositorioUsuarios;
import dados.RepositorioUsuariosMemoria;
import dados.RepositorioUsuariosMongo;
import handlers.AuthHandler;
import handlers.AtividadesHandler;
import handlers.CustosHandler;
import handlers.EstaticosHandler;
import handlers.EstoqueHandler;
import handlers.PropriedadesHandler;
import handlers.PerfilHandler;
import handlers.SafrasHandler;
import handlers.SaudeHandler;
import handlers.TalhoesHandler;
import handlers.OcorrenciasHandler;
import http.Roteador;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import seguranca.Sessoes;
import servicos.ServicoEmail;

/*
    Servidor HTTP do AgroGestao feito na mao sobre ServerSocket.
    Uso: java -cp "bin;lib/*" Servidor [porta]

    Variaveis de ambiente (todas com padrao):
    SERVIDOR_HOST  127.0.0.1
    SERVIDOR_PORT  8080        (o argumento da linha de comando tem prioridade)
    MONGODB_URI    obrigatoria (ou use o argumento --memoria, sem banco)
    SERVIDOR_RAIZ  ../../frontend       (pasta do front)

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
    public static Roteador montarRoteador (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, RepositorioEstoque estoque,
                                           RepositorioSafras safras, RepositorioCustos custos,
                                           ConexaoMongo conexao, Path raiz) throws Exception
    {
        RepositorioAtividades atividades = conexao == null
            ? new RepositorioAtividadesMemoria ()
            : new RepositorioAtividadesMongo (conexao);
        return Servidor.montarRoteador (usuarios, talhoes, atividades, estoque, safras, custos, conexao, raiz);
    }

    // A sobrecarga permite que a execução principal reutilize o mesmo repositório
    // de atividades durante toda a vida do servidor.
    public static Roteador montarRoteador (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, RepositorioAtividades atividades,
                                           RepositorioEstoque estoque, RepositorioSafras safras, RepositorioCustos custos,
                                           ConexaoMongo conexao, Path raiz) throws Exception
    {
        RepositorioOcorrencias ocorrencias = conexao == null ? new RepositorioOcorrenciasMemoria () : new RepositorioOcorrenciasMongo (conexao);
        Sessoes  sessoes  = new Sessoes ();
        Roteador roteador = new Roteador (sessoes);

        new SaudeHandler   (conexao).registrar           (roteador);
        new AuthHandler    (usuarios, sessoes, ServicoEmail.doAmbiente ()).registrar (roteador);
        new PerfilHandler (usuarios, sessoes, ServicoEmail.doAmbiente ()).registrar (roteador);
        PropriedadesHandler propriedades = new PropriedadesHandler (usuarios, talhoes, custos, sessoes, ocorrencias);
        propriedades.setEstoque (estoque);
        propriedades.setAtividades (atividades);
        propriedades.registrar (roteador);
        TalhoesHandler handlerTalhoes = new TalhoesHandler (talhoes, atividades, sessoes);
        handlerTalhoes.registrar (roteador);
        new AtividadesHandler (atividades, talhoes, safras, sessoes).registrar (roteador);
        new EstoqueHandler (estoque, sessoes).registrar (roteador);
        new OcorrenciasHandler (ocorrencias, talhoes, sessoes).registrar (roteador);
        SafrasHandler handlerSafras = new SafrasHandler (safras, talhoes, custos, atividades, sessoes);
        handlerSafras.registrar (roteador);
        new CustosHandler  (custos, safras, talhoes, sessoes).registrar (roteador);

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
            Path   raiz = Path.of (Servidor.variavel ("SERVIDOR_RAIZ", "../../frontend"));

            ConexaoMongo        conexao  = null;
            RepositorioUsuarios usuarios = new RepositorioUsuariosMemoria ();
            RepositorioTalhoes  talhoes  = new RepositorioTalhoesMemoria  ();
            RepositorioAtividades atividades = new RepositorioAtividadesMemoria ();
            RepositorioEstoque  estoque  = new RepositorioEstoqueMemoria  ();
            RepositorioSafras   safras   = new RepositorioSafrasMemoria   ();
            RepositorioCustos   custos   = new RepositorioCustosMemoria   ();

            if (!memoria)
            {
                conexao = new ConexaoMongo (System.getenv ("MONGODB_URI"));

                // criar os indices ja exige o banco no ar; se nao estiver, para aqui
                try
                {
                    usuarios = new RepositorioUsuariosMongo (conexao);
                    talhoes  = new RepositorioTalhoesMongo  (conexao.getBanco ());
                    atividades = new RepositorioAtividadesMongo (conexao);
                    estoque  = new RepositorioEstoqueMongo  (conexao);
                    safras   = new RepositorioSafrasMongo   (conexao.getBanco ());
                    custos   = new RepositorioCustosMongo   (conexao.getBanco ());
                }
                catch (Exception erro)
                {
                    conexao.fechar ();
                    throw new Exception ("MongoDB indisponivel (" + erro.getClass ().getSimpleName () + ")");
                }
            }

            // A sobrecarga abaixo usa o repositório de atividades da execução.
            Roteador roteador = Servidor.montarRoteador (usuarios, talhoes, atividades, estoque, safras, custos, conexao, raiz);
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
