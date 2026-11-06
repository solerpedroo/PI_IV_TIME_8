/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import protocolo.Resposta;

/*
    proximaData = data realizada + intervalo (AAAA-MM-DD), ou null quando a preventiva nao tem intervalo.
*/
public class RespostaDeProximaPreventiva extends Resposta
{
    private static final long serialVersionUID = 1L;

    private final String proximaData;

    public RespostaDeProximaPreventiva (long idDoPedido, String proximaData)
    {
        super (idDoPedido);

        this.proximaData = proximaData;
    }

    public String getProximaData () { return this.proximaData; }
}
