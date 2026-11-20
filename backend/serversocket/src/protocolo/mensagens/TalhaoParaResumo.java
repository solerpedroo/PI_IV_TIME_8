package protocolo.mensagens;

import java.io.Serializable;

/*
    Recorte de um talhao para o resumo. A API leu o documento no banco e mandou so estes dois campos:
    o servidor soma hectares e conta quantos estao com status "Ativo". Nome, id e cultura nao entram.
    Nao e um Comunicado sozinho: viaja dentro de PedidoDeResumoDeTalhoes, por isso so Serializable.
*/
public class TalhaoParaResumo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final double hectares; // area ja gravada; negativo ou NaN vira DADOS_INCONSISTENTES
    private final String status;   // so "Ativo" entra em quantidadeAtivos; os outros contam so no total

    public TalhaoParaResumo (double hectares, String status)
    {
        this.hectares = hectares;
        this.status   = status;
    }

    public double getHectares () { return this.hectares; }
    public String getStatus ()   { return this.status; }
}
