// Miguel — RA: 25003110
package util.propriedade;

import java.util.List;
import org.bson.Document;

/* Chaves de navegação alinhadas ao data-sidebar-page do frontend. */
public final class TelasAcesso
{
    private TelasAcesso () {}

    public static final List<String> CHAVES = List.of (
        "dashboard", "areas-cultivo", "culturas", "safras", "atividades",
        "insumos", "manutencao", "ocorrencias", "custos", "configuracoes"
    );

    public static Document padraoNovato ()
    {
        Document ret = new Document ();
        for (String chave : TelasAcesso.CHAVES)
            ret.append (chave, !chave.equals ("configuracoes"));
        return ret;
    }

  /* Mantém apenas chaves conhecidas; ausência de campo vira acesso liberado,
     exceto configurações, que fica fechado por padrão. */
    public static Document normalizar (Object bruto)
    {
        Document entrada = bruto instanceof Document ? (Document) bruto : new Document ();
        Document saida   = new Document ();

        for (String chave : TelasAcesso.CHAVES)
        {
            Object valor = entrada.get (chave);
            boolean liberado;

            if (valor == null)
                liberado = !chave.equals ("configuracoes");
            else if (valor instanceof Boolean)
                liberado = (Boolean) valor;
            else
                liberado = Boolean.parseBoolean (String.valueOf (valor));

            saida.append (chave, liberado);
        }

        return saida;
    }
}