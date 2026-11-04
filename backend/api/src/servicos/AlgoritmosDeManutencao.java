/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package servicos;

import http.ErroHttp;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeAgendaDeManutencao;
import protocolo.mensagens.PedidoDeProximaPreventiva;
import protocolo.mensagens.PedidoDeValidacaoDeEquipamento;
import protocolo.mensagens.PedidoDeValidacaoDeIntervencao;
import protocolo.mensagens.PedidoDeValidacaoDePreventiva;
import protocolo.mensagens.PreventivaParaAgenda;
import protocolo.mensagens.RespostaDeAgendaDeManutencao;
import protocolo.mensagens.RespostaDeProximaPreventiva;
import protocolo.mensagens.RespostaDeValidacao;

/*
    Ponte da manutencao com o servidor de algoritmos (ValidadorDeManutencao e AgendaDeManutencao).
    Monta os pedidos a partir dos Document e traduz as respostas:
      tipo errado no corpo (texto onde vai numero...) -> 400 sem chamar o socket
      RespostaDeValidacao invalida                     -> 400 {"error":"Validation failed","fields":{...}}
      RespostaDeErro                                   -> 503 (DADOS_INCONSISTENTES -> 409)
      falha de comunicacao                             -> 503
    Dinheiro vai em BigDecimal no protocolo e volta para a API arredondado a 2 casas.
*/
public class AlgoritmosDeManutencao
{
    private static final String INDISPONIVEL = "Servidor de algoritmos indispon\u00edvel";
    private static final String INVALIDO     = "Valor inv\u00e1lido.";

    public static final class Resultado
    {
        public final http.Resposta respostaErro;
        public final Document      campos;      // normalizados, prontos para gravar

        private Resultado (http.Resposta respostaErro, Document campos)
        {
            this.respostaErro = respostaErro;
            this.campos       = campos;
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private static Resposta chamar (protocolo.Pedido pedido) throws ErroHttp
    {
        Resposta ret;

        try
        {
            ret = ClienteDeAlgoritmos.chamar (pedido);
        }
        catch (Exception erro)
        {
            System.err.println ("Servidor de algoritmos inacessivel: " + erro);
            throw new ErroHttp (503, AlgoritmosDeManutencao.INDISPONIVEL);
        }

        return ret;
    }

    private static http.Resposta falha (Resposta resposta)
    {
        http.Resposta ret;

        if (resposta instanceof RespostaDeErro)
        {
            RespostaDeErro erro   = (RespostaDeErro) resposta;
            int            status = RespostaDeErro.DADOS_INCONSISTENTES.equals (erro.getCodigo ()) ? 409 : 503;

            ret = http.Resposta.json (status, new Document ("error", AlgoritmosDeManutencao.INDISPONIVEL)
                                                  .append ("codigo", erro.getCodigo ()));
        }
        else
            ret = http.Resposta.json (503, new Document ("error", "Resposta inesperada do servidor de algoritmos"));

        return ret;
    }

    private static ErroHttp comoErro (Resposta resposta)
    {
        int    status = 503;
        String codigo = "RESPOSTA_INESPERADA";

        if (resposta instanceof RespostaDeErro)
        {
            codigo = ((RespostaDeErro) resposta).getCodigo ();
            if (RespostaDeErro.DADOS_INCONSISTENTES.equals (codigo))
                status = 409;
        }

        return new ErroHttp (status, AlgoritmosDeManutencao.INDISPONIVEL + " (" + codigo + ")");
    }

    private static http.Resposta campos (Map<String, ?> erros)
    {
        Document fields = new Document ();

        for (Map.Entry<String, ?> par : erros.entrySet ())
            fields.append (par.getKey (), par.getValue ());

        return http.Resposta.json (400, new Document ("error", "Validation failed").append ("fields", fields));
    }

    // texto do corpo; ausente ou null -> null; outro tipo -> erro de tipo
    private static String texto (Document corpo, String campo, Document invalidos)
    {
        Object valor = corpo.get (campo);
        String ret   = null;

        if (valor instanceof String)
            ret = (String) valor;
        else if (valor != null)
            invalidos.append (campo, AlgoritmosDeManutencao.INVALIDO);

        return ret;
    }

    // numero do corpo; ausente, null ou "" -> null; outro tipo -> erro de tipo
    private static Double numero (Document corpo, String campo, Document invalidos)
    {
        Object valor = corpo.get (campo);
        Double ret   = null;

        if (valor instanceof Number)
            ret = Double.valueOf (((Number) valor).doubleValue ());
        else if (valor != null && !"".equals (valor))
            invalidos.append (campo, "Informe um n\u00famero.");

        return ret;
    }

    // inteiro do corpo; numero com casas decimais e erro
    private static Integer inteiro (Document corpo, String campo, Document invalidos)
    {
        Double  valor = AlgoritmosDeManutencao.numero (corpo, campo, invalidos);
        Integer ret   = null;

        if (valor != null)
        {
            if (valor.doubleValue () != Math.rint (valor.doubleValue ()) || Math.abs (valor.doubleValue ()) > 1000000)
                invalidos.append (campo, "Informe um n\u00famero inteiro.");
            else
                ret = Integer.valueOf ((int) valor.doubleValue ());
        }

        return ret;
    }

    // dinheiro: o numero do JSON vira BigDecimal pela representacao em texto (sem erro de double)
    private static BigDecimal dinheiro (Document corpo, String campo, Document invalidos)
    {
        Object     valor = corpo.get (campo);
        BigDecimal ret   = null;

        if (valor instanceof Number)
            ret = new BigDecimal (valor.toString ());
        else if (valor != null && !"".equals (valor))
            invalidos.append (campo, "Informe um valor em reais.");

        return ret;
    }

    private static Resultado interpretar (Resposta resposta)
    {
        Resultado ret;

        if (!(resposta instanceof RespostaDeValidacao))
            ret = new Resultado (AlgoritmosDeManutencao.falha (resposta), null);
        else if (!((RespostaDeValidacao) resposta).isValido ())
            ret = new Resultado (AlgoritmosDeManutencao.campos (((RespostaDeValidacao) resposta).getErros ()), null);
        else
        {
            Document campos = new Document ();

            for (Map.Entry<String, Object> par : ((RespostaDeValidacao) resposta).getNormalizados ().entrySet ())
            {
                Object valor = par.getValue ();

                if (valor instanceof BigDecimal) // dinheiro gravado com 2 casas
                    valor = Double.valueOf (((BigDecimal) valor).doubleValue ());

                campos.append (par.getKey (), valor);
            }

            ret = new Resultado (null, campos);
        }

        return ret;
    }

    private static String id (Object valor)
    {
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    // ------------------------------------------------------------------ validacoes

    public static Resultado validarEquipamento (Document corpo, int anoAtual) throws ErroHttp
    {
        Document invalidos = new Document ();

        PedidoDeValidacaoDeEquipamento pedido = new PedidoDeValidacaoDeEquipamento (
            AlgoritmosDeManutencao.texto   (corpo, "nome",          invalidos),
            AlgoritmosDeManutencao.texto   (corpo, "tipo",          invalidos),
            AlgoritmosDeManutencao.texto   (corpo, "marca",         invalidos),
            AlgoritmosDeManutencao.texto   (corpo, "modelo",        invalidos),
            AlgoritmosDeManutencao.texto   (corpo, "identificacao", invalidos),
            AlgoritmosDeManutencao.inteiro (corpo, "ano",           invalidos),
            AlgoritmosDeManutencao.numero  (corpo, "horimetro",     invalidos),
            AlgoritmosDeManutencao.texto   (corpo, "status",        invalidos),
            Integer.valueOf (anoAtual));

        if (!invalidos.isEmpty ())
            return new Resultado (AlgoritmosDeManutencao.campos (invalidos), null);

        return AlgoritmosDeManutencao.interpretar (AlgoritmosDeManutencao.chamar (pedido));
    }

    public static Resultado validarPreventiva (Document corpo) throws ErroHttp
    {
        Document invalidos = new Document ();

        PedidoDeValidacaoDePreventiva pedido = new PedidoDeValidacaoDePreventiva (
            AlgoritmosDeManutencao.texto   (corpo, "descricao",     invalidos),
            AlgoritmosDeManutencao.texto   (corpo, "dataPrevista",  invalidos),
            AlgoritmosDeManutencao.inteiro (corpo, "intervaloDias", invalidos));

        if (!invalidos.isEmpty ())
            return new Resultado (AlgoritmosDeManutencao.campos (invalidos), null);

        return AlgoritmosDeManutencao.interpretar (AlgoritmosDeManutencao.chamar (pedido));
    }

    // hoje: AAAA-MM-DD no fuso da propriedade (a data nao pode estar no futuro)
    public static Resultado validarIntervencao (Document corpo, String hoje) throws ErroHttp
    {
        Document invalidos = new Document ();

        PedidoDeValidacaoDeIntervencao pedido = new PedidoDeValidacaoDeIntervencao (
            AlgoritmosDeManutencao.texto    (corpo, "tipo",        invalidos),
            AlgoritmosDeManutencao.texto    (corpo, "data",        invalidos),
            AlgoritmosDeManutencao.texto    (corpo, "descricao",   invalidos),
            AlgoritmosDeManutencao.texto    (corpo, "responsavel", invalidos),
            AlgoritmosDeManutencao.numero   (corpo, "horimetro",   invalidos),
            AlgoritmosDeManutencao.dinheiro (corpo, "valor",       invalidos),
            hoje);

        if (!invalidos.isEmpty ())
            return new Resultado (AlgoritmosDeManutencao.campos (invalidos), null);

        Resultado ret = AlgoritmosDeManutencao.interpretar (AlgoritmosDeManutencao.chamar (pedido));

        if (ret.campos != null && !ret.campos.containsKey ("valor"))
            ret.campos.append ("valor", null); // sem valor: sem custo

        return ret;
    }

    // ------------------------------------------------------------------ agenda

    // situacao de cada preventiva programada (chave = id do documento)
    public static RespostaDeAgendaDeManutencao agenda (List<Document> preventivas, String hoje, Integer diasDeAviso) throws ErroHttp
    {
        ArrayList<PreventivaParaAgenda> lista = new ArrayList<PreventivaParaAgenda> ();

        for (Document preventiva : preventivas)
        {
            Object intervalo = preventiva.get ("intervaloDias");

            lista.add (new PreventivaParaAgenda (AlgoritmosDeManutencao.id (preventiva.get ("_id")),
                                                 preventiva.getString ("equipamentoId"),
                                                 preventiva.getString ("descricao"),
                                                 preventiva.getString ("dataPrevista"),
                                                 intervalo instanceof Number ? Integer.valueOf (((Number) intervalo).intValue ()) : null,
                                                 preventiva.getString ("status")));
        }

        Resposta resposta = AlgoritmosDeManutencao.chamar (new PedidoDeAgendaDeManutencao (lista, hoje, diasDeAviso));

        if (!(resposta instanceof RespostaDeAgendaDeManutencao))
            throw AlgoritmosDeManutencao.comoErro (resposta);

        return (RespostaDeAgendaDeManutencao) resposta;
    }

    // data realizada + intervalo; null sem intervalo
    public static String proximaData (String dataRealizada, Integer intervaloDias) throws ErroHttp
    {
        Resposta resposta = AlgoritmosDeManutencao.chamar (new PedidoDeProximaPreventiva (dataRealizada, intervaloDias));

        if (!(resposta instanceof RespostaDeProximaPreventiva))
            throw AlgoritmosDeManutencao.comoErro (resposta);

        return ((RespostaDeProximaPreventiva) resposta).getProximaData ();
    }
}
