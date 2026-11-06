package algoritmos;

import java.util.LinkedHashMap;
import java.util.Map;

/*
    Junta os erros de validacao, campo a campo (guarda so o primeiro erro de cada campo).
    As regras e as mensagens sao as mesmas que o front mostra. Uma instancia por validacao.
*/
public class Validacao
{
    private final LinkedHashMap<String, String> erros = new LinkedHashMap<String, String> ();

    public void erro (String campo, String mensagem)
    {
        if (!this.erros.containsKey (campo))
            this.erros.put (campo, mensagem);
    }

    public boolean temErros ()
    {
        return !this.erros.isEmpty ();
    }

    public Map<String, String> getErros ()
    {
        return new LinkedHashMap<String, String> (this.erros);
    }

    // texto obrigatorio, sem espacos nas pontas; null se invalido
    public String texto (String campo, String valor, String mensagemAusente, int maximo)
    {
        if (valor == null || valor.trim ().isEmpty ())
        {
            this.erro (campo, mensagemAusente);
            return null;
        }

        String ret = valor.trim ();
        if (ret.length () > maximo)
        {
            this.erro (campo, "Use no máximo " + maximo + " caracteres.");
            return null;
        }

        return ret;
    }

    // texto opcional: ausente vale ""; passou do tamanho e erro (e tambem vale "")
    public String textoOpcional (String campo, String valor, int maximo)
    {
        if (valor == null)
            return "";

        String ret = valor.trim ();
        if (ret.length () > maximo)
        {
            this.erro (campo, "Use no máximo " + maximo + " caracteres.");
            return "";
        }

        return ret;
    }

    // e-mail obrigatorio, em minusculas
    public String email (String campo, String valor)
    {
        String ret = this.texto (campo, valor, "Informe seu e-mail.", 254);

        if (ret == null)
            return null;

        if (!ret.matches ("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
        {
            this.erro (campo, "Digite um e-mail válido.");
            return null;
        }

        return ret.toLowerCase ();
    }

    // 8, maiuscula, numero e simbolo: as mesmas de scripts/senha-regras.js.
    // O teto de 128 caracteres e conferido em senha(), antes desta checagem.
    public static boolean senhaForte (String senha)
    {
        return senha.length () >= 8 &&
               senha.matches (".*[A-ZÀ-Ý].*") &&
               senha.matches (".*\\d.*") &&
               senha.matches (".*[^A-Za-zÀ-ÿ\\d].*");
    }

    // senha nao passa por trim: espaco tambem conta como simbolo
    public String senha (String campo, String valor, String mensagemAusente)
    {
        if (valor == null || valor.isEmpty ())
        {
            this.erro (campo, mensagemAusente);
            return null;
        }

        if (valor.length () > 128)
        {
            this.erro (campo, "Valor inválido.");
            return null;
        }

        if (!Validacao.senhaForte (valor))
        {
            this.erro (campo, "Atenda a todas as regras de senha.");
            return null;
        }

        return valor;
    }

    public void confirmacao (String campo, String senha, String confirmacao)
    {
        if (confirmacao == null || confirmacao.isEmpty ())
            this.erro (campo, "Confirme sua senha.");
        else if (senha != null && !senha.equals (confirmacao))
            this.erro (campo, "As senhas não coincidem.");
    }

    // checkbox marcado
    public void marcado (String campo, boolean marcado, String mensagem)
    {
        if (!marcado)
            this.erro (campo, mensagem);
    }
}
