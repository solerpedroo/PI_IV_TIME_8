/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package handlers;

import dados.RepositorioCustos;
import dados.RepositorioEquipamentos;
import dados.RepositorioIntervencoes;
import dados.RepositorioPreventivas;
import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import http.Manipulador;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import protocolo.mensagens.RespostaDeAgendaDeManutencao;
import seguranca.Sessao;
import seguranca.Sessoes;
import servicos.AlgoritmosDeManutencao;
import servicos.CustosDeManutencao;
import util.Json;
import util.Validacao;

/*
    Manutencao de equipamentos agricolas (#234, #235, #236).

    GET    /api/equipamentos?status=&tipo=&q=           lista (com proximaPreventiva e ultimaIntervencao)
    POST   /api/equipamentos                            cadastra (nome unico por propriedade)
    GET    /api/equipamentos/{id}                       consulta
    PUT    /api/equipamentos/{id}                       altera (sem exclusao: sai de uso com status Inativo)
    GET    /api/equipamentos/{id}/historico             intervencoes, da mais recente para a mais antiga
    GET    /api/preventivas?equipamentoId=&de=&ate=&situacao=   agenda (situacao calculada no socket)
    POST   /api/preventivas                             programa
    PUT    /api/preventivas/{id}                        altera ou cancela (status: cancelada)
    DELETE /api/preventivas/{id}                        exclui (409 se ja realizada)
    GET    /api/intervencoes?equipamentoId=&tipo=&de=&ate=
    POST   /api/intervencoes                            registra (com valor: gera custo em Maquinario)
    PUT    /api/intervencoes/{id}                       altera (o custo acompanha)
    DELETE /api/intervencoes/{id}                       exclui (o custo vai junto)
    GET    /api/manutencao/resumo                       indicadores da tela

    Validacoes de campo e as contas da agenda ficam no servidor de algoritmos; aqui ficam o banco,
    a sessao e as regras que dependem dele. Registro de outra propriedade e 404 (nunca 403).
    Ordem das gravacoes: tudo e validado antes (socket, vinculos, safra) e so entao gravado. Se o
    custo falhar depois da intervencao gravada, responde 201/200 com custoPendente: true; editar a
    intervencao de novo recria o custo que faltou.
*/
public class ManutencaoHandler implements Manipulador
{
    private static final ZoneId FUSO        = ZoneId.of ("America/Sao_Paulo");
    private static final String FINALIZACAO = "Finaliza\u00e7\u00e3o";
    private static final String PROGRAMADA  = "programada";
    private static final String REALIZADA   = "realizada";
    private static final String CANCELADA   = "cancelada";
    private static final int    AVISO       = 7;

    private final RepositorioEquipamentos equipamentos;
    private final RepositorioPreventivas  preventivas;
    private final RepositorioIntervencoes intervencoes;
    private final RepositorioTalhoes      talhoes;
    private final RepositorioSafras       safras;
    private final RepositorioCustos       custos;
    private final CustosDeManutencao      manutencao;
    private final Sessoes                 sessoes;

    public ManutencaoHandler (RepositorioEquipamentos equipamentos, RepositorioPreventivas preventivas,
                              RepositorioIntervencoes intervencoes, RepositorioTalhoes talhoes, RepositorioSafras safras,
                              RepositorioCustos custos, CustosDeManutencao manutencao, Sessoes sessoes) throws Exception
    {
        if (equipamentos == null)
            throw new Exception ("Repositorio de equipamentos ausente");

        if (preventivas == null)
            throw new Exception ("Repositorio de preventivas ausente");

        if (intervencoes == null)
            throw new Exception ("Repositorio de intervencoes ausente");

        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        if (safras == null)
            throw new Exception ("Repositorio de safras ausente");

        if (custos == null)
            throw new Exception ("Repositorio de custos ausente");

        if (manutencao == null)
            throw new Exception ("Custos de manutencao ausentes");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        this.equipamentos = equipamentos;
        this.preventivas  = preventivas;
        this.intervencoes = intervencoes;
        this.talhoes      = talhoes;
        this.safras       = safras;
        this.custos       = custos;
        this.manutencao   = manutencao;
        this.sessoes      = sessoes;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET",    "/api/manutencao/resumo",            this);
        roteador.adicionar ("GET",    "/api/equipamentos",                 this);
        roteador.adicionar ("POST",   "/api/equipamentos",                 this);
        roteador.adicionar ("GET",    "/api/equipamentos/{id}",            this);
        roteador.adicionar ("PUT",    "/api/equipamentos/{id}",            this);
        roteador.adicionar ("GET",    "/api/equipamentos/{id}/historico",  this);
        roteador.adicionar ("GET",    "/api/preventivas",                  this);
        roteador.adicionar ("POST",   "/api/preventivas",                  this);
        roteador.adicionar ("PUT",    "/api/preventivas/{id}",             this);
        roteador.adicionar ("DELETE", "/api/preventivas/{id}",             this);
        roteador.adicionar ("GET",    "/api/intervencoes",                 this);
        roteador.adicionar ("POST",   "/api/intervencoes",                 this);
        roteador.adicionar ("PUT",    "/api/intervencoes/{id}",            this);
        roteador.adicionar ("DELETE", "/api/intervencoes/{id}",            this);
    }

    @Override
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   metodo  = requisicao.getMetodo ().equals ("HEAD") ? "GET" : requisicao.getMetodo ();
        String[] partes  = requisicao.getCaminho ().substring (1).split ("/", -1); // api/recurso/{id}/sub
        String   recurso = partes[1];
        boolean  comId   = partes.length >= 3;

        if (recurso.equals ("manutencao"))
            ret = this.resumo (requisicao);
        else if (recurso.equals ("equipamentos"))
        {
            if (partes.length == 4)
                ret = this.historico (requisicao);
            else if (!comId)
                ret = metodo.equals ("GET") ? this.listarEquipamentos (requisicao) : this.criarEquipamento (requisicao);
            else
                ret = metodo.equals ("GET") ? this.consultarEquipamento (requisicao) : this.atualizarEquipamento (requisicao);
        }
        else if (recurso.equals ("preventivas"))
        {
            if (!comId)
                ret = metodo.equals ("GET") ? this.listarPreventivas (requisicao) : this.criarPreventiva (requisicao);
            else
                ret = metodo.equals ("PUT") ? this.atualizarPreventiva (requisicao) : this.excluirPreventiva (requisicao);
        }
        else if (!comId)
            ret = metodo.equals ("GET") ? this.listarIntervencoes (requisicao) : this.criarIntervencao (requisicao);
        else
            ret = metodo.equals ("PUT") ? this.atualizarIntervencao (requisicao) : this.excluirIntervencao (requisicao);

        return ret;
    }

    // ------------------------------------------------------------------ auxiliares

    private static String id (Document documento)
    {
        Object valor = documento.get ("_id");

        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    private static LinkedHashMap<String, Document> porId (List<Document> documentos)
    {
        LinkedHashMap<String, Document> ret = new LinkedHashMap<String, Document> ();

        for (Document documento : documentos)
            ret.put (ManutencaoHandler.id (documento), documento);

        return ret;
    }

    private static Document publico (Document documento)
    {
        return Json.publico (documento, "propriedadeId", "nomeNormalizado");
    }

    private static String hoje ()
    {
        return LocalDate.now (ManutencaoHandler.FUSO).toString ();
    }

    private static boolean finalizada (Document safra)
    {
        return safra != null && ManutencaoHandler.FINALIZACAO.equals (safra.getString ("etapa"));
    }

    private static Resposta safraFechada ()
    {
        return Resposta.json (409, new Document ("error", "Safra finalizada: custos e interven\u00e7\u00f5es dela n\u00e3o podem ser alterados.")
                                       .append ("fields", new Document ("safraId", "A safra escolhida j\u00e1 foi finalizada.")));
    }

    private static Resposta equipamentoNaoEncontrado ()
    {
        return Resposta.json (404, new Document ("error", "Equipamento n\u00e3o encontrado.")
                                       .append ("fields", new Document ("equipamentoId", "Selecione um equipamento da propriedade.")));
    }

    private static Resposta nomeRepetido ()
    {
        return Resposta.json (409, new Document ("error", "Equipment name already exists")
                                       .append ("fields", new Document ("nome", "J\u00e1 existe um equipamento com esse nome.")));
    }

    // a escrita usa a propriedade da sessao; se ela mudou no meio (outra aba selecionou outra), recusa
    private Resposta contextoMudou (Requisicao requisicao)
    {
        Resposta ret   = null;
        Sessao   atual = this.sessoes.buscar (requisicao.getToken ());

        if (atual == null)
            ret = Resposta.erro (401, "Unauthorized");
        else if (!atual.getPropriedadeId ().equals (requisicao.getSessao ().getPropriedadeId ()))
            ret = Resposta.erro (409, "Property context changed; reload before saving");

        return ret;
    }

    // filtro de texto da consulta: null quando ausente, vazio ou "todos"
    private static String filtro (Requisicao requisicao, String nome)
    {
        String ret = requisicao.getConsulta (nome);

        if (ret != null)
        {
            ret = ret.trim ();
            if (ret.isEmpty () || ret.equalsIgnoreCase ("todos") || ret.equalsIgnoreCase ("todas"))
                ret = null;
        }

        return ret;
    }

    // texto opcional do corpo: null se ausente ou vazio; "" se nao for texto (tratado como invalido)
    private static String opcional (Document corpo, String campo)
    {
        String ret   = null;
        Object valor = corpo.get (campo);

        if (valor instanceof String)
            ret = ((String) valor).trim ().isEmpty () ? null : ((String) valor).trim ();
        else if (valor != null)
            ret = "";

        return ret;
    }

    // periodo de/ate da consulta (mesma validacao de data dos formularios); devolve null se valido
    private static Resposta lerPeriodo (Requisicao requisicao, String[] periodo) throws Exception
    {
        Document corpo = new Document ();

        if (ManutencaoHandler.filtro (requisicao, "de") != null)
            corpo.append ("de", requisicao.getConsulta ("de").trim ());
        if (ManutencaoHandler.filtro (requisicao, "ate") != null)
            corpo.append ("ate", requisicao.getConsulta ("ate").trim ());

        Validacao validacao = new Validacao (corpo);
        periodo[0] = corpo.containsKey ("de")  ? validacao.data ("de",  "Informe a data inicial.") : null;
        periodo[1] = corpo.containsKey ("ate") ? validacao.data ("ate", "Informe a data final.")   : null;

        if (periodo[0] != null && periodo[1] != null && periodo[1].compareTo (periodo[0]) < 0)
            validacao.erro ("ate", "A data final deve ser igual ou posterior \u00e0 inicial.");

        return validacao.temErros () ? validacao.resposta () : null;
    }

    private static boolean noPeriodo (String data, String[] periodo)
    {
        return data != null && (periodo[0] == null || data.compareTo (periodo[0]) >= 0) &&
               (periodo[1] == null || data.compareTo (periodo[1]) <= 0);
    }

    // insercao ordenada pela chave (crescente ou decrescente); no empate, pelo id
    private static void inserir (ArrayList<Document> lista, Document documento, String campo, boolean crescente)
    {
        String chave   = documento.getString (campo) + ManutencaoHandler.id (documento);
        int    posicao = 0;

        while (posicao < lista.size ())
        {
            String outra    = lista.get (posicao).getString (campo) + ManutencaoHandler.id (lista.get (posicao));
            int    comparar = outra.compareTo (chave);

            if (crescente ? comparar > 0 : comparar < 0)
                break;
            posicao++;
        }

        lista.add (posicao, documento);
    }

    private static double numero (Document documento, String campo)
    {
        Object valor = documento.get (campo);

        return valor instanceof Number ? ((Number) valor).doubleValue () : 0;
    }

    // a preventiva programada mais proxima do equipamento; null se nao houver
    private static Document proximaPreventiva (String equipamentoId, List<Document> preventivas)
    {
        Document escolhida = null;

        for (Document preventiva : preventivas)
            if (equipamentoId.equals (preventiva.getString ("equipamentoId")) &&
                ManutencaoHandler.PROGRAMADA.equals (preventiva.getString ("status")) &&
                (escolhida == null || preventiva.getString ("dataPrevista").compareTo (escolhida.getString ("dataPrevista")) < 0))
                escolhida = preventiva;

        return escolhida == null ? null : new Document ("id",           ManutencaoHandler.id (escolhida))
                                              .append  ("descricao",    escolhida.getString ("descricao"))
                                              .append  ("dataPrevista", escolhida.getString ("dataPrevista"));
    }

    // a intervencao mais recente do equipamento; null se nao houver
    private static Document ultimaIntervencao (String equipamentoId, List<Document> intervencoes)
    {
        Document escolhida = null;

        for (Document intervencao : intervencoes)
            if (equipamentoId.equals (intervencao.getString ("equipamentoId")) &&
                (escolhida == null ||
                 (intervencao.getString ("data") + ManutencaoHandler.id (intervencao)).compareTo (escolhida.getString ("data") + ManutencaoHandler.id (escolhida)) > 0))
                escolhida = intervencao;

        return escolhida == null ? null : new Document ("id",        ManutencaoHandler.id (escolhida))
                                              .append  ("data",      escolhida.getString ("data"))
                                              .append  ("tipo",      escolhida.getString ("tipo"))
                                              .append  ("descricao", escolhida.getString ("descricao"));
    }

    private Document equipamentoPublico (Document equipamento, List<Document> preventivas, List<Document> intervencoes)
    {
        String equipamentoId = ManutencaoHandler.id (equipamento);

        return ManutencaoHandler.publico (equipamento)
                   .append ("proximaPreventiva", ManutencaoHandler.proximaPreventiva (equipamentoId, preventivas))
                   .append ("ultimaIntervencao", ManutencaoHandler.ultimaIntervencao (equipamentoId, intervencoes));
    }

    private static Document preventivaPublica (Document preventiva, Map<String, Document> equipamentos,
                                               RespostaDeAgendaDeManutencao agenda)
    {
        String   preventivaId = ManutencaoHandler.id (preventiva);
        Document equipamento  = equipamentos.get (preventiva.getString ("equipamentoId"));
        String   situacao     = preventiva.getString ("status");
        Long     dias         = null;

        if (agenda != null && agenda.getSituacao ().containsKey (preventivaId))
        {
            situacao = agenda.getSituacao ().get (preventivaId);
            dias     = agenda.getDiasAte ().get (preventivaId);
        }

        return ManutencaoHandler.publico (preventiva)
                   .append ("equipamentoNome", equipamento == null ? null : equipamento.getString ("nome"))
                   .append ("situacao",        situacao)
                   .append ("diasAte",         dias);
    }

    private static Document intervencaoPublica (Document intervencao, Map<String, Document> equipamentos,
                                                Map<String, Document> talhoes, Map<String, Document> safras)
    {
        Document equipamento = equipamentos.get (intervencao.getString ("equipamentoId"));
        Document talhao      = talhoes.get (intervencao.getString ("talhaoId"));
        Document safra       = safras.get (intervencao.getString ("safraId"));

        return ManutencaoHandler.publico (intervencao)
                   .append ("equipamentoNome", equipamento == null ? null : equipamento.getString ("nome"))
                   .append ("talhaoNome",      talhao == null ? null : talhao.getString ("nome"))
                   .append ("safraNome",       safra == null ? null : safra.getString ("nome"))
                   .append ("custoValor",      intervencao.getString ("custoId") == null ? null : intervencao.get ("valor"));
    }

    // agenda das preventivas pelo socket (lista vazia nao chama o servidor)
    private static RespostaDeAgendaDeManutencao agenda (List<Document> preventivas) throws Exception
    {
        RespostaDeAgendaDeManutencao ret = null;

        if (!preventivas.isEmpty ())
            ret = AlgoritmosDeManutencao.agenda (preventivas, ManutencaoHandler.hoje (), Integer.valueOf (ManutencaoHandler.AVISO));

        return ret;
    }

    // talhao e safra opcionais com as mesmas regras do custo; os campos ficam em destino
    private static void lerVinculos (Document corpo, Validacao validacao, Map<String, Document> talhoes,
                                     Map<String, Document> safras, Document destino)
    {
        String talhaoId = ManutencaoHandler.opcional (corpo, "talhaoId");
        String safraId  = ManutencaoHandler.opcional (corpo, "safraId");

        if (talhaoId != null && !talhoes.containsKey (talhaoId))
            validacao.erro ("talhaoId", "Selecione um talh\u00e3o da propriedade.");

        if (safraId != null && !safras.containsKey (safraId))
            validacao.erro ("safraId", "Selecione uma safra da propriedade.");

        if (talhoes.containsKey (talhaoId) && safras.containsKey (safraId))
        {
            List<String> vinculos = safras.get (safraId).getList ("talhoes", String.class);
            if (vinculos == null || !vinculos.contains (talhaoId))
                validacao.erro ("talhaoId", "O talh\u00e3o n\u00e3o faz parte da safra escolhida.");
        }

        destino.append ("talhaoId", talhaoId).append ("safraId", safraId);
    }

    // horimetro informado maior que o do equipamento atualiza o equipamento
    private void atualizarHorimetro (String propriedadeId, Document equipamento, Document intervencao) throws Exception
    {
        Object informado = intervencao.get ("horimetro");

        if (informado instanceof Number && (!(equipamento.get ("horimetro") instanceof Number) ||
            ((Number) informado).doubleValue () > ManutencaoHandler.numero (equipamento, "horimetro")))
            this.equipamentos.atualizar (propriedadeId, ManutencaoHandler.id (equipamento),
                                         new Document ("horimetro", informado).append ("atualizadoEm", Instant.now ().toString ()));
    }

    // ------------------------------------------------------------------ equipamentos

    // GET /api/equipamentos?status=&tipo=&q=
    private Resposta listarEquipamentos (Requisicao requisicao) throws Exception
    {
        String              propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String              status        = ManutencaoHandler.filtro (requisicao, "status");
        String              tipo          = ManutencaoHandler.filtro (requisicao, "tipo");
        String              busca         = ManutencaoHandler.filtro (requisicao, "q");
        List<Document>      preventivas   = this.preventivas.listar (propriedadeId);
        List<Document>      intervencoes  = this.intervencoes.listar (propriedadeId);
        ArrayList<Document> ret           = new ArrayList<Document> ();

        if (busca != null)
            busca = busca.toLowerCase (Locale.ROOT);

        for (Document equipamento : this.equipamentos.listar (propriedadeId))
        {
            if (status != null && !status.equals (equipamento.getString ("status")))
                continue;

            if (tipo != null && !tipo.equals (equipamento.getString ("tipo")))
                continue;

            if (busca != null && !equipamento.getString ("nomeNormalizado").contains (busca))
                continue;

            ret.add (this.equipamentoPublico (equipamento, preventivas, intervencoes));
        }

        return Resposta.json (200, Json.escrever (ret));
    }

    // GET /api/equipamentos/{id}
    private Resposta consultarEquipamento (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        Document equipamento   = this.equipamentos.buscarPorId (propriedadeId, requisicao.getParametro ("id"));

        if (equipamento == null)
            ret = ManutencaoHandler.equipamentoNaoEncontrado ();
        else
            ret = Resposta.json (200, this.equipamentoPublico (equipamento, this.preventivas.listar (propriedadeId),
                                                               this.intervencoes.listar (propriedadeId)));

        return ret;
    }

    // POST /api/equipamentos
    private Resposta criarEquipamento (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String                           propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            AlgoritmosDeManutencao.Resultado validado      = AlgoritmosDeManutencao.validarEquipamento (
                requisicao.getCorpoJson (), LocalDate.now (ManutencaoHandler.FUSO).getYear ());

            if (validado.respostaErro != null)
                return validado.respostaErro;

            String   agora       = Instant.now ().toString ();
            Document equipamento = new Document (validado.campos).append ("propriedadeId", propriedadeId)
                                                                 .append ("criadoEm",      agora)
                                                                 .append ("atualizadoEm",  agora);

            if (!this.equipamentos.criar (equipamento))
                return ManutencaoHandler.nomeRepetido ();

            return Resposta.json (201, this.equipamentoPublico (equipamento, new ArrayList<Document> (), new ArrayList<Document> ()));
        }
    }

    // PUT /api/equipamentos/{id}
    private Resposta atualizarEquipamento (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");

            if (this.equipamentos.buscarPorId (propriedadeId, id) == null)
                return ManutencaoHandler.equipamentoNaoEncontrado ();

            AlgoritmosDeManutencao.Resultado validado = AlgoritmosDeManutencao.validarEquipamento (
                requisicao.getCorpoJson (), LocalDate.now (ManutencaoHandler.FUSO).getYear ());

            if (validado.respostaErro != null)
                return validado.respostaErro;

            if (!this.equipamentos.atualizar (propriedadeId, id, new Document (validado.campos).append ("atualizadoEm", Instant.now ().toString ())))
                return ManutencaoHandler.nomeRepetido ();

            return Resposta.json (200, this.equipamentoPublico (this.equipamentos.buscarPorId (propriedadeId, id),
                                                                this.preventivas.listar (propriedadeId),
                                                                this.intervencoes.listar (propriedadeId)));
        }
    }

    // GET /api/equipamentos/{id}/historico
    private Resposta historico (Requisicao requisicao) throws Exception
    {
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String   id            = requisicao.getParametro ("id");
        Document equipamento   = this.equipamentos.buscarPorId (propriedadeId, id);

        if (equipamento == null)
            return ManutencaoHandler.equipamentoNaoEncontrado ();

        LinkedHashMap<String, Document> equipamentos = new LinkedHashMap<String, Document> ();
        LinkedHashMap<String, Document> talhoes      = ManutencaoHandler.porId (this.talhoes.listar (propriedadeId));
        LinkedHashMap<String, Document> safras       = ManutencaoHandler.porId (this.safras.listar (propriedadeId));
        ArrayList<Document>             ordenadas    = new ArrayList<Document> ();
        ArrayList<Document>             ret          = new ArrayList<Document> ();

        equipamentos.put (id, equipamento);

        for (Document intervencao : this.intervencoes.listar (propriedadeId))
            if (id.equals (intervencao.getString ("equipamentoId")))
                ManutencaoHandler.inserir (ordenadas, intervencao, "data", false);

        for (Document intervencao : ordenadas)
            ret.add (ManutencaoHandler.intervencaoPublica (intervencao, equipamentos, talhoes, safras));

        return Resposta.json (200, Json.escrever (ret));
    }

    // ------------------------------------------------------------------ preventivas

    // GET /api/preventivas?equipamentoId=&de=&ate=&situacao=
    private Resposta listarPreventivas (Requisicao requisicao) throws Exception
    {
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String   equipamentoId = ManutencaoHandler.filtro (requisicao, "equipamentoId");
        String   situacao      = ManutencaoHandler.filtro (requisicao, "situacao");
        String[] periodo       = new String[2];
        Resposta invalido      = ManutencaoHandler.lerPeriodo (requisicao, periodo);

        if (invalido != null)
            return invalido;

        if (situacao != null && !List.of ("atrasada", "proxima", "futura", "programada", "realizada", "cancelada").contains (situacao))
            return Resposta.json (400, new Document ("error", "Validation failed")
                                           .append ("fields", new Document ("situacao", "Use atrasada, proxima, futura, programada, realizada ou cancelada.")));

        LinkedHashMap<String, Document> equipamentos = ManutencaoHandler.porId (this.equipamentos.listar (propriedadeId));

        if (equipamentoId != null && !equipamentos.containsKey (equipamentoId))
            return ManutencaoHandler.equipamentoNaoEncontrado ();

        ArrayList<Document> filtradas = new ArrayList<Document> ();
        for (Document preventiva : this.preventivas.listar (propriedadeId))
            if ((equipamentoId == null || equipamentoId.equals (preventiva.getString ("equipamentoId"))) &&
                ManutencaoHandler.noPeriodo (preventiva.getString ("dataPrevista"), periodo))
                ManutencaoHandler.inserir (filtradas, preventiva, "dataPrevista", true);

        RespostaDeAgendaDeManutencao agenda = ManutencaoHandler.agenda (filtradas);
        ArrayList<Document>          ret    = new ArrayList<Document> ();

        for (Document preventiva : filtradas)
        {
            Document item = ManutencaoHandler.preventivaPublica (preventiva, equipamentos, agenda);

            if (situacao == null || situacao.equals (item.getString ("situacao")) ||
                (situacao.equals (ManutencaoHandler.PROGRAMADA) && ManutencaoHandler.PROGRAMADA.equals (item.getString ("status"))))
                ret.add (item);
        }

        return Resposta.json (200, Json.escrever (ret));
    }

    // POST /api/preventivas
    private Resposta criarPreventiva (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String    propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            Document  corpo         = requisicao.getCorpoJson ();
            Validacao validacao     = new Validacao (corpo);
            String    equipamentoId = validacao.texto ("equipamentoId", "Selecione o equipamento.", 40);

            if (validacao.temErros ())
                return validacao.resposta ();

            Document equipamento = this.equipamentos.buscarPorId (propriedadeId, equipamentoId);
            if (equipamento == null)
                return ManutencaoHandler.equipamentoNaoEncontrado ();

            AlgoritmosDeManutencao.Resultado validado = AlgoritmosDeManutencao.validarPreventiva (corpo);
            if (validado.respostaErro != null)
                return validado.respostaErro;

            String   agora      = Instant.now ().toString ();
            Document preventiva = new Document ("equipamentoId", equipamentoId)
                                      .append  ("descricao",     validado.campos.getString ("descricao"))
                                      .append  ("dataPrevista",  validado.campos.getString ("dataPrevista"))
                                      .append  ("intervaloDias", validado.campos.get ("intervaloDias"))
                                      .append  ("status",        ManutencaoHandler.PROGRAMADA)
                                      .append  ("intervencaoId", null)
                                      .append  ("propriedadeId", propriedadeId)
                                      .append  ("criadoEm",      agora)
                                      .append  ("atualizadoEm",  agora);

            this.preventivas.criar (preventiva);

            ArrayList<Document> lista = new ArrayList<Document> ();
            lista.add (preventiva);

            return Resposta.json (201, ManutencaoHandler.preventivaPublica (preventiva, ManutencaoHandler.porId (this.equipamentos.listar (propriedadeId)),
                                                                           ManutencaoHandler.agenda (lista)));
        }
    }

    // PUT /api/preventivas/{id}  (cancelar = status: cancelada)
    private Resposta atualizarPreventiva (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document atual         = this.preventivas.buscarPorId (propriedadeId, id);

            if (atual == null)
                return Resposta.erro (404, "Not found");

            if (ManutencaoHandler.REALIZADA.equals (atual.getString ("status")))
                return Resposta.erro (409, "Preventiva j\u00e1 realizada: corrija pela interven\u00e7\u00e3o.");

            Document  corpo         = requisicao.getCorpoJson ();
            Validacao validacao     = new Validacao (corpo);
            String    equipamentoId = ManutencaoHandler.opcional (corpo, "equipamentoId");
            String    status        = ManutencaoHandler.opcional (corpo, "status");

            if (equipamentoId == null)
                equipamentoId = atual.getString ("equipamentoId");
            else if ("".equals (equipamentoId))
                validacao.erro ("equipamentoId", "Selecione o equipamento.");

            if (status == null)
                status = atual.getString ("status");
            else if (!status.equals (ManutencaoHandler.PROGRAMADA) && !status.equals (ManutencaoHandler.CANCELADA))
                validacao.erro ("status", "Use programada ou cancelada (a realizada vem da interven\u00e7\u00e3o).");

            if (validacao.temErros ())
                return validacao.resposta ();

            if (this.equipamentos.buscarPorId (propriedadeId, equipamentoId) == null)
                return ManutencaoHandler.equipamentoNaoEncontrado ();

            AlgoritmosDeManutencao.Resultado validado = AlgoritmosDeManutencao.validarPreventiva (corpo);
            if (validado.respostaErro != null)
                return validado.respostaErro;

            Document campos = new Document ("equipamentoId", equipamentoId)
                                  .append  ("descricao",     validado.campos.getString ("descricao"))
                                  .append  ("dataPrevista",  validado.campos.getString ("dataPrevista"))
                                  .append  ("intervaloDias", validado.campos.get ("intervaloDias"))
                                  .append  ("status",        status)
                                  .append  ("atualizadoEm",  Instant.now ().toString ());

            if (!this.preventivas.atualizar (propriedadeId, id, campos))
                return Resposta.erro (404, "Not found");

            Document            salva = this.preventivas.buscarPorId (propriedadeId, id);
            ArrayList<Document> lista = new ArrayList<Document> ();
            lista.add (salva);

            return Resposta.json (200, ManutencaoHandler.preventivaPublica (salva, ManutencaoHandler.porId (this.equipamentos.listar (propriedadeId)),
                                                                           ManutencaoHandler.agenda (lista)));
        }
    }

    // DELETE /api/preventivas/{id}
    private Resposta excluirPreventiva (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document preventiva    = this.preventivas.buscarPorId (propriedadeId, id);

            if (preventiva == null)
                ret = Resposta.erro (404, "Not found");
            else if (ManutencaoHandler.REALIZADA.equals (preventiva.getString ("status")))
                ret = Resposta.erro (409, "Preventiva realizada faz parte do hist\u00f3rico: exclua a interven\u00e7\u00e3o.");
            else if (!this.preventivas.excluir (propriedadeId, id))
                ret = Resposta.erro (404, "Not found");
            else
                ret = Resposta.semConteudo ();

            return ret;
        }
    }

    // ------------------------------------------------------------------ intervencoes

    // GET /api/intervencoes?equipamentoId=&tipo=&de=&ate=
    private Resposta listarIntervencoes (Requisicao requisicao) throws Exception
    {
        String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String   equipamentoId = ManutencaoHandler.filtro (requisicao, "equipamentoId");
        String   tipo          = ManutencaoHandler.filtro (requisicao, "tipo");
        String[] periodo       = new String[2];
        Resposta invalido      = ManutencaoHandler.lerPeriodo (requisicao, periodo);

        if (invalido != null)
            return invalido;

        if (tipo != null && !tipo.equals ("preventiva") && !tipo.equals ("corretiva"))
            return Resposta.json (400, new Document ("error", "Validation failed")
                                           .append ("fields", new Document ("tipo", "Use preventiva ou corretiva.")));

        LinkedHashMap<String, Document> equipamentos = ManutencaoHandler.porId (this.equipamentos.listar (propriedadeId));

        if (equipamentoId != null && !equipamentos.containsKey (equipamentoId))
            return ManutencaoHandler.equipamentoNaoEncontrado ();

        LinkedHashMap<String, Document> talhoes   = ManutencaoHandler.porId (this.talhoes.listar (propriedadeId));
        LinkedHashMap<String, Document> safras    = ManutencaoHandler.porId (this.safras.listar (propriedadeId));
        ArrayList<Document>             ordenadas = new ArrayList<Document> ();
        ArrayList<Document>             ret       = new ArrayList<Document> ();

        for (Document intervencao : this.intervencoes.listar (propriedadeId))
            if ((equipamentoId == null || equipamentoId.equals (intervencao.getString ("equipamentoId"))) &&
                (tipo == null || tipo.equals (intervencao.getString ("tipo"))) &&
                ManutencaoHandler.noPeriodo (intervencao.getString ("data"), periodo))
                ManutencaoHandler.inserir (ordenadas, intervencao, "data", false);

        for (Document intervencao : ordenadas)
            ret.add (ManutencaoHandler.intervencaoPublica (intervencao, equipamentos, talhoes, safras));

        return Resposta.json (200, Json.escrever (ret));
    }

    // grava o custo (ou o atualiza) sem desfazer a intervencao ja gravada; true se o custo ficou pendente
    private boolean sincronizarCusto (String propriedadeId, Document intervencao, Document equipamento, boolean novo)
    {
        boolean pendente = false;

        try
        {
            Document custo   = novo ? this.manutencao.registrar (propriedadeId, intervencao, equipamento)
                                    : this.manutencao.atualizar (propriedadeId, intervencao, equipamento);
            String   custoId = custo == null ? null : ManutencaoHandler.id (custo);

            this.intervencoes.atualizar (propriedadeId, ManutencaoHandler.id (intervencao), new Document ("custoId", custoId));
            intervencao.put ("custoId", custoId);
        }
        catch (Exception erro)
        {
            System.err.println ("Custo da intervencao " + ManutencaoHandler.id (intervencao) + " pendente: " + erro);
            pendente = true;
        }

        return pendente;
    }

    // POST /api/intervencoes
    private Resposta criarIntervencao (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String                           propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            Document                         corpo         = requisicao.getCorpoJson ();
            AlgoritmosDeManutencao.Resultado validado      = AlgoritmosDeManutencao.validarIntervencao (corpo, ManutencaoHandler.hoje ());

            if (validado.respostaErro != null)
                return validado.respostaErro;

            Validacao validacao     = new Validacao (corpo);
            String    equipamentoId = validacao.texto ("equipamentoId", "Selecione o equipamento.", 40);

            if (validacao.temErros ())
                return validacao.resposta ();

            Document equipamento = this.equipamentos.buscarPorId (propriedadeId, equipamentoId);
            if (equipamento == null)
                return ManutencaoHandler.equipamentoNaoEncontrado ();

            // preventiva opcional: do mesmo equipamento e ainda programada
            String   preventivaId = ManutencaoHandler.opcional (corpo, "preventivaId");
            Document preventiva   = preventivaId == null || preventivaId.isEmpty () ? null : this.preventivas.buscarPorId (propriedadeId, preventivaId);

            if (preventivaId != null && (preventiva == null || !equipamentoId.equals (preventiva.getString ("equipamentoId")) ||
                                         !ManutencaoHandler.PROGRAMADA.equals (preventiva.getString ("status"))))
                validacao.erro ("preventivaId", "Selecione uma preventiva programada deste equipamento.");
            else if (preventiva != null && !"preventiva".equals (validado.campos.getString ("tipo")))
                validacao.erro ("tipo", "A interven\u00e7\u00e3o de uma preventiva programada \u00e9 do tipo preventiva.");

            LinkedHashMap<String, Document> talhoes   = ManutencaoHandler.porId (this.talhoes.listar (propriedadeId));
            LinkedHashMap<String, Document> safras    = ManutencaoHandler.porId (this.safras.listar (propriedadeId));
            Document                        vinculos  = new Document ();
            ManutencaoHandler.lerVinculos (corpo, validacao, talhoes, safras, vinculos);

            if (validacao.temErros ())
                return validacao.resposta ();

            if (ManutencaoHandler.finalizada (safras.get (vinculos.getString ("safraId"))))
                return ManutencaoHandler.safraFechada ();

            // a proxima preventiva e calculada antes de qualquer gravacao
            Object intervalo = preventiva == null ? null : preventiva.get ("intervaloDias");
            String proxima   = intervalo instanceof Number ? AlgoritmosDeManutencao.proximaData (validado.campos.getString ("data"),
                                                                                                 Integer.valueOf (((Number) intervalo).intValue ()))
                                                           : null;

            // ---------------------------------------------------------------- gravacoes
            String   agora       = Instant.now ().toString ();
            Document intervencao = new Document ("equipamentoId", equipamentoId)
                                       .append  ("preventivaId",  preventiva == null ? null : preventivaId)
                                       .append  ("tipo",          validado.campos.getString ("tipo"))
                                       .append  ("data",          validado.campos.getString ("data"))
                                       .append  ("descricao",     validado.campos.getString ("descricao"))
                                       .append  ("responsavel",   validado.campos.get ("responsavel"))
                                       .append  ("horimetro",     validado.campos.get ("horimetro"))
                                       .append  ("valor",         validado.campos.get ("valor"))
                                       .append  ("talhaoId",      vinculos.getString ("talhaoId"))
                                       .append  ("safraId",       vinculos.getString ("safraId"))
                                       .append  ("custoId",       null)
                                       .append  ("proximaPreventivaId", null)
                                       .append  ("propriedadeId", propriedadeId)
                                       .append  ("criadoEm",      agora)
                                       .append  ("atualizadoEm",  agora);

            this.intervencoes.criar (intervencao);
            String intervencaoId = ManutencaoHandler.id (intervencao);

            if (preventiva != null)
            {
                this.preventivas.atualizar (propriedadeId, preventivaId,
                                            new Document ("status",        ManutencaoHandler.REALIZADA)
                                                .append  ("intervencaoId", intervencaoId)
                                                .append  ("realizadaEm",   intervencao.getString ("data"))
                                                .append  ("atualizadoEm",  agora));

                if (proxima != null)
                {
                    Document nova = new Document ("equipamentoId", equipamentoId)
                                        .append  ("descricao",     preventiva.getString ("descricao"))
                                        .append  ("dataPrevista",  proxima)
                                        .append  ("intervaloDias", intervalo)
                                        .append  ("status",        ManutencaoHandler.PROGRAMADA)
                                        .append  ("intervencaoId", null)
                                        .append  ("geradaPorIntervencaoId", intervencaoId)
                                        .append  ("propriedadeId", propriedadeId)
                                        .append  ("criadoEm",      agora)
                                        .append  ("atualizadoEm",  agora);

                    this.preventivas.criar (nova);
                    this.intervencoes.atualizar (propriedadeId, intervencaoId, new Document ("proximaPreventivaId", ManutencaoHandler.id (nova)));
                    intervencao.put ("proximaPreventivaId", ManutencaoHandler.id (nova));
                }
            }

            this.atualizarHorimetro (propriedadeId, equipamento, intervencao);

            boolean  pendente = intervencao.get ("valor") != null && this.sincronizarCusto (propriedadeId, intervencao, equipamento, true);
            Document resposta = ManutencaoHandler.intervencaoPublica (intervencao, ManutencaoHandler.porId (this.equipamentos.listar (propriedadeId)),
                                                                     talhoes, safras);

            if (pendente)
                resposta.append ("custoPendente", true);

            return Resposta.json (201, resposta);
        }
    }

    // PUT /api/intervencoes/{id}
    private Resposta atualizarIntervencao (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document atual         = this.intervencoes.buscarPorId (propriedadeId, id);

            if (atual == null)
                return Resposta.erro (404, "Not found");

            LinkedHashMap<String, Document> talhoes = ManutencaoHandler.porId (this.talhoes.listar (propriedadeId));
            LinkedHashMap<String, Document> safras  = ManutencaoHandler.porId (this.safras.listar (propriedadeId));

            // sair de uma safra finalizada tambem mexeria no historico (e no custo) dela
            if (ManutencaoHandler.finalizada (safras.get (atual.getString ("safraId"))))
                return ManutencaoHandler.safraFechada ();

            Document                         corpo    = requisicao.getCorpoJson ();
            AlgoritmosDeManutencao.Resultado validado = AlgoritmosDeManutencao.validarIntervencao (corpo, ManutencaoHandler.hoje ());

            if (validado.respostaErro != null)
                return validado.respostaErro;

            Validacao validacao     = new Validacao (corpo);
            String    equipamentoId = ManutencaoHandler.opcional (corpo, "equipamentoId");
            String    preventivaId  = ManutencaoHandler.opcional (corpo, "preventivaId");
            String    vinculada     = atual.getString ("preventivaId");

            if (equipamentoId != null && !equipamentoId.equals (atual.getString ("equipamentoId")))
                validacao.erro ("equipamentoId", "O equipamento de uma interven\u00e7\u00e3o n\u00e3o pode ser trocado.");

            if (preventivaId != null && !preventivaId.equals (vinculada))
                validacao.erro ("preventivaId", "Para trocar a preventiva, exclua a interven\u00e7\u00e3o e registre de novo.");

            if (vinculada != null && !"preventiva".equals (validado.campos.getString ("tipo")))
                validacao.erro ("tipo", "A interven\u00e7\u00e3o de uma preventiva programada \u00e9 do tipo preventiva.");

            Document vinculos = new Document ();
            ManutencaoHandler.lerVinculos (corpo, validacao, talhoes, safras, vinculos);

            if (validacao.temErros ())
                return validacao.resposta ();

            if (ManutencaoHandler.finalizada (safras.get (vinculos.getString ("safraId"))))
                return ManutencaoHandler.safraFechada ();

            Document equipamento = this.equipamentos.buscarPorId (propriedadeId, atual.getString ("equipamentoId"));
            if (equipamento == null)
                return ManutencaoHandler.equipamentoNaoEncontrado ();

            // data mudou: a proxima preventiva gerada (ainda programada) acompanha
            String   novaData = validado.campos.getString ("data");
            Document proxima  = atual.getString ("proximaPreventivaId") == null ? null
                                : this.preventivas.buscarPorId (propriedadeId, atual.getString ("proximaPreventivaId"));
            String   recalculada = null;

            if (proxima != null && ManutencaoHandler.PROGRAMADA.equals (proxima.getString ("status")) &&
                !novaData.equals (atual.getString ("data")) && proxima.get ("intervaloDias") instanceof Number)
                recalculada = AlgoritmosDeManutencao.proximaData (novaData, Integer.valueOf (((Number) proxima.get ("intervaloDias")).intValue ()));

            // ---------------------------------------------------------------- gravacoes
            String   agora  = Instant.now ().toString ();
            Document campos = new Document ("tipo",         validado.campos.getString ("tipo"))
                                  .append  ("data",         novaData)
                                  .append  ("descricao",    validado.campos.getString ("descricao"))
                                  .append  ("responsavel",  validado.campos.get ("responsavel"))
                                  .append  ("horimetro",    validado.campos.get ("horimetro"))
                                  .append  ("valor",        validado.campos.get ("valor"))
                                  .append  ("talhaoId",     vinculos.getString ("talhaoId"))
                                  .append  ("safraId",      vinculos.getString ("safraId"))
                                  .append  ("atualizadoEm", agora);

            if (!this.intervencoes.atualizar (propriedadeId, id, campos))
                return Resposta.erro (404, "Not found");

            if (vinculada != null)
                this.preventivas.atualizar (propriedadeId, vinculada, new Document ("realizadaEm", novaData).append ("atualizadoEm", agora));

            if (recalculada != null)
                this.preventivas.atualizar (propriedadeId, ManutencaoHandler.id (proxima),
                                            new Document ("dataPrevista", recalculada).append ("atualizadoEm", agora));

            Document intervencao = this.intervencoes.buscarPorId (propriedadeId, id);
            this.atualizarHorimetro (propriedadeId, equipamento, intervencao);

            boolean  pendente = this.sincronizarCusto (propriedadeId, intervencao, equipamento, false);
            Document resposta = ManutencaoHandler.intervencaoPublica (intervencao, ManutencaoHandler.porId (this.equipamentos.listar (propriedadeId)),
                                                                     talhoes, safras);

            if (pendente)
                resposta.append ("custoPendente", true);

            return Resposta.json (200, resposta);
        }
    }

    // DELETE /api/intervencoes/{id}  (o custo vai junto; a preventiva volta a programada)
    private Resposta excluirIntervencao (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document intervencao   = this.intervencoes.buscarPorId (propriedadeId, id);

            if (intervencao == null)
                return Resposta.erro (404, "Not found");

            if (ManutencaoHandler.finalizada (this.safras.buscarPorId (propriedadeId, intervencao.getString ("safraId"))))
                return ManutencaoHandler.safraFechada ();

            String   proximaId = intervencao.getString ("proximaPreventivaId");
            Document proxima   = proximaId == null ? null : this.preventivas.buscarPorId (propriedadeId, proximaId);

            if (proxima != null && ManutencaoHandler.REALIZADA.equals (proxima.getString ("status")))
                return Resposta.erro (409, "A pr\u00f3xima preventiva gerada por esta interven\u00e7\u00e3o j\u00e1 foi realizada. Exclua aquela interven\u00e7\u00e3o antes.");

            // ---------------------------------------------------------------- gravacoes
            this.manutencao.excluir (propriedadeId, id);

            if (proxima != null && ManutencaoHandler.PROGRAMADA.equals (proxima.getString ("status")))
                this.preventivas.excluir (propriedadeId, proximaId);

            if (intervencao.getString ("preventivaId") != null)
                this.preventivas.atualizar (propriedadeId, intervencao.getString ("preventivaId"),
                                            new Document ("status",        ManutencaoHandler.PROGRAMADA)
                                                .append  ("intervencaoId", null)
                                                .append  ("realizadaEm",   null)
                                                .append  ("atualizadoEm",  Instant.now ().toString ()));

            if (!this.intervencoes.excluir (propriedadeId, id))
                ret = Resposta.erro (404, "Not found");
            else
                ret = Resposta.semConteudo ();

            return ret;
        }
    }

    // ------------------------------------------------------------------ resumo

    // GET /api/manutencao/resumo
    private Resposta resumo (Requisicao requisicao) throws Exception
    {
        String propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String ano           = String.valueOf (LocalDate.now (ManutencaoHandler.FUSO).getYear ());
        int    total         = 0;
        int    ativos        = 0;
        int    emManutencao  = 0;
        long   centavos      = 0;

        for (Document equipamento : this.equipamentos.listar (propriedadeId))
        {
            total++;
            if ("Ativo".equals (equipamento.getString ("status")))
                ativos++;
            else if ("Em manuten\u00e7\u00e3o".equals (equipamento.getString ("status")))
                emManutencao++;
        }

        RespostaDeAgendaDeManutencao agenda = ManutencaoHandler.agenda (this.preventivas.listar (propriedadeId));

        // dinheiro somado em centavos (long): so os custos gerados por manutencao no ano atual
        for (Document custo : this.custos.listar (propriedadeId))
        {
            Document origem = custo.get ("origem", Document.class);

            if (origem != null && CustosDeManutencao.ORIGEM.equals (origem.getString ("tipo")) &&
                custo.getString ("date") != null && custo.getString ("date").startsWith (ano + "-"))
                centavos += Math.round (ManutencaoHandler.numero (custo, "value") * 100);
        }

        return Resposta.json (200, new Document ("equipamentos",         total)
                                       .append  ("ativos",               ativos)
                                       .append  ("emManutencao",         emManutencao)
                                       .append  ("preventivasAtrasadas", agenda == null ? 0 : agenda.getAtrasadas ())
                                       .append  ("preventivasProximas",  agenda == null ? 0 : agenda.getProximas ())
                                       .append  ("gastoNoAno",           centavos / 100.0)
                                       .append  ("ano",                  ano));
    }
}
