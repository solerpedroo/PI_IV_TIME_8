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
    Porta de entrada do servidor HTTP.

    Fica parada em accept() esperando o navegador conectar. Para cada conexão
    cria uma SupervisoraDeConexao (outra thread) que lê o pedido e responde.

    O Semaphore é o limite de clientes ao mesmo tempo. tryAcquire() sem espera:
    se não houver vaga, respondemos 503 na hora e fechamos, em vez de deixar
    a fila crescer até a máquina travar. Quando a supervisora termina, ela
    devolve a vaga (release).

    desativar() fecha o ServerSocket. O accept() então lança SocketException
    e o loop acaba. aguardarAtivas() espera quem ainda estava no meio de um pedido.
*/
public class AceitadoraDeConexao extends Thread
{
    /*
        Resposta HTTP já pronta, em texto, para quem chegou sem vaga.
        Não passa pelo Roteador: a supervisora nem é criada.

        Primeira linha: versão, status 503 e o texto do status.
        Cabeçalhos separados por \r\n (exigência do HTTP, não \n sozinho).
        Content-Length 34 é o tamanho em bytes do JSON abaixo, em UTF-8.
        Retry-After: 1 pede ao cliente esperar 1 segundo antes de tentar de novo.
        Uma linha em branco (\r\n\r\n) separa os cabeçalhos do corpo.
        Corpo: {"error":"Servidor ocupado. Tente novamente."}
    */
    private static final String LOTADO =
        "HTTP/1.1 503 Serviço indisponível\r\n" +
        "Content-Type: application/json; charset=utf-8\r\n" +
        "Content-Length: 34\r\n" +
        "Retry-After: 1\r\n" +
        "Connection: close\r\n" +
        "\r\n" +
        "{\"error\":\"Servidor ocupado. Tente novamente.\"}";

    // Socket que escuta a porta (8080 em geral). accept() bloqueia até alguém conectar.
    private final ServerSocket              pedido;
    // Tabela de rotas. Cada supervisora usa o mesmo roteador; ele não guarda estado do cliente.
    private final Roteador                  roteador;
    // "Fichas" de atendimento. O construtor coloca maximoDeConexoes fichas.
    // tryAcquire tira uma; a supervisora devolve com release no finally.
    private final Semaphore                 vagas;
    // Quem ainda está atendendo. synchronizedSet porque a aceitadora adiciona
    // e cada supervisora se remove, em threads diferentes.
    private final Set<SupervisoraDeConexao> ativas = Collections.synchronizedSet (new HashSet<SupervisoraDeConexao> ());

    public AceitadoraDeConexao (ServerSocket pedido, Roteador roteador, int maximoDeConexoes) throws Exception
    {
        if (pedido == null)
            throw new Exception ("ServerSocket ausente");

        if (roteador == null)
            throw new Exception ("Roteador ausente");

        if (maximoDeConexoes < 1)
            throw new Exception ("Maximo de conexoes invalido");

        // Semaphore(n) começa com n permissões. Cada cliente em atendimento segura uma.
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
                    // Bloqueia esta thread até um navegador abrir TCP. O Socket devolvido
                // é só esse cliente; o ServerSocket continua livre para o próximo accept.
                conexao = this.pedido.accept ();
            }
            catch (SocketException erro)
            {
                // desativar() fechou o ServerSocket de propósito. Não é falha: saímos do loop.
                break;
            }
            catch (Exception erro)
            {
                System.err.println ("Erro no accept: " + erro);
                continue;
            }

            // tryAcquire não espera. false = todas as fichas estão com outras supervisoras.
            if (!this.vagas.tryAcquire ())
            {
                AceitadoraDeConexao.recusar (conexao);
                continue;
            }

            try
            {
                SupervisoraDeConexao supervisora = new SupervisoraDeConexao (conexao, this.roteador, this.vagas, this.ativas);
                // Entra na lista ANTES de start: se desativar() rodar no mesmo instante,
                // aguardarAtivas ainda enxerga esta thread.
                this.ativas.add (supervisora);
                // start() chama run() em outra thread. Esta aqui volta ao accept.
                supervisora.start ();
            }
            catch (Exception erro)
            {
                System.err.println ("Erro ao criar supervisora: " + erro);
                // A ficha já tinha sido tirada e a supervisora não chegou a nascer.
                // Sem este release o limite ficaria menor para sempre.
                this.vagas.release ();
                AceitadoraDeConexao.fechar (conexao);
            }
        }
    }

    // Copia a lista antes de esperar, porque as supervisoras se removem de ativas
    // enquanto este método roda. join(15000) não espera para sempre se alguma travar.
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

    /*
        Escreve LOTADO e fecha. setSoTimeout(1000) evita ficar preso se o cliente
        não ler. O catch vazio é de propósito: quem já desligou não precisa de 503.
    */
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
