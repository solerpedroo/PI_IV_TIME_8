package servicos;

/*
    Uso: java -cp bin servicos.TesteEnvioSmtp destino@email.com
    Requer SMTP_* no ambiente (carregue o .env antes).
*/
public final class TesteEnvioSmtp
{
    public static void main (String[] args) throws Exception
    {
        if (args.length < 1)
            throw new Exception ("Informe o e-mail de destino.");

        ServicoEmail email = ServicoEmail.doAmbiente ();
        if (email.simulado ())
            throw new Exception ("SMTP_* nao configurado — modo simulado.");

        email.enviarCodigo (args[0], "Teste AgroGestao SMTP", "000000");
        System.out.println ("SMTP ok: mensagem de teste enviada para " + args[0]);
    }
}
