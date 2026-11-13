package util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Período agrícola no formato AAAA/AA (ex.: 2025/26). */
public final class PeriodoSafra
{
    private static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");

    private PeriodoSafra ()
    {
    }

    public static boolean valida (String safra)
    {
        if (safra == null)
            return false;

        String limpa = safra.trim ();
        if (!limpa.matches ("\\d{4}/\\d{2}"))
            return false;

        int ano     = Integer.parseInt (limpa.substring (0, 4));
        int sufixo  = Integer.parseInt (limpa.substring (5, 7));
        int esperado = (ano + 1) % 100;

        return ano >= 1900 && ano <= 2100 && sufixo == esperado;
    }

    /** Julho em diante: safra ano/(ano+1); antes disso, (ano-1)/ano. */
    public static String padraoAtual ()
    {
        LocalDate hoje = LocalDate.now (PeriodoSafra.FUSO);
        int       ano  = hoje.getYear ();

        if (hoje.getMonthValue () >= 7)
            return PeriodoSafra.formatar (ano);

        return PeriodoSafra.formatar (ano - 1);
    }

    public static String formatar (int anoInicio)
    {
        return anoInicio + "/" + String.format ("%02d", (anoInicio + 1) % 100);
    }

    public static String deData (LocalDate data)
    {
        if (data == null)
            return PeriodoSafra.padraoAtual ();

        int ano = data.getYear ();
        if (data.getMonthValue () >= 7)
            return PeriodoSafra.formatar (ano);

        return PeriodoSafra.formatar (ano - 1);
    }

    public static List<String> sugerir (int antes, int depois)
    {
        int                 base = Integer.parseInt (PeriodoSafra.padraoAtual ().substring (0, 4));
        ArrayList<String>   ret  = new ArrayList<String> ();

        for (int i = -antes; i <= depois; i++)
            ret.add (PeriodoSafra.formatar (base + i));

        return ret;
    }

    public static List<String> unirOrdenar (Iterable<String>... listas)
    {
        LinkedHashSet<String> uniao = new LinkedHashSet<String> ();

        for (Iterable<String> lista : listas)
        {
            if (lista == null)
                continue;

            for (String item : lista)
                if (item != null)
                {
                    String limpa = item.trim ();
                    if (PeriodoSafra.valida (limpa))
                        uniao.add (limpa);
                }
        }

        ArrayList<String> ret = new ArrayList<String> (uniao);
        ret.sort ((a, b) -> Integer.compare (
            Integer.parseInt (b.substring (0, 4)),
            Integer.parseInt (a.substring (0, 4))));

        return ret;
    }
}
