package protocolo.mensagens;

import java.io.Serializable;

/*
    Linha do relatorio: um registro contabilizado ou uma pendencia (registro que ficou de fora; veja "motivo").
    categoria e valorCentavos so existem para custos; motivo so existe nas pendencias.
*/
public class ItemDoRelatorio implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String tipo;
    private final String id;
    private final String descricao;
    private final String data;
    private final String talhao;
    private final String status;
    private final String categoria;
    private final Long   valorCentavos;
    private final String motivo;

    public ItemDoRelatorio (String tipo, String id, String descricao, String data, String talhao,
                            String status, String categoria, Long valorCentavos, String motivo)
    {
        this.tipo          = tipo;
        this.id            = id;
        this.descricao     = descricao;
        this.data          = data;
        this.talhao        = talhao;
        this.status        = status;
        this.categoria     = categoria;
        this.valorCentavos = valorCentavos;
        this.motivo        = motivo;
    }

    public ItemDoRelatorio comMotivo (String motivo)
    {
        return new ItemDoRelatorio (this.tipo, this.id, this.descricao, this.data, this.talhao,
                                    this.status, this.categoria, this.valorCentavos, motivo);
    }

    public String getTipo ()          { return this.tipo; }
    public String getId ()            { return this.id; }
    public String getDescricao ()     { return this.descricao; }
    public String getData ()          { return this.data; }
    public String getTalhao ()        { return this.talhao; }
    public String getStatus ()        { return this.status; }
    public String getCategoria ()     { return this.categoria; }
    public Long   getValorCentavos () { return this.valorCentavos; }
    public String getMotivo ()        { return this.motivo; }
}
