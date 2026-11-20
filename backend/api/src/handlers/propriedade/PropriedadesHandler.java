/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */

package handlers.propriedade;

import dados.propriedade.RepositorioEquipe;
import dados.financeiro.RepositorioCustos;
import dados.operacao.RepositorioAtividades;
import dados.cultivo.RepositorioTalhoes;
import dados.operacao.RepositorioOcorrencias;
import dados.infra.ConflitoDeCadastro;
import dados.conta.RepositorioUsuarios;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessoes;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import util.comum.Json;
import util.propriedade.PapelNaPropriedade;
import util.cultivo.PeriodoSafra;
import util.comum.Validacao;

/*
    CRUD das propriedades do usuário autenticado e a troca da propriedade
    ativa da sessão.

    Quem chama: a tela de propriedades e o seletor do topo. Talhões, custos,
    estoque e o restante da API não recebem o id da fazenda no corpo: leem
    a propriedade que está na sessão. Por isso selecionar persiste o contexto
    no servidor e ele sobrevive a um recarregar de página.

    Rotas autenticadas (sem token o roteador responde 401):
      GET    /api/propriedades
      GET    /api/propriedades/periodos-safra
      POST   /api/propriedades
      GET    /api/propriedades/{id}
      PUT    /api/propriedades/{id}
      DELETE /api/propriedades/{id}
      POST   /api/propriedades/{id}/selecionar

    O que persiste: o documento da propriedade (nome, localização, município,
    safra atual e área em hectares), ligado ao usuário. Excluir só apaga a
    propriedade vazia; talhão, estoque, custo, atividade, safra, ocorrência
    e equipamento bloqueiam o DELETE com 409. A propriedade selecionada
    fica na sessão em memória, não no documento.
*/
public class PropriedadesHandler
{
    private final RepositorioUsuarios usuarios;
    private final RepositorioTalhoes  talhoes;
    private final RepositorioCustos   custos;
    private final Sessoes             sessoes;
    private dados.estoque.RepositorioEstoque estoque;
    private RepositorioAtividades atividades;
    private dados.cultivo.RepositorioSafras safras;
    private dados.manutencao.RepositorioEquipamentos equipamentos;

    /*
        Injeção opcional. Se ficar nula, a exclusão não consulta safras
        (o servidor liga isso quando o módulo de safras sobe).
    */
    public void setSafras (dados.cultivo.RepositorioSafras safras)
    {
        this.safras = safras;
    }

    /*
        Injeção opcional. Equipamento, mesmo inativo, segura o histórico
        de manutenção e impede excluir a propriedade.
    */
    public void setEquipamentos (dados.manutencao.RepositorioEquipamentos equipamentos)
    {
        this.equipamentos = equipamentos;
    }

    /*
        Injeção opcional. Com equipe, a listagem também mostra fazendas em
        que o usuário é colaborador, e selecionar aceita esse vínculo.
    */
    public void setEquipe (RepositorioEquipe equipe)
    {
        this.equipe = equipe;
    }
    private final RepositorioOcorrencias ocorrencias;
    private RepositorioEquipe equipe;

    // O servidor injeta o estoque para impedir exclusão de propriedade com histórico.
    public void setEstoque (dados.estoque.RepositorioEstoque estoque)
    {
        this.estoque = estoque;
    }

    // Atividades e sua timeline também dependem da propriedade selecionada.
    public void setAtividades (RepositorioAtividades atividades)
    {
        this.atividades = atividades;
    }

    /*
        Atalho com custos e sem ocorrências. O DELETE ainda barra custos;
        sem o repositório de ocorrências, esse histórico não entra na checagem.
    */
    public PropriedadesHandler (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, RepositorioCustos custos,
                                Sessoes sessoes) throws Exception
    {
        this (usuarios, talhoes, custos, sessoes, null);
    }

    /*
        Atalho sem o repositório de custos. Ocorrências, se vierem, entram
        na trava do DELETE.
    */
    public PropriedadesHandler (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, Sessoes sessoes,
                                RepositorioOcorrencias ocorrencias) throws Exception
    {
        this (usuarios, talhoes, null, sessoes, ocorrencias);
    }

    /*
        Usuários, talhões e sessões são obrigatórios. Custos e ocorrências
        podem ser nulos; os outros módulos entram pelos setters.
    */
    public PropriedadesHandler (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, RepositorioCustos custos,
                                Sessoes sessoes, RepositorioOcorrencias ocorrencias) throws Exception
    {
        if (usuarios == null || talhoes == null || sessoes == null)
            throw new Exception ("Dependencia de propriedades ausente");
        this.usuarios = usuarios;
        this.talhoes  = talhoes;
        this.custos   = custos;
        this.sessoes  = sessoes;
        this.ocorrencias = ocorrencias;
    }

    /*
        Registra o CRUD e a seleção. periodos-safra vem antes de {id}
        no código, mas o casamento é por segmento, não por ordem de prefixo.
    */
    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/propriedades",                 this::listar);
        roteador.adicionar ("GET",    "/api/propriedades/periodos-safra",  this::periodosSafra);
        roteador.adicionar ("POST",   "/api/propriedades",                 this::criar);
        roteador.adicionar ("GET",    "/api/propriedades/{id}",            this::consultar);
        roteador.adicionar ("PUT",    "/api/propriedades/{id}",            this::atualizar);
        roteador.adicionar ("DELETE", "/api/propriedades/{id}",            this::excluir);
        roteador.adicionar ("POST",   "/api/propriedades/{id}/selecionar", this::selecionar);
    }

    /*
        JSON que pode sair da API. Esconde usuarioId.
        Cadastro antigo gravava farm/area; se nome vier vazio, copia farm.
        Não inventa área numérica quando o documento legado não tem.
    */
    private static Document publico (Document propriedade)
    {
        Document ret = Json.publico (propriedade, "usuarioId");
        // Registros criados pela primeira versão de cadastro usavam farm/area.
        // A leitura continua compatível, sem inventar uma área numérica inexistente.
        if (ret.getString ("nome") == null)
            ret.append ("nome", ret.getString ("farm"));
        return ret;
    }

    /*
        Lê o _id seja ObjectId (Mongo) ou texto (modo memória).
        Usado para comparar a propriedade da sessão com a da lista.
    */
    private static String id (Document propriedade)
    {
        Object valor = propriedade.get ("_id");
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : valor.toString ();
    }

    /*
        Valida o corpo de criar e de atualizar. Não é rota.
        Devolve null quando há erro; o chamador responde com o 400 do Validacao.
        Área tem de ser número finito, maior que zero e até 100 milhões de hectares.
        Safra, se preenchida, precisa do formato AAAA/AA.
    */
    private static Document lerPropriedade (Document corpo, Validacao validacao)
    {
        String nome      = validacao.texto ("nome", "Informe o nome da propriedade.", 120);
        String localizacao = validacao.texto ("localizacao", "Informe a localização.", 160);
        String municipio = validacao.texto ("municipio", "Informe o município.", 120);
        String safra     = validacao.texto ("safraAtual", "Informe a safra atual.", 20);
        if (safra != null && !PeriodoSafra.valida (safra))
            validacao.erro ("safraAtual", "Use o formato AAAA/AA (ex.: 2026/27).");
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

    /*
        Propriedades das quais o usuário da sessão é dono.
        Não inclui as fazendas em que ele só colabora.
    */
    private List<Document> propriedadesDoUsuario (Requisicao requisicao) throws Exception
    {
        return this.usuarios.listarPropriedades (requisicao.getSessao ().getUsuarioId ());
    }

    /*
        Uma propriedade do dono, pelo id da URL. Null se o id não for dele:
        o chamador responde 404, sem dizer se a fazenda existe para outra conta.
    */
    private Document propriedadeDoUsuario (Requisicao requisicao, String propriedadeId) throws Exception
    {
        for (Document propriedade : this.propriedadesDoUsuario (requisicao))
            if (PropriedadesHandler.id (propriedade).equals (propriedadeId))
                return propriedade;
        return null;
    }

    /*
        GET /api/propriedades/periodos-safra
        Monta os períodos que o seletor de safra pode oferecer.
        200: padrão da data de hoje, sugestões, períodos já usados
        (talhão, ciclo e safra atual da propriedade) e a união ordenada.
        Não há 400: a rota não recebe corpo.
    */
    private Resposta periodosSafra (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        LinkedHashSet<String> emUso = new LinkedHashSet<String> ();

        for (Document talhao : this.talhoes.listar (propriedadeId))
        {
            String safra = talhao.getString ("safra");
            if (safra != null && PeriodoSafra.valida (safra))
                emUso.add (safra.trim ());
        }

        if (this.safras != null)
            for (Document ciclo : this.safras.listar (propriedadeId))
            {
                String inicio = ciclo.getString ("dataInicio");
                if (inicio == null)
                    continue;

                try
                {
                    emUso.add (PeriodoSafra.deData (LocalDate.parse (inicio)));
                }
                catch (Exception ignorado)
                {
                    // Data de início ilegível não entra na lista; a rota segue.
                }
            }

        for (Document propriedade : this.propriedadesDoUsuario (requisicao))
            if (PropriedadesHandler.id (propriedade).equals (propriedadeId))
            {
                String atual = propriedade.getString ("safraAtual");
                if (atual != null && PeriodoSafra.valida (atual))
                    emUso.add (atual.trim ());

                break;
            }

        List<String> sugeridos = PeriodoSafra.sugerir (3, 2);
        List<String> opcoes    = PeriodoSafra.unirOrdenar (emUso, sugeridos);

        return Resposta.json (200, new Document ("padrao", PeriodoSafra.padraoAtual ())
                                 .append ("sugeridos", sugeridos)
                                 .append ("emUso", new ArrayList<String> (emUso))
                                 .append ("opcoes", opcoes));
    }

    /*
        GET /api/propriedades
        Lista as fazendas do dono e, se a equipe estiver ligada, as em que
        ele colabora. Cada item diz se está selecionada na sessão e o papel.
        200 com o array. Dono repetido na equipe não aparece duas vezes.
    */
    private Resposta listar (Requisicao requisicao) throws Exception
    {
        ArrayList<Document> ret = new ArrayList<Document> ();
        String ativa = requisicao.getSessao ().getPropriedadeId ();
        LinkedHashSet<String> vistos = new LinkedHashSet<String> ();

        for (Document propriedade : this.propriedadesDoUsuario (requisicao))
        {
            String id = PropriedadesHandler.id (propriedade);
            vistos.add (id);
            Document item = PropriedadesHandler.publico (propriedade);
            item.append ("selecionada", id.equals (ativa));
            item.append ("papel", PapelNaPropriedade.PROPRIETARIO);
            ret.add (item);
        }

        if (this.equipe != null)
            for (String id : this.equipe.listarPropriedadesColaborador (requisicao.getSessao ().getUsuarioId ()))
            {
                // Já entrou como dono: não duplica a linha.
                if (vistos.contains (id))
                    continue;

                Document propriedade = this.usuarios.buscarPropriedade (id);
                if (propriedade == null)
                    continue;

                Document item = PropriedadesHandler.publico (propriedade);
                item.append ("selecionada", id.equals (ativa));
                item.append ("papel", PapelNaPropriedade.COLABORADOR);
                ret.add (item);
                vistos.add (id);
            }

        return Resposta.json (200, Json.escrever (ret));
    }

    /*
        POST /api/propriedades
        Cria outra fazenda para o usuário da sessão. Não a seleciona sozinha.
        400: nome, localização, município, safra ou área inválidos.
        404: o repositório devolveu null (usuário ausente na memória, ou id inválido).
        409: já existe propriedade com esse nome.
        201: documento público da fazenda criada.
    */
    private Resposta criar (Requisicao requisicao) throws Exception
    {
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        Document propriedade = PropriedadesHandler.lerPropriedade (corpo, validacao);
        if (propriedade == null)
            return validacao.resposta ();
        propriedade.append ("criadaEm", propriedade.getString ("atualizadoEm"));
        try
        {
            Document criada = this.usuarios.criarPropriedade (requisicao.getSessao ().getUsuarioId (), propriedade);
            if (criada == null)
                return Resposta.erro (404, "Não encontrado.");
            return Resposta.json (201, PropriedadesHandler.publico (criada));
        }
        catch (ConflitoDeCadastro conflito)
        {
            return Resposta.json (409, new Document ("error", "Já existe uma propriedade com esse nome.")
                                       .append ("fields", new Document ("nome", "Este nome de propriedade já está em uso.")));
        }
    }

    /*
        GET /api/propriedades/{id}
        Só devolve fazenda da qual o usuário é dono.
        404: id alheio ou inexistente (a mesma frase nos dois casos).
        200: documento público.
    */
    private Resposta consultar (Requisicao requisicao) throws Exception
    {
        Document propriedade = this.propriedadeDoUsuario (requisicao, requisicao.getParametro ("id"));
        return propriedade == null ? Resposta.erro (404, "Não encontrado.") : Resposta.json (200, PropriedadesHandler.publico (propriedade));
    }

    /*
        PUT /api/propriedades/{id}
        Atualiza nome, localização, município, safra e área.
        A trava em sessoes serializa com login, seleção e exclusão, para o
        contexto da sessão não mudar no meio da gravação.
        Status reais estão em atualizarProtegido.
    */
    private Resposta atualizar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes) { return this.atualizarProtegido (requisicao); }
    }

    /*
        Corpo do PUT, já dentro da trava.
        404: a fazenda não é do usuário da sessão.
        400: algum campo obrigatório falhou em lerPropriedade.
        200: documento relido depois da gravação.
    */
    private Resposta atualizarProtegido (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        if (this.propriedadeDoUsuario (requisicao, id) == null)
            return Resposta.erro (404, "Não encontrado.");
        Document corpo = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        Document campos = PropriedadesHandler.lerPropriedade (corpo, validacao);
        if (campos == null)
            return validacao.resposta ();
        this.usuarios.atualizarPropriedade (requisicao.getSessao ().getUsuarioId (), id, campos);
        return Resposta.json (200, PropriedadesHandler.publico (this.propriedadeDoUsuario (requisicao, id)));
    }

    /*
        DELETE /api/propriedades/{id}
        synchronized (sessoes): exclusão, seleção e login compartilham o
        monitor. Assim ninguém seleciona a fazenda enquanto os 409 são
        avaliados, nem o login reabre sessão no meio da exclusão.
        Status reais estão em excluirProtegido.
    */
    private Resposta excluir (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes) { return this.excluirProtegido (requisicao); }
    }

    /*
        Corpo do DELETE, já dentro da trava.
        404: id alheio, inexistente, ou a exclusão no repositório não achou a linha.
        409, um motivo por vez: última fazenda da conta, fazenda ativa nesta
        sessão, fazenda ativa em outra sessão, ou ainda há talhão, estoque,
        ocorrência, custo, atividade, safra ou equipamento.
        204: propriedade removida, sem corpo.
    */
    private Resposta excluirProtegido (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        if (this.propriedadeDoUsuario (requisicao, id) == null)
            return Resposta.erro (404, "Não encontrado.");
        if (this.propriedadesDoUsuario (requisicao).size () <= 1)
            return Resposta.json (409, new Document ("error", "A última propriedade não pode ser excluída."));
        if (id.equals (requisicao.getSessao ().getPropriedadeId ()))
            return Resposta.json (409, new Document ("error", "Selecione outra propriedade antes de excluir a atual."));
        if (this.sessoes.propriedadeEmUso (id))
            return Resposta.json (409, new Document ("error", "A propriedade está em uso em outra sessão. Encerre ou troque esse contexto antes de excluir."));
        if (!this.talhoes.listar (id).isEmpty ())
            return Resposta.json (409, new Document ("error", "Exclua ou mova os talhões antes de excluir a propriedade."));
        if (this.estoque != null && (!this.estoque.listar (id).isEmpty () || !this.estoque.listarMovimentacoes (id, null).isEmpty ()))
            return Resposta.json (409, new Document ("error", "A propriedade possui catálogo ou histórico de estoque e não pode ser excluída."));
        // Uma ocorrência geral também depende da propriedade, mesmo depois de excluída da lista.
        if (this.ocorrencias != null && this.ocorrencias.temRegistros (id))
            return Resposta.json (409, new Document ("error", "A propriedade possui ocorrências ou histórico preservado e não pode ser excluída."));
        // Custos sem talhão também pertencem à propriedade e ficariam órfãos.
        if (this.custos != null && !this.custos.listar (id).isEmpty ())
            return Resposta.json (409, new Document ("error", "Exclua os custos antes de excluir a propriedade."));
        if (this.atividades != null && !this.atividades.listar (id).isEmpty ())
            return Resposta.json (409, new Document ("error", "Exclua as atividades antes de excluir a propriedade."));
        if (this.safras != null && !this.safras.listar (id).isEmpty ())
            return Resposta.json (409, new Document ("error", "Exclua as safras antes de excluir a propriedade."));
        // Equipamentos não são excluídos: mesmo inativos preservam a manutenção.
        if (this.equipamentos != null && !this.equipamentos.listar (id).isEmpty ())
            return Resposta.json (409, new Document ("error", "A propriedade possui equipamentos e histórico de manutenção preservado."));
        if (!this.usuarios.excluirPropriedade (requisicao.getSessao ().getUsuarioId (), id))
            return Resposta.erro (404, "Não encontrado.");
        return Resposta.semConteudo ();
    }

    /*
        POST /api/propriedades/{id}/selecionar
        Troca a propriedade ativa guardada na sessão deste token.
        A trava impede selecionar ao mesmo tempo em que outra requisição
        exclui a fazenda ou cria a sessão no login.
    */
    private Resposta selecionar (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes) { return this.selecionarProtegido (requisicao); }
    }

    /*
        Dono ou colaborador com vínculo ativo podem selecionar.
        Equipe nula: só o dono. Não é rota; selecionarProtegido responde 404.
    */
    private boolean podeSelecionar (Requisicao requisicao, String propriedadeId) throws Exception
    {
        if (this.propriedadeDoUsuario (requisicao, propriedadeId) != null)
            return true;

        if (this.equipe == null)
            return false;

        return this.equipe.membroAtivoDoUsuario (propriedadeId, requisicao.getSessao ().getUsuarioId ()) != null;
    }

    /*
        Corpo do POST selecionar, já dentro da trava.
        404: o usuário não é dono nem colaborador ativo dessa fazenda.
        401: o token não existe mais quando a sessão vai ser atualizada.
        200: a sessão deste token passou a apontar para o id.
    */
    private Resposta selecionarProtegido (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        if (!this.podeSelecionar (requisicao, id))
            return Resposta.erro (404, "Não encontrado.");
        if (!this.sessoes.selecionarPropriedade (requisicao.getToken (), id))
            return Resposta.erro (401, "Não autorizado.");
        return Resposta.json (200, new Document ("propriedadeId", id));
    }
}
