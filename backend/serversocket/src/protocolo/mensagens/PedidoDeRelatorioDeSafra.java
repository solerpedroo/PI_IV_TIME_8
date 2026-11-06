package protocolo.mensagens;

import java.util.ArrayList;
import java.util.List;
import protocolo.Pedido;

/*
    Consolida uma safra: area, registros dentro do periodo, pendencias e total de custos em centavos.
    A API envia SO os dados da safra: as datas dela, os ids dos talhoes vinculados, os talhoes da propriedade
    (para resolver os vinculos) e os registros ja filtrados pela safra (atividades e custos da safra; ocorrencias
    dos talhoes dela). Quem le o banco e a API; o servidor so calcula.
*/
public class PedidoDeRelatorioDeSafra extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String               dataInicio; // AAAA-MM-DD
    private final String               dataFim;    // AAAA-MM-DD, inclusiva
    private final List<String>         idsDosTalhoes;
    private final List<TalhaoDaSafra>  talhoes;
    private final List<RegistroDaSafra> registros;

    public PedidoDeRelatorioDeSafra (String dataInicio, String dataFim, List<String> idsDosTalhoes,
                                     List<TalhaoDaSafra> talhoes, List<RegistroDaSafra> registros)
    {
        this.dataInicio    = dataInicio;
        this.dataFim       = dataFim;
        this.idsDosTalhoes = idsDosTalhoes == null ? null : new ArrayList<String> (idsDosTalhoes);
        this.talhoes       = talhoes       == null ? null : new ArrayList<TalhaoDaSafra> (talhoes);
        this.registros     = registros     == null ? null : new ArrayList<RegistroDaSafra> (registros);
    }

    public String                getDataInicio ()    { return this.dataInicio; }
    public String                getDataFim ()       { return this.dataFim; }
    public List<String>          getIdsDosTalhoes () { return this.idsDosTalhoes; }
    public List<TalhaoDaSafra>   getTalhoes ()       { return this.talhoes; }
    public List<RegistroDaSafra> getRegistros ()     { return this.registros; }
}
