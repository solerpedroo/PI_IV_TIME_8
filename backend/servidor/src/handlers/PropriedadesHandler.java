/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers;

import dados.RepositorioTalhoes;
import dados.RepositorioOcorrencias;
import dados.RepositorioUsuarios;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;
import util.Json;
import util.Validacao;

/*
    CRUD das propriedades pertencentes ao usuario autenticado.
    A propriedade ativa fica na sessao: assim os endpoints de talhoes ja existentes
    sempre usam o mesmo contexto no servidor, inclusive apos atualizar a pagina.
*/
public class PropriedadesHandler
{
    private final RepositorioUsuarios usuarios;
    private final RepositorioTalhoes  talhoes;
    private final Sessoes             sessoes;
    private final RepositorioOcorrencias ocorrencias;

    public PropriedadesHandler (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, Sessoes sessoes) throws Exception
    {
        this (usuarios, talhoes, sessoes, null);
    }

    public PropriedadesHandler (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, Sessoes sessoes,
                                RepositorioOcorrencias ocorrencias) throws Exception
    {
        if (usuarios == null || talhoes == null || sessoes == null)
            throw new Exception ("Dependencia de propriedades ausente");
        this.usuarios = usuarios;
        this.talhoes  = talhoes;
        this.sessoes  = sessoes;
        this.ocorrencias = ocorrencias;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/propriedades",                 this::listar);
        roteador.adicionar ("POST",   "/api/propriedades",                 this::criar);
        roteador.adicionar ("GET",    "/api/propriedades/{id}",            this::consultar);
        roteador.adicionar ("PUT",    "/api/propriedades/{id}",            this::atualizar);
        roteador.adicionar ("DELETE", "/api/propriedades/{id}",            this::excluir);
        roteador.adicionar ("POST",   "/api/propriedades/{id}/selecionar", this::selecionar);
    }

    // Nenhum dado de vinculo interno ou do proprietario e exposto pela API.
    private static Document publico (Document propriedade)
    {
        Document ret = Json.publico (propriedade, "usuarioId");
        // Registros criados pela primeira versão de cadastro usavam farm/area.
        // A leitura continua compatível, sem inventar uma área numérica inexistente.
        if (ret.getString ("nome") == null)
            ret.append ("nome", ret.getString ("farm"));
        return ret;
    }

    private static String id (Document propriedade)
    {
        Object valor = propriedade.get ("_id");
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : valor.toString ();
    }

    // Campos obrigatorios; area em hectares evita ambiguidades como "50 ha" ou "cinquenta".
    private static Document lerPropriedade (Document corpo, Validacao validacao)
    {
        String nome      = validacao.texto ("nome", "Informe o nome da propriedade.", 120);
        String localizacao = validacao.texto ("localizacao", "Informe a localização.", 160);
        String municipio = validacao.texto ("municipio", "Informe o município.", 120);
        String safra     = validacao.texto ("safraAtual", "Informe a safra atual.", 20);
        if (safra != null && !TalhoesHandler.SAFRAS.contains (safra))
            validacao.erro ("safraAtual", "Selecione uma safra válida.");
        Object area      = corpo.get ("areaHectares");

        if (!(area instanceof Number) || !Double.isFinite (((Number) area).doubleValue ()) ||
            ((Number) area).doubleValue () <= 0 || ((Number) area).doubleValue () > 100000000)
            validacao.erro ("areaHectares", "Informe uma área maior que zero.");

        if (validacao.temErros ())
            return null;

        return new Document ("nome", nome).append ("localizacao", localizacao).append ("municipio", municipio)
                                          .append ("safraAtual", safra)
                                          .append ("areaHectares", ((Number) area).doubleValue ())
                                          .append ("atualizadoEm", Instant.now ().toString ());
    }

    private List<Document> propriedadesDoUsuario (Requisicao requisicao) throws Exception
    {
        return this.usuarios.listarPropriedades (requisicao.getSessao ().getUsuarioId ());
    }

    private Document propriedadeDoUsuario (Requisicao requisicao, String propriedadeId) throws Exception
    {
        for (Document propriedade : this.propriedadesDoUsuario (requisicao))
            if (PropriedadesHandler.id (propriedade).equals (propriedadeId))
                return propriedade;
        return null;
    }

    private Resposta listar (Requisicao requisicao) throws Exception
    {
        ArrayList<Document> ret = new ArrayList<Document> ();
        String ativa = requisicao.getSessao ().getPropriedadeId ();
        for (Document propriedade : this.propriedadesDoUsuario (requisicao))
        {
            Document item = PropriedadesHandler.publico (propriedade);
            item.append ("selecionada", PropriedadesHandler.id (propriedade).equals (ativa));
            ret.add (item);
        }
        return Resposta.json (200, Json.escrever (ret));
    }

    private Resposta criar (Requisicao requisicao) throws Exception
    {
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        Document propriedade = PropriedadesHandler.lerPropriedade (corpo, validacao);
        if (propriedade == null)
            return validacao.resposta ();
        propriedade.append ("criadaEm", propriedade.getString ("atualizadoEm"));
        Document criada = this.usuarios.criarPropriedade (requisicao.getSessao ().getUsuarioId (), propriedade);
        if (criada == null)
            return Resposta.erro (404, "Not found");
        return Resposta.json (201, PropriedadesHandler.publico (criada));
    }

    private Resposta consultar (Requisicao requisicao) throws Exception
    {
        Document propriedade = this.propriedadeDoUsuario (requisicao, requisicao.getParametro ("id"));
        return propriedade == null ? Resposta.erro (404, "Not found") : Resposta.json (200, PropriedadesHandler.publico (propriedade));
    }

    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes) { return this.atualizarProtegido (requisicao); }
    }

    private Resposta atualizarProtegido (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        if (this.propriedadeDoUsuario (requisicao, id) == null)
            return Resposta.erro (404, "Not found");
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        Document campos = PropriedadesHandler.lerPropriedade (corpo, validacao);
        if (campos == null)
            return validacao.resposta ();
        this.usuarios.atualizarPropriedade (requisicao.getSessao ().getUsuarioId (), id, campos);
        return Resposta.json (200, PropriedadesHandler.publico (this.propriedadeDoUsuario (requisicao, id)));
    }

    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes) { return this.excluirProtegido (requisicao); }
    }

    private Resposta excluirProtegido (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        if (this.propriedadeDoUsuario (requisicao, id) == null)
            return Resposta.erro (404, "Not found");
        if (this.propriedadesDoUsuario (requisicao).size () <= 1)
            return Resposta.json (409, new Document ("error", "A última propriedade não pode ser excluída."));
        if (id.equals (requisicao.getSessao ().getPropriedadeId ()))
            return Resposta.json (409, new Document ("error", "Selecione outra propriedade antes de excluir a atual."));
        if (this.sessoes.propriedadeEmUso (id))
            return Resposta.json (409, new Document ("error", "A propriedade está em uso em outra sessão. Encerre ou troque esse contexto antes de excluir."));
        if (!this.talhoes.listar (id).isEmpty ())
            return Resposta.json (409, new Document ("error", "Exclua ou mova os talhões antes de excluir a propriedade."));
        // Uma ocorrência geral também depende da propriedade, mesmo depois de excluída da lista.
        if (this.ocorrencias != null && this.ocorrencias.temRegistros (id))
            return Resposta.erro (409, "A propriedade possui ocorrências ou histórico preservado e não pode ser excluída.");
        if (!this.usuarios.excluirPropriedade (requisicao.getSessao ().getUsuarioId (), id))
            return Resposta.erro (404, "Not found");
        return Resposta.semConteudo ();
    }

    private Resposta selecionar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes) { return this.selecionarProtegido (requisicao); }
    }

    private Resposta selecionarProtegido (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        if (this.propriedadeDoUsuario (requisicao, id) == null)
            return Resposta.erro (404, "Not found");
        if (!this.sessoes.selecionarPropriedade (requisicao.getToken (), id))
            return Resposta.erro (401, "Unauthorized");
        return Resposta.json (200, new Document ("propriedadeId", id));
    }
}
