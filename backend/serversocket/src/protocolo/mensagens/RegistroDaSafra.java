package protocolo.mensagens;

import java.io.Serializable;
import java.math.BigDecimal;

/*
    Um registro operacional que a API leu do banco e quer ver consolidado na safra.
    tipo: "atividade" (vinculada ao talhao pelo NOME), "ocorrencia" (pelo talhaoId; ignora excluidas)
    ou "custo" (talhaoId opcional; "valor" em reais, ex.: new BigDecimal ("0.10"), nunca double).
    Campos que nao se aplicam ao tipo ficam nulos.
*/
public class RegistroDaSafra implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final String ATIVIDADE  = "atividade";
    public static final String OCORRENCIA = "ocorrencia";
    public static final String CUSTO      = "custo";

    private final String     tipo;
    private final String     id;
    private final String     descricao;
    private final String     data;       // AAAA-MM-DD
    private final String     status;
    private final String     talhaoId;
    private final String     talhaoNome;
    private final String     categoria;
    private final BigDecimal valor;
    private final boolean    excluido;

    public RegistroDaSafra (String tipo, String id, String descricao, String data, String status,
                            String talhaoId, String talhaoNome, String categoria, BigDecimal valor, boolean excluido)
    {
        this.tipo       = tipo;
        this.id         = id;
        this.descricao  = descricao;
        this.data       = data;
        this.status     = status;
        this.talhaoId   = talhaoId;
        this.talhaoNome = talhaoNome;
        this.categoria  = categoria;
        this.valor      = valor;
        this.excluido   = excluido;
    }

    public String     getTipo ()       { return this.tipo; }
    public String     getId ()         { return this.id; }
    public String     getDescricao ()  { return this.descricao; }
    public String     getData ()       { return this.data; }
    public String     getStatus ()     { return this.status; }
    public String     getTalhaoId ()   { return this.talhaoId; }
    public String     getTalhaoNome () { return this.talhaoNome; }
    public String     getCategoria ()  { return this.categoria; }
    public BigDecimal getValor ()      { return this.valor; }
    public boolean    isExcluido ()    { return this.excluido; }
}
