// Miguel — RA: 25003110
package util;

import dados.RepositorioEquipe;
import dados.RepositorioUsuarios;
import org.bson.Document;

/** Distingue dono da propriedade e colaborador convidado. */
public final class PapelNaPropriedade
{
    public static final String PROPRIETARIO  = "proprietario";
    public static final String COLABORADOR   = "colaborador";

    private PapelNaPropriedade () {}

    public static boolean ehProprietario (RepositorioUsuarios usuarios, String propriedadeId, String usuarioId) throws Exception
    {
        if (usuarios == null || propriedadeId == null || usuarioId == null)
            return false;

        Document propriedade = usuarios.buscarPropriedade (propriedadeId);
        return propriedade != null && usuarioId.equals (propriedade.getString ("usuarioId"));
    }

    public static Document colaboracaoAtiva (RepositorioEquipe equipe, String propriedadeId, String usuarioId) throws Exception
    {
        if (equipe == null || propriedadeId == null || usuarioId == null)
            return null;

        return equipe.membroAtivoDoUsuario (propriedadeId, usuarioId);
    }

    public static boolean podeAcessar (RepositorioUsuarios usuarios, RepositorioEquipe equipe,
                                       String propriedadeId, String usuarioId) throws Exception
    {
        return PapelNaPropriedade.ehProprietario (usuarios, propriedadeId, usuarioId)
            || PapelNaPropriedade.colaboracaoAtiva (equipe, propriedadeId, usuarioId) != null;
    }
}