/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package algoritmos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.GeometriaParaMapa;
import protocolo.mensagens.PedidoDeGeometriaDoMapa;
import protocolo.mensagens.PedidoDePontoNoTalhao;
import protocolo.mensagens.PedidoDeValidacaoDeLocalizacao;
import protocolo.mensagens.RespostaDeGeometriaDoMapa;
import protocolo.mensagens.RespostaDeValidacao;

/*
    Localizacao simplificada do talhao (ponto ou poligono leve) e as contas do mapa. Funcoes puras.

    Projecao local em metros a partir do primeiro vertice (serve para areas pequenas, como um talhao):
        x = (lng - lng0) * cos (lat0) * 111320        y = (lat - lat0) * 110540
    Area: formula do laco ("shoelace") na projecao, em m2, depois em hectares com 2 casas.
    Centro: centroide do poligono na mesma projecao, convertido de volta para lat/lng.
    Ponto no poligono: teste do raio direto em lat/lng. Ponto NA BORDA (ate ~0,1 mm) conta como dentro:
    quem marca a ocorrencia na cerca do talhao continua no talhao.
    A area calculada e so informativa: nao substitui os hectares digitados no cadastro do talhao.
*/
public class GeometriaDeTalhao
{
    public static final String PONTO              = "ponto";
    public static final String POLIGONO           = "poligono";
    public static final int    MINIMO_DE_VERTICES = 3;
    public static final int    MAXIMO_DE_VERTICES = 50;

    private static final double METROS_POR_GRAU_LAT = 110540;
    private static final double METROS_POR_GRAU_LNG = 111320; // no equador; multiplicado pelo cos da latitude
    private static final double AREA_MINIMA_M2      = 1;      // abaixo disso nao ha area de verdade
    private static final double TOLERANCIA_BORDA    = 1e-9;   // em graus
    private static final double TOLERANCIA_ANGULO   = 1e-9;   // seno do angulo: abaixo disso, alinhados

    // ------------------------------------------------------------------ contas

    // 6 casas decimais (~0,1 m): o que fica gravado
    public static double arredondar (double valor)
    {
        return Math.round (valor * 1e6) / 1e6;
    }

    private static double duasCasas (double valor)
    {
        return Math.round (valor * 100) / 100.0;
    }

    private static double escalaDaLongitude (double lat0)
    {
        return Math.cos (Math.toRadians (lat0)) * GeometriaDeTalhao.METROS_POR_GRAU_LNG;
    }

    // [0] = x em metros, [1] = y em metros, a partir do primeiro vertice
    private static double[][] projetar (List<Double> lats, List<Double> lngs)
    {
        int        n      = lats.size ();
        double[][] ret    = new double[2][n];
        double     lat0   = lats.get (0).doubleValue ();
        double     lng0   = lngs.get (0).doubleValue ();
        double     escala = GeometriaDeTalhao.escalaDaLongitude (lat0);

        for (int i = 0; i < n; i++)
        {
            ret[0][i] = (lngs.get (i).doubleValue () - lng0) * escala;
            ret[1][i] = (lats.get (i).doubleValue () - lat0) * GeometriaDeTalhao.METROS_POR_GRAU_LAT;
        }

        return ret;
    }

    // positiva no sentido anti-horario
    private static double areaComSinal (double[] x, double[] y)
    {
        double soma = 0;
        int    n    = x.length;

        for (int i = 0; i < n; i++)
        {
            int j = (i + 1) % n;
            soma += x[i] * y[j] - x[j] * y[i];
        }

        return soma / 2;
    }

    // area em m2 do poligono (vertices em lat/lng, sem repetir o primeiro no fim)
    public static double area (List<Double> lats, List<Double> lngs)
    {
        double[][] xy = GeometriaDeTalhao.projetar (lats, lngs);

        return Math.abs (GeometriaDeTalhao.areaComSinal (xy[0], xy[1]));
    }

    public static double hectares (double metrosQuadrados)
    {
        return GeometriaDeTalhao.duasCasas (metrosQuadrados / 10000);
    }

    // {lat, lng} do centro: o proprio ponto, ou o centroide do poligono
    public static double[] centro (List<Double> lats, List<Double> lngs)
    {
        double[] ret  = new double[2];
        int      n    = lats.size ();
        double   lat0 = lats.get (0).doubleValue ();
        double   lng0 = lngs.get (0).doubleValue ();

        if (n < GeometriaDeTalhao.MINIMO_DE_VERTICES)
        {
            ret[0] = lat0;
            ret[1] = lng0;
            return ret;
        }

        double[][] xy    = GeometriaDeTalhao.projetar (lats, lngs);
        double[]   x     = xy[0];
        double[]   y     = xy[1];
        double     areaS = GeometriaDeTalhao.areaComSinal (x, y);
        double     cx    = 0;
        double     cy    = 0;

        if (areaS == 0)
        {
            // so por seguranca (poligono validado sempre tem area): media dos vertices
            for (int i = 0; i < n; i++)
            {
                cx += x[i] / n;
                cy += y[i] / n;
            }
        }
        else
        {
            for (int i = 0; i < n; i++)
            {
                int    j     = (i + 1) % n;
                double fator = x[i] * y[j] - x[j] * y[i];

                cx += (x[i] + x[j]) * fator;
                cy += (y[i] + y[j]) * fator;
            }

            cx = cx / (6 * areaS);
            cy = cy / (6 * areaS);
        }

        ret[0] = GeometriaDeTalhao.arredondar (lat0 + cy / GeometriaDeTalhao.METROS_POR_GRAU_LAT);
        ret[1] = GeometriaDeTalhao.arredondar (lng0 + cx / GeometriaDeTalhao.escalaDaLongitude (lat0));

        return ret;
    }

    // distancia do ponto p ao segmento ab (mesma unidade das coordenadas)
    private static double distanciaAoSegmento (double px, double py, double ax, double ay, double bx, double by)
    {
        double dx    = bx - ax;
        double dy    = by - ay;
        double tam2  = dx * dx + dy * dy;
        double t     = 0;

        if (tam2 > 0)
            t = Math.max (0, Math.min (1, ((px - ax) * dx + (py - ay) * dy) / tam2));

        double qx = ax + t * dx;
        double qy = ay + t * dy;

        return Math.sqrt ((px - qx) * (px - qx) + (py - qy) * (py - qy));
    }

    // o ponto (lat, lng) esta dentro do poligono? a borda conta como dentro
    public static boolean dentro (double lat, double lng, List<Double> lats, List<Double> lngs)
    {
        boolean ret   = false;
        boolean borda = false;
        int     n     = lats.size ();
        int     j     = n - 1;

        for (int i = 0; i < n; i++)
        {
            double yi = lats.get (i).doubleValue ();
            double xi = lngs.get (i).doubleValue ();
            double yj = lats.get (j).doubleValue ();
            double xj = lngs.get (j).doubleValue ();

            if (GeometriaDeTalhao.distanciaAoSegmento (lng, lat, xj, yj, xi, yi) <= GeometriaDeTalhao.TOLERANCIA_BORDA)
                borda = true;

            // o lado (j, i) cruza a reta horizontal do ponto a direita dele?
            if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi)
                ret = !ret;

            j = i;
        }

        return ret || borda;
    }

    // -1, 0 ou 1: lado em que c fica em relacao a reta ab
    private static int orientacao (double ax, double ay, double bx, double by, double cx, double cy)
    {
        double valor = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        int    ret   = 0;

        if (valor > 0)
            ret = 1;
        else if (valor < 0)
            ret = -1;

        return ret;
    }

    // c esta na caixa do segmento ab (usado quando os tres estao alinhados)
    private static boolean naCaixa (double ax, double ay, double bx, double by, double cx, double cy)
    {
        return Math.min (ax, bx) <= cx && cx <= Math.max (ax, bx) &&
               Math.min (ay, by) <= cy && cy <= Math.max (ay, by);
    }

    // os segmentos ab e cd se tocam (cruzam, encostam ou se sobrepoem)?
    private static boolean segmentosSeTocam (double ax, double ay, double bx, double by,
                                             double cx, double cy, double dx, double dy)
    {
        int o1 = GeometriaDeTalhao.orientacao (ax, ay, bx, by, cx, cy);
        int o2 = GeometriaDeTalhao.orientacao (ax, ay, bx, by, dx, dy);
        int o3 = GeometriaDeTalhao.orientacao (cx, cy, dx, dy, ax, ay);
        int o4 = GeometriaDeTalhao.orientacao (cx, cy, dx, dy, bx, by);

        boolean ret = o1 != o2 && o3 != o4;

        if (o1 == 0 && GeometriaDeTalhao.naCaixa (ax, ay, bx, by, cx, cy))
            ret = true;
        if (o2 == 0 && GeometriaDeTalhao.naCaixa (ax, ay, bx, by, dx, dy))
            ret = true;
        if (o3 == 0 && GeometriaDeTalhao.naCaixa (cx, cy, dx, dy, ax, ay))
            ret = true;
        if (o4 == 0 && GeometriaDeTalhao.naCaixa (cx, cy, dx, dy, bx, by))
            ret = true;

        return ret;
    }

    // a e b saem do mesmo vertice s: estao alinhados e para o mesmo lado (um lado "volta" sobre o outro)?
    private static boolean dobram (double sx, double sy, double ax, double ay, double bx, double by)
    {
        double ux     = ax - sx;
        double uy     = ay - sy;
        double vx     = bx - sx;
        double vy     = by - sy;
        double tamU   = Math.sqrt (ux * ux + uy * uy);
        double tamV   = Math.sqrt (vx * vx + vy * vy);
        double cruz   = ux * vy - uy * vx;
        double escala = ux * vx + uy * vy;

        return Math.abs (cruz) <= GeometriaDeTalhao.TOLERANCIA_ANGULO * tamU * tamV && escala > 0;
    }

    // algum par de lados do poligono se cruza? (n <= 50: comparar todos os pares, O(n2), serve)
    public static boolean ladosSeCruzam (double[] x, double[] y)
    {
        boolean ret = false;
        int     n   = x.length;

        for (int i = 0; i < n && !ret; i++)
        {
            int i2 = (i + 1) % n;

            for (int j = i + 1; j < n && !ret; j++)
            {
                int j2 = (j + 1) % n;

                if (j == i + 1)
                    ret = GeometriaDeTalhao.dobram (x[i2], y[i2], x[i], y[i], x[j2], y[j2]);    // vizinhos em i2
                else if (j2 == i)
                    ret = GeometriaDeTalhao.dobram (x[i], y[i], x[i2], y[i2], x[j], y[j]);      // vizinhos em i
                else
                    ret = GeometriaDeTalhao.segmentosSeTocam (x[i], y[i], x[i2], y[i2], x[j], y[j], x[j2], y[j2]);
            }
        }

        return ret;
    }

    // todos os vertices na mesma reta (poligono sem area)?
    private static boolean alinhados (double[] x, double[] y)
    {
        boolean ret  = true;
        double  ux   = x[1] - x[0];
        double  uy   = y[1] - y[0];
        double  tamU = Math.sqrt (ux * ux + uy * uy);

        for (int k = 2; k < x.length && ret; k++)
        {
            double vx   = x[k] - x[0];
            double vy   = y[k] - y[0];
            double tamV = Math.sqrt (vx * vx + vy * vy);

            if (Math.abs (ux * vy - uy * vx) > GeometriaDeTalhao.TOLERANCIA_ANGULO * tamU * tamV)
                ret = false;
        }

        return ret;
    }

    private static boolean latitudeValida (Double lat)
    {
        return lat != null && Double.isFinite (lat.doubleValue ()) && lat.doubleValue () >= -90 && lat.doubleValue () <= 90;
    }

    private static boolean longitudeValida (Double lng)
    {
        return lng != null && Double.isFinite (lng.doubleValue ()) && lng.doubleValue () >= -180 && lng.doubleValue () <= 180;
    }

    // vertices completos: listas do mesmo tamanho, sem null, com o minimo pedido
    private static boolean completos (List<Double> lats, List<Double> lngs, int minimo)
    {
        boolean ret = lats != null && lngs != null && lats.size () >= minimo && lats.size () == lngs.size ();

        for (int i = 0; ret && i < lats.size (); i++)
            if (!GeometriaDeTalhao.latitudeValida (lats.get (i)) || !GeometriaDeTalhao.longitudeValida (lngs.get (i)))
                ret = false;

        return ret;
    }

    // ------------------------------------------------------------------ pedidos

    // PedidoDeValidacaoDeLocalizacao -> RespostaDeValidacao (erros em "tipo" e "pontos")
    public static Resposta validarLocalizacao (PedidoDeValidacaoDeLocalizacao pedido)
    {
        Validacao         v          = new Validacao ();
        String            tipo       = pedido.getTipo () == null ? "" : pedido.getTipo ().trim ();
        List<Double>      latitudes  = pedido.getLatitudes ();
        List<Double>      longitudes = pedido.getLongitudes ();
        ArrayList<Double> lats       = new ArrayList<Double> ();
        ArrayList<Double> lngs       = new ArrayList<Double> ();

        if (tipo.isEmpty ())
            v.erro ("tipo", "Escolha ponto ou pol\u00edgono.");
        else if (!GeometriaDeTalhao.PONTO.equals (tipo) && !GeometriaDeTalhao.POLIGONO.equals (tipo))
            v.erro ("tipo", "Use ponto ou pol\u00edgono.");

        if (latitudes == null || longitudes == null || latitudes.isEmpty () || latitudes.size () != longitudes.size ())
            v.erro ("pontos", "Informe os pontos da localiza\u00e7\u00e3o.");
        else
        {
            for (int i = 0; i < latitudes.size (); i++)
            {
                if (!GeometriaDeTalhao.latitudeValida (latitudes.get (i)))
                    v.erro ("pontos", "Latitude inv\u00e1lida no ponto " + (i + 1) + ": use um valor entre -90 e 90.");
                else if (!GeometriaDeTalhao.longitudeValida (longitudes.get (i)))
                    v.erro ("pontos", "Longitude inv\u00e1lida no ponto " + (i + 1) + ": use um valor entre -180 e 180.");
                else
                {
                    lats.add (Double.valueOf (GeometriaDeTalhao.arredondar (latitudes.get (i).doubleValue ())));
                    lngs.add (Double.valueOf (GeometriaDeTalhao.arredondar (longitudes.get (i).doubleValue ())));
                }
            }
        }

        if (!v.temErros () && GeometriaDeTalhao.PONTO.equals (tipo) && lats.size () != 1)
            v.erro ("pontos", "Informe exatamente um ponto.");

        if (!v.temErros () && GeometriaDeTalhao.POLIGONO.equals (tipo))
        {
            int ultimo = lats.size () - 1;

            // o fechamento (ultimo vertice igual ao primeiro) e implicito: descarta a repeticao
            if (ultimo > 0 && lats.get (ultimo).equals (lats.get (0)) && lngs.get (ultimo).equals (lngs.get (0)))
            {
                lats.remove (ultimo);
                lngs.remove (ultimo);
            }

            GeometriaDeTalhao.validarPoligono (v, lats, lngs);
        }

        LinkedHashMap<String, Object> normalizados = new LinkedHashMap<String, Object> ();

        if (!v.temErros ())
        {
            double[] centro = GeometriaDeTalhao.centro (lats, lngs);
            Double   area   = null;

            if (GeometriaDeTalhao.POLIGONO.equals (tipo))
                area = Double.valueOf (GeometriaDeTalhao.hectares (GeometriaDeTalhao.area (lats, lngs)));

            normalizados.put ("tipo",         tipo);
            normalizados.put ("latitudes",    lats);
            normalizados.put ("longitudes",   lngs);
            normalizados.put ("areaHectares", area);
            normalizados.put ("centroLat",    Double.valueOf (centro[0]));
            normalizados.put ("centroLng",    Double.valueOf (centro[1]));
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }

    // regras do poligono, na ordem em que o usuario consegue corrigir
    private static void validarPoligono (Validacao v, List<Double> lats, List<Double> lngs)
    {
        int n = lats.size ();

        if (n < GeometriaDeTalhao.MINIMO_DE_VERTICES || n > GeometriaDeTalhao.MAXIMO_DE_VERTICES)
        {
            v.erro ("pontos", "Informe de 3 a 50 v\u00e9rtices.");
            return;
        }

        for (int i = 0; i < n; i++)
        {
            int j = (i + 1) % n;

            if (lats.get (i).equals (lats.get (j)) && lngs.get (i).equals (lngs.get (j)))
                v.erro ("pontos", "H\u00e1 dois v\u00e9rtices seguidos iguais (v\u00e9rtice " + (j == 0 ? n : i + 1) + ").");
        }

        if (v.temErros ())
            return;

        double[][] xy = GeometriaDeTalhao.projetar (lats, lngs);

        if (GeometriaDeTalhao.alinhados (xy[0], xy[1]))
            v.erro ("pontos", "Os v\u00e9rtices est\u00e3o na mesma reta: o pol\u00edgono precisa ter \u00e1rea maior que zero.");
        else if (GeometriaDeTalhao.ladosSeCruzam (xy[0], xy[1]))
            v.erro ("pontos", "Os lados do pol\u00edgono n\u00e3o podem se cruzar.");
        else if (Math.abs (GeometriaDeTalhao.areaComSinal (xy[0], xy[1])) < GeometriaDeTalhao.AREA_MINIMA_M2)
            v.erro ("pontos", "O pol\u00edgono precisa ter \u00e1rea maior que zero.");
    }

    // PedidoDePontoNoTalhao -> RespostaDeValidacao (erro em "localizacao"; normalizados lat e lng)
    public static Resposta pontoNoTalhao (PedidoDePontoNoTalhao pedido)
    {
        if (!GeometriaDeTalhao.completos (pedido.getLatitudes (), pedido.getLongitudes (), GeometriaDeTalhao.MINIMO_DE_VERTICES))
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Pol\u00edgono do talh\u00e3o ausente ou incompleto.");

        Validacao                     v            = new Validacao ();
        LinkedHashMap<String, Object> normalizados = new LinkedHashMap<String, Object> ();
        Double                        lat          = pedido.getLat ();
        Double                        lng          = pedido.getLng ();

        if (lat == null || lng == null)
            v.erro ("localizacao", "Informe a latitude e a longitude da ocorr\u00eancia.");
        else if (!GeometriaDeTalhao.latitudeValida (lat) || !GeometriaDeTalhao.longitudeValida (lng))
            v.erro ("localizacao", "Use latitude entre -90 e 90 e longitude entre -180 e 180.");
        else
        {
            double la = GeometriaDeTalhao.arredondar (lat.doubleValue ());
            double ln = GeometriaDeTalhao.arredondar (lng.doubleValue ());

            if (!GeometriaDeTalhao.dentro (la, ln, pedido.getLatitudes (), pedido.getLongitudes ()))
                v.erro ("localizacao", "A posi\u00e7\u00e3o fica fora do pol\u00edgono do talh\u00e3o.");
            else
            {
                normalizados.put ("lat", Double.valueOf (la));
                normalizados.put ("lng", Double.valueOf (ln));
            }
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }

    // PedidoDeGeometriaDoMapa -> RespostaDeGeometriaDoMapa (centro, area e limites de todos)
    public static Resposta geometriaDoMapa (PedidoDeGeometriaDoMapa pedido)
    {
        List<GeometriaParaMapa> talhoes = pedido.getTalhoes ();

        if (talhoes == null)
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Lista de talh\u00f5es ausente.");

        LinkedHashMap<String, Double> centroLat = new LinkedHashMap<String, Double> ();
        LinkedHashMap<String, Double> centroLng = new LinkedHashMap<String, Double> ();
        LinkedHashMap<String, Double> areas     = new LinkedHashMap<String, Double> ();
        Double                        latMin    = null;
        Double                        latMax    = null;
        Double                        lngMin    = null;
        Double                        lngMax    = null;

        for (GeometriaParaMapa talhao : talhoes)
        {
            if (talhao == null || talhao.getTalhaoId () == null)
                return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Talh\u00e3o sem identifica\u00e7\u00e3o no pedido.");

            List<Double> lats    = talhao.getLatitudes ();
            List<Double> lngs    = talhao.getLongitudes ();
            boolean      ponto   = GeometriaDeTalhao.PONTO.equals (talhao.getTipo ());
            boolean      valido  = ponto ? GeometriaDeTalhao.completos (lats, lngs, 1) && lats.size () == 1
                                         : GeometriaDeTalhao.POLIGONO.equals (talhao.getTipo ()) &&
                                           GeometriaDeTalhao.completos (lats, lngs, GeometriaDeTalhao.MINIMO_DE_VERTICES);

            if (!valido)
                return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO,
                                           "Localiza\u00e7\u00e3o incompleta no talh\u00e3o " + talhao.getTalhaoId () + ".");

            double[] centro = GeometriaDeTalhao.centro (lats, lngs);
            centroLat.put (talhao.getTalhaoId (), Double.valueOf (centro[0]));
            centroLng.put (talhao.getTalhaoId (), Double.valueOf (centro[1]));

            if (!ponto)
                areas.put (talhao.getTalhaoId (), Double.valueOf (GeometriaDeTalhao.hectares (GeometriaDeTalhao.area (lats, lngs))));

            for (int i = 0; i < lats.size (); i++)
            {
                double la = lats.get (i).doubleValue ();
                double ln = lngs.get (i).doubleValue ();

                latMin = latMin == null || la < latMin.doubleValue () ? Double.valueOf (la) : latMin;
                latMax = latMax == null || la > latMax.doubleValue () ? Double.valueOf (la) : latMax;
                lngMin = lngMin == null || ln < lngMin.doubleValue () ? Double.valueOf (ln) : lngMin;
                lngMax = lngMax == null || ln > lngMax.doubleValue () ? Double.valueOf (ln) : lngMax;
            }
        }

        return new RespostaDeGeometriaDoMapa (pedido.getId (), centroLat, centroLng, areas, latMin, latMax, lngMin, lngMax);
    }
}
