package protocolo.mensagens;

import protocolo.Pedido;

/*
    Valida uma nova senha e a confirmacao. Usado na redefinicao e na troca de senha do perfil.
    As regras sao as mesmas do cadastro (Validacao.senha): 8 a 128, maiuscula, numero e simbolo.
    A senha atual NAO vem aqui: conferir a senha antiga e da API, que tem o hash.
    Os nomes dos campos seguem o front (password / password-confirm); a API remapeia "new"/"confirm".
    Nao ha trim: espaco conta como simbolo.
*/
public class PedidoDeValidacaoDeSenha extends Pedido
{
    private static final long serialVersionUID = 1L;

    private final String password;        // senha nova, como digitada
    private final String passwordConfirm; // precisa ser igual a password; senao o erro vai em password-confirm

    public PedidoDeValidacaoDeSenha (String password, String passwordConfirm)
    {
        this.password        = password;
        this.passwordConfirm = passwordConfirm;
    }

    public String getPassword ()        { return this.password; }
    public String getPasswordConfirm () { return this.passwordConfirm; }
}
