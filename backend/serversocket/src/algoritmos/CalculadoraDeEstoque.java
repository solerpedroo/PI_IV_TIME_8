package algoritmos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeMovimentacaoDeEstoque;
import protocolo.mensagens.RespostaDeMovimentacaoDeEstoque;

/*
    Efeito de uma movimentacao sobre o saldo e o custo medio de um insumo.
    Quantidades usam seis casas decimais; o custo medio tambem usa seis.
    Entrada com custo informado pondera o estoque anterior e a compra; entrada sem custo mantem o custo medio.
    Ajuste define o saldo. Saida maior que o saldo e recusada.
*/
public class CalculadoraDeEstoque
{
    public static final List<String> TIPOS = List.of ("entrada", "saida", "ajuste");

    private static final double LIMITE = 1000000000;
    private static final int    CASAS  = 6;

    public static double arredondar (double valor, int casas)
    {
        return BigDecimal.valueOf (valor).setScale (casas, RoundingMode.HALF_UP).doubleValue ();
    }

    // numero entre zero e um bilhao com ate 6 casas; vale 0 se invalido (o erro fica em v)
    private static double numero (Validacao v, String campo, Double valor)
    {
        if (valor == null || !Double.isFinite (valor) || valor < 0 || valor > CalculadoraDeEstoque.LIMITE)
        {
            v.erro (campo, "Informe um número entre zero e um bilhão.");
            return 0;
        }

        if (CalculadoraDeEstoque.arredondar (valor, CalculadoraDeEstoque.CASAS) != valor)
            v.erro (campo, "Use no máximo " + CalculadoraDeEstoque.CASAS + " casas decimais.");

        return valor;
    }

    private static Resposta recusa (long id, String situacao, Validacao v)
    {
        return new RespostaDeMovimentacaoDeEstoque (id, situacao, v.getErros (), 0, 0, 0);
    }

    public static Resposta movimentar (PedidoDeMovimentacaoDeEstoque pedido)
    {
        long   id         = pedido.getId ();
        double saldo      = pedido.getSaldoAtual ();
        double custoAtual = pedido.getCustoUnitarioAtual ();

        // saldo e custo vem do banco da API: se estao errados, o problema nao e do usuario
        if (!Double.isFinite (saldo) || saldo < 0 || !Double.isFinite (custoAtual) || custoAtual < 0)
            return new RespostaDeErro (id, RespostaDeErro.PEDIDO_INVALIDO, "Saldo e custo atuais devem ser números não negativos.");

        Validacao v = new Validacao ();

        String tipo = v.texto ("tipo", pedido.getTipo (), "Informe o tipo.", 10);
        if (tipo != null && !CalculadoraDeEstoque.TIPOS.contains (tipo))
            v.erro ("tipo", "Tipo inválido.");

        double quantidade = CalculadoraDeEstoque.numero (v, "quantidade", pedido.getQuantidade ());
        if (!"ajuste".equals (tipo) && quantidade == 0)
            v.erro ("quantidade", "Informe quantidade maior que zero.");

        String observacao = v.textoOpcional ("observacao", pedido.getObservacao (), 240);
        if ("ajuste".equals (tipo) && observacao.isBlank ())
            v.erro ("observacao", "Explique o motivo do ajuste.");

        Double custo = pedido.getCustoUnitario () == null ? null :
                       CalculadoraDeEstoque.numero (v, "custoUnitario", pedido.getCustoUnitario ());

        if (v.temErros ())
            return CalculadoraDeEstoque.recusa (id, RespostaDeMovimentacaoDeEstoque.INVALIDO, v);

        // nao altera nada antes de validar a saida
        if ("saida".equals (tipo) && quantidade > saldo)
        {
            v.erro ("quantidade", "A saída supera o saldo disponível.");
            return CalculadoraDeEstoque.recusa (id, RespostaDeMovimentacaoDeEstoque.SALDO_INSUFICIENTE, v);
        }

        double novo = "entrada".equals (tipo) ? saldo + quantidade :
                      "saida".equals (tipo)   ? saldo - quantidade : quantidade;
        novo = CalculadoraDeEstoque.arredondar (novo, CalculadoraDeEstoque.CASAS);

        double custoNovo = custoAtual;
        if ("entrada".equals (tipo) && custo != null)
            custoNovo = CalculadoraDeEstoque.arredondar ((saldo * custoAtual + quantidade * custo) / novo, CalculadoraDeEstoque.CASAS);

        return new RespostaDeMovimentacaoDeEstoque (id, RespostaDeMovimentacaoDeEstoque.OK,
                                                    new LinkedHashMap<String, String> (), saldo, novo, custoNovo);
    }
}
