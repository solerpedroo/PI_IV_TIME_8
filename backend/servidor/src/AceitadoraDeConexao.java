/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import http.Roteador;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

/*
    Thread que fica no accept() e cria uma SupervisoraDeConexao por cliente.
    Limita as conexoes simultaneas com um Semaphore; quem passar do limite recebe 503.
*/
public class AceitadoraDeConexao extends Thread
{
    private static final String LOTADO =
        "HTTP/1.1 503 Service Unavailable\r\n" +
        "Content-Type: application/json; charset=utf-8\r\n" +
        "Content-Length: 34\r\n" +
        "Retry-After: 1\r\n" +
        "Connection: close\r\n" +
        "\r\n" +
        "{\"error\":\"Server too busy, retry\"}";

    private final ServerSocket              pedido;
    private final Roteador                  roteador;
    private final Semaphore                 vagas;
    private final Set<SupervisoraDeConexao> ativas = Collections.synchronizedSet (new HashSet<SupervisoraDeConexao> ());

    public AceitadoraDeConexao (ServerSocket pedido, Roteador roteador, int maximoDeConexoes) throws Exception
    {
        if (pedido == null)
            throw new Exception ("ServerSocket ausente");

        if (roteador == null)
            throw new Exception ("Roteador ausente");

        if (maximoDeConexoes < 1)
            throw new Exception ("Maximo de conexoes invalido");

        this.pedido   = pedido;
        this.roteador = roteador;
        this.vagas    = new Semaphore (maximoDeConexoes);

        this.setName ("aceitadora");
    }

    public void run ()
    {
        for (;;)
        {
            Socket conexao;
            try
            {
                conexao = this.pedido.accept ();
            }
            catch (SocketException erro)
            {
                break; // ServerSocket fechado pelo desativar
            }
            catch (Exception erro)
            {
                System.err.println ("Erro no accept: " + erro);
                continue;
            }

            if (!this.vagas.tryAcquire ())
            {
                AceitadoraDeConexao.recusar (conexao);
                continue;
            }

            try
            {
                SupervisoraDeConexao supervisora = new SupervisoraDeConexao (conexao, this.roteador, this.vagas, this.ativas);
                this.ativas.add (supervisora);
                supervisora.start ();
            }
            catch (Exception erro)
            {
                System.err.println ("Erro ao criar supervisora: " + erro);
                this.vagas.release ();
                AceitadoraDeConexao.fechar (conexao);
            }
        }
    }

    // espera as conexoes em andamento terminarem (cada uma tem no maximo o timeout de leitura)
    public void aguardarAtivas () throws InterruptedException
    {
        ArrayList<SupervisoraDeConexao> copia;
        synchronized (this.ativas)
        {
            copia = new ArrayList<SupervisoraDeConexao> (this.ativas);
        }

        for (SupervisoraDeConexao supervisora : copia)
            supervisora.join (15000);
    }

    private static void recusar (Socket conexao)
    {
        try
        {
            conexao.setSoTimeout (1000);
            OutputStream saida = conexao.getOutputStream ();
            saida.write (AceitadoraDeConexao.LOTADO.getBytes (StandardCharsets.UTF_8));
            saida.flush ();
        }
        catch (Exception erro)
        {}

        AceitadoraDeConexao.fechar (conexao);
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
