/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package seguranca;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
    Guarda senha sem guardar a senha.

    PBKDF2WithHmacSHA256 (já vem no JDK) mistura a senha com um salt aleatório
    e repete o cálculo 120 mil vezes. O que vai para o banco é o texto:
      pbkdf2$120000$<salt em Base64>$<hash em Base64>
    Dá para conferir a senha depois, mas não dá para ler a senha original.

    HASH_FALSO existe para o login de um e-mail que não está cadastrado demorar
    o mesmo tempo que um e-mail que existe. Sem isso, um atacante descobre
    quais e-mails são válidos só medindo a resposta.
*/
public class Senhas
{
    private static final int ITERACOES = 120000;
    private static final int BYTES_SALT = 16;
    private static final int BITS_HASH  = 256;

    private static final SecureRandom sorteador = new SecureRandom ();

    // hash fixo usado quando o e-mail nao existe, para o login demorar o mesmo tempo
    private static final String HASH_FALSO = Senhas.gerarSemErro ("senha-que-nao-existe");

    // Cria um hash novo. Cada chamada sorteia um salt diferente, então a mesma
    // senha gera textos diferentes no banco. Isso é o esperado.
    public static String gerar (String senha) throws Exception
    {
        if (senha == null)
            throw new Exception ("Senha ausente");

        // 16 bytes aleatórios. Sem salt, duas pessoas com a mesma senha teriam o mesmo hash.
        byte[] salt = new byte[Senhas.BYTES_SALT];
        Senhas.sorteador.nextBytes (salt);

        // 32 bytes (256 bits) depois de 120 mil voltas de HMAC-SHA256.
        byte[] hash = Senhas.derivar (senha, salt, Senhas.ITERACOES);

        // Quatro pedaços separados por $. conferir() faz split("\\$") e exige length == 4.
        // Ex.: pbkdf2$120000$ab12...base64...$cd34...base64...
        return "pbkdf2$" + Senhas.ITERACOES + "$" +
               Base64.getEncoder ().encodeToString (salt) + "$" +
               Base64.getEncoder ().encodeToString (hash);
    }

    // Recalcula o hash com o salt que estava gravado e compara com o hash antigo.
    // MessageDigest.isEqual compara em tempo constante: não para no primeiro byte diferente.
    public static boolean conferir (String senha, String guardado) throws Exception
    {
        if (senha == null)
            throw new Exception ("Senha ausente");

        if (guardado == null)
        {
            // E-mail inexistente. Gastamos o mesmo tempo de um hash real e respondemos false.
            Senhas.conferir (senha, Senhas.HASH_FALSO);
            return false;
        }

        // partes[0]=pbkdf2, [1]=iterações, [2]=salt Base64, [3]=hash Base64.
        String[] partes = guardado.split ("\\$");
        if (partes.length != 4 || !partes[0].equals ("pbkdf2"))
            throw new Exception ("Hash de senha invalido");

        int    iteracoes = Integer.parseInt (partes[1]);
        byte[] salt      = Base64.getDecoder ().decode (partes[2]);
        byte[] esperado  = Base64.getDecoder ().decode (partes[3]);

        return MessageDigest.isEqual (esperado, Senhas.derivar (senha, salt, iteracoes));
    }

    // O cálculo pesado de verdade. clearPassword apaga a senha da memória do JDK ao terminar.
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

    // O hash falso é criado quando a classe carrega. gerar() lança Exception checada,
    // e um inicializador estático não pode declarar throws, então embrulhamos aqui.
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
