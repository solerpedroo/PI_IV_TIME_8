// Miguel — RA: 25003110
package handlers;

import dados.RepositorioEquipe;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import org.bson.Document;
import util.Json;
import util.TelasAcesso;
import util.Validacao;

/** CRUD dos membros da equipe da propriedade ativa. */
public class EquipeHandler
{
    private final RepositorioEquipe equipe;

    public EquipeHandler (RepositorioEquipe equipe) throws Exception
    {
        if (equipe == null)
            throw new Exception ("Repositorio de equipe ausente");

        this.equipe = equipe;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/equipe",           this::listar);
        roteador.adicionar ("POST",   "/api/equipe",           this::criar);
        roteador.adicionar ("PUT",    "/api/equipe/{id}",      this::atualizar);
        roteador.adicionar ("DELETE", "/api/equipe/{id}",      this::excluir);
    }

    private Resposta listar (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        return Resposta.json (200, Json.escrever (this.equipe.listar (propriedadeId)));
    }

    // Cargo é opcional. null e texto vazio significam "sem função" e não são gravados,
    // porque o schema de equipe_membros só aceita string nesse campo.
    private static String textoOpcional (Document corpo, Validacao validacao, String campo, int maximo)
    {
        if (!corpo.containsKey (campo) || corpo.get (campo) == null)
            return null;

        Object valor = corpo.get (campo);
        if (!(valor instanceof String))
        {
            validacao.erro (campo, "Valor inválido.");
            return null;
        }

        String texto = ((String) valor).trim ();
        if (texto.length () > maximo)
        {
            validacao.erro (campo, "Use no máximo " + maximo + " caracteres.");
            return null;
        }

        return texto;
    }

    private Resposta validarCorpo (Requisicao requisicao, Document[] saida) throws Exception
    {
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        String nome = validacao.texto ("nome", "Informe o nome do integrante.", 120);
        String cargo = EquipeHandler.textoOpcional (corpo, validacao, "cargo", 80);
        Object telas = corpo.get ("telas");

        // Qualquer formato que não seja objeto cairia no padrão "tudo liberado".
        if (telas != null && !(telas instanceof Document))
            validacao.erro ("telas", "Informe o acesso às telas.");

        if (nome != null && nome.length () < 2)
            validacao.erro ("nome", "Use pelo menos 2 caracteres.");

        if (validacao.temErros ())
            return validacao.resposta ();

        Document dados = new Document ("nome", nome);
        if (cargo != null && !cargo.isEmpty ())
            dados.append ("cargo", cargo);
        dados.append ("telas", TelasAcesso.normalizar (telas));
        saida[0] = dados;
        return null;
    }

    private Resposta criar (Requisicao requisicao) throws Exception
    {
        Document[] dados = new Document[1];
        Resposta invalido = this.validarCorpo (requisicao, dados);
        if (invalido != null)
            return invalido;
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String titular       = requisicao.getSessao ().getUsuarioId ();

        Document criado = this.equipe.criar (propriedadeId, titular, dados[0]);
        if (criado == null)
            return Resposta.json (409, new Document ("error", "Integrante já cadastrado.")
                                         .append ("fields", new Document ("nome", "Já existe alguém com esse nome na equipe.")));

        return Resposta.json (201, criado);
    }

    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        Document[] dados = new Document[1];
        Resposta invalido = this.validarCorpo (requisicao, dados);
        if (invalido != null)
            return invalido;

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        if (this.equipe.buscarPorId (propriedadeId, id) == null)
            return Resposta.erro (404, "Não encontrado.");

        Document atualizado = this.equipe.atualizar (propriedadeId, id, dados[0]);
        if (atualizado == null)
            return Resposta.json (409, new Document ("error", "Integrante já cadastrado.")
                                         .append ("fields", new Document ("nome", "Já existe alguém com esse nome na equipe.")));

        return Resposta.json (200, atualizado);
    }

    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        if (!this.equipe.excluir (propriedadeId, id))
            return Resposta.erro (404, "Não encontrado.");

        return Resposta.semConteudo ();
    }
}