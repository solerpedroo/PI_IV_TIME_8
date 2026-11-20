package protocolo;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/*
    O outro lado da conversa: um socket e os dois streams de objetos.
    Os nomes seguem o exemplo da disciplina, vistos daqui:
      receba: o parceiro recebe o comunicado, ou seja, nos escrevemos nele;
      envie:  o parceiro nos envia o proximo comunicado, ou seja, nos lemos dele;
      adeus:  fecha o socket.
    O servidor tem um Parceiro por cliente. A API tem um Parceiro para o servidor, dentro de Cliente.
    Quem constroi precisa criar o seu transmissor antes de bloquear no receptor, senao os dois lados
    ficam esperando o cabecalho um do outro.
*/
public class Parceiro
{
    private final Socket             conexao;
    private final ObjectOutputStream transmissor; // saida: writeObject
    private final ObjectInputStream  receptor;    // entrada: readObject, ja com filtro e teto de bytes

    public Parceiro (Socket conexao) throws Exception
    {
        if (conexao == null)
            throw new Exception ("Conexao ausente");

        this.conexao = conexao;

        // uma mensagem pequena por vez: o Nagle juntaria a resposta com a proxima e a API esperaria a toa
        conexao.setTcpNoDelay (true);

        // o cabecalho do ObjectOutputStream so sai no flush; o ObjectInputStream do outro lado le isso primeiro
        this.transmissor = new ObjectOutputStream (conexao.getOutputStream ());
        this.transmissor.flush ();

        InputStream entrada = new LimiteDeLeitura (conexao.getInputStream (), Protocolo.MAXIMO_BYTES_POR_MENSAGEM);
        this.receptor = new ObjectInputStream (entrada);
        this.receptor.setObjectInputFilter (FiltroDeClasses.criar ());
    }

    // SoTimeout da leitura. Zero esperaria para sempre. A supervisora troca isto depois do cumprimento.
    public void setTempoLimite (int milissegundos) throws Exception
    {
        this.conexao.setSoTimeout (milissegundos);
    }

    // ip:porta do outro lado, para o log. Nao entra no protocolo.
    public String getEndereco ()
    {
        return String.valueOf (this.conexao.getRemoteSocketAddress ());
    }

    /*
        Escreve um comunicado e empurra os bytes agora.
        synchronized porque o desligamento escreve desta thread e a supervisora escreve de outra:
        dois writeObject ao mesmo tempo corrompem o stream.
    */
    public synchronized void receba (Comunicado comunicado) throws Exception
    {
        if (comunicado == null)
            throw new Exception ("Comunicado ausente");

        try
        {
            this.transmissor.writeObject (comunicado);
            this.transmissor.flush ();

            // sem reset o stream guarda todo objeto ja escrito, para reaproveitar referencia; aqui cada mensagem e nova
            this.transmissor.reset ();
        }
        catch (IOException erro)
        {
            throw new Exception ("Erro de transmissao");
        }
    }

    /*
        Le o proximo comunicado. Bloqueia ate chegar, ate estourar o SoTimeout ou ate o outro lado fechar.
        null: EOF, a conexao acabou sem objeto. Quem chamou decide se isso e saida normal ou erro.
        SocketTimeoutException, InvalidClassException (filtro) e ErroDeProtocolo nao sao engolidos aqui.
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

    // fecha o socket (e, com ele, os dois streams). Ja fechado nao e falha: nao ha o que avisar.
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
