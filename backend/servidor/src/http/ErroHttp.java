package http;

/*
    Erro que ja sabe qual status HTTP deve virar.
    Ex.: throw new ErroHttp (400, "Malformed request");
*/
public class ErroHttp extends Exception
{
    private final int status;

    public ErroHttp (int status, String mensagem)
    {
        super (mensagem);
        this.status = status;
    }

    public int getStatus ()
    {
        return this.status;
    }
}
