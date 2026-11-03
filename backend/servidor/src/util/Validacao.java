/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package util;

import http.Resposta;
import java.time.LocalDate;
import org.bson.Document;

/*
    Junta os erros de validacao de um corpo JSON, campo a campo,
    e monta a resposta 400 {"error":"Validation failed","fields":{campo: mensagem}}.
    As mensagens sao as mesmas que o front mostra.
*/
public class Validacao
{
    private final Document corpo;
    private final Document erros = new Document ();

    public Validacao (Document corpo) throws Exception
    {
        if (corpo == null)
            throw new Exception ("Corpo ausente");

        this.corpo = corpo;
    }

    // guarda so o primeiro erro de cada campo
    public void erro (String campo, String mensagem)
    {
        if (!this.erros.containsKey (campo))
            this.erros.put (campo, mensagem);
    }

    public boolean temErros ()
    {
        return !this.erros.isEmpty ();
    }

    public Resposta resposta ()
    {
        return Resposta.json (400, new Document ("error", "Validation failed").append ("fields", this.erros));
    }

    // texto obrigatorio, sem espacos nas pontas; null se invalido
    public String texto (String campo, String mensagemAusente, int maximo)
    {
        Object valor = this.corpo.get (campo);

        if (valor == null || (valor instanceof String && ((String) valor).trim ().isEmpty ()))
        {
            this.erro (campo, mensagemAusente);
            return null;
        }

        if (!(valor instanceof String))
        {
            this.erro (campo, "Valor inválido.");
            return null;
        }

        String ret = ((String) valor).trim ();
        if (ret.length () > maximo)
        {
            this.erro (campo, "Use no máximo " + maximo + " caracteres.");
            return null;
        }

        return ret;
    }

    // data obrigatoria no formato AAAA-MM-DD (o mesmo do input type="date"); null se invalida
    public String data (String campo, String mensagemAusente)
    {
        String ret = this.texto (campo, mensagemAusente, 40);

        if (ret == null)
            return null;

        try
        {
            if (!ret.matches ("\\d{4}-\\d{2}-\\d{2}"))
                throw new Exception ("Formato de data invalido");

            int ano = LocalDate.parse (ret).getYear (); // recusa 2026-02-30
            if (ano < 2000 || ano > 2100)
                throw new Exception ("Ano fora do intervalo");
        }
        catch (Exception erro)
        {
            this.erro (campo, "Informe uma data v\u00e1lida.");
            ret = null;
        }

        return ret;
    }

    // e-mail obrigatorio, em minusculas
    public String email (String campo)
    {
        String ret = this.texto (campo, "Informe seu e-mail.", 254);

        if (ret == null)
            return null;

        if (!ret.matches ("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
        {
            this.erro (campo, "Digite um e-mail válido.");
            return null;
        }

        return ret.toLowerCase ();
    }

    // mesmas regras do medidor de forca do front (frontend/scripts/auth.js, getPasswordChecks)
    public static boolean senhaForte (String senha)
    {
        return senha.length () >= 8 &&
               senha.matches (".*[A-ZÀ-Ý].*") &&
               senha.matches (".*\\d.*") &&
               senha.matches (".*[^A-Za-zÀ-ÿ\\d].*");
    }

    // senha nao passa por trim: espaco tambem conta como simbolo
    public String senha (String campo, String mensagemAusente)
    {
        Object valor = this.corpo.get (campo);

        if (valor == null || "".equals (valor))
        {
            this.erro (campo, mensagemAusente);
            return null;
        }

        if (!(valor instanceof String) || ((String) valor).length () > 128)
        {
            this.erro (campo, "Valor inválido.");
            return null;
        }

        String ret = (String) valor;
        if (!Validacao.senhaForte (ret))
        {
            this.erro (campo, "Atenda a todas as regras de senha.");
            return null;
        }

        return ret;
    }

    // confirmacao da senha: aceita "password-confirm" (nome no front) ou "confirm"
    public void confirmacao (String senha)
    {
        String campo = this.corpo.containsKey ("password-confirm") ? "password-confirm" : "confirm";
        Object valor = this.corpo.get (campo);

        if (valor == null || "".equals (valor))
            this.erro (campo, "Confirme sua senha.");
        else if (senha != null && !senha.equals (valor))
            this.erro (campo, "As senhas não coincidem.");
    }

    // checkbox marcado: true, "true" ou "on" (valor padrao de checkbox em formulario)
    public void marcado (String campo, String mensagem)
    {
        Object valor = this.corpo.get (campo);

        if (!Boolean.TRUE.equals (valor) && !"true".equals (valor) && !"on".equals (valor))
            this.erro (campo, mensagem);
    }
}
