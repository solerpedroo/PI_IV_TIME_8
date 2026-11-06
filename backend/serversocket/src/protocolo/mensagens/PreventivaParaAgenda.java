/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package protocolo.mensagens;

import java.io.Serializable;

/*
    So o que a agenda precisa de uma preventiva gravada.
*/
public class PreventivaParaAgenda implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String  id;
    private final String  equipamentoId;
    private final String  descricao;
    private final String  dataPrevista;
    private final Integer intervaloDias;
    private final String  status;

    public PreventivaParaAgenda (String id, String equipamentoId, String descricao, String dataPrevista, Integer intervaloDias, String status)
    {
        this.id            = id;
        this.equipamentoId = equipamentoId;
        this.descricao     = descricao;
        this.dataPrevista  = dataPrevista;
        this.intervaloDias = intervaloDias;
        this.status        = status;
    }

    public String  getId () { return this.id; }
    public String  getEquipamentoId () { return this.equipamentoId; }
    public String  getDescricao () { return this.descricao; }
    public String  getDataPrevista () { return this.dataPrevista; }
    public Integer getIntervaloDias () { return this.intervaloDias; }
    public String  getStatus () { return this.status; }
}
