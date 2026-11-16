/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package util.comum;

import http.Resposta;
import java.time.LocalDate;
import org.bson.Document;

/*
    Junta os erros de validacao de um corpo JSON, campo a campo, e no fim
    monta a resposta 400 {"error":"Dados inválidos.","fields":{campo: mensagem}}.

    O controlador chama varios metodos (email, senha, texto...) mesmo quando
    um deles ja falhou. Cada um devolve null no campo invalido e guarda a
    mensagem. Assim o front recebe todos os problemas de uma vez e marca
    varios inputs, em vez de um round-trip por campo.

    As mensagens sao as mesmas que a interface mostra. Mudar o texto aqui
    sem mudar o front faz o usuario ver uma frase diferente da que o
    formulario ensaiou.
*/
public class Validacao
{
    private final Document corpo;
    private final Document erros = new Document ();

    /*
        Guarda o JSON ja interpretado. Corpo null e defeito de quem chamou
        (a rota deveria ter lido o JSON antes), por isso e Exception e nao
        um erro de campo.
    */
    public Validacao (Document corpo) throws Exception
    {
        if (corpo == null)
            throw new Exception ("Corpo ausente");

        this.corpo = corpo;
    }

    /*
        Registra a primeira mensagem de cada campo e ignora as seguintes.

        A ordem das chamadas no controlador define qual regra "vence".
        Sem este if, uma checagem generica posterior apagaria a mensagem
        mais especifica que o usuario precisa ler.
    */
    public void erro (String campo, String mensagem)
    {
        if (!this.erros.containsKey (campo))
            this.erros.put (campo, mensagem);
    }

    /*
        Diz se ja existe algum campo reprovado.

        Os metodos de leitura devolvem null tanto para "ausente" quanto para
        "invalido". O controlador usa temErros() (ou a propria resposta) para
        decidir se interrompe, em vez de comparar cada retorno com null.
    */
    public boolean temErros ()
    {
        return !this.erros.isEmpty ();
    }

    /*
        Empacota os erros no formato que o front espera.

        So deve ser chamada quando temErros() e verdadeiro; senao o cliente
        receberia 400 com "fields" vazio, o que nao explica o que corrigir.
    */
    public Resposta resposta ()
    {
        return Resposta.json (400, new Document ("error", "Dados inválidos.").append ("fields", this.erros));
    }

    /*
        Le um texto obrigatorio, sem espacos nas pontas, com tamanho maximo.

        null ou string so de espacos contam como ausente (mensagemAusente).
        Tipo que nao e String (numero, objeto) e "Valor inválido.": o contrato
        da API e texto, e misturar tipos escondia erro de cliente. Acima de
        maximo a mensagem cita o limite, para o usuario saber quanto cortar.
        Devolve null em qualquer falha para o controlador seguir validando
        os outros campos.
    */
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

    /*
        Le o codigo numerico de 6 digitos enviado por e-mail.

        Reaproveita texto(...) com teto 6: mais que 6 caracteres ja falha
        por tamanho. O regex garante que o que sobrou sao exatamente seis
        digitos, nao letras nem um codigo mais curto com espacos.
    */
    public String codigo6 (String campo, String mensagemAusente)
    {
        String ret = this.texto (campo, mensagemAusente, 6);

        if (ret == null)
            return null;

        if (!ret.matches ("\\d{6}"))
        {
            this.erro (campo, "Use o código de 6 dígitos enviado por e-mail.");
            return null;
        }

        return ret;
    }

    /*
        Le uma data obrigatoria no formato AAAA-MM-DD, o mesmo do input type="date".

        O regex so olha a forma. LocalDate.parse recusa dia impossivel
        (2026-02-30) porque o calendario nao o reconhece. O intervalo
        2000-2100 e regra de negocio deste sistema (safra e cadastro
        atuais), nao uma limitacao do tipo LocalDate. Qualquer falha
        vira a mesma mensagem de data invalida.
    */
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

    /*
        Le um e-mail obrigatorio e devolve em minusculas.

        O teto 254 e o comprimento maximo pratico de um endereco. O regex
        e de proposito simples (tem @ e um ponto no dominio, sem espaco):
        a confirmacao de verdade e o codigo enviado a caixa de entrada.
        Minusculas evitam dois usuarios "Ana@x.com" e "ana@x.com".
    */
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

    /*
        Repete as regras do medidor de forca do front
        (frontend/scripts/auth.js, getPasswordChecks).

        Oito caracteres, uma maiuscula (incluindo acentuadas), um digito
        e um simbolo. Se o back exigir algo que a tela nao mostra, o
        usuario cumpre o medidor e mesmo assim recebe erro.
    */
    public static boolean senhaForte (String senha)
    {
        return senha.length () >= 8 &&
               senha.matches (".*[A-ZÀ-Ý].*") &&
               senha.matches (".*\\d.*") &&
               senha.matches (".*[^A-Za-zÀ-ÿ\\d].*");
    }

    /*
        Le a senha sem trim: espaco conta como simbolo na regra de forca.

        String vazia e campo ausente. Tipo errado ou mais de 128 caracteres
        e "Valor inválido.", nao a mensagem de forca: nao adianta aplicar
        senhaForte em lixo, e 128 limita o que segue para o hash. So a senha
        que passou do tamanho e do tipo e medida pelas quatro regras.
    */
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

    /*
        Confere se a segunda digitacao e igual a senha ja validada.

        O front antigo manda "password-confirm"; outro formulario manda
        "confirm". Olhamos a chave que existir. Se a senha original ja
        falhou (senha == null), nao comparamos: o erro util ja esta no
        campo da senha, e dizer "nao coincidem" contra null so confundiria.
    */
    public void confirmacao (String senha)
    {
        String campo = this.corpo.containsKey ("password-confirm") ? "password-confirm" : "confirm";
        Object valor = this.corpo.get (campo);

        if (valor == null || "".equals (valor))
            this.erro (campo, "Confirme sua senha.");
        else if (senha != null && !senha.equals (valor))
            this.erro (campo, "As senhas não coincidem.");
    }

    /*
        Exige um checkbox marcado.

        JSON pode trazer true ou a string "true". Formulario HTML classico
        envia "on" quando o checkbox vai no POST. Qualquer outro valor
        (false, ausente, "yes") conta como desmarcado.
    */
    public void marcado (String campo, String mensagem)
    {
        Object valor = this.corpo.get (campo);

        if (!Boolean.TRUE.equals (valor) && !"true".equals (valor) && !"on".equals (valor))
            this.erro (campo, mensagem);
    }
}
