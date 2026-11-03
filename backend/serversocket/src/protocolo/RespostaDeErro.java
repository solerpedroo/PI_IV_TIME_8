package protocolo;

/*
    O servidor nao conseguiu atender: o pedido e invalido, os dados sao inconsistentes ou houve falha interna.
    A API decide como traduzir cada codigo para o front (por exemplo, DADOS_INCONSISTENTES vira 409).
    Falha de validacao de campo NAO e erro: volta como RespostaDeValidacao.
*/
public class RespostaDeErro extends Resposta
{
    private static final long serialVersionUID = 1L;

    public static final String PEDIDO_INVALIDO      = "PEDIDO_INVALIDO";      // faltam dados no pedido
    public static final String PEDIDO_DESCONHECIDO  = "PEDIDO_DESCONHECIDO";  // o servidor nao conhece este tipo de pedido
    public static final String DADOS_INCONSISTENTES = "DADOS_INCONSISTENTES"; // os dados enviados se contradizem
    public static final String COMUNICADO_PROIBIDO  = "COMUNICADO_PROIBIDO";  // classe fora da lista de permissao (filtro)
    public static final String COMUNICADO_INESPERADO = "COMUNICADO_INESPERADO"; // nao e um Pedido
    public static final String SERVIDOR_LOTADO      = "SERVIDOR_LOTADO";      // limite de conexoes atingido
    public static final String ERRO_INTERNO         = "ERRO_INTERNO";

    private final String codigo;
    private final String mensagem;

    public RespostaDeErro (long idDoPedido, String codigo, String mensagem)
    {
        super (idDoPedido);

        this.codigo   = codigo;
        this.mensagem = mensagem;
    }

    public String getCodigo ()
    {
        return this.codigo;
    }

    public String getMensagem ()
    {
        return this.mensagem;
    }

    public String toString ()
    {
        return super.toString () + "[" + this.codigo + "]";
    }
}
