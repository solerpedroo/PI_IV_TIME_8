package protocolo;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/*
    O "outro lado" da conversa: encapsula o socket e os dois streams de objetos.
    Os nomes seguem o exemplo da disciplina: receba (o parceiro recebe o comunicado, ou seja, enviamos a ele),
    envie (o parceiro nos envia o proximo comunicado, ou seja, lemos dele) e adeus.
    O mesmo Parceiro e usado pelo servidor (um por cliente) e pela API (um para o servidor).
*/
public class Parceiro
{
    private final Socket             conexao;
    private final ObjectOutputStream transmissor;
    private final ObjectInputStream  receptor;

    public Parceiro (Socket conexao) throws Exception
    {
        if (conexao == null)
            throw new Exception ("Conexao ausente");

        this.conexao = conexao;

        // mensagens pequenas e uma por vez: sem isto o Nagle atrasa cada resposta
        conexao.setTcpNoDelay (true);

        // primeiro o transmissor, com flush: o ObjectInputStream do outro lado fica esperando este cabecalho
        this.transmissor = new ObjectOutputStream (conexao.getOutputStream ());
        this.transmissor.flush ();

        InputStream entrada = new LimiteDeLeitura (conexao.getInputStream (), Protocolo.MAXIMO_BYTES_POR_MENSAGEM);
        this.receptor = new ObjectInputStream (entrada);
        this.receptor.setObjectInputFilter (FiltroDeClasses.criar ());
    }

    // evita que o servidor (ou a API) fique parado para sempre esperando o outro lado
    public void setTempoLimite (int milissegundos) throws Exception
    {
        this.conexao.setSoTimeout (milissegundos);
    }

    public String getEndereco ()
    {
        return String.valueOf (this.conexao.getRemoteSocketAddress ());
    }

    // synchronized: o desligamento do servidor escreve de outra thread
    public synchronized void receba (Comunicado comunicado) throws Exception
    {
        if (comunicado == null)
            throw new Exception ("Comunicado ausente");

        try
        {
            this.transmissor.writeObject (comunicado);
            this.transmissor.flush ();

            // sem o reset, o ObjectOutputStream guarda todo objeto ja enviado e a memoria cresce a cada mensagem
            this.transmissor.reset ();
        }
        catch (IOException erro)
        {
            throw new Exception ("Erro de transmissao");
        }
    }

    /*
        Le o proximo comunicado; bloqueia ate ele chegar.
        Devolve null se o parceiro fechou a conexao. SocketTimeoutException (tempo limite),
        InvalidClassException (classe recusada pelo filtro) e ErroDeProtocolo sobem para quem chamou.
    */
    public Comunicado envie () throws Exception
    {
        Object lido;

        try
        {
            lido = this.receptor.readObject ();
        }
        catch (EOFException erro)
        {
            return null;
        }

        if (!(lido instanceof Comunicado))
            throw new ErroDeProtocolo ("Objeto recebido nao e um Comunicado");

        return (Comunicado) lido;
    }

    // fecha tudo; ignora erro porque nao ha o que fazer se ja estava fechado
    public void adeus ()
    {
        try
        {
            this.conexao.close ();
        }
        catch (Exception erro)
        {}
    }
}
