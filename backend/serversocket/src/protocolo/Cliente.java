package protocolo;

import java.net.InetSocketAddress;
import java.net.Socket;

/*
    Lado da API: conecta ao servidor e faz "chamadas" (um Pedido, uma Resposta).
    Esta classe e o que a API copia junto com o pacote protocolo.

    Uma conexao atende um pedido por vez (chamar e synchronized); para atender requisicoes em paralelo,
    a API abre varias conexoes. Se uma chamada estourar o tempo limite, descarte o Cliente e abra outro.
*/
public class Cliente
{
    private final Parceiro servidor;

    public Cliente (String host, int porta, int tempoLimiteEmMilissegundos) throws Exception
    {
        if (host == null)
            throw new Exception ("Host ausente");

        Socket conexao = new Socket ();

        try
        {
            conexao.connect (new InetSocketAddress (host, porta), tempoLimiteEmMilissegundos);
            conexao.setSoTimeout (tempoLimiteEmMilissegundos);

            this.servidor = new Parceiro (conexao);

            // o servidor fala primeiro: boas-vindas, ou o motivo da recusa
            Comunicado primeiro = this.servidor.envie ();

            if (primeiro instanceof RespostaDeErro)
                throw new Exception ("Servidor recusou a conexao: " + ((RespostaDeErro) primeiro).getCodigo ());

            if (!(primeiro instanceof ComunicadoDeBoasVindas))
                throw new Exception ("O servidor nao respondeu conforme o protocolo");
        }
        catch (Exception erro)
        {
            try
            {
                conexao.close ();
            }
            catch (Exception falha)
            {} // so tentando fechar antes de propagar o erro

            throw erro;
        }
    }

    /*
        Envia o pedido e devolve a resposta dele. Erros de aplicacao chegam como RespostaDeErro (nao como excecao);
        excecao significa falha de comunicacao (rede, tempo limite, servidor desligando).
    */
    public synchronized Resposta chamar (Pedido pedido) throws Exception
    {
        if (pedido == null)
            throw new Exception ("Pedido ausente");

        this.servidor.receba (pedido);

        for (;;)
        {
            Comunicado comunicado = this.servidor.envie ();

            if (comunicado == null)
                throw new Exception ("O servidor encerrou a conexao");

            if (comunicado instanceof ComunicadoDeDesligamento)
                throw new Exception ("O servidor esta sendo desligado");

            if (comunicado instanceof Resposta)
            {
                Resposta resposta = (Resposta) comunicado;

                if (resposta.getIdDoPedido () == pedido.getId ())
                    return resposta;

                // erro de conexao, sem pedido associado (por exemplo, comunicado recusado pelo filtro)
                if (resposta instanceof RespostaDeErro && resposta.getIdDoPedido () == 0)
                    return resposta;

                // senao e a resposta atrasada de um pedido anterior que estourou o tempo limite: descarta
            }
        }
    }

    public void fechar ()
    {
        try
        {
            this.servidor.receba (new PedidoParaSair ());
        }
        catch (Exception erro)
        {} // so tentando avisar; o adeus fecha de qualquer jeito

        this.servidor.adeus ();
    }
}
