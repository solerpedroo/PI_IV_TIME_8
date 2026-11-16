package seguranca;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

/*
    Foto de quem está logado naquele token.

    Não é a tabela de sessões (isso fica em Sessoes). Esta classe é só o
    recorte que o handler precisa: qual usuário, qual fazenda, até quando vale
    e em que ordem foi criada. Os campos são final, então várias threads podem
    ler a mesma sessão sem trava.

    expiraEm é um timestamp em milissegundos (System.currentTimeMillis).
    ordem serve para saber qual sessão é a mais nova quando há várias abertas.
*/
public class Sessao
{
    // Hex do _id da coleção usuarios. Os handlers filtram "o que é meu" com este valor.
    private final String usuarioId;
    // Hex do _id da coleção propriedades. Trocar de fazenda cria outra Sessao, não altera esta.
    private final String propriedadeId;
    // Milissegundos absolutos. expirou() compara com o relógio de agora, não guarda duração.
    private final long   expiraEm;
    // Número crescente. Sessoes usa para achar a sessão mais recente do mesmo usuário.
    private final long   ordem;

    // Recusa sessão sem dono ou sem fazenda: o restante da API assume que os dois existem.
    public Sessao (String usuarioId, String propriedadeId, long expiraEm, long ordem) throws Exception
    {
        if (usuarioId == null)
            throw new Exception ("Usuario ausente");

        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        // Nada é copiado do banco aqui. Quem chama (Sessoes.criar) já leu o usuário e a fazenda.
        this.usuarioId     = usuarioId;
        this.propriedadeId = propriedadeId;
        this.expiraEm      = expiraEm;
        this.ordem         = ordem;
    }

    public String getUsuarioId ()
    {
        return this.usuarioId;
    }

    public String getPropriedadeId ()
    {
        return this.propriedadeId;
    }

    // true quando o relógio do servidor já passou do prazo gravado no login.
    public boolean expirou ()
    {
        return System.currentTimeMillis () > this.expiraEm;
    }

    public long getExpiraEm ()
    {
        return this.expiraEm;
    }

    public long getOrdem ()
    {
        return this.ordem;
    }
}
