import java.io.InvalidClassException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.Socket;
import protocolo.Comunicado;
import protocolo.ComunicadoDeBoasVindas;
import protocolo.ComunicadoDeDesligamento;
import protocolo.ErroDeProtocolo;
import protocolo.Parceiro;
import protocolo.Pedido;
import protocolo.PedidoParaSair;
import protocolo.Protocolo;
import protocolo.Resposta;
import protocolo.RespostaDeErro;

/*
    Atendente de UMA conexao (uma thread por conexao, como no exemplo da disciplina).
    Fica num laco: le um Pedido, chama o Atendente e devolve a Resposta, ate a API sair ou ficar ociosa.
    Um erro numa conexao nunca derruba o servidor nem as outras conexoes.
*/
public class SupervisoraDeConexao extends Thread
{
    private static final int TEMPO_DO_CUMPRIMENTO = 10000; // cabecalho dos streams, em milissegundos

    private final Socket              conexao;
    private final AceitadoraDeConexao aceitadora;
    private final int                 ociosoEmMilissegundos;

    // lidos tambem pela thread do Servidor (desligar), por isso volatile
    private volatile Parceiro parceiro;
    private volatile boolean  desligando = false;

    // um pedido por vez: o desligamento espera o pedido em andamento terminar antes de fechar a conexao
    private final Object trava = new Object ();

    public SupervisoraDeConexao (Socket conexao, AceitadoraDeConexao aceitadora, int ociosoEmMilissegundos) throws Exception
    {
        if (conexao == null)
            throw new Exception ("Conexao ausente");

        if (aceitadora == null)
            throw new Exception ("Aceitadora ausente");

        this.conexao               = conexao;
        this.aceitadora            = aceitadora;
        this.ociosoEmMilissegundos = ociosoEmMilissegundos;
    }

    public void run ()
    {
        try
        {
            // o cabecalho dos streams precisa chegar logo; depois disso, o limite e o de ociosidade
            this.conexao.setSoTimeout (SupervisoraDeConexao.TEMPO_DO_CUMPRIMENTO);
            this.parceiro = new Parceiro (this.conexao);
            this.conexao.setSoTimeout (this.ociosoEmMilissegundos);

            this.parceiro.receba (new ComunicadoDeBoasVindas (Protocolo.VERSAO));

            for (;;)
            {
                Comunicado comunicado = this.parceiro.envie ();

                if (comunicado == null || comunicado instanceof PedidoParaSair)
                    return;

                synchronized (this.trava)
                {
                    if (!(comunicado instanceof Pedido))
                    {
                        this.parceiro.receba (new RespostaDeErro (0, RespostaDeErro.COMUNICADO_INESPERADO,
                                                                  "O servidor so aceita pedidos."));
                        return;
                    }

                    Pedido   pedido   = (Pedido) comunicado;
                    Resposta resposta = Atendente.atender (pedido);

                    this.parceiro.receba (resposta);
                    this.registrar (pedido, resposta);
                }
            }
        }
        catch (SocketTimeoutException erro)
        {
            // ficou ocioso: fecha em silencio; a API reconecta quando precisar
        }
        catch (InvalidClassException | ErroDeProtocolo erro)
        {
            // classe fora da lista de permissao, ou objeto que nao e um Comunicado: avisa e encerra
            this.recusar (erro);
        }
        catch (Exception erro)
        {
            // a API fechou a conexao (SocketException) ou o servidor esta desligando: sem barulho
            if (!this.desligando && !(erro instanceof SocketException))
                System.err.println ("Conexao " + this.getEndereco () + " encerrada por erro: " + erro);
        }
        finally
        {
            if (this.parceiro != null)
                this.parceiro.adeus ();
            else
                this.fechar ();

            this.aceitadora.terminou (this);
        }
    }

    // chamado pela thread do Servidor ao desativar
    public void desligar ()
    {
        this.desligando = true;

        synchronized (this.trava)
        {
            if (this.parceiro != null)
            {
                try
                {
                    this.parceiro.receba (new ComunicadoDeDesligamento ());
                }
                catch (Exception erro)
                {} // a API pode ja ter ido embora

                this.parceiro.adeus ();
            }
            else
                this.fechar ();
        }
    }

    private void recusar (Exception motivo)
    {
        System.err.println ("Comunicado recusado de " + this.getEndereco () + ": " + motivo.getMessage ());

        try
        {
            this.parceiro.receba (new RespostaDeErro (0, RespostaDeErro.COMUNICADO_PROIBIDO, "Comunicado recusado pelo servidor."));
        }
        catch (Exception erro)
        {} // so tentando avisar antes de fechar
    }

    private void registrar (Pedido pedido, Resposta resposta)
    {
        if (Servidor.registrarPedidos)
            System.out.println (this.getEndereco () + " " + pedido + " -> " + resposta);
    }

    private String getEndereco ()
    {
        return String.valueOf (this.conexao.getRemoteSocketAddress ());
    }

    private void fechar ()
    {
        try
        {
            this.conexao.close ();
        }
        catch (Exception erro)
        {}
    }
}
