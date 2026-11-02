package protocolo.mensagens;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import protocolo.Resposta;

/*
    situacao: OK (use saldoPosterior e custoUnitario), INVALIDO (veja erros) ou SALDO_INSUFICIENTE
    (saida maior que o saldo; a API costuma devolver 409). Fora de OK os numeros valem zero.
*/
public class RespostaDeMovimentacaoDeEstoque extends Resposta
{
    private static final long serialVersionUID = 1L;

    public static final String OK                 = "OK";
    public static final String INVALIDO           = "INVALIDO";
    public static final String SALDO_INSUFICIENTE = "SALDO_INSUFICIENTE";

    private final String              situacao;
    private final Map<String, String> erros;
    private final double              saldoAnterior;
    private final double              saldoPosterior;
    private final double              custoUnitario;

    public RespostaDeMovimentacaoDeEstoque (long idDoPedido, String situacao, Map<String, String> erros,
                                            double saldoAnterior, double saldoPosterior, double custoUnitario)
    {
        super (idDoPedido);

        this.situacao       = situacao;
        this.erros          = new LinkedHashMap<String, String> (erros);
        this.saldoAnterior  = saldoAnterior;
        this.saldoPosterior = saldoPosterior;
        this.custoUnitario  = custoUnitario;
    }

    public String              getSituacao ()       { return this.situacao; }
    public Map<String, String> getErros ()          { return Collections.unmodifiableMap (this.erros); }
    public double              getSaldoAnterior ()  { return this.saldoAnterior; }
    public double              getSaldoPosterior () { return this.saldoPosterior; }
    public double              getCustoUnitario ()  { return this.custoUnitario; }

    public boolean isOk ()
    {
        return OK.equals (this.situacao);
    }
}
