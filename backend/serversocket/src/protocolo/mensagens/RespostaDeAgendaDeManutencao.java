/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.util.LinkedHashMap;
import java.util.Map;
import protocolo.Resposta;

/*
    Para cada preventiva programada (chave = id): situacao (atrasada, proxima ou futura) e diasAte
    (negativo quando atrasada). Preventivas realizadas ou canceladas nao entram. Mais os totais por situacao.
*/
public class RespostaDeAgendaDeManutencao extends Resposta
{
    private static final long serialVersionUID = 1L;

    private final LinkedHashMap<String, String> situacao;
    private final LinkedHashMap<String, Long>   diasAte;
    private final long                          atrasadas;
    private final long                          proximas;
    private final long                          futuras;

    public RespostaDeAgendaDeManutencao (long idDoPedido, Map<String, String> situacao, Map<String, Long> diasAte, long atrasadas, long proximas, long futuras)
    {
        super (idDoPedido);

        this.situacao  = new LinkedHashMap<String, String> (situacao);
        this.diasAte   = new LinkedHashMap<String, Long> (diasAte);
        this.atrasadas = atrasadas;
        this.proximas  = proximas;
        this.futuras   = futuras;
    }

    public Map<String, String> getSituacao () { return this.situacao; }
    public Map<String, Long>   getDiasAte () { return this.diasAte; }
    public long                getAtrasadas () { return this.atrasadas; }
    public long                getProximas () { return this.proximas; }
    public long                getFuturas () { return this.futuras; }
}
