package protocolo;

import java.io.ObjectInputFilter;
import java.util.Set;

/*
    Lista de permissao do ObjectInputStream. Desserializar qualquer classe que chegue pela rede
    permite que um cliente monte um objeto perigoso (gadget) so por estar na conversa.
    Aqui so passam classes do pacote protocolo e a meia duzia do JDK que os campos usam de fato.
    Qualquer outra faz o readObject lancar InvalidClassException. A Supervisora transforma isso
    em RespostaDeErro COMUNICADO_PROIBIDO e fecha a conexao.

    Por isso as mensagens usam ArrayList e LinkedHashMap. List.of e Map.of geram classes
    internas do JDK (ListN, MapN) que nao estao na lista e seriam recusadas.
    O filtro olha o nome da classe, nao a interface: "e uma List" nao basta.
*/
public class FiltroDeClasses
{
    public static final int MAXIMO_DE_ELEMENTOS    = 100000; // um array ou uma lista maior que isso e rejeitado
    public static final int MAXIMO_DE_PROFUNDIDADE = 20;     // objetos dentro de objetos; pedido honesto nao chega perto

    // nome exato, nao prefixo: java.lang.ProcessBuilder e parecidos ficam de fora mesmo comecando com java.
    private static final Set<String> DO_JDK = Set.of (
        "java.lang.String",   "java.lang.Number",     "java.lang.Double",     "java.lang.Long",
        "java.lang.Integer",  "java.lang.Boolean",    "java.math.BigDecimal", "java.math.BigInteger",
        "java.util.ArrayList", "java.util.HashMap",   "java.util.LinkedHashMap");

    /*
        Um filtro novo por stream. O JDK chama isto a cada classe, array e nivel que aparece no objeto.
        REJECTED aborta a leitura. ALLOWED aceita aquela classe. UNDECIDED deixa a decisao para o
        proximo filtro; aqui nao ha outro, entao UNDECIDED so ocorre na checagem sem classe.
    */
    public static ObjectInputFilter criar ()
    {
        return informacao ->
        {
            // arrayLength vale -1 quando o informe nao e de array; depth cresce a cada aninhamento
            if (informacao.arrayLength () > FiltroDeClasses.MAXIMO_DE_ELEMENTOS ||
                informacao.depth ()       > FiltroDeClasses.MAXIMO_DE_PROFUNDIDADE)
                return ObjectInputFilter.Status.REJECTED;

            Class<?> classe = informacao.serialClass ();

            // chamada so para conferir tamanho ou profundidade, ainda sem a classe do elemento
            if (classe == null)
                return ObjectInputFilter.Status.UNDECIDED;

            // o comprimento ja foi barrado acima; o componente (String, Double...) e julgado quando chega
            if (classe.isArray ())
                return ObjectInputFilter.Status.ALLOWED;

            String nome = classe.getName ();

            // protocolo. cobre Comunicado, Pedido, Resposta e as pastas de protocolo.mensagens
            if (nome.startsWith ("protocolo.") || FiltroDeClasses.DO_JDK.contains (nome))
                return ObjectInputFilter.Status.ALLOWED;

            return ObjectInputFilter.Status.REJECTED;
        };
    }
}
