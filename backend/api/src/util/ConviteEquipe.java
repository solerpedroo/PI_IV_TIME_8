// Miguel — RA: 25003110
package util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/** Token opaco para convite manual por link. */
public final class ConviteEquipe
{
    private static final SecureRandom ALEATORIO = new SecureRandom ();
    public static final long VALIDADE_MS = 7L * 24 * 60 * 60 * 1000;

    private ConviteEquipe () {}

    public static String gerarToken ()
    {
        byte[] bytes = new byte[24];
        ConviteEquipe.ALEATORIO.nextBytes (bytes);
        return Base64.getUrlEncoder ().withoutPadding ().encodeToString (bytes);
    }

    public static String expiraEm ()
    {
        return Instant.ofEpochMilli (System.currentTimeMillis () + ConviteEquipe.VALIDADE_MS).toString ();
    }

    public static boolean expirado (Object valor)
    {
        if (valor == null)
            return true;
        try
        {
            return Instant.parse (String.valueOf (valor)).toEpochMilli () < System.currentTimeMillis ();
        }
        catch (Exception erro)
        {
            return true;
        }
    }
}