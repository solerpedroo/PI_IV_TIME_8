package protocolo.mensagens;

import java.io.Serializable;

/*
    Talhao que a API ja vinculou a safra, para o ConsolidadorDeSafra.
    hectares entra na area atual da safra. nome casa com o talhao escrito nos registros
    (atividade, custo, ocorrencia) sem diferenciar maiusculas. id volta no relatorio para a tela.
    Viaja dentro de PedidoDeRelatorioDeSafra; sozinho nao e um Comunicado.
*/
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
