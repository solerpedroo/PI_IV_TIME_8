package handlers;

import http.Requisicao;
import http.Resposta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/*
    Serve os arquivos do front (raiz do repositorio).
    So entrega extensoes da lista de permissao e barra caminhos perigosos:
    "..", arquivos/pastas que comecam com ponto (.env, .git), backend/ etc.
*/
public class EstaticosHandler
{
    private static final Map<String, String> TIPOS = Map.of (
        "html",  "text/html; charset=utf-8",
        "css",   "text/css; charset=utf-8",
        "js",    "text/javascript; charset=utf-8",
        "jpg",   "image/jpeg",
        "png",   "image/png",
        "svg",   "image/svg+xml",
        "ico",   "image/x-icon",
        "woff2", "font/woff2"
    );

    private final Path raiz;

    public EstaticosHandler (Path raiz) throws Exception
    {
        if (raiz == null)
            throw new Exception ("Raiz dos estaticos ausente");

        if (!Files.isDirectory (raiz))
            throw new Exception ("Raiz dos estaticos nao e uma pasta: " + raiz);

        this.raiz = raiz.toRealPath ();
    }

    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        String caminho = requisicao.getCaminho ();

        if (caminho.equals ("/"))
            return Resposta.redirecionar ("/login.html");

        if (!EstaticosHandler.permitido (caminho))
            return Resposta.erro (404, "Not found");

        String extensao = caminho.substring (caminho.lastIndexOf ('.') + 1).toLowerCase ();

        Path arquivo;
        try
        {
            arquivo = this.raiz.resolve (caminho.substring (1)).normalize ();
        }
        catch (Exception erro) // caminho invalido para o sistema de arquivos
        {
            return Resposta.erro (404, "Not found");
        }

        if (!arquivo.startsWith (this.raiz) || !Files.isRegularFile (arquivo))
            return Resposta.erro (404, "Not found");

        // confere de novo depois de seguir links simbolicos
        if (!arquivo.toRealPath ().startsWith (this.raiz))
            return Resposta.erro (404, "Not found");

        return new Resposta (200, EstaticosHandler.TIPOS.get (extensao), Files.readAllBytes (arquivo));
    }

    // regras sobre o texto do caminho, antes de encostar no disco
    private static boolean permitido (String caminho)
    {
        // "\", ":" e "~" nunca aparecem no front e no Windows mudam o sentido do caminho
        // (ex.: "BACKEN~1" e o nome curto de "backend")
        if (caminho.contains ("..") || caminho.contains ("\\") || caminho.contains (":") ||
            caminho.contains ("~")  || caminho.contains ("//"))
            return false;

        String[] segmentos = caminho.substring (1).split ("/", -1);

        if (segmentos[0].equalsIgnoreCase ("backend"))
            return false;

        for (String segmento : segmentos)
            if (segmento.isEmpty () || segmento.startsWith (".") || segmento.endsWith (".") || segmento.endsWith (" "))
                return false;

        String nome = segmentos[segmentos.length - 1];
        int    ponto = nome.lastIndexOf ('.');
        if (ponto < 0)
            return false;

        return EstaticosHandler.TIPOS.containsKey (nome.substring (ponto + 1).toLowerCase ());
    }
}
