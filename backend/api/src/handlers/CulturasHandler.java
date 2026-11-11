/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers;

import dados.RepositorioCulturas;
import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import org.bson.Document;
import util.Json;
import util.Validacao;

/** Catálogo de culturas da propriedade (coleção culturas). */
public class CulturasHandler
{
    private final RepositorioCulturas culturas;
    private final RepositorioTalhoes  talhoes;
    private final RepositorioSafras safras;

    public CulturasHandler (RepositorioCulturas culturas, RepositorioTalhoes talhoes,
                            RepositorioSafras safras) throws Exception
    {
        if (culturas == null)
            throw new Exception ("Repositorio de culturas ausente");

        this.culturas = culturas;
        this.talhoes  = talhoes;
        this.safras   = safras;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/culturas",     this::listar);
        roteador.adicionar ("POST",   "/api/culturas",     this::criar);
        roteador.adicionar ("DELETE", "/api/culturas/{id}", this::excluir);
    }

    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String titular       = requisicao.getSessao ().getUsuarioId ();

        return Resposta.json (200, Json.escrever (this.culturas.listar (propriedadeId)));
    }

  // POST /api/culturas  {nome}
    private Resposta criar (Requisicao requisicao) throws Exception
    {
        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    nome      = validacao.texto ("nome", "Informe o nome da cultura.", 40);

        if (validacao.temErros ())
            return validacao.resposta ();

        if (nome.length () < 2)
            return Resposta.json (400, new Document ("error", "Dados inválidos.")
                                         .append ("fields", new Document ("nome", "Use pelo menos 2 caracteres.")));

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String titular       = requisicao.getSessao ().getUsuarioId ();

        Document criada = this.culturas.criar (propriedadeId, titular, nome);
        if (criada == null)
            return Resposta.json (409, new Document ("error", "Cultura já cadastrada.")
                                         .append ("fields", new Document ("nome", "Já existe uma cultura com esse nome.")));

        return Resposta.json (201, criada);
    }

  // DELETE /api/culturas/{id}
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

    /** Validação compartilhada com talhões e safras. */
    public static Resposta erroCulturaNaoCadastrada ()
    {
        return Resposta.json (400, new Document ("error", "Dados inválidos.")
                                     .append ("fields", new Document ("cultura",
                                         "Selecione uma cultura cadastrada ou inclua uma nova em Culturas.")));
    }

    public boolean cadastrada (String propriedadeId, String proprietarioId, String cultura) throws Exception
    {
        if (cultura == null || cultura.isBlank ())
            return false;

        return this.culturas.existeNome (propriedadeId, cultura.trim ());
    }
}
