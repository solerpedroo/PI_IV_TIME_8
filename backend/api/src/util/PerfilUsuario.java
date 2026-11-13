/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package util;

import org.bson.Document;

/* Projeção compartilhada: nunca permite que hash, senha ou códigos saiam na API. */
public class PerfilUsuario
{
    public static final java.util.List<String> PREFERENCIAS = java.util.List.of ("stock", "weather", "activities", "occurrences", "reports");

    public static Document preferencias (Document usuario)
    {
        Document salvas = usuario.get ("preferencias", Document.class);
        Document ret = new Document ();
        for (String chave : PREFERENCIAS)
            ret.append (chave, salvas == null ? !chave.equals ("reports") : Boolean.TRUE.equals (salvas.get (chave)));
        return ret;
    }

    public static boolean onboardingConcluido (Document usuario)
    {
        if (usuario == null)
            return true;

        if (!usuario.containsKey ("onboardingConcluido"))
            return true;

        Object valor = usuario.get ("onboardingConcluido");
        if (valor instanceof Boolean)
            return Boolean.TRUE.equals (valor);

        if (valor instanceof String)
            return "true".equalsIgnoreCase (((String) valor).trim ());

        return true;
    }

    public static Document publico (Document usuario)
    {
        return new Document ("id", usuario.getObjectId ("_id").toHexString ())
            .append ("name", usuario.getString ("name")).append ("email", usuario.getString ("email"))
            .append ("phone", usuario.getString ("phone")).append ("emailConfirmado", usuario.getBoolean ("emailConfirmado"))
            .append ("emailPendente", usuario.getString ("emailPendente"))
            .append ("propriedadeId", usuario.getString ("propriedadeId"))
            .append ("propriedadePadraoId", usuario.getString ("propriedadeId"))
            .append ("onboardingConcluido", PerfilUsuario.onboardingConcluido (usuario));
    }
}
