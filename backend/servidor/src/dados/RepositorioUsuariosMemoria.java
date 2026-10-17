package dados;

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

        Document novaPropriedade = new Document (propriedade).append ("_id", idPropriedade);
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
}
