// Miguel — RA: 25003110
package util;

import java.util.Locale;
import org.bson.Document;

/** Campos e projeção pública dos registros de equipe_membros. */
public final class EquipeMembro
{
    public static final String STATUS_LOCAL           = "local";
    public static final String STATUS_CONVITE_PENDENTE = "convite_pendente";
    public static final String STATUS_ATIVO            = "ativo";

    private EquipeMembro () {}

    public static String emailNormalizado (String email)
    {
        return email == null ? "" : email.trim ().toLowerCase (Locale.ROOT);
    }

    public static String nomeNormalizado (String nome)
    {
        return nome == null ? "" : nome.trim ().toLowerCase (Locale.ROOT);
    }

    public static Document publico (Document doc, boolean incluirConvite)
    {
        Document telas = doc.get ("telas", Document.class);
        Document ret = new Document ("id", doc.getObjectId ("_id").toHexString ())
                           .append ("nome", doc.getString ("nome"))
                           .append ("status", doc.getString ("status"))
                           .append ("telas", telas == null ? new Document () : new Document (telas))
                           .append ("criadoEm", doc.get ("criadoEm"))
                           .append ("atualizadoEm", doc.get ("atualizadoEm"));

        String cargo = doc.getString ("cargo");
        if (cargo != null && !cargo.isBlank ())
            ret.append ("cargo", cargo);

        String email = doc.getString ("emailConvidado");
        if (email != null && !email.isBlank ())
            ret.append ("email", email);

        String usuarioId = doc.getString ("usuarioId");
        if (usuarioId != null && !usuarioId.isBlank ())
            ret.append ("usuarioId", usuarioId);

        if (incluirConvite && EquipeMembro.STATUS_CONVITE_PENDENTE.equals (doc.getString ("status")))
        {
            ret.append ("conviteExpiraEm", doc.get ("conviteExpiraEm"));
            String token = doc.getString ("conviteToken");
            if (token != null)
                ret.append ("conviteToken", token);
        }

        return ret;
    }
}