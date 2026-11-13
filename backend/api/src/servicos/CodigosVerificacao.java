/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package servicos;

import java.security.SecureRandom;

/** Codigos numericos curtos para confirmacao de e-mail e redefinicao de senha. */
public final class CodigosVerificacao
{
    private static final SecureRandom ALEATORIO = new SecureRandom ();

    private CodigosVerificacao () {}

    public static String seisDigitos ()
    {
        int valor = 100_000 + ALEATORIO.nextInt (900_000);
        return String.format ("%06d", valor);
    }
}
