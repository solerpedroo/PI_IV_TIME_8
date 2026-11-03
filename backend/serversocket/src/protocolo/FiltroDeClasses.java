package protocolo;

import java.io.ObjectInputFilter;
import java.util.Set;

/*
    Lista de permissao do ObjectInputStream. Desserializar qualquer classe que chegue pela rede e
    uma falha de seguranca conhecida; aqui so passam as classes do pacote protocolo e meia duzia
    de classes do JDK. Qualquer outra faz o readObject lancar InvalidClassException.
    Por isso as mensagens usam ArrayList e LinkedHashMap (e nao List.of / Map.of, que seriam recusados).
*/
public class FiltroDeClasses
{
    public static final int MAXIMO_DE_ELEMENTOS    = 100000; // tamanho maximo de um array ou lista
    public static final int MAXIMO_DE_PROFUNDIDADE = 20;     // objetos aninhados

    private static final Set<String> DO_JDK = Set.of (
        "java.lang.String",   "java.lang.Number",     "java.lang.Double",     "java.lang.Long",
        "java.lang.Integer",  "java.lang.Boolean",    "java.math.BigDecimal", "java.math.BigInteger",
        "java.util.ArrayList", "java.util.HashMap",   "java.util.LinkedHashMap");

    public static ObjectInputFilter criar ()
    {
        return informacao ->
        {
            if (informacao.arrayLength () > FiltroDeClasses.MAXIMO_DE_ELEMENTOS ||
                informacao.depth ()       > FiltroDeClasses.MAXIMO_DE_PROFUNDIDADE)
                return ObjectInputFilter.Status.REJECTED;

            Class<?> classe = informacao.serialClass ();

            // verificacao so de limites (sem classe): ja foi tratada acima
            if (classe == null)
                return ObjectInputFilter.Status.UNDECIDED;

            // o tamanho do array foi conferido acima; o tipo de cada elemento e conferido um a um quando ele chega
            if (classe.isArray ())
                return ObjectInputFilter.Status.ALLOWED;

            String nome = classe.getName ();

            if (nome.startsWith ("protocolo.") || FiltroDeClasses.DO_JDK.contains (nome))
                return ObjectInputFilter.Status.ALLOWED;

            return ObjectInputFilter.Status.REJECTED;
        };
    }
}
