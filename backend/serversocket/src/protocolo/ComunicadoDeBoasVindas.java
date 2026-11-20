package protocolo;

/*
    Primeira coisa que o servidor diz depois de aceitar uma conexao que coube no limite.
    O Cliente so segue se o primeiro comunicado for deste tipo. Qualquer outra coisa
    (RespostaDeErro de servidor lotado, ou um objeto inesperado) faz a API descartar o socket.
    versao e Protocolo.VERSAO ("1.0"): a API pode recusar um servidor de outra geracao.
*/
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
