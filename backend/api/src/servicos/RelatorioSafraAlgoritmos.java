/* Miguel — RA: 25003110 */
package servicos;

import handlers.SafrasHandler;
import http.ErroHttp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import protocolo.mensagens.ItemDoRelatorio;
import protocolo.mensagens.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.RegistroDaSafra;
import protocolo.mensagens.RespostaDeRelatorioDeSafra;
import protocolo.mensagens.TalhaoDaSafra;

/*
    Monta o pedido do ServerSocket e converte a resposta para o JSON do relatório de safra.
    Colheitas continuam na API (RelatorioSafra) porque ainda não entram no ConsolidadorDeSafra.
*/
public class RelatorioSafraAlgoritmos
{
    public static PedidoDeRelatorioDeSafra montarPedido (Document safra, List<Document> talhoes,
                                                         List<Document> atividades, List<Document> ocorrencias,
                                                         List<Document> custos) throws ErroHttp
    {
        LocalDate inicio = RelatorioSafraAlgoritmos.dia (safra.get ("dataInicio"));
        LocalDate fim    = RelatorioSafraAlgoritmos.dia (safra.get ("dataFim"));

        if (inicio == null || fim == null || fim.isBefore (inicio))
            throw new ErroHttp (409, "A safra possui período inválido; corrija o cadastro antes de consultar.");

        List<String> ids = safra.getList ("talhoes", String.class);
        if (ids == null)
            ids = List.of ();

        ArrayList<TalhaoDaSafra> talhoesDaSafra = new ArrayList<TalhaoDaSafra> ();
        for (Document talhao : talhoes)
            talhoesDaSafra.add (new TalhaoDaSafra (RelatorioSafraAlgoritmos.id (talhao),
                                                   talhao.getString ("nome"),
                                                   SafrasHandler.hectares (talhao)));

        String safraId = RelatorioSafraAlgoritmos.id (safra);
        ArrayList<RegistroDaSafra> registros = new ArrayList<RegistroDaSafra> ();

        for (Document atividade : atividades)
        {
            if (!safraId.equals (atividade.getString ("safraId")))
                continue;

            registros.add (new RegistroDaSafra (RegistroDaSafra.ATIVIDADE, RelatorioSafraAlgoritmos.id (atividade),
                                                atividade.getString ("nome"), atividade.getString ("data"),
                                                atividade.getString ("status"), null, atividade.getString ("talhao"),
                                                null, null, false));
        }

        for (Document ocorrencia : ocorrencias)
        {
            registros.add (new RegistroDaSafra (RegistroDaSafra.OCORRENCIA, RelatorioSafraAlgoritmos.id (ocorrencia),
                                                ocorrencia.getString ("titulo"), ocorrencia.getString ("data"),
                                                ocorrencia.getString ("status"), ocorrencia.getString ("talhaoId"),
                                                ocorrencia.getString ("talhaoNome"), null, null,
                                                Boolean.TRUE.equals (ocorrencia.get ("excluida"))));
        }

        for (Document custo : custos)
        {
            if (!safraId.equals (custo.getString ("safraId")))
                continue;

            BigDecimal valor = new BigDecimal (custo.get ("value").toString ());
            registros.add (new RegistroDaSafra (RegistroDaSafra.CUSTO, RelatorioSafraAlgoritmos.id (custo),
                                                custo.getString ("description"), custo.getString ("date"),
                                                null, custo.getString ("talhaoId"), null, custo.getString ("category"),
                                                valor, false));
        }

        return new PedidoDeRelatorioDeSafra (inicio.toString (), fim.toString (), ids, talhoesDaSafra, registros);
    }

    public static Document documentoDeResposta (Document propriedade, Document safra, RespostaDeRelatorioDeSafra remoto)
    {
        ArrayList<Document> areas = new ArrayList<Document> ();
        for (TalhaoDaSafra talhao : remoto.getTalhoes ())
            areas.add (new Document ("id", talhao.getId ()).append ("nome", talhao.getNome ())
                           .append ("hectares", talhao.getHectares ()));

        ArrayList<Document> registros  = RelatorioSafraAlgoritmos.itens (remoto.getRegistros (), false);
        ArrayList<Document> pendencias = RelatorioSafraAlgoritmos.itens (remoto.getPendencias (), true);

        Document contagens = new Document ();
        for (Map.Entry<String, Long> par : remoto.getContagens ().entrySet ())
            contagens.append (par.getKey (), par.getValue ());

        LocalDate inicio = RelatorioSafraAlgoritmos.dia (safra.get ("dataInicio"));
        LocalDate fim    = RelatorioSafraAlgoritmos.dia (safra.get ("dataFim"));

        return new Document ("propriedade", new Document ("id", RelatorioSafraAlgoritmos.id (propriedade))
                                   .append ("nome", propriedade.getString ("nome") == null ? propriedade.getString ("farm")
                                                                                          : propriedade.getString ("nome")))
            .append ("safra", new Document ("id", RelatorioSafraAlgoritmos.id (safra)).append ("nome", safra.getString ("nome"))
                           .append ("cultura", safra.getString ("cultura")).append ("etapa", safra.getString ("etapa")))
            .append ("periodo", new Document ("inicio", inicio.toString ()).append ("fim", fim.toString ()))
            .append ("areaHectares", remoto.getAreaHectares ())
            .append ("origemArea", "Área atual dos talhões associados (ha)")
            .append ("regraOcorrencias", "Ocorrências do usuário nos talhões da safra, dentro do período inclusivo.")
            .append ("talhoes", areas)
            .append ("registros", registros)
            .append ("pendencias", pendencias)
            .append ("contagens", contagens)
            .append ("totalCentavos", remoto.getTotalCentavos ());
    }

    private static ArrayList<Document> itens (List<ItemDoRelatorio> linhas, boolean pendencia)
    {
        ArrayList<Document> ret = new ArrayList<Document> ();

        for (ItemDoRelatorio linha : linhas)
        {
            Document item = new Document ("tipo", linha.getTipo ()).append ("id", linha.getId ())
                              .append ("descricao", linha.getDescricao ()).append ("data", linha.getData ())
                              .append ("talhao", linha.getTalhao ()).append ("status", linha.getStatus ());

            if (linha.getCategoria () != null)
                item.append ("categoria", linha.getCategoria ());

            if (linha.getValorCentavos () != null)
                item.append ("valorCentavos", linha.getValorCentavos ());

            if (pendencia && linha.getMotivo () != null)
                item.append ("motivo", linha.getMotivo ());

            ret.add (item);
        }

        return ret;
    }

    private static String id (Document registro)
    {
        Object id = registro.get ("_id");

        if (id instanceof ObjectId)
            return ((ObjectId) id).toHexString ();

        return String.valueOf (id);
    }

    private static LocalDate dia (Object valor)
    {
        if (!(valor instanceof String) || !((String) valor).matches ("\\d{4}-\\d{2}-\\d{2}"))
            return null;

        try
        {
            return LocalDate.parse ((String) valor);
        }
        catch (Exception erro)
        {
            return null;
        }
    }
}
