package protocolo.mensagens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import protocolo.Resposta;

/*
    areaHectares: soma dos talhoes vinculados que existem. talhoes: esses talhoes. registros: o que entrou nos
    totais, ordenado por data. pendencias: o que ficou de fora e por que. contagens: quantidade por tipo
    (atividade, ocorrencia, custo). totalCentavos: soma exata dos custos contabilizados.
*/
public class RespostaDeRelatorioDeSafra extends Resposta
{
    private static final long serialVersionUID = 1L;

    private final double                areaHectares;
    private final List<TalhaoDaSafra>   talhoes;
    private final List<ItemDoRelatorio> registros;
    private final List<ItemDoRelatorio> pendencias;
    private final Map<String, Long>     contagens;
    private final long                  totalCentavos;

    public RespostaDeRelatorioDeSafra (long idDoPedido, double areaHectares, List<TalhaoDaSafra> talhoes,
                                       List<ItemDoRelatorio> registros, List<ItemDoRelatorio> pendencias,
                                       Map<String, Long> contagens, long totalCentavos)
    {
        super (idDoPedido);

        this.areaHectares  = areaHectares;
        this.talhoes       = new ArrayList<TalhaoDaSafra> (talhoes);
        this.registros     = new ArrayList<ItemDoRelatorio> (registros);
        this.pendencias    = new ArrayList<ItemDoRelatorio> (pendencias);
        this.contagens     = new LinkedHashMap<String, Long> (contagens);
        this.totalCentavos = totalCentavos;
    }

    public double                getAreaHectares ()  { return this.areaHectares; }
    public List<TalhaoDaSafra>   getTalhoes ()       { return Collections.unmodifiableList (this.talhoes); }
    public List<ItemDoRelatorio> getRegistros ()     { return Collections.unmodifiableList (this.registros); }
    public List<ItemDoRelatorio> getPendencias ()    { return Collections.unmodifiableList (this.pendencias); }
    public Map<String, Long>     getContagens ()     { return Collections.unmodifiableMap (this.contagens); }
    public long                  getTotalCentavos () { return this.totalCentavos; }
}
