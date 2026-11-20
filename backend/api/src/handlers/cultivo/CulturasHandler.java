/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers.cultivo;

import dados.cultivo.RepositorioCulturas;
import dados.cultivo.RepositorioSafras;
import dados.cultivo.RepositorioTalhoes;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import org.bson.Document;
import util.comum.Json;
import util.comum.Validacao;

/*
    Catálogo de culturas da propriedade ativa (coleção culturas).

    Quem chama: a tela de culturas. Talhões e safras não gravam um nome
    solto: consultam este catálogo (cadastrada e erroCulturaNaoCadastrada)
    antes de aceitar o campo cultura.

    Rotas autenticadas. A propriedade vem da sessão, não do corpo.
    Sem token, o roteador responde 401.
      GET    /api/culturas
      POST   /api/culturas
      DELETE /api/culturas/{id}

    O que persiste: um documento por nome de cultura, ligado à propriedade
    e ao usuário que criou. Excluir só é permitido se nenhum talhão e
    nenhuma safra da mesma propriedade ainda usam esse nome.
*/
public class CulturasHandler
{
    private final RepositorioCulturas culturas;
    private final RepositorioTalhoes  talhoes;
    private final RepositorioSafras safras;

    /*
        Culturas é obrigatório. Talhões e safras podem vir nulos: nesse
        caso o DELETE não consegue detectar uso e segue para a exclusão.
    */
    public CulturasHandler (RepositorioCulturas culturas, RepositorioTalhoes talhoes,
                            RepositorioSafras safras) throws Exception
    {
        if (culturas == null)
            throw new Exception ("Repositorio de culturas ausente");

        this.culturas = culturas;
        this.talhoes  = talhoes;
        this.safras   = safras;
    }

    /*
        Três rotas da propriedade da sessão. Não há PUT: renomear cultura
        não existe nesta entrega.
    */
    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/culturas",     this::listar);
        roteador.adicionar ("POST",   "/api/culturas",     this::criar);
        roteador.adicionar ("DELETE", "/api/culturas/{id}", this::excluir);
    }

    /*
        GET /api/culturas
        Lista o catálogo da propriedade ativa.
        200 com o array, possivelmente vazio. Não filtra por query string.
    */
    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String titular       = requisicao.getSessao ().getUsuarioId ();

        return Resposta.json (200, Json.escrever (this.culturas.listar (propriedadeId)));
    }

    /*
        POST /api/culturas  {nome}
        Inclui um nome no catálogo da propriedade da sessão.
        400: nome ausente, acima de 40 caracteres, ou com menos de 2.
        409: já existe cultura com esse nome nesta propriedade.
        201: o documento criado.
    */
    private Resposta criar (Requisicao requisicao) throws Exception
    {
        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    nome      = validacao.texto ("nome", "Informe o nome da cultura.", 40);

        // 400 do Validacao: obrigatório e tamanho máximo.
        if (validacao.temErros ())
            return validacao.resposta ();

        // texto() aceita 1 caractere; a regra de negócio pede pelo menos 2.
        if (nome.length () < 2)
            return Resposta.json (400, new Document ("error", "Dados inválidos.")
                                         .append ("fields", new Document ("nome", "Use pelo menos 2 caracteres.")));

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String titular       = requisicao.getSessao ().getUsuarioId ();

        Document criada = this.culturas.criar (propriedadeId, titular, nome);
        // null: o repositório recusou nome repetido (comparação sem diferenciar maiúsculas).
        if (criada == null)
            return Resposta.json (409, new Document ("error", "Cultura já cadastrada.")
                                         .append ("fields", new Document ("nome", "Já existe uma cultura com esse nome.")));

        return Resposta.json (201, criada);
    }

    /*
        DELETE /api/culturas/{id}
        Remove a cultura se ela não estiver citada em talhão nem em safra.
        404: id de outra propriedade, inexistente, ou a linha sumiu na exclusão.
        409: algum talhão ou alguma safra desta propriedade usa o mesmo nome.
        204: removida. O corpo é um documento vazio, não o 204 sem bytes de semConteudo.
    */
    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        Document cultura = this.culturas.buscarPorId (propriedadeId, id);
        if (cultura == null)
            return Resposta.erro (404, "Não encontrado.");

        String nome = cultura.getString ("nome");

        if (this.talhoes != null)
            for (Document talhao : this.talhoes.listar (propriedadeId))
                if (nome.equalsIgnoreCase (talhao.getString ("cultura")))
                    return Resposta.erro (409, "A cultura está em uso em talhões e não pode ser excluída.");

        if (this.safras != null)
            for (Document safra : this.safras.listar (propriedadeId))
                if (nome.equalsIgnoreCase (safra.getString ("cultura")))
                    return Resposta.erro (409, "A cultura está em uso em safras e não pode ser excluída.");

        if (!this.culturas.excluir (propriedadeId, id))
            return Resposta.erro (404, "Não encontrado.");

        return Resposta.json (204, new Document ());
    }

    /*
        400 compartilhado com talhões e safras quando o nome informado
        não está no catálogo da propriedade. Não é uma rota.
    */
    public static Resposta erroCulturaNaoCadastrada ()
    {
        return Resposta.json (400, new Document ("error", "Dados inválidos.")
                                     .append ("fields", new Document ("cultura",
                                         "Selecione uma cultura cadastrada ou inclua uma nova em Culturas.")));
    }

    /*
        true se o nome, depois do trim, existe no catálogo da propriedade.
        Nome nulo ou em branco é false, sem consultar o banco.
        proprietarioId permanece na assinatura para quem já chama o método;
        a busca em si é por propriedade e nome.
    */
    public boolean cadastrada (String propriedadeId, String proprietarioId, String cultura) throws Exception
    {
        if (cultura == null || cultura.isBlank ())
            return false;

        return this.culturas.existeNome (propriedadeId, cultura.trim ());
    }
}
