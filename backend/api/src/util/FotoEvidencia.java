/* Autor Principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package util;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.Binary;

/*
    Evidências fotográficas de campo (#29, #175, #176).

    Decisão de armazenamento (aprovada nesta entrega):
    - Os bytes da imagem ficam no MESMO documento Mongo da ocorrência ou atividade.
    - O driver BSON representa o array de bytes como org.bson.types.Binary.
    - JSON público NUNCA inclui os bytes: só metadados (temFoto, mime, nome, tamanho).

    O cliente envia POST .../foto com JSON { conteudoBase64, tipoMime?, nomeArquivo? }.
    Isso mantém o stack HTTP manual (sem multipart) e separa o upload do cadastro,
    de modo que falha no anexo não desfaz título/descrição já confirmados (#175).
*/
public final class FotoEvidencia
{
    /** Nome do subdocumento BSON onde tudo fica agrupado. */
    public static final String CAMPO_RAIZ = "fotoEvidencia";

    /** Chaves internas do subdocumento — não expor "dados" na API JSON. */
    public static final String SUB_DADOS         = "dados";
    public static final String SUB_MIME          = "mime";
    public static final String SUB_NOME_ARQUIVO  = "nomeArquivo";
    public static final String SUB_TAMANHO_BYTES = "tamanhoBytes";
    public static final String SUB_ENVIADA_EM    = "enviadaEm";

    /** Limite alinhado a evidências de campo em rede móvel (5 MiB). */
    public static final int TAMANHO_MAXIMO_BYTES = 5 * 1024 * 1024;

    /** Tipos aceitos após validação por MIME declarado e assinatura mágica. */
    public static final List<String> MIME_PERMITIDOS = List.of (
        "image/jpeg", "image/png", "image/webp", "image/gif");

    private FotoEvidencia () { }

    /*
        Monta o subdocumento pronto para $set no Mongo a partir do corpo JSON do upload.
        Preenche Validacao em caso de rejeição (tipo, tamanho, base64 inválido).
    */
    public static Document montarSubdocumento (Document corpo, Validacao validacao, String enviadaEmIso) throws Exception
    {
        Object bruto = corpo.get ("conteudoBase64");
        if (!(bruto instanceof String) || ((String) bruto).isBlank ())
        {
            validacao.erro ("conteudoBase64", "Informe a imagem codificada em Base64.");
            return null;
        }

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

        if (bytes.length == 0)
            validacao.erro ("conteudoBase64", "A imagem está vazia.");
        else if (bytes.length > FotoEvidencia.TAMANHO_MAXIMO_BYTES)
            validacao.erro ("conteudoBase64", "A imagem excede 5 MB. Reduza o arquivo ou comprima antes de enviar.");

        String mimeDeclarado = inferirMime (corpo, bytes);
        if (mimeDeclarado == null)
            validacao.erro ("tipoMime", "Formato não suportado. Use JPEG, PNG, WebP ou GIF.");

        if (validacao.temErros ())
            return null;

        String nome = corpo.getString ("nomeArquivo");
        if (nome != null)
        {
            nome = nome.trim ();
            if (nome.length () > 200)
                nome = nome.substring (0, 200);
            if (nome.isEmpty ())
                nome = null;
        }

        return new Document (FotoEvidencia.SUB_DADOS, new Binary (bytes))
            .append (FotoEvidencia.SUB_MIME, mimeDeclarado)
            .append (FotoEvidencia.SUB_NOME_ARQUIVO, nome)
            .append (FotoEvidencia.SUB_TAMANHO_BYTES, bytes.length)
            .append (FotoEvidencia.SUB_ENVIADA_EM, enviadaEmIso);
    }

    /*
        Remove cabeçalho data:image/...;base64, que alguns navegadores incluem
        ao usar readAsDataURL — o servidor aceita com ou sem esse prefixo.
    */
    public static String limparPrefixoDataUrl (String valor)
    {
        int indice = valor.indexOf ("base64,");
        if (indice >= 0)
            return valor.substring (indice + "base64,".length ());
        return valor;
    }

    /*
        Prioridade: tipoMime explícito no JSON, senão detecção por magic bytes.
        Só retorna MIME da lista branca se os bytes baterem com a assinatura.
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

    /** Confere os primeiros bytes conhecidos de cada formato suportado. */
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

    /** Indica se o registro persistido já possui bytes de foto. */
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

    /** Extrai bytes e MIME para montar resposta HTTP image/*. */
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
        Copia o documento para resposta JSON: remove o subdocumento completo
        e publica metadados seguros para a UI montar link GET .../foto.
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

    /** Impede envio de foto embutida no POST/PUT de cadastro (usar rota /foto). */
    public static void rejeitarCamposDeUploadNoCadastro (Document corpo, Validacao validacao)
    {
        for (String proibido : List.of ("foto", "fotoUrl", "conteudoBase64", FotoEvidencia.CAMPO_RAIZ))
            if (corpo.containsKey (proibido))
                validacao.erro ("vinculos", "Use POST /api/.../foto para anexar imagem após salvar o cadastro.");
    }
}
