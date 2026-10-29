/* Miguel — RA: 25003110 */
package servicos;

import handlers.SafrasHandler;
import http.ErroHttp;
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
        LocalDate inicio = dia (safra.get ("dataInicio")), fim = dia (safra.get ("dataFim"));
        if (inicio == null || fim == null || fim.isBefore (inicio))
            throw new ErroHttp (409, "A safra possui período inválido; corrija o cadastro antes de consultar.");

        Map<String, Document> mapa = new LinkedHashMap<> ();
        for (Document talhao : talhoes) mapa.put (id (talhao), talhao);
        List<String> ids = safra.getList ("talhoes", String.class);
        List<Document> areas = new ArrayList<> (), registros = new ArrayList<> (), pendencias = new ArrayList<> ();
        for (String id : ids)
        {
            Document talhao = mapa.get (id);
            if (talhao == null)
                pendencias.add (new Document ("tipo", "talhao").append ("id", id).append ("descricao", "Talhão indisponível")
                    .append ("motivo", "Vínculo inexistente; área não contabilizada."));
            else areas.add (new Document ("id", id).append ("nome", talhao.getString ("nome"))
                .append ("hectares", SafrasHandler.hectares (talhao)));
        }

        String safraId = id (safra);
        for (Document atividade : atividades)
        {
            if (!safraId.equals (atividade.getString ("safraId"))) continue;
            Document item = item ("atividade", atividade, atividade.getString ("nome"), atividade.getString ("data"), atividade.getString ("talhao"));
            if (areas.stream ().noneMatch (t -> t.getString ("nome").equals (atividade.getString ("talhao"))))
                pendencias.add (new Document (item).append ("motivo", "Talhão da atividade não encontrado na safra; não incluído nos totais."));
            else if (noPeriodo (item, inicio, fim, pendencias)) registros.add (item);
        }
        // Ocorrências não têm safraId no contrato atual: a associação é por talhão e data.
        for (Document ocorrencia : ocorrencias)
        {
            if (Boolean.TRUE.equals (ocorrencia.get ("excluida")) || ocorrencia.getString ("talhaoId") == null || !ids.contains (ocorrencia.getString ("talhaoId"))) continue;
            LocalDate data = dia (ocorrencia.get ("data"));
            Document item = item ("ocorrencia", ocorrencia, ocorrencia.getString ("titulo"), ocorrencia.getString ("data"), ocorrencia.getString ("talhaoNome"));
            if (data == null) noPeriodo (item, inicio, fim, pendencias);
            else if (!data.isBefore (inicio) && !data.isAfter (fim)) registros.add (item);
        }
        long totalCentavos = 0;
        for (Document custo : custos)
        {
            if (!safraId.equals (custo.getString ("safraId"))) continue;
            Document talhao = mapa.get (custo.getString ("talhaoId"));
            Document item = item ("custo", custo, custo.getString ("description"), custo.getString ("date"),
                talhao == null ? "Sem talhão" : talhao.getString ("nome"));
            item.append ("categoria", custo.getString ("category"));
            // Mesmo arredondamento dos custos; long e soma exata evitam perda silenciosa.
            long centavos = centavos (custo);
            item.append ("valorCentavos", centavos);
            if (custo.getString ("talhaoId") != null && (!ids.contains (custo.getString ("talhaoId")) || talhao == null))
                pendencias.add (new Document (item).append ("motivo", "Talhão do custo não encontrado na safra; não incluído nos totais."));
            else if (noPeriodo (item, inicio, fim, pendencias))
            {
                registros.add (item);
                totalCentavos = Math.addExact (totalCentavos, centavos);
            }
        }
        registros.sort ((a, b) -> {
            int data = a.getString ("data").compareTo (b.getString ("data"));
            return data != 0 ? data : (a.getString ("tipo") + a.getString ("id")).compareTo (b.getString ("tipo") + b.getString ("id"));
        });
        Document contagens = new Document ();
        for (String tipo : List.of ("atividade", "ocorrencia", "custo"))
            contagens.append (tipo, registros.stream ().filter (r -> tipo.equals (r.getString ("tipo"))).count ());
        return new Document ("propriedade", new Document ("id", id (propriedade)).append ("nome", propriedade.getString ("nome") == null ? propriedade.getString ("farm") : propriedade.getString ("nome")))
            .append ("safra", new Document ("id", safraId).append ("nome", safra.getString ("nome"))
                .append ("cultura", safra.getString ("cultura")).append ("etapa", safra.getString ("etapa")))
            .append ("periodo", new Document ("inicio", inicio.toString ()).append ("fim", fim.toString ()))
            .append ("areaHectares", SafrasHandler.area (safra, mapa)).append ("origemArea", "Área atual dos talhões associados (ha)")
            .append ("regraOcorrencias", "Ocorrências do usuário nos talhões da safra, dentro do período inclusivo.")
            .append ("talhoes", areas).append ("registros", registros).append ("pendencias", pendencias)
            .append ("contagens", contagens).append ("totalCentavos", totalCentavos);
    }
}
