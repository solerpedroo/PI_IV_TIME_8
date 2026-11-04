/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package algoritmos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeValidacaoDeEquipamento;
import protocolo.mensagens.PedidoDeValidacaoDeIntervencao;
import protocolo.mensagens.PedidoDeValidacaoDePreventiva;
import protocolo.mensagens.RespostaDeValidacao;

/*
    Regras de campo da manutencao de equipamentos (#235, #236): equipamento, preventiva e intervencao.
    As regras que dependem do banco (nome unico, equipamento da propriedade, safra finalizada) sao da API.
    Datas no formato AAAA-MM-DD; data impossivel (2026-02-30) e recusada.
*/
public class ValidadorDeManutencao
{
    public static final ArrayList<String> TIPOS_DE_EQUIPAMENTO = new ArrayList<String> ();
    public static final ArrayList<String> STATUS_DE_EQUIPAMENTO = new ArrayList<String> ();
    public static final ArrayList<String> TIPOS_DE_INTERVENCAO = new ArrayList<String> ();

    public static final String     STATUS_PADRAO = "Ativo";
    public static final int        ANO_MINIMO     = 1950;
    public static final double     HORIMETRO_MAXIMO = 1000000;
    public static final int        INTERVALO_MAXIMO = 3650;
    public static final BigDecimal VALOR_MINIMO   = new BigDecimal ("0.01");
    public static final BigDecimal VALOR_MAXIMO   = new BigDecimal ("1000000000");

    static
    {
        ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.add ("Trator");
        ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.add ("Colheitadeira");
        ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.add ("Pulverizador");
        ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.add ("Plantadeira");
        ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.add ("Implemento");
        ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.add ("Ve\u00edculo");
        ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.add ("Outro");

        ValidadorDeManutencao.STATUS_DE_EQUIPAMENTO.add ("Ativo");
        ValidadorDeManutencao.STATUS_DE_EQUIPAMENTO.add ("Em manuten\u00e7\u00e3o");
        ValidadorDeManutencao.STATUS_DE_EQUIPAMENTO.add ("Inativo");

        ValidadorDeManutencao.TIPOS_DE_INTERVENCAO.add ("preventiva");
        ValidadorDeManutencao.TIPOS_DE_INTERVENCAO.add ("corretiva");
    }

    // ------------------------------------------------------------------ auxiliares

    // data AAAA-MM-DD valida (de 2000 a 2100); null se ausente ou invalida
    public static LocalDate data (String texto)
    {
        LocalDate ret = null;

        if (texto != null && texto.trim ().matches ("\\d{4}-\\d{2}-\\d{2}"))
        {
            try
            {
                ret = LocalDate.parse (texto.trim ()); // ISO estrito: recusa 2026-02-30
                if (ret.getYear () < 2000 || ret.getYear () > 2100)
                    ret = null;
            }
            catch (Exception erro)
            {
                ret = null;
            }
        }

        return ret;
    }

    // texto opcional: ausente ou so espacos vira null (nao grava texto vazio)
    private static String opcional (Validacao v, String campo, String valor, int maximo)
    {
        String ret = v.textoOpcional (campo, valor, maximo);

        return ret.isEmpty () ? null : ret;
    }

    private static Double horimetro (Validacao v, Double valor)
    {
        Double ret = null;

        if (valor != null)
        {
            if (!(valor.doubleValue () >= 0) || valor.doubleValue () > ValidadorDeManutencao.HORIMETRO_MAXIMO) // !(>= 0) barra NaN
                v.erro ("horimetro", "Informe um hor\u00edmetro entre 0 e 1.000.000 horas.");
            else
                ret = Double.valueOf (Math.round (valor.doubleValue () * 10) / 10.0); // uma casa
        }

        return ret;
    }

    // ------------------------------------------------------------------ equipamento

    public static Resposta validarEquipamento (PedidoDeValidacaoDeEquipamento pedido)
    {
        if (pedido.getAnoAtual () == null)
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Ano atual ausente no pedido.");

        Validacao v       = new Validacao ();
        String    nome    = v.texto ("nome", pedido.getNome (), "Informe o nome do equipamento.", 80);
        String    tipo    = v.texto ("tipo", pedido.getTipo (), "Selecione o tipo do equipamento.", 40);
        String    marca   = ValidadorDeManutencao.opcional (v, "marca",         pedido.getMarca (),         60);
        String    modelo  = ValidadorDeManutencao.opcional (v, "modelo",        pedido.getModelo (),        60);
        String    placa   = ValidadorDeManutencao.opcional (v, "identificacao", pedido.getIdentificacao (), 60);
        Integer   ano     = pedido.getAno ();
        int       anoMax  = pedido.getAnoAtual ().intValue () + 1;
        Double    horas   = ValidadorDeManutencao.horimetro (v, pedido.getHorimetro ());
        String    status  = ValidadorDeManutencao.STATUS_PADRAO;

        if (tipo != null && !ValidadorDeManutencao.TIPOS_DE_EQUIPAMENTO.contains (tipo))
            v.erro ("tipo", "Selecione um tipo v\u00e1lido.");

        if (ano != null && (ano.intValue () < ValidadorDeManutencao.ANO_MINIMO || ano.intValue () > anoMax))
            v.erro ("ano", "Informe um ano entre " + ValidadorDeManutencao.ANO_MINIMO + " e " + anoMax + ".");

        if (pedido.getStatus () != null && !pedido.getStatus ().trim ().isEmpty ())
        {
            if (!ValidadorDeManutencao.STATUS_DE_EQUIPAMENTO.contains (pedido.getStatus ().trim ()))
                v.erro ("status", "Selecione um status v\u00e1lido.");
            else
                status = pedido.getStatus ().trim ();
        }

        LinkedHashMap<String, Object> normalizados = new LinkedHashMap<String, Object> ();

        if (!v.temErros ())
        {
            normalizados.put ("nome",            nome);
            normalizados.put ("nomeNormalizado", nome.toLowerCase (Locale.ROOT)); // unicidade sem diferenciar maiusculas
            normalizados.put ("tipo",            tipo);
            normalizados.put ("marca",           marca);
            normalizados.put ("modelo",          modelo);
            normalizados.put ("identificacao",   placa);
            normalizados.put ("ano",             ano);
            normalizados.put ("horimetro",       horas);
            normalizados.put ("status",          status);
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }

    // ------------------------------------------------------------------ preventiva

    public static Resposta validarPreventiva (PedidoDeValidacaoDePreventiva pedido)
    {
        Validacao v         = new Validacao ();
        String    descricao = v.texto ("descricao", pedido.getDescricao (), "Descreva a manuten\u00e7\u00e3o preventiva.", 120);
        LocalDate prevista  = ValidadorDeManutencao.data (pedido.getDataPrevista ());
        Integer   intervalo = pedido.getIntervaloDias ();

        if (pedido.getDataPrevista () == null || pedido.getDataPrevista ().trim ().isEmpty ())
            v.erro ("dataPrevista", "Informe a data prevista.");
        else if (prevista == null)
            v.erro ("dataPrevista", "Informe uma data v\u00e1lida.");

        if (intervalo != null && (intervalo.intValue () < 1 || intervalo.intValue () > ValidadorDeManutencao.INTERVALO_MAXIMO))
            v.erro ("intervaloDias", "Use um intervalo de 1 a 3650 dias, ou deixe em branco.");

        LinkedHashMap<String, Object> normalizados = new LinkedHashMap<String, Object> ();

        if (!v.temErros ())
        {
            normalizados.put ("descricao",     descricao);
            normalizados.put ("dataPrevista",  prevista.toString ());
            normalizados.put ("intervaloDias", intervalo);
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }

    // ------------------------------------------------------------------ intervencao

    public static Resposta validarIntervencao (PedidoDeValidacaoDeIntervencao pedido)
    {
        LocalDate hoje = ValidadorDeManutencao.data (pedido.getHoje ());

        if (hoje == null)
            return new RespostaDeErro (pedido.getId (), RespostaDeErro.PEDIDO_INVALIDO, "Data de hoje ausente no pedido.");

        Validacao  v           = new Validacao ();
        String     tipo        = v.texto ("tipo", pedido.getTipo (), "Selecione o tipo da interven\u00e7\u00e3o.", 20);
        String     descricao   = v.texto ("descricao", pedido.getDescricao (), "Descreva a interven\u00e7\u00e3o.", 240);
        String     responsavel = ValidadorDeManutencao.opcional (v, "responsavel", pedido.getResponsavel (), 80);
        LocalDate  data        = ValidadorDeManutencao.data (pedido.getData ());
        Double     horas       = ValidadorDeManutencao.horimetro (v, pedido.getHorimetro ());
        BigDecimal valor       = null;

        if (tipo != null && !ValidadorDeManutencao.TIPOS_DE_INTERVENCAO.contains (tipo))
            v.erro ("tipo", "Use preventiva ou corretiva.");

        if (pedido.getData () == null || pedido.getData ().trim ().isEmpty ())
            v.erro ("data", "Informe a data da interven\u00e7\u00e3o.");
        else if (data == null)
            v.erro ("data", "Informe uma data v\u00e1lida.");
        else if (data.isAfter (hoje))
            v.erro ("data", "A data da interven\u00e7\u00e3o n\u00e3o pode estar no futuro.");

        // sem valor nao ha custo (a issue proibe despesa zerada)
        if (pedido.getValor () != null)
        {
            valor = pedido.getValor ().setScale (2, RoundingMode.HALF_UP);

            if (valor.compareTo (ValidadorDeManutencao.VALOR_MINIMO) < 0 || valor.compareTo (ValidadorDeManutencao.VALOR_MAXIMO) > 0)
            {
                v.erro ("valor", "Informe um valor de R$ 0,01 at\u00e9 R$ 1 bilh\u00e3o, ou deixe em branco.");
                valor = null;
            }
        }

        LinkedHashMap<String, Object> normalizados = new LinkedHashMap<String, Object> ();

        if (!v.temErros ())
        {
            normalizados.put ("tipo",        tipo);
            normalizados.put ("data",        data.toString ());
            normalizados.put ("descricao",   descricao);
            normalizados.put ("responsavel", responsavel);
            normalizados.put ("horimetro",   horas);

            if (valor != null)
                normalizados.put ("valor", valor);
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }
}
