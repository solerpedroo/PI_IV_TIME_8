/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.math.BigDecimal;
import protocolo.Pedido;

/*
    Valida uma intervencao (o que foi feito no equipamento). Dinheiro em BigDecimal.
    hoje (AAAA-MM-DD, fuso America/Sao_Paulo) vem da API: a data nao pode estar no futuro.
*/
public class PedidoDeValidacaoDeIntervencao extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String     tipo;
    private final String     data;
    private final String     descricao;
    private final String     responsavel;
    private final Double     horimetro;
    private final BigDecimal valor;
    private final String     hoje;

    public PedidoDeValidacaoDeIntervencao (String tipo, String data, String descricao, String responsavel, Double horimetro, BigDecimal valor, String hoje)
    {
        this.tipo        = tipo;
        this.data        = data;
        this.descricao   = descricao;
        this.responsavel = responsavel;
        this.horimetro   = horimetro;
        this.valor       = valor;
        this.hoje        = hoje;
    }

    public String     getTipo () { return this.tipo; }
    public String     getData () { return this.data; }
    public String     getDescricao () { return this.descricao; }
    public String     getResponsavel () { return this.responsavel; }
    public Double     getHorimetro () { return this.horimetro; }
    public BigDecimal getValor () { return this.valor; }
    public String     getHoje () { return this.hoje; }
}
