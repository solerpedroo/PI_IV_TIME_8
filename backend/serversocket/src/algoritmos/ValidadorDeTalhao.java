package algoritmos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import protocolo.Resposta;
import protocolo.mensagens.PedidoDeValidacaoDeTalhao;
import protocolo.mensagens.RespostaDeValidacao;

// Regras de um talhao (as mesmas para criar e editar). As listas abaixo sao as do cadastro atual.
public class ValidadorDeTalhao
{
    public static final List<String> STATUS = List.of ("Ativo", "Irrigação pendente", "Em preparo");
    public static final String STATUS_PADRAO = "Ativo";

    public static Resposta validar (PedidoDeValidacaoDeTalhao pedido)
    {
        Validacao v = new Validacao ();

        String nome    = v.texto ("nome", pedido.getNome (), "Informe o nome do talhão.", 80);
        String cultura = v.texto ("cultura", pedido.getCultura (), "Selecione uma cultura.", 40);

        String safra = pedido.getSafra () == null ? PeriodoSafra.padraoAtual () : pedido.getSafra ().trim ();
        if (safra.isEmpty ())
            v.erro ("safra", "Informe o período da safra.");

        if (cultura != null && cultura.length () < 2)
            v.erro ("cultura", "Informe o nome da cultura.");

        if (!PeriodoSafra.valida (safra))
            v.erro ("safra", "Use o formato AAAA/AA (ex.: 2026/27).");

        Double hectares = pedido.getHectares ();
        if (hectares == null || !(hectares > 0) || hectares > 1000000) // o !(> 0) tambem barra NaN
            v.erro ("hectares", "Informe um valor de hectares maior que zero.");

        String status = ValidadorDeTalhao.STATUS_PADRAO;
        if (pedido.getStatus () != null && !pedido.getStatus ().isEmpty ())
        {
            if (!ValidadorDeTalhao.STATUS.contains (pedido.getStatus ()))
                v.erro ("status", "Selecione um status válido.");
            else
                status = pedido.getStatus ();
        }

        LinkedHashMap<String, Object> normalizados = new LinkedHashMap<String, Object> ();

        if (!v.temErros ())
        {
            normalizados.put ("nome",            nome);
            normalizados.put ("nomeNormalizado", nome.toLowerCase (Locale.ROOT)); // unicidade sem diferenciar maiusculas
            normalizados.put ("cultura",         cultura);
            normalizados.put ("safra",           safra);
            normalizados.put ("hectares",        hectares);
            normalizados.put ("status",          status);
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }
}
