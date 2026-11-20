package protocolo;

/*
    O servidor nao conseguiu atender. Nao e falha de campo: essa volta como RespostaDeValidacao,
    com o mapa de erros para o front. Isto e falha do pedido, da conexao ou do proprio servidor.
    A API traduz o codigo para HTTP (DADOS_INCONSISTENTES vira 409, por exemplo). A mensagem
    fica no log da API; o front recebe um texto generico, nao este.
    idDoPedido 0: ainda nao havia pedido (servidor lotado, ou comunicado lido fora do laco).
*/
public class RespostaDeErro extends Resposta
{
    private static final long serialVersionUID = 1L;

    public static final String PEDIDO_INVALIDO       = "PEDIDO_INVALIDO";        // faltou lista, data de hoje, poligono do banco
    public static final String PEDIDO_DESCONHECIDO   = "PEDIDO_DESCONHECIDO";    // o Atendente nao tem "if" para esta classe
    public static final String DADOS_INCONSISTENTES  = "DADOS_INCONSISTENTES";   // numero que nao fecha: area NaN, custo que nao vira centavos
    public static final String COMUNICADO_PROIBIDO   = "COMUNICADO_PROIBIDO";    // o filtro recusou a classe, ou o objeto nao e Comunicado
    public static final String COMUNICADO_INESPERADO = "COMUNICADO_INESPERADO";  // chegou algo que nao e Pedido nem PedidoParaSair
    public static final String SERVIDOR_LOTADO       = "SERVIDOR_LOTADO";        // Semaphore sem vaga; a conexao fecha em seguida
    public static final String ERRO_INTERNO          = "ERRO_INTERNO";           // excecao no algoritmo; o detalhe fica so no console

    private final String codigo;    // uma das constantes acima; a API compara isto, nao o texto
    private final String mensagem;  // frase para log; nao e a mensagem que o usuario ve na tela

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
