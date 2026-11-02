package protocolo.mensagens;

import java.io.Serializable;

public class TalhaoDaSafra implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String nome;
    private final double hectares;

    public TalhaoDaSafra (String id, String nome, double hectares)
    {
        this.id       = id;
        this.nome     = nome;
        this.hectares = hectares;
    }

    public String getId ()       { return this.id; }
    public String getNome ()     { return this.nome; }
    public double getHectares () { return this.hectares; }
}
