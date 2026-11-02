package protocolo.mensagens;

import protocolo.Pedido;

// Valida uma nova senha e a confirmacao (redefinir senha). Mesmas regras do cadastro.
public class PedidoDeValidacaoDeSenha extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String password;
    private final String passwordConfirm;

    public PedidoDeValidacaoDeSenha (String password, String passwordConfirm)
    {
        this.password        = password;
        this.passwordConfirm = passwordConfirm;
    }

    public String getPassword ()        { return this.password; }
    public String getPasswordConfirm () { return this.passwordConfirm; }
}
