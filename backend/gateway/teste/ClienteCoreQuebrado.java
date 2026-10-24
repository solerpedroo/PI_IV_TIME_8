/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
import util.ClienteCore;

// Cliente do core que sempre falha, para exercitar o tratamento de erro do RotasHandler.
public class ClienteCoreQuebrado extends ClienteCore
{
    public ClienteCoreQuebrado () throws Exception
    {
        super ("http://127.0.0.1:1");
    }

    @Override
    public boolean coreSaudavel ()
    {
        throw new RuntimeException ("falha proposital do teste");
    }
}
