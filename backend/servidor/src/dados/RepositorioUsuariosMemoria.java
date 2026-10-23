package dados;

/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Usuarios em memoria, para os testes nao dependerem do banco.
    Todos os metodos sao synchronized; sempre devolve copias.
*/
public class RepositorioUsuariosMemoria implements RepositorioUsuarios
{
    private final HashMap<String, Document> usuarios     = new HashMap<String, Document> ();
    private final HashMap<String, Document> propriedades = new HashMap<String, Document> ();

    public synchronized Document criar (Document usuario, Document propriedade) throws Exception
    {
        if (usuario == null)
            throw new Exception ("Usuario ausente");

        if (propriedade == null)
            throw new Exception ("Propriedade ausente");

        if (this.buscarPor ("email", usuario.getString ("email")) != null)
            return null;

        ObjectId idPropriedade = new ObjectId ();
        ObjectId idUsuario     = new ObjectId ();

        Document novaPropriedade = new Document (propriedade).append ("_id", idPropriedade)
                                                        .append ("usuarioId", idUsuario.toHexString ());
        Document novoUsuario     = new Document (usuario).append ("_id", idUsuario)
                                                         .append ("propriedadeId", idPropriedade.toHexString ());

        this.propriedades.put (idPropriedade.toHexString (), novaPropriedade);
        this.usuarios.put     (idUsuario.toHexString (),     novoUsuario);

        return new Document (novoUsuario);
    }

    public synchronized Document buscarPorId (String id) throws Exception
    {
        Document ret = this.usuarios.get (id);
        return ret == null ? null : new Document (ret);
    }

    public synchronized Document buscarPor (String campo, Object valor) throws Exception
    {
        if (campo == null)
            throw new Exception ("Campo ausente");

        for (Document usuario : this.usuarios.values ())
            if (usuario.containsKey (campo) && Objects.equals (usuario.get (campo), valor))
                return new Document (usuario);

        return null;
    }

    public synchronized void atualizar (String id, Document campos) throws Exception
    {
        if (campos == null)
            throw new Exception ("Campos ausentes");

        Document usuario = this.usuarios.get (id);
        if (usuario != null)
            usuario.putAll (campos);
    }

    public synchronized Document buscarPropriedade (String id) throws Exception
    {
        Document ret = this.propriedades.get (id);
        return ret == null ? null : new Document (ret);
    }

    public synchronized ArrayList<Document> listarPropriedades (String usuarioId) throws Exception
    {
        ArrayList<Document> ret = new ArrayList<Document> ();
        for (Document propriedade : this.propriedades.values ())
            if (usuarioId.equals (propriedade.getString ("usuarioId")))
                ret.add (new Document (propriedade));
        return ret;
    }

    public synchronized Document criarPropriedade (String usuarioId, Document propriedade) throws Exception
    {
        if (this.usuarios.get (usuarioId) == null || propriedade == null)
            return null;

        Document criada = new Document (propriedade).append ("_id", new ObjectId ())
                                                   .append ("usuarioId", usuarioId);
        this.propriedades.put (((ObjectId) criada.get ("_id")).toHexString (), criada);
        return new Document (criada);
    }

    public synchronized boolean atualizarPropriedade (String usuarioId, String propriedadeId, Document campos) throws Exception
    {
        Document propriedade = this.propriedades.get (propriedadeId);
        if (propriedade == null || !usuarioId.equals (propriedade.getString ("usuarioId")))
            return false;
        propriedade.putAll (campos);
        return true;
    }

    public synchronized boolean excluirPropriedade (String usuarioId, String propriedadeId) throws Exception
    {
        Document propriedade = this.propriedades.get (propriedadeId);
        if (propriedade == null || !usuarioId.equals (propriedade.getString ("usuarioId")))
            return false;
        this.propriedades.remove (propriedadeId);
        return true;
    }
}
