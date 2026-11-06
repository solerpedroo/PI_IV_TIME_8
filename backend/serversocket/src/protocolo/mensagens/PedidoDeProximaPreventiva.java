/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import protocolo.Pedido;

/*
    Data da proxima preventiva: data realizada + intervalo em dias (sem intervalo, nao ha proxima).
*/
public class PedidoDeProximaPreventiva extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String  dataRealizada;
    private final Integer intervaloDias;

    public PedidoDeProximaPreventiva (String dataRealizada, Integer intervaloDias)
    {
        this.dataRealizada = dataRealizada;
        this.intervaloDias = intervaloDias;
    }

    public String  getDataRealizada () { return this.dataRealizada; }
    public Integer getIntervaloDias () { return this.intervaloDias; }
}
