package algoritmos.talhao;

import java.time.LocalDate;
import java.time.ZoneId;

/** Período agrícola AAAA/AA — mesmas regras da API HTTP. O sufixo e os dois digitos do ano seguinte. */
public final class PeriodoSafra
{
    private static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");

    // nao instancia: so funcoes
    private PeriodoSafra ()
    {
    }

    // 1900/01 ate 2100/01, e o sufixo tem de ser (ano+1) % 100. 2026/27 passa; 2026/26 nao.
    public static boolean valida (String safra)
    {
        if (safra == null)
            return false;

        String limpa = safra.trim ();
        if (!limpa.matches ("\\d{4}/\\d{2}"))
            return false;

        int ano      = Integer.parseInt (limpa.substring (0, 4));
        int sufixo   = Integer.parseInt (limpa.substring (5, 7));
        int esperado = (ano + 1) % 100;

        return ano >= 1900 && ano <= 2100 && sufixo == esperado;
    }

    // safra "atual": a partir de julho vale o ano corrente; antes disso, o ano anterior (ciclo que comeca no segundo semestre)
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
}
