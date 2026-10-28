/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package handlers;

import dados.RepositorioAtividades;
import http.Manipulador;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.ObjectId;
import util.Json;
import util.Validacao;

/*
    Calendario das atividades da propriedade de quem esta logado.

    GET /api/calendario?de=AAAA-MM-DD&ate=AAAA-MM-DD&status=&tipo=&talhao=&q=

    Devolve so as atividades com data dentro do periodo (semana ou mes da tela),
    a contagem de cada dia do periodo (inclusive os dias sem nada), e os totais
    por status e por tipo. Os filtros opcionais sao os mesmos de GET /api/atividades,
    para que as contagens batam com o quadro filtrado.

    A data da atividade pode estar em ISO (2026-08-12) ou no formato do prototipo
    (12 ago), que nao tem ano. Nesse caso vale a ocorrencia mais proxima da data em
    que a atividade foi criada: "10 jan" criada em dezembro de 2026 cai em 2027.
*/
public class CalendarioHandler implements Manipulador
{
    public static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");

    // maior periodo aceito: um mes com folga (a tela pede uma semana ou um mes)
    public static final int MAXIMO_DIAS = 62;

    private static final List<String> MESES = List.of ("jan", "fev", "mar", "abr", "mai", "jun",
                                                       "jul", "ago", "set", "out", "nov", "dez");

    private final RepositorioAtividades atividades;

    public CalendarioHandler (RepositorioAtividades atividades) throws Exception
    {
        if (atividades == null)
            throw new Exception ("Repositorio de atividades ausente");

        this.atividades = atividades;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET", "/api/calendario", this);
    }

    // ------------------------------------------------------------------ datas

    // dia em que a atividade foi criada: criadaEm; sem ele, o instante do ObjectId; sem os dois, hoje
    private static LocalDate criacao (Document atividade)
    {
        LocalDate ret   = null;
        Object    id    = atividade.get ("_id");
        String    texto = atividade.getString ("criadaEm");

        try
        {
            if (texto != null)
                ret = Instant.parse (texto).atZone (CalendarioHandler.FUSO).toLocalDate ();
        }
        catch (Exception erro)
        {
            ret = null;
        }

        if (ret == null && id instanceof ObjectId)
            ret = ((ObjectId) id).getDate ().toInstant ().atZone (CalendarioHandler.FUSO).toLocalDate ();

        if (ret == null && id instanceof String && ObjectId.isValid ((String) id))
            ret = new ObjectId ((String) id).getDate ().toInstant ().atZone (CalendarioHandler.FUSO).toLocalDate ();

        if (ret == null)
            ret = LocalDate.now (CalendarioHandler.FUSO);

        return ret;
    }

    // data da atividade como LocalDate; null se o texto nao for uma data valida
    public static LocalDate resolver (String data, LocalDate criacao)
    {
        LocalDate ret = null;

        if (data == null)
            return null;

        String texto = data.trim ().toLowerCase (Locale.ROOT);

        if (texto.matches ("\\d{4}-\\d{2}-\\d{2}"))
        {
            try
            {
                ret = LocalDate.parse (texto);
            }
            catch (Exception erro)
            {
                ret = null;
            }
        }
        else if (texto.matches ("\\d{1,2}\\s+[a-z]{3}") && criacao != null)
        {
            String[] partes = texto.split ("\\s+");
            int      dia    = Integer.parseInt (partes[0]);
            int      mes    = CalendarioHandler.MESES.indexOf (partes[1]) + 1;

            // tenta o ano anterior, o da criacao e o seguinte; fica com o mais proximo
            for (int ano = criacao.getYear () - 1; mes > 0 && ano <= criacao.getYear () + 1; ano++)
            {
                try
                {
                    LocalDate candidata = LocalDate.of (ano, mes, dia);

                    if (ret == null ||
                        Math.abs (ChronoUnit.DAYS.between (criacao, candidata)) <
                        Math.abs (ChronoUnit.DAYS.between (criacao, ret)))
                        ret = candidata;
                }
                catch (Exception erro)
                {
                    // 30 fev, 31 abr...: esse ano nao tem o dia
                }
            }
        }

        return ret;
    }

    // ------------------------------------------------------------------ filtros

    // filtro de texto da consulta: null quando ausente, vazio ou "todos"
    private static String filtro (Requisicao requisicao, String nome)
    {
        String ret = requisicao.getConsulta (nome);

        if (ret != null)
        {
            ret = ret.trim ();
            if (ret.isEmpty () || ret.equalsIgnoreCase ("todos") || ret.equalsIgnoreCase ("todas"))
                ret = null;
        }

        return ret;
    }

    private static String texto (Document atividade, String campo)
    {
        String ret = atividade.getString (campo);

        return ret == null ? "" : ret;
    }

    // mesmas regras de GET /api/atividades: status, tipo e talhao exatos (sem caixa), busca no nome e no talhao
    private static boolean passa (Document atividade, String status, String tipo, String talhao, String busca)
    {
        boolean ret = true;

        if (status != null && !status.equalsIgnoreCase (CalendarioHandler.texto (atividade, "status")))
            ret = false;

        if (tipo != null && !tipo.equalsIgnoreCase (CalendarioHandler.texto (atividade, "tipo")))
            ret = false;

        if (talhao != null && !talhao.equalsIgnoreCase (CalendarioHandler.texto (atividade, "talhao")))
            ret = false;

        if (busca != null &&
            !CalendarioHandler.texto (atividade, "nome").toLowerCase (Locale.ROOT).contains (busca) &&
            !CalendarioHandler.texto (atividade, "talhao").toLowerCase (Locale.ROOT).contains (busca))
            ret = false;

        return ret;
    }

    private static Document contador (List<String> chaves)
    {
        Document ret = new Document ();

        for (String chave : chaves)
            ret.append (chave, 0);

        return ret;
    }

    // soma 1 na chave; uma chave fora da lista (dado antigo) entra no fim
    private static void somar (Document contador, String chave)
    {
        Integer atual = contador.getInteger (chave);

        contador.put (chave, (atual == null ? 0 : atual.intValue ()) + 1);
    }

    // insercao ordenada por data, horario e nome (atividade sem horario vem antes no dia)
    private static void inserirOrdenado (ArrayList<Document> lista, Document atividade)
    {
        String chave   = CalendarioHandler.chave (atividade);
        int    posicao = 0;

        while (posicao < lista.size () && CalendarioHandler.chave (lista.get (posicao)).compareTo (chave) <= 0)
            posicao++;

        lista.add (posicao, atividade);
    }

    private static String chave (Document atividade)
    {
        return atividade.getString ("dataIso") + " " + CalendarioHandler.texto (atividade, "horario") + " " +
               CalendarioHandler.texto (atividade, "nome").toLowerCase (Locale.ROOT);
    }

    // ------------------------------------------------------------------ rota

    @Override
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        Document periodo = new Document ();

        if (CalendarioHandler.filtro (requisicao, "de") != null)
            periodo.append ("de", requisicao.getConsulta ("de").trim ());
        if (CalendarioHandler.filtro (requisicao, "ate") != null)
            periodo.append ("ate", requisicao.getConsulta ("ate").trim ());

        Validacao validacao = new Validacao (periodo);
        String    de        = validacao.data ("de",  "Informe a data inicial.");
        String    ate       = validacao.data ("ate", "Informe a data final.");

        if (de != null && ate != null && ate.compareTo (de) < 0)
            validacao.erro ("ate", "A data final deve ser igual ou posterior \u00e0 inicial.");
        else if (de != null && ate != null &&
                 ChronoUnit.DAYS.between (LocalDate.parse (de), LocalDate.parse (ate)) >= CalendarioHandler.MAXIMO_DIAS)
            validacao.erro ("ate", "Use um per\u00edodo de no m\u00e1ximo " + CalendarioHandler.MAXIMO_DIAS + " dias.");

        if (validacao.temErros ())
            return validacao.resposta ();

        String status = CalendarioHandler.filtro (requisicao, "status");
        String tipo   = CalendarioHandler.filtro (requisicao, "tipo");
        String talhao = CalendarioHandler.filtro (requisicao, "talhao");
        String busca  = CalendarioHandler.filtro (requisicao, "q");

        if (busca != null)
            busca = busca.toLowerCase (Locale.ROOT);

        LocalDate                       inicio    = LocalDate.parse (de);
        LocalDate                       fim       = LocalDate.parse (ate);
        Document                        porStatus = CalendarioHandler.contador (AtividadesHandler.STATUS_VALIDOS);
        Document                        porTipo   = CalendarioHandler.contador (AtividadesHandler.TIPOS_VALIDOS);
        LinkedHashMap<String, Document> dias      = new LinkedHashMap<String, Document> ();
        ArrayList<Document>             lista     = new ArrayList<Document> ();
        int                             semData   = 0;

        for (LocalDate dia = inicio; !dia.isAfter (fim); dia = dia.plusDays (1))
            dias.put (dia.toString (), new Document ("data", dia.toString ())
                                           .append ("total", 0)
                                           .append ("porStatus", CalendarioHandler.contador (AtividadesHandler.STATUS_VALIDOS)));

        for (Document atividade : this.atividades.listar (requisicao.getSessao ().getPropriedadeId ()))
        {
            if (!CalendarioHandler.passa (atividade, status, tipo, talhao, busca))
                continue;

            LocalDate data = CalendarioHandler.resolver (atividade.getString ("data"), CalendarioHandler.criacao (atividade));

            if (data == null)
                semData++;
            else if (!data.isBefore (inicio) && !data.isAfter (fim))
            {
                Document publica = Json.publico (atividade, "propriedadeId", "usuarioId").append ("dataIso", data.toString ());
                Document doDia   = dias.get (data.toString ());

                CalendarioHandler.inserirOrdenado (lista, publica);
                CalendarioHandler.somar (porStatus, CalendarioHandler.texto (atividade, "status"));
                CalendarioHandler.somar (porTipo,   CalendarioHandler.texto (atividade, "tipo"));
                CalendarioHandler.somar (doDia.get ("porStatus", Document.class), CalendarioHandler.texto (atividade, "status"));
                doDia.put ("total", doDia.getInteger ("total") + 1);
            }
        }

        return Resposta.json (200, new Document ("de",          de)
                                       .append  ("ate",         ate)
                                       .append  ("total",       lista.size ())
                                       .append  ("semData",     semData)
                                       .append  ("porStatus",   porStatus)
                                       .append  ("porTipo",     porTipo)
                                       .append  ("dias",        new ArrayList<Document> (dias.values ()))
                                       .append  ("atividades",  lista));
    }
}
