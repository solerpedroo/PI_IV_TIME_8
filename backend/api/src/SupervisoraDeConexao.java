/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

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
    Atende um único cliente e morre.

    Passos, sempre nesta ordem:
      1. ParceiroHttp lê os bytes do socket e monta a Requisicao.
      2. Roteador escolhe o handler e devolve a Resposta.
      3. ParceiroHttp escreve status, cabeçalhos e corpo.
      4. O finally fecha o socket, sai da lista de ativas e devolve a vaga
         do Semaphore, mesmo se o cliente tiver desligado no meio.

    ErroHttp vira o status que o próprio erro pediu (400, 404...).
    Timeout de leitura vira 408. Qualquer outra falha vira 500 e vai para o
    stderr do servidor, não para o JSON do cliente.
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
            // ParceiroHttp segura os dois fluxos do socket: entrada (pedido) e saída (resposta).
            parceiro = new ParceiroHttp (this.conexao);

            Resposta   resposta;
            Requisicao requisicao = null;
            try
            {
                // Lê até a linha em branco e o corpo (Content-Length). null = zero bytes.
                requisicao = parceiro.lerRequisicao ();
                if (requisicao == null)
                    return; // o navegador abriu o socket e fechou sem enviar HTTP. Não há o que responder.

                // tratar escolhe o handler pelo método e pelo caminho e devolve status + corpo.
                resposta = this.roteador.tratar (requisicao);
            }
            // Erro esperado: o status já veio dentro da exceção (400, 404, 413...).
            catch (ErroHttp erro)
            {
                resposta = Resposta.erro (erro.getStatus (), erro.getMessage ());
            }
            // O cliente conectou e parou de enviar antes do prazo de leitura do socket.
            catch (SocketTimeoutException erro)
            {
                resposta = Resposta.erro (408, "Tempo da requisição esgotado.");
            }
            catch (Exception erro)
            {
                // Pilha fica no terminal do servidor. O JSON do cliente só diz "erro interno".
                System.err.println ("Erro na conexao: " + erro);
                resposta = Resposta.erro (500, "Erro interno do servidor.");
            }

            // Escreve "HTTP/1.1 <status> ...", os cabeçalhos e o corpo, e dá flush.
            parceiro.enviarResposta (resposta);

            // Linha de log: "GET /api/talhoes 200". Se a leitura falhou, o método vira "-".
            System.out.println ((requisicao == null ? "-" : requisicao.getMetodo () + " " + requisicao.getCaminho ()) +
                                " " + resposta.getStatus ());
        }
        catch (Exception erro)
        {} // o cliente fechou antes de receber a resposta. Não há para quem escrever; o finally ainda libera a vaga.
        finally
        {
            if (parceiro != null)
                parceiro.fechar ();
            else
                SupervisoraDeConexao.fechar (this.conexao);

            // Sem estas duas linhas o limite de conexões encheria e nunca esvaziaria.
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
