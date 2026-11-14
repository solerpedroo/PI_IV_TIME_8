// Miguel — RA: 25003110
package dados;

import java.util.List;
import org.bson.Document;

/** Membros e convites da equipe por propriedade. */
public interface RepositorioEquipe
{
    List<Document> listar (String propriedadeId) throws Exception;

    Document buscarPorId (String propriedadeId, String id) throws Exception;

    /** Integrante sem login (apenas referência operacional). */
    Document criar (String propriedadeId, String proprietarioId, Document dados) throws Exception;

    /** Gera convite com token para envio manual do link. */
    Document criarConvite (String propriedadeId, String proprietarioId, Document dados) throws Exception;

    Document atualizar (String propriedadeId, String id, Document dados) throws Exception;

    Document regenerarConvite (String propriedadeId, String id) throws Exception;

    boolean excluir (String propriedadeId, String id) throws Exception;

    Document buscarPorConviteToken (String token) throws Exception;

    Document aceitarConvite (String token, String usuarioId, String nomeUsuario, String emailUsuario) throws Exception;

    Document membroAtivoDoUsuario (String propriedadeId, String usuarioId) throws Exception;

    List<String> listarPropriedadesColaborador (String usuarioId) throws Exception;
}