package handlers.conta;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import http.Requisicao;
import http.Resposta;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/*
    Entrega os arquivos do front (HTML, CSS, JS, imagens e fonte).

    Quem chama: o roteador, só para GET ou HEAD fora de /api. Se o método
    não for GET, o próprio roteador responde 405 antes de chegar aqui.
    Não há rota registrada neste handler: ele é o fallback de estáticos.

    O que persiste: nada. Lê bytes do disco a partir da pasta raiz recebida
    no construtor (em geral o diretório frontend) e devolve o corpo.

    Só sai extensão da lista TIPOS. Caminho com "..", barra invertida,
    ":", "~", barra dupla, segmento que começa com ponto (.env, .git) ou
    a pasta backend é recusado com 404, antes de virar leitura de arquivo.
*/
public class EstaticosHandler
{
    private static final long MAXIMO_ARQUIVO = 2L * 1024 * 1024; // acima de 2 MiB a resposta é 413
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

    /*
        Guarda a raiz já resolvida (toRealPath), para a comparação posterior
        não ser enganada por link simbólico. Recusa raiz nula ou que não seja pasta.
    */
    public EstaticosHandler (Path raiz) throws Exception
    {
        if (raiz == null)
            throw new Exception ("Raiz dos estaticos ausente");

        if (!Files.isDirectory (raiz))
            throw new Exception ("Raiz dos estaticos nao e uma pasta: " + raiz);

        this.raiz = raiz.toRealPath ();
    }

    /*
        GET de um arquivo do front. Não é uma rota /api.
        302: a raiz "/" manda o navegador para /login.html.
        404: extensão fora da lista, caminho perigoso, arquivo inexistente
        ou alvo que, depois de normalizar e seguir link, sai da raiz.
        413: o arquivo regular passa de 2 MiB.
        200: bytes do arquivo, com o Content-Type da extensão.
    */
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        String caminho = requisicao.getCaminho ();

        if (caminho.equals ("/"))
            return Resposta.redirecionar ("/login.html");

        // 404 de propósito: não diferencia "proibido" de "não existe".
        if (!EstaticosHandler.permitido (caminho))
            return Resposta.erro (404, "Não encontrado.");

        String extensao = caminho.substring (caminho.lastIndexOf ('.') + 1).toLowerCase ();

        Path arquivo;
        try
        {
            arquivo = this.raiz.resolve (caminho.substring (1)).normalize ();
        }
        catch (Exception erro) // caminho inválido para o sistema de arquivos
        {
            return Resposta.erro (404, "Não encontrado.");
        }

        // normalize tira ".." que tenha sobrado; startsWith impede sair da raiz.
        if (!arquivo.startsWith (this.raiz) || !Files.isRegularFile (arquivo))
            return Resposta.erro (404, "Não encontrado.");

        // Confere de novo depois de seguir links simbólicos.
        if (!arquivo.toRealPath ().startsWith (this.raiz))
            return Resposta.erro (404, "Não encontrado.");

        if (Files.size (arquivo) > EstaticosHandler.MAXIMO_ARQUIVO)
            return Resposta.erro (413, "Arquivo estático grande demais.");

        return new Resposta (200, EstaticosHandler.TIPOS.get (extensao), Files.readAllBytes (arquivo));
    }

    /*
        Regras sobre o texto do caminho, antes de encostar no disco.
        Devolve false (e o chamador responde 404) se houver travessia,
        segmento oculto ou extensão que não está em TIPOS.
    */
    private static boolean permitido (String caminho)
    {
        // "\", ":" e "~" nunca aparecem no front e no Windows mudam o sentido do caminho
        // (ex.: "BACKEN~1" é o nome curto de "backend")
        if (caminho.contains ("..") || caminho.contains ("\\") || caminho.contains (":") ||
            caminho.contains ("~")  || caminho.contains ("//"))
            return false;

        String[] segmentos = caminho.substring (1).split ("/", -1);

        // A pasta do servidor não é conteúdo do front.
        if (segmentos[0].equalsIgnoreCase ("backend"))
            return false;

        // Segmento vazio, oculto (começa com ponto), terminado em ponto ou em espaço.
        for (String segmento : segmentos)
            if (segmento.isEmpty () || segmento.startsWith (".") || segmento.endsWith (".") || segmento.endsWith (" "))
                return false;

        String nome = segmentos[segmentos.length - 1];
        int    ponto = nome.lastIndexOf ('.');
        // Sem extensão não há tipo permitido.
        if (ponto < 0)
            return false;

        return EstaticosHandler.TIPOS.containsKey (nome.substring (ponto + 1).toLowerCase ());
    }
}
