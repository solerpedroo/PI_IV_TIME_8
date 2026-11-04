/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import protocolo.Pedido;

/*
    Valida uma manutencao preventiva programada: descricao, data prevista e o intervalo (dias) para a proxima.
*/
public class PedidoDeValidacaoDePreventiva extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String  descricao;
    private final String  dataPrevista;
    private final Integer intervaloDias;

    public PedidoDeValidacaoDePreventiva (String descricao, String dataPrevista, Integer intervaloDias)
    {
        this.descricao     = descricao;
        this.dataPrevista  = dataPrevista;
        this.intervaloDias = intervaloDias;
    }

    public String  getDescricao () { return this.descricao; }
    public String  getDataPrevista () { return this.dataPrevista; }
    public Integer getIntervaloDias () { return this.intervaloDias; }
}
