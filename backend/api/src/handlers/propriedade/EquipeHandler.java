// Miguel — RA: 25003110
package handlers.propriedade;

import dados.propriedade.RepositorioEquipe;
import dados.conta.RepositorioUsuarios;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.util.ArrayList;
import org.bson.Document;
import servicos.conta.ServicoEmail;
import util.propriedade.ConviteEquipe;
import util.propriedade.EquipeMembro;
import util.comum.Json;
import util.propriedade.PapelNaPropriedade;
import util.propriedade.TelasAcesso;
import util.comum.Validacao;

/** CRUD da equipe, convites por link e aceite de colaboradores. */
public class EquipeHandler
{
    private final RepositorioEquipe   equipe;
    private final RepositorioUsuarios usuarios;
    private final ServicoEmail        email;

    public EquipeHandler (RepositorioEquipe equipe, RepositorioUsuarios usuarios, ServicoEmail email) throws Exception
    {
        if (equipe == null || usuarios == null || email == null)
            throw new Exception ("Dependencia de equipe ausente");

        this.equipe   = equipe;
        this.usuarios = usuarios;
        this.email    = email;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/equipe",                           this::listar);
        roteador.adicionar ("POST",   "/api/equipe",                           this::criar);
        roteador.adicionar ("PUT",    "/api/equipe/{id}",                      this::atualizar);
        roteador.adicionar ("DELETE", "/api/equipe/{id}",                      this::excluir);
        roteador.adicionar ("POST",   "/api/equipe/{id}/convite/renovar",      this::renovarConvite);
        roteador.adicionarPublica ("GET",  "/api/equipe/convite",              this::visualizarConvite);
        roteador.adicionar        ("POST", "/api/equipe/convite/aceitar",      this::aceitarConvite);
    }

    private boolean ehProprietarioAtivo (Requisicao requisicao) throws Exception
    {
        return PapelNaPropriedade.ehProprietario (this.usuarios,
                                                  requisicao.getSessao ().getPropriedadeId (),
                                                  requisicao.getSessao ().getUsuarioId ());
    }

    private static String montarLink (Requisicao requisicao, String token)
    {
        String origem = requisicao.getCabecalho ("Origin");
        if (origem == null || origem.isBlank ())
            origem = "http://" + requisicao.getCabecalho ("Host");

        if (origem.endsWith ("/"))
            origem = origem.substring (0, origem.length () - 1);

        return origem + "/equipe-convite.html?token=" + token;
    }

    private static Document comLink (Requisicao requisicao, Document membro)
    {
        if (membro == null || !membro.containsKey ("conviteToken"))
            return membro;

        Document ret = new Document (membro);
        ret.append ("conviteLink", EquipeHandler.montarLink (requisicao, membro.getString ("conviteToken")));
        return ret;
    }

    private Document respostaComConvite (Requisicao requisicao, Document membro) throws Exception
    {
        Document publico = EquipeHandler.comLink (requisicao, membro);
        boolean enviado  = false;
        try
        {
            enviado = this.tentarEnviarConvite (requisicao, membro, publico.getString ("conviteLink"));
        }
        catch (Exception erro)
        {
            System.err.println ("Convite criado, mas o e-mail não foi enviado: " + erro.getMessage ());
        }
        publico.append ("conviteEmailEnviado", enviado);
        return publico;
    }

    private boolean tentarEnviarConvite (Requisicao requisicao, Document membro, String link) throws Exception
    {
        String destinatario = membro.getString ("email");
        if (destinatario == null || destinatario.isBlank ())
            destinatario = membro.getString ("emailConvidado");
        if (destinatario == null || destinatario.isBlank () || link == null || link.isBlank ())
            return false;

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        Document propriedade = this.usuarios.buscarPropriedade (propriedadeId);
        String nomeFazenda   = propriedade == null ? null : propriedade.getString ("nome");
        if (nomeFazenda == null || nomeFazenda.isBlank ())
            nomeFazenda = propriedade == null ? null : propriedade.getString ("farm");

        String nomeConvidado = membro.getString ("nome");
        return this.email.enviarConviteEquipe (destinatario.trim (), nomeConvidado, nomeFazenda, link);
    }

    private Resposta listar (Requisicao requisicao) throws Exception
    {
        if (!this.ehProprietarioAtivo (requisicao))
            return Resposta.erro (403, "Somente o proprietário gerencia a equipe.");

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        ArrayList<Document> itens = new ArrayList<Document> ();
        for (Document membro : this.equipe.listar (propriedadeId))
            itens.add (EquipeHandler.comLink (requisicao, membro));

        return Resposta.json (200, Json.escrever (itens));
    }

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

    private Resposta validarCorpo (Requisicao requisicao, Document[] saida, boolean convite) throws Exception
    {
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);

        String email = convite ? validacao.email ("email") : EquipeHandler.textoOpcional (corpo, validacao, "email", 254);
        String nome  = convite ? EquipeHandler.textoOpcional (corpo, validacao, "nome", 120) : validacao.texto ("nome", "Informe o nome do integrante.", 120);
        String cargo = EquipeHandler.textoOpcional (corpo, validacao, "cargo", 80);
        Object telas = corpo.get ("telas");

        if (telas != null && !(telas instanceof Document))
            validacao.erro ("telas", "Informe o acesso às telas.");

        if (!convite && nome != null && nome.length () < 2)
            validacao.erro ("nome", "Use pelo menos 2 caracteres.");

        if (validacao.temErros ())
            return validacao.resposta ();

        Document dados = new Document ("telas", TelasAcesso.normalizar (telas));
        if (cargo != null && !cargo.isEmpty ())
            dados.append ("cargo", cargo);

        if (convite)
        {
            dados.append ("emailConvidado", email);
            if (nome != null && !nome.isEmpty ())
                dados.append ("nome", nome);
        }
        else
            dados.append ("nome", nome);

        saida[0] = dados;
        return null;
    }

    private Resposta criar (Requisicao requisicao) throws Exception
    {
        if (!this.ehProprietarioAtivo (requisicao))
            return Resposta.erro (403, "Somente o proprietário gerencia a equipe.");

        Document corpo = requisicao.getCorpoJson ();
        boolean convite = corpo.get ("email") instanceof String && !((String) corpo.get ("email")).trim ().isEmpty ();

        Document[] dados = new Document[1];
        Resposta invalido = this.validarCorpo (requisicao, dados, convite);
        if (invalido != null)
            return invalido;

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String titular       = requisicao.getSessao ().getUsuarioId ();

        if (convite)
        {
            String email = EquipeMembro.emailNormalizado (dados[0].getString ("emailConvidado"));
            Document dono = this.usuarios.buscarPorId (titular);
            if (dono != null && email.equals (EquipeMembro.emailNormalizado (dono.getString ("email"))))
                return Resposta.json (409, new Document ("error", "Você não pode convidar a si mesmo.")
                                             .append ("fields", new Document ("email", "Use outro e-mail de colaborador.")));

            Document criado = this.equipe.criarConvite (propriedadeId, titular, dados[0]);
            if (criado == null)
                return Resposta.json (409, new Document ("error", "Convite já existente.")
                                             .append ("fields", new Document ("email", "Já há convite ou colaborador com esse e-mail.")));

            return Resposta.json (201, this.respostaComConvite (requisicao, criado));
        }

        Document criado = this.equipe.criar (propriedadeId, titular, dados[0]);
        if (criado == null)
            return Resposta.json (409, new Document ("error", "Integrante já cadastrado.")
                                         .append ("fields", new Document ("nome", "Já existe alguém com esse nome na equipe.")));

        return Resposta.json (201, criado);
    }

    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        if (!this.ehProprietarioAtivo (requisicao))
            return Resposta.erro (403, "Somente o proprietário gerencia a equipe.");

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        Document atual = this.equipe.buscarPorId (propriedadeId, id);
        if (atual == null)
            return Resposta.erro (404, "Não encontrado.");

        Document[] dados = new Document[1];
        Resposta invalido;

        if (EquipeMembro.STATUS_CONVITE_PENDENTE.equals (atual.getString ("status")))
        {
            Document corpo = requisicao.getCorpoJson ();
            Validacao validacao = new Validacao (corpo);
            String cargo = EquipeHandler.textoOpcional (corpo, validacao, "cargo", 80);
            Object telas = corpo.get ("telas");
            if (telas != null && !(telas instanceof Document))
                validacao.erro ("telas", "Informe o acesso às telas.");
            if (validacao.temErros ())
                return validacao.resposta ();
            Document patch = new Document ("nome", atual.getString ("nome"))
                                 .append ("telas", TelasAcesso.normalizar (telas));
            if (cargo != null && !cargo.isEmpty ())
                patch.append ("cargo", cargo);
            dados[0] = patch;
        }
        else
        {
            invalido = this.validarCorpo (requisicao, dados, false);
            if (invalido != null)
                return invalido;
        }

        Document atualizado = this.equipe.atualizar (propriedadeId, id, dados[0]);
        if (atualizado == null)
            return Resposta.json (409, new Document ("error", "Integrante já cadastrado.")
                                         .append ("fields", new Document ("nome", "Já existe alguém com esse nome na equipe.")));

        return Resposta.json (200, EquipeHandler.comLink (requisicao, atualizado));
    }

    private Resposta renovarConvite (Requisicao requisicao) throws Exception
    {
        if (!this.ehProprietarioAtivo (requisicao))
            return Resposta.erro (403, "Somente o proprietário gerencia a equipe.");

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        Document renovado = this.equipe.regenerarConvite (propriedadeId, id);
        if (renovado == null)
            return Resposta.erro (404, "Convite não encontrado ou já aceito.");

        return Resposta.json (200, this.respostaComConvite (requisicao, renovado));
    }

    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        if (!this.ehProprietarioAtivo (requisicao))
            return Resposta.erro (403, "Somente o proprietário gerencia a equipe.");

        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String id            = requisicao.getParametro ("id");

        if (!this.equipe.excluir (propriedadeId, id))
            return Resposta.erro (404, "Não encontrado.");

        return Resposta.semConteudo ();
    }

    // GET /api/equipe/convite?token=...
    private Resposta visualizarConvite (Requisicao requisicao) throws Exception
    {
        String token = requisicao.getConsulta ("token");
        if (token == null || token.isBlank ())
            return Resposta.erro (400, "Token ausente.");

        Document convite = this.equipe.buscarPorConviteToken (token.trim ());
        if (convite == null)
            return Resposta.erro (404, "Convite inválido.");

        boolean expirado = ConviteEquipe.expirado (convite.get ("conviteExpiraEm"));
        if (!EquipeMembro.STATUS_CONVITE_PENDENTE.equals (convite.getString ("status")))
            return Resposta.erro (410, "Convite já utilizado.");

        Document propriedade = this.usuarios.buscarPropriedade (convite.getString ("propriedadeId"));
        Document dono = this.usuarios.buscarPorId (convite.getString ("proprietarioId"));

        Document ret = new Document ("valido", !expirado)
                           .append ("expirado", expirado)
                           .append ("emailConvidado", convite.getString ("emailConvidado"))
                           .append ("cargo", convite.getString ("cargo"))
                           .append ("conviteExpiraEm", convite.get ("conviteExpiraEm"));

        if (propriedade != null)
            ret.append ("propriedade", propriedade.getString ("nome") == null
                ? propriedade.getString ("farm") : propriedade.getString ("nome"));

        if (dono != null)
            ret.append ("convidadoPor", dono.getString ("name"));

        return Resposta.json (200, ret);
    }

    // POST /api/equipe/convite/aceitar  {token}
    private Resposta aceitarConvite (Requisicao requisicao) throws Exception
    {
        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String token = validacao.texto ("token", "Informe o token do convite.", 200);
        if (validacao.temErros ())
            return validacao.resposta ();

        Document usuario = this.usuarios.buscarPorId (requisicao.getSessao ().getUsuarioId ());
        if (usuario == null)
            return Resposta.erro (401, "Não autorizado.");

        if (!Boolean.TRUE.equals (usuario.getBoolean ("emailConfirmado")))
            return Resposta.json (409, new Document ("error", "Confirme seu e-mail antes de aceitar convites."));

        Document convite = this.equipe.buscarPorConviteToken (token.trim ());
        String propriedadeId = convite == null ? null : convite.getString ("propriedadeId");

        Document aceito = this.equipe.aceitarConvite (token.trim (),
            requisicao.getSessao ().getUsuarioId (),
            usuario.getString ("name"),
            usuario.getString ("email"));

        if (aceito == null)
            return Resposta.json (409, new Document ("error", "Não foi possível aceitar o convite.")
                                         .append ("fields", new Document ("token",
                                             "Convite inválido, expirado, e-mail diferente ou você é o proprietário.")));

        return Resposta.json (200, new Document ("message", "Convite aceito.")
                                     .append ("propriedadeId", propriedadeId)
                                     .append ("membro", aceito));
    }
}