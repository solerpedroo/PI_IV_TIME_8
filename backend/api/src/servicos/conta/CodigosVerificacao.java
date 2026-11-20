/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package servicos.conta;

import java.security.SecureRandom;

/*
    Gera os codigos numericos curtos de confirmacao de e-mail e de redefinicao de senha.

    A classe e final e o construtor e privado: nao ha objeto para guardar.
    O gerador fica numa constante estatica para nao recriar a semente a cada codigo.
    SecureRandom, e nao java.util.Random, porque o codigo e um segredo temporario.
    Random comum e previsivel se alguem observa valores anteriores.
*/
public final class CodigosVerificacao
{
    private static final SecureRandom ALEATORIO = new SecureRandom ();

    private CodigosVerificacao () {}

    /*
        Sorteia um texto de exatamente seis digitos, de 100000 a 999999.

        nextInt(900_000) devolve 0..899999. Somar 100_000 empurra o intervalo
        para 100000..999999, entao o numero ja nasce com seis digitos e nunca
        com zero a esquerda. format("%06d") ainda forca a largura: se o
        intervalo mudar no futuro, o e-mail e a validacao (regex de 6 digitos)
        continuam recebendo string do mesmo tamanho. Guardar como texto, e nao
        como int, evita que um zero a esquerda desapareca numa conversao numerica.
    */
    public static String seisDigitos ()
    {
        int valor = 100_000 + ALEATORIO.nextInt (900_000);
        return String.format ("%06d", valor);
    }
}
