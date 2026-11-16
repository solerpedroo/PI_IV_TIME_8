package http;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import seguranca.Sessao;

/*
    Representa uma requisicao HTTP que o ParceiroHttp ja leu e separou em partes.

    Cada rota recebe este objeto pronto, em vez de interpretar bytes do socket.
    Metodo, caminho, consulta (?nome=valor), cabecalhos e corpo ficam em campos
    distintos para o controlador nao precisar fatiar a mensagem de novo.

    Os nomes dos cabecalhos ficam sempre em minusculas. No HTTP o nome e
    case-insensitive: "Authorization" e "authorization" sao o mesmo cabecalho.
    Guardar a chave ja normalizada evita que uma busca falhe so por causa de maiuscula.

    parametros e sessao nao vem do cliente. O roteador preenche os parametros
    depois de casar o caminho com o padrao da rota (ex.: {id}). O filtro de
    autenticacao preenche a sessao quando o token e valido. Por isso esses
    dois campos nao sao final: nascem vazios e sao completados no meio do caminho.
*/
public class Requisicao
{
    private final String              metodo;
    private final String              caminho;
    private final Map<String, String> consulta;
    private final Map<String, String> cabecalhos;
    private final byte[]              corpo;
    private final String              enderecoRemoto;

    // preenchidos pelo roteador
    private Map<String, String> parametros = new HashMap<String, String> ();
    private Sessao              sessao     = null;

    /*
        So monta a requisicao se todas as partes obrigatorias existem.

        A falha aqui e Exception, nao ErroHttp: quem chama este construtor e o
        proprio servidor, depois de ler o socket. Um null indica defeito interno,
        nao um cliente malformado. O corpo pode ser um array de tamanho zero
        (GET sem conteudo), mas nao pode ser null, para os metodos de leitura
        nunca precisarem testar ausencia.
    */
    public Requisicao (String metodo, String caminho, Map<String, String> consulta,
                       Map<String, String> cabecalhos, byte[] corpo, String enderecoRemoto) throws Exception
    {
        if (metodo == null)
            throw new Exception ("Metodo ausente");

        if (caminho == null)
            throw new Exception ("Caminho ausente");

        if (consulta == null)
            throw new Exception ("Consulta ausente");

        if (cabecalhos == null)
            throw new Exception ("Cabecalhos ausentes");

        if (corpo == null)
            throw new Exception ("Corpo ausente");

        if (enderecoRemoto == null || enderecoRemoto.isBlank ())
            throw new Exception ("Endereco remoto ausente");

        this.metodo     = metodo;
        this.caminho    = caminho;
        this.consulta   = consulta;
        this.cabecalhos = cabecalhos;
        this.corpo      = corpo;
        this.enderecoRemoto = enderecoRemoto;
    }

    public String getMetodo ()
    {
        return this.metodo;
    }

    public String getCaminho ()
    {
        return this.caminho;
    }

    /*
        Devolve o valor de um parametro da query string (?nome=valor).

        Retorna null quando o cliente nao enviou esse nome. Quem chama decide
        se a ausencia vira erro 400 ou se existe um valor padrao. A decodificacao
        de %XX ja foi feita em ParceiroHttp ao montar o mapa.
    */
    public String getConsulta (String nome)
    {
        return this.consulta.get (nome);
    }

    /*
        Busca um cabecalho pelo nome, sem diferenciar maiusculas de minusculas.

        O mapa foi gravado com chaves ja em minusculas. Sem o toLowerCase,
        getCabecalho("Host") nao acharia a chave "host" e a rota acharia que
        o cabecalho nao foi enviado.
    */
    public String getCabecalho (String nome)
    {
        return this.cabecalhos.get (nome.toLowerCase ());
    }

    public byte[] getCorpo ()
    {
        return this.corpo;
    }

    public String getEnderecoRemoto ()
    {
        return this.enderecoRemoto;
    }

    /*
        Interpreta o corpo como um unico objeto JSON (org.bson.Document).

        A API so aceita objeto: o texto precisa comecar com '{'. Uma lista
        ([...]) ou um valor solto nao e corpo de rota aqui. Document.parse
        lanca excecao quando a sintaxe quebra. Os dois caminhos viram ErroHttp
        400 com a mesma mensagem, para o cliente nao distinguir "nao e objeto"
        de "JSON malformado".
    */
    public Document getCorpoJson () throws ErroHttp
    {
        String texto = new String (this.corpo, StandardCharsets.UTF_8).trim ();

        /*
            Objeto JSON comeca com '{'. Qualquer outra forma (lista, numero,
            texto vazio) e recusada antes de chamar o parser.
        */
        if (!texto.startsWith ("{"))
            throw new ErroHttp (400, "Corpo JSON inválido.");

        try
        {
            return Document.parse (texto);
        }
        catch (Exception erro)
        {
            throw new ErroHttp (400, "Corpo JSON inválido.");
        }
    }

    /*
        Devolve o trecho capturado de {nome} no padrao da rota.

        Exemplo: a rota /api/ocorrencias/{id} com o caminho /api/ocorrencias/abc
        faz getParametro("id") retornar "abc". O mapa nao vem do construtor;
        o roteador o preenche em setParametros depois de escolher a rota.
    */
    public String getParametro (String nome)
    {
        return this.parametros.get (nome);
    }

    /*
        Recebe os parametros de rota depois que o caminho casou com um padrao.

        O roteador so conhece esses valores depois de escolher a rota, por isso
        eles nao entram no construtor. O mapa vazio criado no campo e substituido
        pelo mapa ja extraido.
    */
    public void setParametros (Map<String, String> parametros)
    {
        this.parametros = parametros;
    }

    public Sessao getSessao ()
    {
        return this.sessao;
    }

    /*
        Associa a sessao autenticada a esta requisicao.

        Rotas protegidas leem getSessao() para saber qual usuario chamou.
        Permanece null em rotas publicas (login, cadastro) e quando o token
        falta ou e invalido. Nesse ultimo caso o filtro responde 401 antes
        de o controlador rodar.
    */
    public void setSessao (Sessao sessao)
    {
        this.sessao = sessao;
    }

    /*
        Extrai o token do cabecalho Authorization no esquema Bearer.

        O formato esperado e "Bearer <token>". regionMatches com ignoreCase
        aceita "bearer" e "BEARER", porque o HTTP nao diferencia maiusculas
        no esquema. Devolve null, e nao excecao, quando o cabecalho falta,
        nao e Bearer ou o token esta vazio. A decisao de recusar a rota fica
        no filtro de sessao, que separa "nao enviou token" de "token invalido".
    */
    public String getToken ()
    {
        String autorizacao = this.getCabecalho ("Authorization");

        /*
            Os 7 primeiros caracteres precisam ser "Bearer " (com o espaco).
            Sem esse prefixo nao ha token para autenticar.
        */
        if (autorizacao == null || !autorizacao.regionMatches (true, 0, "Bearer ", 0, 7))
            return null;

        String token = autorizacao.substring (7).trim ();

        /*
            "Bearer " seguido so de espacos nao e um token. Tratar como ausente
            evita gravar string vazia na busca de sessao.
        */
        if (token.isEmpty ())
            return null;

        return token;
    }
}
