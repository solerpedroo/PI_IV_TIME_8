package http;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
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
    e escrever uma resposta. Nao ha biblioteca de servidor: o HTTP e feito
    aqui, byte a byte, para o grupo enxergar o protocolo em vez de esconde-lo
    atras de um framework.

    Uma conexao atende uma requisicao e depois e fechada (Connection: close).
    Isso simplifica o ciclo de vida: nao ha fila de pedidos no mesmo socket
    nem corpo de uma requisicao vazando para a seguinte.
*/
public class ParceiroHttp
{
    /*
        Teto da linha de requisicao mais os cabecalhos, em bytes.

        Sem limite, um cliente pode segurar a thread enviando cabecalhos e
        nunca mandar a linha em branco que termina o bloco. 16 KB cabe em
        qualquer chamada legitima desta API e ainda barra esse abuso.
    */
    public static final int MAXIMO_CABECALHOS = 16 * 1024;   // 16 KB

    /*
        Teto do corpo na maioria das rotas (login, cadastro, JSON comum).

        1 MB e folgado para documentos de texto e impede que um POST enorme
        encha a memoria do processo antes da validacao de negocio.
    */
    public static final int MAXIMO_CORPO      = 1024 * 1024; // 1 MB (JSON comum)

    /*
        POST .../foto traz a imagem em Base64, que ocupa cerca de 4/3 do binario.
        FotoEvidencia.TAMANHO_MAXIMO_BYTES e 5 MiB de imagem; 8 MiB de corpo
        deixam margem para o JSON em volta do Base64 sem aceitar upload arbitrario.
    */
    public static final int MAXIMO_CORPO_FOTO_EVIDENCIA = 8 * 1024 * 1024;

    /*
        Tempo maximo, em milissegundos, que o socket espera por dados.

        setSoTimeout usa este valor. Se o cliente parar no meio da requisicao,
        a thread nao fica bloqueada para sempre.
    */
    public static final int TEMPO_LIMITE      = 10 * 1000;   // 10 s

    /*
        Escolhe o teto do corpo conforme o caminho, antes de ler os bytes.

        So a rota de foto precisa do limite maior. As demais continuam em 1 MB
        para um JSON comum nao herdar a folga do upload. O caminho ja vem sem
        a query string, por isso endsWith("/foto") basta.
    */
    public static int limiteCorpoBytes (String caminho)
    {
        if (caminho != null && caminho.endsWith ("/foto"))
            return ParceiroHttp.MAXIMO_CORPO_FOTO_EVIDENCIA;
        return ParceiroHttp.MAXIMO_CORPO;
    }

    private final Socket       conexao;
    private final InputStream  receptor;
    private final OutputStream transmissor;

    /*
        Amarra os fluxos do socket e arma o tempo limite de leitura.

        BufferedInputStream e BufferedOutputStream evitam uma chamada de
        sistema por byte. O timeout vale para esta conexao inteira: tanto
        a espera do proximo pedido quanto a leitura do corpo.
    */
    public ParceiroHttp (Socket conexao) throws Exception
    {
        if (conexao == null)
            throw new Exception ("Conexao ausente");

        this.conexao = conexao;
        this.conexao.setSoTimeout (ParceiroHttp.TEMPO_LIMITE);

        this.receptor    = new BufferedInputStream  (conexao.getInputStream  ());
        this.transmissor = new BufferedOutputStream (conexao.getOutputStream ());
    }

    /*
        Le uma requisicao completa e devolve o objeto que as rotas consomem.

        Retorna null se o cliente fechou o socket sem enviar nada (comum em
        sondagem de conexao). Qualquer mensagem pela metade ou fora das
        regras do HTTP/1.0 e HTTP/1.1 vira ErroHttp com o status correspondente,
        para o servidor responder em vez de derrubar a thread.
    */
    public Requisicao lerRequisicao () throws Exception
    {
        String cabeca = this.lerCabeca ();
        if (cabeca == null)
            return null;

        String[] linhas = cabeca.split ("\r?\n");

        /*
            A primeira linha tem exatamente tres campos separados por espaco:
            METODO, alvo (caminho + consulta) e versao. split com limite -1
            nao descarta campos vazios, entao "GET  HTTP/1.1" (alvo vazio)
            nao passa batido como se tivesse dois pedacos.
        */
        String[] partes = linhas[0].split (" ", -1);
        if (partes.length != 3)
            throw new ErroHttp (400, "Linha da requisição malformada.");

        String metodo = partes[0];
        String alvo   = partes[1];
        String versao = partes[2];

        /*
            Metodo so com letras maiusculas e ate 16 caracteres. Isso recusa
            lixo binario na primeira linha sem precisar de uma lista fechada
            de verbos (o roteador e quem decide se GET/POST/etc. existe).
        */
        if (!metodo.matches ("[A-Z]{1,16}"))
            throw new ErroHttp (400, "Linha da requisição malformada.");

        /*
            O alvo de uma requisicao origin-form comeca com '/'. Recusar o
            resto evita tratar URL absoluta ou autoridade (formato de proxy)
            como caminho interno.
        */
        if (!alvo.startsWith ("/"))
            throw new ErroHttp (400, "Destino da requisição malformado.");

        /*
            So HTTP/1.0 e HTTP/1.1 sao implementados. Uma versao no formato
            HTTP/n.n porem desconhecida (ex.: HTTP/2.0 escrita na linha, o
            que este parser de texto nao fala) e 505. Qualquer outra coisa
            na posicao da versao e linha malformada, 400.
        */
        if (!versao.equals ("HTTP/1.1") && !versao.equals ("HTTP/1.0"))
        {
            if (versao.matches ("HTTP/\\d\\.\\d"))
                throw new ErroHttp (505, "Versão HTTP não suportada.");
            throw new ErroHttp (400, "Linha da requisição malformada.");
        }

        /*
            Cada linha seguinte e "Nome: valor". O nome e guardado em
            minusculas para a busca depois ser case-insensitive.
        */
        Map<String, String> cabecalhos = new HashMap<String, String> ();
        for (int i = 1; i < linhas.length; i++)
        {
            String linha = linhas[i];
            int    dois  = linha.indexOf (':');

            /*
                Precisa haver ':' e o nome nao pode ser vazio (dois <= 0).
                Linha sem separador nao e cabecalho.
            */
            if (dois <= 0)
                throw new ErroHttp (400, "Cabeçalho malformado.");

            String nome  = linha.substring (0, dois).toLowerCase ();
            String valor = linha.substring (dois + 1).trim ();

            /*
                O nome segue o token do HTTP (letras, digitos e alguns simbolos).
                Espaco no nome tambem barra o "line folding" antigo, em que a
                continuacao de um cabecalho vinha na linha de baixo com espaco.
                Este servidor nao junta essas linhas; prefere recusar.
            */
            if (!nome.matches ("[a-z0-9!#$%&'*+.^_`|~-]+"))
                throw new ErroHttp (400, "Cabeçalho malformado.");

            /*
                Cabecalho repetido: Content-Length e Host nao podem ser
                juntados com virgula, porque cada um precisa de um unico
                valor. Dois Content-Length diferentes sao um classico de
                contrabando de requisicao (o proxy le um tamanho e o
                servidor le outro). Os demais cabecalhos seguem a regra
                do HTTP de combinar os valores com ", ".
            */
            String anterior = cabecalhos.get (nome);
            if (anterior == null)
                cabecalhos.put (nome, valor);
            else if (nome.equals ("content-length") || nome.equals ("host"))
                throw new ErroHttp (400, "Cabeçalho duplicado: " + nome);
            else
                cabecalhos.put (nome, anterior + ", " + valor);
        }

        /*
            HTTP/1.1 exige Host para saber a que sitio a mensagem se destina.
            HTTP/1.0 nao tem essa obrigacao, por isso o teste olha a versao.
        */
        if (versao.equals ("HTTP/1.1") && cabecalhos.get ("host") == null)
            throw new ErroHttp (400, "O cabeçalho Host é obrigatório.");

        /*
            Transfer-Encoding (em geral "chunked") mudaria a forma de ler o
            corpo. Como so sabemos ler pelo Content-Length, recusamos com 501
            em vez de interpretar o corpo errado.
        */
        if (cabecalhos.get ("transfer-encoding") != null)
            throw new ErroHttp (501, "Transfer-Encoding não é suportado.");

        /*
            Separa caminho e consulta antes de decidir o tamanho maximo do
            corpo. O limite da foto depende do caminho, nao da query.
        */
        String caminho  = alvo;
        String consulta = "";
        int    marca    = alvo.indexOf ('?');
        if (marca >= 0)
        {
            caminho  = alvo.substring (0, marca);
            consulta = alvo.substring (marca + 1);
        }
        caminho = ParceiroHttp.decodificarCaminho (caminho);
        int maxCorpo = ParceiroHttp.limiteCorpoBytes (caminho);

        /*
            Sem Content-Length o corpo tem tamanho zero (tipico de GET).
            Com o cabecalho, o numero precisa ser decimal, de ate 10 digitos,
            e nao pode passar do teto da rota. 413 avisa que o servidor
            entendeu o pedido e recusou o tamanho, em vez de 400 generico.
        */
        int    tamanho = 0;
        String texto   = cabecalhos.get ("content-length");
        if (texto != null)
        {
            if (!texto.matches ("\\d{1,10}"))
                throw new ErroHttp (400, "Content-Length inválido.");

            long lido = Long.parseLong (texto);
            if (lido > maxCorpo)
                throw new ErroHttp (413, "Corpo da requisição grande demais.");

            tamanho = (int) lido;
        }

        /*
            readNBytes pode voltar menos bytes se o cliente desconectar.
            Corpo curto nao e JSON valido; e requisicao incompleta.
        */
        byte[] corpo = this.receptor.readNBytes (tamanho);
        if (corpo.length < tamanho)
            throw new ErroHttp (400, "Corpo da requisição incompleto.");

        return new Requisicao (metodo, caminho,
                               ParceiroHttp.lerConsulta (consulta), cabecalhos, corpo,
                               this.conexao.getInetAddress ().getHostAddress ());
    }

    /*
        Le os bytes ate a linha em branco que separa cabecalhos e corpo.

        O corpo nao entra aqui: ele e lido depois, ja com o tamanho conhecido.
        Devolve null quando o cliente fecha sem ter enviado nenhum byte, para
        o servidor encerrar a conexao em silencio. Se ja havia bytes e o
        socket acaba antes do separador, a requisicao esta incompleta (400).
    */
    private String lerCabeca () throws Exception
    {
        ByteArrayOutputStream cabeca = new ByteArrayOutputStream ();

        /*
            ultimos guarda os quatro bytes mais recentes. Deslocar 8 bits e
            juntar o byte novo forma uma janela deslizante: da para detectar
            \r\n\r\n (0x0D0A0D0A) ou \n\n (0x0A0A) sem guardar a mensagem
            inteira so para procurar o fim.
        */
        int ultimos = 0; // quatro ultimos bytes lidos, para achar \r\n\r\n ou \n\n
        for (;;)
        {
            int lido = this.receptor.read ();

            /*
                Fim do socket. Sem nenhum byte util, nao ha requisicao.
                Com bytes pela metade, o cliente desistiu no meio.
            */
            if (lido == -1)
            {
                if (cabeca.size () == 0)
                    return null;
                throw new ErroHttp (400, "Requisição incompleta.");
            }

            /*
                Alguns clientes mandam CRLF extra antes da linha de pedido.
                Enquanto o buffer ainda esta vazio, esses bytes sao ignorados
                para a primeira linha real comecar limpa.
            */
            if (cabeca.size () == 0 && (lido == '\r' || lido == '\n'))
                continue;

            cabeca.write (lido);
            ultimos = (ultimos << 8) | lido;

            /*
                431 e o status previsto para cabecalhos grandes demais.
                Interromper aqui evita encher a memoria so com o cabecalho.
            */
            if (cabeca.size () > ParceiroHttp.MAXIMO_CABECALHOS)
                throw new ErroHttp (431, "Cabeçalhos da requisição grandes demais.");

            if (ultimos == 0x0D0A0D0A || (ultimos & 0xFFFF) == 0x0A0A)
                break;
        }

        /*
            ISO-8859-1 mapeia cada byte 0-255 para um char, sem erro de UTF-8.
            Cabecalho HTTP e historicamente essa sequencia de bytes, nao texto
            Unicode. trim() tira a linha em branco final que marcou o fim.
        */
        return cabeca.toString (StandardCharsets.ISO_8859_1).trim ();
    }

    /*
        Troca cada %XX pelos bytes correspondentes e so entao interpreta UTF-8.

        Nao usamos URLDecoder no caminho: ele converte '+' em espaco, regra da
        query string, nao do path. Um nome de recurso com '+' ficaria errado.
        O decodificador de charset esta em REPORT para rejeitar UTF-8 quebrado
        com 400, em vez de inserir o caractere de substituicao. O byte nulo
        tambem e recusado: em varias APIs ele trunca a string e esconde o
        resto do caminho.
    */
    private static String decodificarCaminho (String caminho) throws ErroHttp
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream ();

        for (int i = 0; i < caminho.length (); i++)
        {
            char c = caminho.charAt (i);

            if (c == '%')
            {
                /*
                    % precisa de exatamente dois digitos hexadecimais depois.
                    Faltar digito ou usar letra fora de 0-9A-F e caminho invalido.
                */
                if (i + 2 >= caminho.length ())
                    throw new ErroHttp (400, "Destino da requisição malformado.");

                int alto  = Character.digit (caminho.charAt (i + 1), 16);
                int baixo = Character.digit (caminho.charAt (i + 2), 16);
                if (alto < 0 || baixo < 0)
                    throw new ErroHttp (400, "Destino da requisição malformado.");

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
            throw new ErroHttp (400, "Destino da requisição malformado.");
        }

        if (ret.indexOf ('\0') >= 0)
            throw new ErroHttp (400, "Destino da requisição malformado.");

        return ret;
    }

    /*
        Quebra a query string em pares nome/valor.

        URLDecoder aqui e o certo: na consulta, '+' significa espaco e %XX
        tambem e decodificado. Consulta vazia devolve mapa vazio, nao null,
        para Requisicao nao precisar tratar ausencia. % incompleto faz o
        decoder lancar IllegalArgumentException, convertida em 400.
    */
    private static Map<String, String> lerConsulta (String consulta) throws ErroHttp
    {
        Map<String, String> ret = new HashMap<String, String> ();

        if (consulta.isEmpty ())
            return ret;

        try
        {
            for (String par : consulta.split ("&"))
            {
                /*
                    "a=1&&b=2" gera um pedaco vazio entre os &. Ignorar
                    evita criar uma chave em branco.
                */
                if (par.isEmpty ())
                    continue;

                /*
                    Sem '=', o par e uma flag (nome presente, valor vazio).
                    Com '=', so o primeiro '=' separa: o valor pode conter
                    outro '='.
                */
                int    igual = par.indexOf ('=');
                String nome  = igual < 0 ? par : par.substring (0, igual);
                String valor = igual < 0 ? ""  : par.substring (igual + 1);

                ret.put (URLDecoder.decode (nome,  StandardCharsets.UTF_8),
                         URLDecoder.decode (valor, StandardCharsets.UTF_8));
            }
        }
        catch (IllegalArgumentException erro)
        {
            throw new ErroHttp (400, "Consulta malformada.");
        }

        return ret;
    }

    /*
        Escreve a resposta HTTP/1.1 no socket e descarrega o buffer.

        A ordem e a do protocolo: linha de status, cabecalhos, linha em
        branco, corpo. Se a Resposta nao trouxe Content-Length, calculamos
        pelo tamanho do array para o cliente saber onde o corpo acaba.
        Connection: close combina com o modelo de uma requisicao por
        conexao; o cliente nao deve reaproveitar o socket. O cabecalho sai
        em ISO-8859-1 pelo mesmo motivo da leitura: byte a byte, sem UTF-8.
    */
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

    /*
        Fecha o socket ao terminar a troca (ou ao desistir dela).

        A excecao e engolida de proposito: se a outra ponta ja desconectou,
        close lanca IOException e nao ha nada util a fazer no encerramento.
    */
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
