import http.ErroHttp;
import http.ParceiroHttp;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.Semaphore;

/*
    Uma thread por conexao: le a requisicao, chama o roteador,
    escreve a resposta e fecha. Erro de um cliente nunca derruba o servidor.
*/
public class SupervisoraDeConexao extends Thread
{
    private final Socket                    conexao;
    private final Roteador                  roteador;
    private final Semaphore                 vagas;
    private final Set<SupervisoraDeConexao> ativas;

    public SupervisoraDeConexao (Socket conexao, Roteador roteador, Semaphore vagas,
                                 Set<SupervisoraDeConexao> ativas) throws Exception
    {
        if (conexao == null)
            throw new Exception ("Conexao ausente");

        if (roteador == null)
            throw new Exception ("Roteador ausente");

        if (vagas == null)
            throw new Exception ("Vagas ausentes");

        if (ativas == null)
            throw new Exception ("Conjunto de ativas ausente");

        this.conexao  = conexao;
        this.roteador = roteador;
        this.vagas    = vagas;
        this.ativas   = ativas;

        this.setName ("supervisora-" + conexao.getPort ());
    }

    public void run ()
    {
        ParceiroHttp parceiro = null;

        try
        {
            parceiro = new ParceiroHttp (this.conexao);

            Resposta   resposta;
            Requisicao requisicao = null;
            try
            {
                requisicao = parceiro.lerRequisicao ();
                if (requisicao == null)
                    return; // cliente conectou e foi embora sem mandar nada

                resposta = this.roteador.tratar (requisicao);
            }
            catch (ErroHttp erro)
            {
                resposta = Resposta.erro (erro.getStatus (), erro.getMessage ());
            }
            catch (SocketTimeoutException erro)
            {
                resposta = Resposta.erro (408, "Request timeout");
            }
            catch (Exception erro)
            {
                System.err.println ("Erro na conexao: " + erro);
                resposta = Resposta.erro (500, "Internal server error");
            }

            parceiro.enviarResposta (resposta);

            System.out.println ((requisicao == null ? "-" : requisicao.getMetodo () + " " + requisicao.getCaminho ()) +
                                " " + resposta.getStatus ());
        }
        catch (Exception erro)
        {} // nao deu para responder (cliente fechou antes); so segue para o finally
        finally
        {
            if (parceiro != null)
                parceiro.fechar ();
            else
                SupervisoraDeConexao.fechar (this.conexao);

            this.ativas.remove (this);
            this.vagas.release ();
        }
    }

    private static void fechar (Socket conexao)
    {
        try
        {
            conexao.close ();
        }
        catch (Exception erro)
        {}
    }
}
