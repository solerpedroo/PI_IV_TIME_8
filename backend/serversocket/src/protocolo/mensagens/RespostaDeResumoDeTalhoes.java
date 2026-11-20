package protocolo.mensagens;

import protocolo.Resposta;

/*
    Resultado de PedidoDeResumoDeTalhoes. A API devolve estes tres numeros no JSON do resumo.
    totalHectares e a soma crua das areas recebidas (nao revalida o teto de 1.000.000 ha).
    quantidadeAtivos conta so status igual a "Ativo"; os demais status entram apenas no total.
*/
public class RespostaDeResumoDeTalhoes extends Resposta
{
    private static final long serialVersionUID = 1L;

    private final double totalHectares;
    private final int    quantidadeTotal;
    private final int    quantidadeAtivos;

    public RespostaDeResumoDeTalhoes (long idDoPedido, double totalHectares, int quantidadeTotal, int quantidadeAtivos)
    {
        super (idDoPedido);

        this.totalHectares    = totalHectares;
        this.quantidadeTotal  = quantidadeTotal;
        this.quantidadeAtivos = quantidadeAtivos;
    }

    public double getTotalHectares ()    { return this.totalHectares; }
    public int    getQuantidadeTotal ()  { return this.quantidadeTotal; }
    public int    getQuantidadeAtivos () { return this.quantidadeAtivos; }
}
