/* Miguel — RA: 25003110 */
package servicos;

import handlers.SafrasHandler;
import http.ErroHttp;
import protocolo.mensagens.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.RespostaDeRelatorioDeSafra;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;

/* Composição somente de leitura. O mesmo resultado alimenta tela e exportação,
   sem confundir eventos históricos de talhão com registros operacionais reais. */
public class RelatorioSafra
{
    public static String id (Document registro)
    {
        return String.valueOf (registro.get ("_id"));
    }

    public static LocalDate dia (Object valor)
    {
        if (!(valor instanceof String) || !((String) valor).matches ("\\d{4}-\\d{2}-\\d{2}")) return null;
        try { return LocalDate.parse ((String) valor); }
        catch (Exception erro) { return null; }
    }

    // Os relatórios somam os mesmos centavos dos lançamentos cadastrados.
    public static long centavos (Document custo) throws ErroHttp
    {
        long valor = new BigDecimal (custo.get ("value").toString ()).movePointRight (2)
            .setScale (0, RoundingMode.HALF_UP).longValueExact ();
        if (valor < 0) throw new ErroHttp (409, "Custo inválido na safra.");
        return valor;
    }

    private static Document item (String tipo, Document registro, String descricao, String data, String talhao)
    {
        return new Document ("tipo", tipo).append ("id", id (registro)).append ("descricao", descricao)
            .append ("data", data).append ("talhao", talhao).append ("status", registro.getString ("status"));
    }

    // Datas sem ano continuam preservadas na origem, mas não provam o recorte.
    private static boolean noPeriodo (Document item, LocalDate inicio, LocalDate fim, List<Document> pendencias)
    {
        LocalDate data = dia (item.get ("data"));
        String motivo = data == null ? "Data ausente, inválida ou sem ano; não incluído nos totais."
            : data.isBefore (inicio) || data.isAfter (fim) ? "Data fora do período da safra; não incluído nos totais." : null;
        if (motivo != null) pendencias.add (new Document (item).append ("motivo", motivo));
        return motivo == null;
    }

    public static Document compor (Document propriedade, Document safra, List<Document> talhoes,
                                    List<Document> atividades, List<Document> ocorrencias, List<Document> custos) throws Exception
    {
        return compor (propriedade, safra, talhoes, atividades, ocorrencias, custos, List.of ());
    }

    public static Document compor (Document propriedade, Document safra, List<Document> talhoes,
                                    List<Document> atividades, List<Document> ocorrencias, List<Document> custos,
                                    List<Document> colheitas) throws Exception
    {
        // Consolidação principal no ServerSocket (ConsolidadorDeSafra); colheitas ainda só na API.
        PedidoDeRelatorioDeSafra pedido = RelatorioSafraAlgoritmos.montarPedido (safra, talhoes, atividades, ocorrencias, custos);
        RespostaDeRelatorioDeSafra remoto = ClienteDeAlgoritmos.consolidarRelatorio (pedido);
        Document ret = RelatorioSafraAlgoritmos.documentoDeResposta (propriedade, safra, remoto);

        LocalDate inicio = dia (safra.get ("dataInicio"));
        LocalDate fim    = dia (safra.get ("dataFim"));
        Map<String, Document> mapa = new LinkedHashMap<> ();
        for (Document talhao : talhoes)
            mapa.put (id (talhao), talhao);

        List<String> ids = safra.getList ("talhoes", String.class);
        if (ids == null)
            ids = List.of ();

        String safraId = id (safra);
        List<Document> registros  = ret.getList ("registros", Document.class);
        List<Document> pendencias = ret.getList ("pendencias", Document.class);
        List<Document> areas      = ret.getList ("talhoes", Document.class);

        long totalColheitaGramas = 0;
        for (Document colheita : colheitas)
        {
            if (!safraId.equals (colheita.getString ("safraId"))) continue;
            Document talhao = mapa.get (colheita.getString ("talhaoId"));
            String unidade = colheita.getString ("unidade") == null ? "kg" : colheita.getString ("unidade");
            Object quantidade = colheita.get ("quantidade");
            String descricao = (quantidade instanceof Number ? quantidade.toString () : "?") + " " + unidade;
            Document item = item ("colheita", colheita, descricao, colheita.getString ("data"),
                talhao == null ? "Sem talhão" : talhao.getString ("nome"));
            Object kg = colheita.get ("quantidadeKg");
            long gramas = kg instanceof Number ? Math.round (((Number) kg).doubleValue () * 1000) : 0;
            item.append ("quantidadeKg", kg instanceof Number ? ((Number) kg).doubleValue () : null);
            if (colheita.getString ("talhaoId") != null && (!ids.contains (colheita.getString ("talhaoId")) || talhao == null))
                pendencias.add (new Document (item).append ("motivo", "Talhão da colheita não encontrado na safra; não incluído nos totais."));
            else if (noPeriodo (item, inicio, fim, pendencias))
            {
                registros.add (item);
                totalColheitaGramas += gramas;
            }
        }

        registros.sort ((a, b) -> {
            int data = a.getString ("data").compareTo (b.getString ("data"));
            return data != 0 ? data : (a.getString ("tipo") + a.getString ("id")).compareTo (b.getString ("tipo") + b.getString ("id"));
        });

        Document contagens = ret.get ("contagens", Document.class);
        for (String tipo : List.of ("atividade", "ocorrencia", "custo", "colheita"))
            contagens.append (tipo, registros.stream ().filter (r -> tipo.equals (r.getString ("tipo"))).count ());

        double area = ret.getDouble ("areaHectares");
        double totalKg = Math.round (totalColheitaGramas / 10.0) / 100.0;
        Double produtividadeKgHa = area > 0 && totalColheitaGramas > 0
            ? Double.valueOf (Math.round (totalColheitaGramas / area / 100.0) / 10.0) : null;
        Double produtividadeScHa = area > 0 && totalColheitaGramas > 0
            ? Double.valueOf (Math.round (totalColheitaGramas / area / 60.0 / 100.0) / 10.0) : null;

        ret.append ("registros", registros);
        ret.append ("pendencias", pendencias);
        ret.append ("contagens", contagens);
        ret.append ("totalColheitaKg", totalKg);
        ret.append ("produtividadeKgHa", produtividadeKgHa);
        ret.append ("produtividadeScHa", produtividadeScHa);
        return ret;
    }
}
