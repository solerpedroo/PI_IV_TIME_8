package protocolo.mensagens;

import java.io.Serializable;

// So o que o resumo precisa de um talhao: a area e o status.
public class TalhaoParaResumo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final double hectares;
    private final String status;

    public TalhaoParaResumo (double hectares, String status)
    {
        this.hectares = hectares;
        this.status   = status;
    }

    public double getHectares () { return this.hectares; }
    public String getStatus ()   { return this.status; }
}
