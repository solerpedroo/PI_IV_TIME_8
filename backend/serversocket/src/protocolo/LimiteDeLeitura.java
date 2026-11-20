package protocolo;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

// Limita bytes lidos por mensagem serializada (protege contra payloads enormes na rede).
// ObjectInputStream le por read() e read(byte[], int, int); os dois passam por contar().
// O contador nao zera entre mensagens: o teto vale para o que ja entrou nesta conexao desde a abertura do stream.
class LimiteDeLeitura extends FilterInputStream
{
    private long lidos;
    private final long maximo;

    LimiteDeLeitura (InputStream origem, long maximo)
    {
        super (origem);
        this.maximo = maximo;
    }

    private void contar (int bytes) throws IOException
    {
        if (bytes <= 0)
            return;

        this.lidos += bytes;

        if (this.lidos > this.maximo)
            throw new IOException ("Mensagem excede " + this.maximo + " bytes");
    }

    @Override
    public int read () throws IOException
    {
        int ret = super.read ();
        this.contar (ret == -1 ? 0 : 1);
        return ret;
    }

    @Override
    public int read (byte[] buffer, int offset, int length) throws IOException
    {
        int ret = super.read (buffer, offset, length);
        this.contar (ret == -1 ? 0 : ret);
        return ret;
    }
}
