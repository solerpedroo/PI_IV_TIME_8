package protocolo;

import java.io.Serializable;

/*
    Tudo que trafega entre a API e o servidor e um Comunicado.
    Cada subclasse declara seu serialVersionUID: a API e o servidor sao compilados em separado
    e, sem ele, qualquer diferenca de compilacao quebraria a conversa.
*/
public abstract class Comunicado implements Serializable
{
    private static final long serialVersionUID = 1L;
}
