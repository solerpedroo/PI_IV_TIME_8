package protocolo;

/*
    O servidor esta sendo desativado ("desativar" no terminal).
    A Supervisora envia isto na conexao que ainda esta aberta, depois fecha.
    A API, se estiver bloqueada em chamar(), recebe este comunicado e lanca excecao
    para descartar o Cliente e reconectar mais tarde. Nao ha campos: o aviso e o proprio tipo.
*/
public class ComunicadoDeDesligamento extends Comunicado
{
    private static final long serialVersionUID = 1L;
}
