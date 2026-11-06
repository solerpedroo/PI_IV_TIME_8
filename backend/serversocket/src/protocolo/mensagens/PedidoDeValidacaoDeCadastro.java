package protocolo.mensagens;

import protocolo.Pedido;

/*
    Valida os campos do formulario de criar conta (mesmos nomes do front).
    A API envia o que o usuario digitou, sem tratar nada. Valores nulos contam como "nao informado".
    Unicidade de e-mail NAO e verificada aqui: depende do banco e continua na API.
*/
public class PedidoDeValidacaoDeCadastro extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String  name;
    private final String  email;
    private final String  phone;
    private final String  password;
    private final String  passwordConfirm;
    private final String  farm;
    private final String  area;
    private final boolean terms;

    public PedidoDeValidacaoDeCadastro (String name, String email, String phone, String password,
                                        String passwordConfirm, String farm, String area, boolean terms)
    {
        this.name            = name;
        this.email           = email;
        this.phone           = phone;
        this.password        = password;
        this.passwordConfirm = passwordConfirm;
        this.farm            = farm;
        this.area            = area;
        this.terms           = terms;
    }

    public String  getName ()            { return this.name; }
    public String  getEmail ()           { return this.email; }
    public String  getPhone ()           { return this.phone; }
    public String  getPassword ()        { return this.password; }
    public String  getPasswordConfirm () { return this.passwordConfirm; }
    public String  getFarm ()            { return this.farm; }
    public String  getArea ()            { return this.area; }
    public boolean isTerms ()            { return this.terms; }
}
