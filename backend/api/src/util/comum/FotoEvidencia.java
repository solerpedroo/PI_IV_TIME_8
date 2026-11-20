/* Autor Principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package util.comum;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.Binary;

/*
    Evidencias fotograficas de campo (#29, #175, #176).

    Decisao de armazenamento (aprovada nesta entrega):
    - Os bytes da imagem ficam no MESMO documento Mongo da ocorrencia ou atividade.
    - O driver BSON representa o array de bytes como org.bson.types.Binary.
    - JSON publico NUNCA inclui os bytes: so metadados (temFoto, mime, nome, tamanho).

    O cliente envia POST .../foto com JSON { conteudoBase64, tipoMime?, nomeArquivo? }.
    Isso mantem o stack HTTP manual (sem multipart) e separa o upload do cadastro,
    de modo que falha no anexo nao desfaz titulo/descricao ja confirmados (#175).

    A classe e final e o construtor e privado porque nao ha estado: so funcoes
    em torno do subdocumento "fotoEvidencia". Instanciar nao faria sentido.
*/
public final class FotoEvidencia
{
    /** Nome do subdocumento BSON onde tudo fica agrupado. */
    public static final String CAMPO_RAIZ = "fotoEvidencia";

    /** Chaves internas do subdocumento — nao expor "dados" na API JSON. */
    public static final String SUB_DADOS         = "dados";
    public static final String SUB_MIME          = "mime";
    public static final String SUB_NOME_ARQUIVO  = "nomeArquivo";
    public static final String SUB_TAMANHO_BYTES = "tamanhoBytes";
    public static final String SUB_ENVIADA_EM    = "enviadaEm";

    /** Limite alinhado a evidencias de campo em rede movel (5 MiB). */
    public static final int TAMANHO_MAXIMO_BYTES = 5 * 1024 * 1024;

    /*
        Tipos aceitos depois da validacao por MIME declarado e pela assinatura
        magica dos bytes. A lista e fechada: SVG e HTML ficam de fora porque
        o navegador poderia executa-los se o Content-Type fosse ecoado.
    */
    public static final List<String> MIME_PERMITIDOS = List.of (
        "image/jpeg", "image/png", "image/webp", "image/gif");

    private FotoEvidencia () { }

    /*
        Monta o subdocumento pronto para $set no Mongo a partir do corpo JSON do upload.

        Nao lanca ErroHttp: preenche Validacao e devolve null quando a imagem
        e recusada (tipo, tamanho, Base64 invalido). O controlador junta esse
        resultado ao restante da validacao e responde 400 no formato padrao.
        enviadaEmIso e gravado junto para a tela mostrar quando a foto chegou,
        sem precisar de outro campo no documento pai.
    */
    public static Document montarSubdocumento (Document corpo, Validacao validacao, String enviadaEmIso) throws Exception
    {
        Object bruto = corpo.get ("conteudoBase64");
        if (!(bruto instanceof String) || ((String) bruto).isBlank ())
        {
            validacao.erro ("conteudoBase64", "Informe a imagem codificada em Base64.");
            return null;
        }

        /*
            readAsDataURL manda "data:image/...;base64,<bytes>". O decodificador
            padrao nao entende esse prefixo, entao ele e removido antes.
        */
        String textoBase64 = limparPrefixoDataUrl (((String) bruto).trim ());
        byte[] bytes;
        try
        {
            bytes = Base64.getDecoder ().decode (textoBase64);
        }
        catch (IllegalArgumentException erro)
        {
            validacao.erro ("conteudoBase64", "Base64 inválido. Selecione o arquivo novamente.");
            return null;
        }

        /*
            Vazio e "maior que 5 MiB" sao erros do mesmo campo. Nao retornamos
            ainda: o MIME tambem e avaliado para o front marcar os dois inputs
            quando os dois estao errados.
        */
        if (bytes.length == 0)
            validacao.erro ("conteudoBase64", "A imagem está vazia.");
        else if (bytes.length > FotoEvidencia.TAMANHO_MAXIMO_BYTES)
            validacao.erro ("conteudoBase64", "A imagem excede 5 MB. Reduza o arquivo ou comprima antes de enviar.");

        /*
            inferirMime devolve null quando o tipo declarado nao esta na lista
            ou quando os bytes nao batem com a assinatura. Confiar so no
            tipoMime permitiria rotular um arquivo qualquer como image/jpeg.
        */
        String mimeDeclarado = inferirMime (corpo, bytes);
        if (mimeDeclarado == null)
            validacao.erro ("tipoMime", "Formato não suportado. Use JPEG, PNG, WebP ou GIF.");

        if (validacao.temErros ())
            return null;

        /*
            O nome do arquivo e metadado, nao criterio para recusar a foto.
            Cortamos em 200 caracteres e descartamos string so de espacos
            para nao gravar lixo ao lado dos bytes que ja passaram.
        */
        String nome = corpo.getString ("nomeArquivo");
        if (nome != null)
        {
            nome = nome.trim ();
            if (nome.length () > 200)
                nome = nome.substring (0, 200);
            if (nome.isEmpty ())
                nome = null;
        }

        /*
            Subdocumento fotoEvidencia, gravado DENTRO do documento da atividade
            ou da ocorrência (campo fotoEvidencia), não numa coleção separada.

            {
              "dados": Binary dos pixels,     // não sai no JSON público
              "tipoMime": "image/png",
              "nomeArquivo": "campo.png" ou null,
              "tamanhoBytes": 1234,
              "enviadaEm": "2026-...Z"
            }

            O GET .../foto lê "dados" e devolve os bytes com Content-Type = tipoMime.
            A listagem usa publicoSemBytes e no lugar manda só "temFoto": true.
        */
        return new Document (FotoEvidencia.SUB_DADOS, new Binary (bytes))
            .append (FotoEvidencia.SUB_MIME, mimeDeclarado)
            .append (FotoEvidencia.SUB_NOME_ARQUIVO, nome)
            .append (FotoEvidencia.SUB_TAMANHO_BYTES, bytes.length)
            .append (FotoEvidencia.SUB_ENVIADA_EM, enviadaEmIso);
    }

    /*
        Remove o cabecalho data:image/...;base64, que alguns navegadores incluem
        ao usar readAsDataURL. O servidor aceita com ou sem esse prefixo.

        indexOf acha a ultima marca estavel ("base64,") em vez de tentar
        interpretar o MIME do proprio prefixo. Se nao houver prefixo, o texto
        ja e o Base64 puro e volta igual.
    */
    public static String limparPrefixoDataUrl (String valor)
    {
        int indice = valor.indexOf ("base64,");
        if (indice >= 0)
            return valor.substring (indice + "base64,".length ());
        return valor;
    }

    /*
        Descobre o MIME que vamos gravar.

        Prioridade: tipoMime explicito no JSON, desde que esteja na lista branca
        E os bytes confiram a assinatura. Sem tipo no JSON, tentamos os quatro
        formatos na ordem. Nos dois caminhos, arquivo que nao "parece" a imagem
        anunciada devolve null e o chamador registra o erro de formato.
    */
    private static String inferirMime (Document corpo, byte[] bytes)
    {
        String declarado = corpo.getString ("tipoMime");
        if (declarado != null)
        {
            declarado = declarado.trim ().toLowerCase (Locale.ROOT);
            if (FotoEvidencia.MIME_PERMITIDOS.contains (declarado) && assinaturaCompativel (bytes, declarado))
                return declarado;
            return null;
        }

        if (assinaturaCompativel (bytes, "image/jpeg")) return "image/jpeg";
        if (assinaturaCompativel (bytes, "image/png"))  return "image/png";
        if (assinaturaCompativel (bytes, "image/gif"))  return "image/gif";
        if (assinaturaCompativel (bytes, "image/webp")) return "image/webp";
        return null;
    }

    /*
        Confere os primeiros bytes conhecidos de cada formato (assinatura magica).

        Menos de 12 bytes nao cobre o cabecalho do WebP (RIFF....WEBP), entao
        recusamos cedo. O & 0xFF no JPEG evita que o byte 0xFF, que em Java e
        negativo quando lido como byte com sinal, falhe na comparacao com 0xFF.
    */
    private static boolean assinaturaCompativel (byte[] bytes, String mime)
    {
        if (bytes == null || bytes.length < 12)
            return false;

        switch (mime)
        {
            case "image/jpeg":
                return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
            case "image/png":
                return bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
            case "image/gif":
                return bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
            case "image/webp":
                return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default:
                return false;
        }
    }

    /*
        Diz se o registro persistido ja tem bytes de foto.

        Binary com comprimento zero conta como ausente: a interface nao deve
        montar o link GET .../foto para uma imagem vazia. Documento ou
        subdocumento nulo tambem e "sem foto", nao erro.
    */
    public static boolean possuiFoto (Document registro)
    {
        if (registro == null)
            return false;
        Document bloco = registro.get (FotoEvidencia.CAMPO_RAIZ, Document.class);
        if (bloco == null)
            return false;
        Object dados = bloco.get (FotoEvidencia.SUB_DADOS);
        if (dados instanceof Binary)
            return ((Binary) dados).getData ().length > 0;
        return false;
    }

    /*
        Extrai os bytes para a resposta HTTP da rota GET .../foto.

        Diferente de possuiFoto, aqui a ausencia e erro: a rota ja decidiu
        devolver a imagem. Tipo que nao e Binary indica documento corrompido
        (alguem gravou a foto como string ou a chave sumiu).
    */
    public static byte[] extrairBytes (Document registro) throws Exception
    {
        Document bloco = registro.get (FotoEvidencia.CAMPO_RAIZ, Document.class);
        if (bloco == null)
            throw new Exception ("Este registro não possui foto anexada.");
        Object dados = bloco.get (FotoEvidencia.SUB_DADOS);
        if (!(dados instanceof Binary))
            throw new Exception ("Foto corrompida ou ausente no banco.");
        return ((Binary) dados).getData ();
    }

    /*
        Escolhe o Content-Type ao servir a imagem.

        Se o MIME gravado sumiu ou nao esta na lista branca, devolvemos
        application/octet-stream. Ecoar um tipo arbitrario do banco
        permitiria que um valor antigo (text/html, image/svg+xml) fosse
        interpretado pelo navegador.
    */
    public static String extrairMime (Document registro) throws Exception
    {
        Document bloco = registro.get (FotoEvidencia.CAMPO_RAIZ, Document.class);
        if (bloco == null)
            throw new Exception ("Este registro não possui foto anexada.");
        String mime = bloco.getString (FotoEvidencia.SUB_MIME);
        if (mime == null || !FotoEvidencia.MIME_PERMITIDOS.contains (mime))
            return "application/octet-stream";
        return mime;
    }

    /*
        Copia o documento para a resposta JSON: tira o subdocumento inteiro
        (onde estao os bytes) e publica metadados para a UI montar o link
        GET .../foto.

        ocultosAdicionais cobre outros campos sensiveis do registro pai
        (nao so a foto). temFoto sai sempre, mesmo falso, para o front nao
        precisar testar ausencia da chave. Mime, nome, tamanho e data so
        acompanham quando ha bytes de verdade.
    */
    public static Document publicoSemBytes (Document registro, String... ocultosAdicionais)
    {
        Document copia = new Document ();
        for (String chave : registro.keySet ())
        {
            if (chave.equals (FotoEvidencia.CAMPO_RAIZ))
                continue;
            if (List.of (ocultosAdicionais).contains (chave))
                continue;
            copia.put (chave, registro.get (chave));
        }

        boolean tem = FotoEvidencia.possuiFoto (registro);
        copia.put ("temFoto", tem);
        if (tem)
        {
            Document bloco = registro.get (FotoEvidencia.CAMPO_RAIZ, Document.class);
            copia.append ("fotoMime", bloco.getString (FotoEvidencia.SUB_MIME))
                 .append ("fotoNomeArquivo", bloco.get (FotoEvidencia.SUB_NOME_ARQUIVO))
                 .append ("fotoTamanhoBytes", bloco.get (FotoEvidencia.SUB_TAMANHO_BYTES))
                 .append ("fotoEnviadaEm", bloco.getString (FotoEvidencia.SUB_ENVIADA_EM));
        }
        return copia;
    }

    /*
        Impede foto embutida no POST/PUT de cadastro.

        O anexo tem rota propria (/foto) de proposito: se a imagem falha,
        titulo e descricao ja confirmados nao sao desfeitos (#175). Aceitar
        conteudoBase64, foto, fotoUrl ou o proprio subdocumento no cadastro
        furaria essa separacao. O erro vai no campo "vinculos", que o
        formulario de cadastro ja sabe exibir.
    */
    public static void rejeitarCamposDeUploadNoCadastro (Document corpo, Validacao validacao)
    {
        for (String proibido : List.of ("foto", "fotoUrl", "conteudoBase64", FotoEvidencia.CAMPO_RAIZ))
            if (corpo.containsKey (proibido))
                validacao.erro ("vinculos", "Use POST /api/.../foto para anexar imagem após salvar o cadastro.");
    }
}
