package protocolo;

// Primeira coisa que o servidor diz depois de aceitar uma conexao.
public class ComunicadoDeBoasVindas extends Comunicado
{
    private static final long serialVersionUID = 1L;

    private final String versao;

    public ComunicadoDeBoasVindas (String versao)
    {
        this.versao = versao;
    }

    public String getVersao ()
    {
        return this.versao;
    }
}
