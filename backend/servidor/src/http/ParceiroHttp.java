package http;

/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/*
    Encapsula o socket de um cliente e sabe ler uma requisicao HTTP/1.x
    e escrever uma resposta. O HTTP e todo feito aqui, na mao.
*/
public class ParceiroHttp
{
    public static final int MAXIMO_CABECALHOS = 16 * 1024;   // 16 KB
    public static final int MAXIMO_CORPO      = 1024 * 1024; // 1 MB
    public static final int TEMPO_LIMITE      = 10 * 1000;   // 10 s

    private final Socket       conexao;
    private final InputStream  receptor;
    private final OutputStream transmissor;

    public ParceiroHttp (Socket conexao) throws Exception
    {
        if (conexao == null)
            throw new Exception ("Conexao ausente");

        this.conexao = conexao;
        this.conexao.setSoTimeout (ParceiroHttp.TEMPO_LIMITE);

        this.receptor    = new BufferedInputStream  (conexao.getInputStream  ());
        this.transmissor = new BufferedOutputStream (conexao.getOutputStream ());
    }

    // devolve null se o cliente fechou a conexao sem mandar nada
    public Requisicao lerRequisicao () throws Exception
    {
        String cabeca = this.lerCabeca ();
        if (cabeca == null)
            return null;

        String[] linhas = cabeca.split ("\r?\n");

        // linha de requisicao: METODO ALVO VERSAO
        String[] partes = linhas[0].split (" ", -1);
        if (partes.length != 3)
            throw new ErroHttp (400, "Malformed request line");

        String metodo = partes[0];
        String alvo   = partes[1];
        String versao = partes[2];

        if (!metodo.matches ("[A-Z]{1,16}"))
            throw new ErroHttp (400, "Malformed request line");

        if (!alvo.startsWith ("/"))
            throw new ErroHttp (400, "Malformed request target");

        if (!versao.equals ("HTTP/1.1") && !versao.equals ("HTTP/1.0"))
        {
            if (versao.matches ("HTTP/\\d\\.\\d"))
                throw new ErroHttp (505, "HTTP version not supported");
            throw new ErroHttp (400, "Malformed request line");
        }

        // cabecalhos "Nome: valor"
        Map<String, String> cabecalhos = new HashMap<String, String> ();
        for (int i = 1; i < linhas.length; i++)
        {
            String linha = linhas[i];
            int    dois  = linha.indexOf (':');

            if (dois <= 0)
                throw new ErroHttp (400, "Malformed header");

            String nome  = linha.substring (0, dois).toLowerCase ();
            String valor = linha.substring (dois + 1).trim ();

            // nome nao pode ter espaco (isso tambem barra cabecalho dobrado em varias linhas)
            if (!nome.matches ("[a-z0-9!#$%&'*+.^_`|~-]+"))
                throw new ErroHttp (400, "Malformed header");

            String anterior = cabecalhos.get (nome);
            if (anterior == null)
                cabecalhos.put (nome, valor);
            else if (nome.equals ("content-length") || nome.equals ("host"))
                throw new ErroHttp (400, "Duplicated " + nome + " header");
            else
                cabecalhos.put (nome, anterior + ", " + valor);
        }

        if (versao.equals ("HTTP/1.1") && cabecalhos.get ("host") == null)
            throw new ErroHttp (400, "Host header required");

        if (cabecalhos.get ("transfer-encoding") != null)
            throw new ErroHttp (501, "Transfer-Encoding not supported");

        // corpo pelo Content-Length
        int    tamanho = 0;
        String texto   = cabecalhos.get ("content-length");
        if (texto != null)
        {
            if (!texto.matches ("\\d{1,10}"))
                throw new ErroHttp (400, "Invalid Content-Length");

            long lido = Long.parseLong (texto);
            if (lido > ParceiroHttp.MAXIMO_CORPO)
                throw new ErroHttp (413, "Request body too large");

            tamanho = (int) lido;
        }

        byte[] corpo = this.receptor.readNBytes (tamanho);
        if (corpo.length < tamanho)
            throw new ErroHttp (400, "Incomplete request body");

        // separa caminho e consulta (?a=1&b=2)
        String caminho  = alvo;
        String consulta = "";
        int    marca    = alvo.indexOf ('?');
        if (marca >= 0)
        {
            caminho  = alvo.substring (0, marca);
            consulta = alvo.substring (marca + 1);
        }

        return new Requisicao (metodo, ParceiroHttp.decodificarCaminho (caminho),
                               ParceiroHttp.lerConsulta (consulta), cabecalhos, corpo,
                               this.conexao.getInetAddress ().getHostAddress ());
    }

    // le ate a linha em branco que separa cabecalhos e corpo
    private String lerCabeca () throws Exception
    {
        ByteArrayOutputStream cabeca = new ByteArrayOutputStream ();

        int ultimos = 0; // quatro ultimos bytes lidos, para achar \r\n\r\n ou \n\n
        for (;;)
        {
            int lido = this.receptor.read ();

            if (lido == -1)
            {
                if (cabeca.size () == 0)
                    return null;
                throw new ErroHttp (400, "Incomplete request");
            }

            // ignora linhas em branco antes da linha de requisicao
            if (cabeca.size () == 0 && (lido == '\r' || lido == '\n'))
                continue;

            cabeca.write (lido);
            ultimos = (ultimos << 8) | lido;

            if (cabeca.size () > ParceiroHttp.MAXIMO_CABECALHOS)
                throw new ErroHttp (431, "Request headers too large");

            if (ultimos == 0x0D0A0D0A || (ultimos & 0xFFFF) == 0x0A0A)
                break;
        }

        return cabeca.toString (StandardCharsets.ISO_8859_1).trim ();
    }

    // troca %XX pelos bytes e interpreta como UTF-8; 400 se estiver mal formado
    private static String decodificarCaminho (String caminho) throws ErroHttp
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream ();

        for (int i = 0; i < caminho.length (); i++)
        {
            char c = caminho.charAt (i);

            if (c == '%')
            {
                if (i + 2 >= caminho.length ())
                    throw new ErroHttp (400, "Malformed request target");

                int alto  = Character.digit (caminho.charAt (i + 1), 16);
                int baixo = Character.digit (caminho.charAt (i + 2), 16);
                if (alto < 0 || baixo < 0)
                    throw new ErroHttp (400, "Malformed request target");

                bytes.write (alto * 16 + baixo);
                i += 2;
            }
            else
                bytes.write (c);
        }

        String ret;
        try
        {
            ret = StandardCharsets.UTF_8.newDecoder ()
                                        .onMalformedInput      (CodingErrorAction.REPORT)
                                        .onUnmappableCharacter (CodingErrorAction.REPORT)
                                        .decode (ByteBuffer.wrap (bytes.toByteArray ()))
                                        .toString ();
        }
        catch (CharacterCodingException erro)
        {
            throw new ErroHttp (400, "Malformed request target");
        }

        if (ret.indexOf ('\0') >= 0)
            throw new ErroHttp (400, "Malformed request target");

        return ret;
    }

    private static Map<String, String> lerConsulta (String consulta) throws ErroHttp
    {
        Map<String, String> ret = new HashMap<String, String> ();

        if (consulta.isEmpty ())
            return ret;

        try
        {
            for (String par : consulta.split ("&"))
            {
                if (par.isEmpty ())
                    continue;

                int    igual = par.indexOf ('=');
                String nome  = igual < 0 ? par : par.substring (0, igual);
                String valor = igual < 0 ? ""  : par.substring (igual + 1);

                ret.put (URLDecoder.decode (nome,  StandardCharsets.UTF_8),
                         URLDecoder.decode (valor, StandardCharsets.UTF_8));
            }
        }
        catch (IllegalArgumentException erro)
        {
            throw new ErroHttp (400, "Malformed query string");
        }

        return ret;
    }

    public void enviarResposta (Resposta resposta) throws IOException
    {
        StringBuilder cabeca = new StringBuilder ();

        cabeca.append ("HTTP/1.1 ").append (resposta.getStatus ()).append (' ')
              .append (Resposta.textoDoStatus (resposta.getStatus ())).append ("\r\n");

        Map<String, String> cabecalhos = resposta.getCabecalhos ();
        for (Map.Entry<String, String> cabecalho : cabecalhos.entrySet ())
            cabeca.append (cabecalho.getKey ()).append (": ").append (cabecalho.getValue ()).append ("\r\n");

        if (!cabecalhos.containsKey ("Content-Length"))
            cabeca.append ("Content-Length: ").append (resposta.getCorpo ().length).append ("\r\n");

        cabeca.append ("Connection: close\r\n");
        cabeca.append ("\r\n");

        this.transmissor.write (cabeca.toString ().getBytes (StandardCharsets.ISO_8859_1));
        this.transmissor.write (resposta.getCorpo ());
        this.transmissor.flush ();
    }

    public void fechar ()
    {
        try
        {
            this.conexao.close ();
        }
        catch (IOException erro)
        {} // ja estava fechada
    }
}
