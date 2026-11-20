package protocolo.mensagens.estoque;

import protocolo.Pedido;

/*
    Calcula o efeito de uma movimentacao de estoque (entrada, saida ou ajuste) sobre UM insumo.
    A API envia o saldo e o custo medio que leu do banco e os dados da movimentacao; o servidor valida,
    calcula o novo saldo e o novo custo medio e devolve. Quem grava no banco (de forma atomica) e a API.
    Vinculo com atividade e propriedade do insumo dependem do banco e continuam na API.
*/
public class PedidoDeMovimentacaoDeEstoque extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final double saldoAtual;         // lido do banco; negativo ou NaN e pedido invalido, nao erro de campo
    private final double custoUnitarioAtual; // custo medio atual, mesma regra do saldo
    private final String tipo;               // entrada, saida ou ajuste
    private final Double quantidade;         // seis casas; ajuste pode ser zero
    private final Double custoUnitario;      // so a entrada com custo recalcula a media; null mantem a atual
    private final String observacao;         // obrigatoria no ajuste, ate 240

    public PedidoDeMovimentacaoDeEstoque (double saldoAtual, double custoUnitarioAtual, String tipo,
                                          Double quantidade, Double custoUnitario, String observacao)
    {
        this.saldoAtual         = saldoAtual;
        this.custoUnitarioAtual = custoUnitarioAtual;
        this.tipo               = tipo;
        this.quantidade         = quantidade;
        this.custoUnitario      = custoUnitario;
        this.observacao         = observacao;
    }

    public double getSaldoAtual ()         { return this.saldoAtual; }
    public double getCustoUnitarioAtual () { return this.custoUnitarioAtual; }
    public String getTipo ()               { return this.tipo; }
    public Double getQuantidade ()         { return this.quantidade; }
    public Double getCustoUnitario ()      { return this.custoUnitario; }
    public String getObservacao ()         { return this.observacao; }
}
