import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import protocolo.Parceiro;
import protocolo.RespostaDeErro;

/*
    Fica em accept() e entrega cada cliente a uma SupervisoraDeConexao (uma thread por cliente).
    O Semaphore limita as conexoes simultaneas: passou do limite, o cliente recebe SERVIDOR_LOTADO e e fechado.
*/
public class AceitadoraDeConexao extends Thread
{
    private static final int TEMPO_DA_RECUSA = 500; // espera pelo cabecalho do cliente recusado, em milissegundos

    private final ServerSocket              pedido;
    private final int                       ociosoEmMilissegundos;
    private final Semaphore                 vagas;
    private final Set<SupervisoraDeConexao> ativas = new HashSet<SupervisoraDeConexao> ();

    public AceitadoraDeConexao (ServerSocket pedido, int maximoDeConexoes, int ociosoEmMilissegundos) throws Exception
    {
        if (pedido == null)
            throw new Exception ("Pedido de conexao ausente");

        if (maximoDeConexoes < 1)
            throw new Exception ("Maximo de conexoes invalido");

        this.pedido                = pedido;
        this.vagas                 = new Semaphore (maximoDeConexoes);
        this.ociosoEmMilissegundos = ociosoEmMilissegundos;
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
                return; // o ServerSocket foi fechado por Servidor.desativar
            }
            catch (Exception erro)
            {
                System.err.println ("Falha ao aceitar conexao: " + erro);
                continue;
            }

            if (!this.vagas.tryAcquire ())
            {
                this.recusar (conexao);
                continue;
            }

            try
            {
                SupervisoraDeConexao supervisora = new SupervisoraDeConexao (conexao, this, this.ociosoEmMilissegundos);

                synchronized (this.ativas)
                {
                    this.ativas.add (supervisora);
                }

                supervisora.start ();
            }
            catch (Exception erro)
            {
                this.vagas.release ();
                this.fechar (conexao);
            }
        }
    }

    // a supervisora chama no fim: tira-se da lista e devolve a vaga (e o que mantem o limite correto)
    public void terminou (SupervisoraDeConexao supervisora)
    {
        synchronized (this.ativas)
        {
            this.ativas.remove (supervisora);
        }

        this.vagas.release ();
    }

    public int getConexoesAtivas ()
    {
        synchronized (this.ativas)
        {
            return this.ativas.size ();
        }
    }

    // avisa cada cliente conectado e espera as threads terminarem (ate 15 s cada)
    public void desligarAtivas () throws Exception
    {
        ArrayList<SupervisoraDeConexao> copia;

        synchronized (this.ativas)
        {
            copia = new ArrayList<SupervisoraDeConexao> (this.ativas);
        }

        for (SupervisoraDeConexao supervisora : copia)
            supervisora.desligar ();

        for (SupervisoraDeConexao supervisora : copia)
            supervisora.join (15000);
    }

    // o cliente ainda faz o cumprimento dos streams; so entao le o motivo da recusa
    private void recusar (Socket conexao)
    {
        try
        {
            conexao.setSoTimeout (AceitadoraDeConexao.TEMPO_DA_RECUSA);

            Parceiro parceiro = new Parceiro (conexao);
            parceiro.receba (new RespostaDeErro (0, RespostaDeErro.SERVIDOR_LOTADO, "Servidor lotado; tente novamente."));
        }
        catch (Exception erro)
        {} // cliente que nao cumprimenta a tempo apenas e fechado

        this.fechar (conexao);
    }

    private void fechar (Socket conexao)
    {
        try
        {
            conexao.close ();
        }
        catch (Exception erro)
        {}
    }
}
