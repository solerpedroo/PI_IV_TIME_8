package dados;

/*
    Cadastro transacional: indice unico em email ou em (proprietarioId, nome) da propriedade.
*/
public final class ConflitoDeCadastro extends Exception
{
    private final String campo;

    public ConflitoDeCadastro (String campo)
    {
        super (campo);
        this.campo = campo;
    }

    public String getCampo ()
    {
        return this.campo;
    }
}
