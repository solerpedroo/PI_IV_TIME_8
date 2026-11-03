/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package seguranca;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
    Hash de senha com PBKDF2WithHmacSHA256 do proprio JDK.
    Formato guardado: pbkdf2$iteracoes$salt$hash (salt e hash em Base64).
*/
public class Senhas
{
    private static final int ITERACOES = 120000;
    private static final int BYTES_SALT = 16;
    private static final int BITS_HASH  = 256;

    private static final SecureRandom sorteador = new SecureRandom ();

    // hash fixo usado quando o e-mail nao existe, para o login demorar o mesmo tempo
    private static final String HASH_FALSO = Senhas.gerarSemErro ("senha-que-nao-existe");

    public static String gerar (String senha) throws Exception
    {
        if (senha == null)
            throw new Exception ("Senha ausente");

        byte[] salt = new byte[Senhas.BYTES_SALT];
        Senhas.sorteador.nextBytes (salt);

        byte[] hash = Senhas.derivar (senha, salt, Senhas.ITERACOES);

        return "pbkdf2$" + Senhas.ITERACOES + "$" +
               Base64.getEncoder ().encodeToString (salt) + "$" +
               Base64.getEncoder ().encodeToString (hash);
    }

    public static boolean conferir (String senha, String guardado) throws Exception
    {
        if (senha == null)
            throw new Exception ("Senha ausente");

        if (guardado == null)
        {
            Senhas.conferir (senha, Senhas.HASH_FALSO);
            return false;
        }

        String[] partes = guardado.split ("\\$");
        if (partes.length != 4 || !partes[0].equals ("pbkdf2"))
            throw new Exception ("Hash de senha invalido");

        int    iteracoes = Integer.parseInt (partes[1]);
        byte[] salt      = Base64.getDecoder ().decode (partes[2]);
        byte[] esperado  = Base64.getDecoder ().decode (partes[3]);

        // comparacao em tempo constante
        return MessageDigest.isEqual (esperado, Senhas.derivar (senha, salt, iteracoes));
    }

    private static byte[] derivar (String senha, byte[] salt, int iteracoes) throws Exception
    {
        PBEKeySpec especificacao = new PBEKeySpec (senha.toCharArray (), salt, iteracoes, Senhas.BITS_HASH);

        try
        {
            return SecretKeyFactory.getInstance ("PBKDF2WithHmacSHA256").generateSecret (especificacao).getEncoded ();
        }
        finally
        {
            especificacao.clearPassword ();
        }
    }

    private static String gerarSemErro (String senha)
    {
        try
        {
            return Senhas.gerar (senha);
        }
        catch (Exception erro)
        {
            throw new RuntimeException (erro);
        }
    }
}
