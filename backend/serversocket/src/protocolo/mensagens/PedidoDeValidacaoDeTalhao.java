package protocolo.mensagens;

import protocolo.Pedido;

/*
    Valida os campos de um talhao (criar e editar usam as mesmas regras).
    safra nula vale o período atual calculado e status nulo ou vazio vale "Ativo".
    Nome unico por propriedade depende do banco e continua na API.
*/
public class PedidoDeValidacaoDeTalhao extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String nome;
    private final String cultura;
    private final String safra;
    private final Double hectares;
    private final String status;

    public PedidoDeValidacaoDeTalhao (String nome, String cultura, String safra, Double hectares, String status)
    {
        this.nome     = nome;
        this.cultura  = cultura;
        this.safra    = safra;
        this.hectares = hectares;
        this.status   = status;
    }

    public String getNome ()     { return this.nome; }
    public String getCultura ()  { return this.cultura; }
    public String getSafra ()    { return this.safra; }
    public Double getHectares () { return this.hectares; }
    public String getStatus ()   { return this.status; }
}
