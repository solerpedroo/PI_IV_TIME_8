/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package handlers;

import dados.RepositorioOcorrencias;
import dados.RepositorioTalhoes;
import http.ErroHttp;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessao;
import seguranca.Sessoes;
import util.Json;
import util.Validacao;

/* Regras do projeto §8.7. O cliente informa dados; titularidade, status inicial,
   ordem dos eventos e datas de publicação/resolução são definidos pelo servidor. */
public class OcorrenciasHandler
{
    public static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");
    private static final List<String> TIPOS = List.of ("Praga", "Doença", "Clima", "Equipamento", "Outro");
    private static final List<String> PRIORIDADES = List.of ("Baixa", "Média", "Alta");
    private final RepositorioOcorrencias ocorrencias;
    private final RepositorioTalhoes talhoes;
    private final Sessoes sessoes;
    private final Clock relogio;

    public OcorrenciasHandler (RepositorioOcorrencias ocorrencias, RepositorioTalhoes talhoes, Sessoes sessoes)
    {
        this (ocorrencias, talhoes, sessoes, Clock.systemUTC ());
    }

    // Um relógio explícito permite testar a virada do mês sem alterar o computador.
    public OcorrenciasHandler (RepositorioOcorrencias ocorrencias, RepositorioTalhoes talhoes, Sessoes sessoes, Clock relogio)
    {
        this.ocorrencias = ocorrencias;
        this.talhoes = talhoes;
        this.sessoes = sessoes;
        this.relogio = relogio;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET", "/api/ocorrencias/resumo", this::resumo);
        roteador.adicionar ("GET", "/api/ocorrencias", this::listar);
        roteador.adicionar ("GET", "/api/ocorrencias/{id}", this::consultar);
        roteador.adicionar ("GET", "/api/ocorrencias/{id}/timeline", this::timeline);
        roteador.adicionar ("POST", "/api/ocorrencias", r -> this.mutar (r, "criar"));
        roteador.adicionar ("PUT", "/api/ocorrencias/{id}", r -> this.mutar (r, "editar"));
        roteador.adicionar ("POST", "/api/ocorrencias/{id}/timeline", r -> this.mutar (r, "evento"));
        roteador.adicionar ("DELETE", "/api/ocorrencias/{id}", r -> this.mutar (r, "excluir"));
    }

    private static Document publico (Document registro)
    {
        return Json.publico (registro, "usuarioId");
    }

    private Document buscar (Requisicao r) throws Exception
    {
        Document registro = this.ocorrencias.buscar (r.getSessao ().getUsuarioId (), r.getSessao ().getPropriedadeId (), r.getParametro ("id"));
        if (registro == null) throw new ErroHttp (404, "Ocorrência não encontrada.");
        return registro;
    }

    private Resposta consultar (Requisicao r) throws Exception
    {
        return Resposta.json (200, publico (this.buscar (r)));
    }

    private Resposta timeline (Requisicao r) throws Exception
    {
        this.buscar (r);
        List<Document> ret = new ArrayList<> ();
        for (Document evento : this.ocorrencias.timeline (r.getSessao ().getUsuarioId (), r.getSessao ().getPropriedadeId (), r.getParametro ("id")))
            ret.add (publico (evento));
        return Resposta.json (200, Json.escrever (ret));
    }

    // Lista e indicadores compartilham exatamente o mesmo contexto de consulta.
    private List<Document> filtrar (Requisicao r) throws Exception
    {
        String talhao = r.getConsulta ("talhaoId");
        if (talhao != null && !talhao.isEmpty () && this.talhoes.buscarPorId (r.getSessao ().getPropriedadeId (), talhao) == null)
            throw new ErroHttp (400, "Talhão inválido para a propriedade atual.");
        String q = r.getConsulta ("q");
        q = q == null ? "" : q.trim ().toLowerCase (Locale.ROOT);
        boolean excluidas = "true".equals (r.getConsulta ("incluirExcluidas"));
        List<Document> ret = new ArrayList<> ();
        for (Document registro : this.ocorrencias.listar (r.getSessao ().getUsuarioId (), r.getSessao ().getPropriedadeId ()))
        {
            if (!excluidas && Boolean.TRUE.equals (registro.getBoolean ("excluida"))) continue;
            if (talhao != null && !talhao.isEmpty () && !talhao.equals (registro.getString ("talhaoId"))) continue;
            if (!registro.getString ("titulo").toLowerCase (Locale.ROOT).contains (q)) continue;
            ret.add (registro);
        }
        return ret;
    }

    private Resposta listar (Requisicao r) throws Exception
    {
        List<Document> ret = new ArrayList<> ();
        for (Document registro : this.filtrar (r)) ret.add (publico (registro));
        return Resposta.json (200, Json.escrever (ret));
    }

    private Resposta resumo (Requisicao r) throws Exception
    {
        String valor = r.getConsulta ("mes");
        YearMonth mes;
        try
        {
            if (valor != null && !valor.matches ("\\d{4}-\\d{2}")) throw new IllegalArgumentException ();
            mes = valor == null ? YearMonth.now (this.relogio.withZone (FUSO)) : YearMonth.parse (valor);
        }
        catch (Exception erro) { return Resposta.erro (400, "Mês inválido. Use AAAA-MM."); }
        int abertas = 0, acompanhamento = 0, resolvidas = 0, altas = 0;
        for (Document registro : this.filtrar (r))
        {
            String status = registro.getString ("status");
            if (status.equals ("ABERTA")) abertas++;
            if (status.equals ("EM_ACOMPANHAMENTO")) acompanhamento++;
            if (!status.equals ("RESOLVIDA") && registro.getString ("prioridade").equals ("Alta")) altas++;
            String resolvidaEm = registro.getString ("resolvidaEm");
            if (status.equals ("RESOLVIDA") && resolvidaEm != null &&
                YearMonth.from (Instant.parse (resolvidaEm).atZone (FUSO)).equals (mes)) resolvidas++;
        }
        return Resposta.json (200, new Document ("abertas", abertas).append ("emAcompanhamento", acompanhamento)
            .append ("resolvidasMes", resolvidas).append ("prioridadeAlta", altas).append ("mes", mes.toString ())
            .append ("fuso", FUSO.toString ()));
    }

    private Document lerCampos (Document corpo, Validacao v, String propriedade) throws Exception
    {
        String titulo = v.texto ("titulo", "Informe um título.", 160);
        String tipo = v.texto ("tipo", "Selecione o tipo.", 40);
        String prioridade = v.texto ("prioridade", "Selecione a prioridade.", 20);
        String data = v.texto ("data", "Informe a data da ocorrência.", 10);
        if (tipo != null && !TIPOS.contains (tipo)) v.erro ("tipo", "Selecione um tipo válido.");
        if (prioridade != null && !PRIORIDADES.contains (prioridade)) v.erro ("prioridade", "Selecione uma prioridade válida.");
        if (data != null)
            try
            {
                if (!data.matches ("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException ();
                LocalDate.parse (data);
            }
            catch (Exception erro) { v.erro ("data", "Informe uma data válida no formato AAAA-MM-DD."); }
        Object descricao = corpo.getOrDefault ("descricao", "");
        if (!(descricao instanceof String) || ((String) descricao).length () > 6000)
            v.erro ("descricao", "Use um texto de até 6000 caracteres.");
        Object vinculo = corpo.get ("talhaoId");
        Document talhao = null;
        if (vinculo != null && !"".equals (vinculo))
        {
            if (!(vinculo instanceof String)) v.erro ("talhaoId", "Selecione um talhão válido.");
            else
            {
                talhao = this.talhoes.buscarPorId (propriedade, (String) vinculo);
                if (talhao == null) v.erro ("talhaoId", "O talhão não pertence à propriedade atual.");
            }
        }
        // A safra é o vínculo já existente no talhão desta branch, nunca texto livre do cliente.
        if (corpo.containsKey ("foto") || corpo.containsKey ("safraId"))
            v.erro ("vinculos", "Foto e vínculo por safraId não estão disponíveis nesta versão.");
        if (v.temErros ()) return null;
        return new Document ("titulo", titulo).append ("tipo", tipo).append ("prioridade", prioridade)
            .append ("data", data).append ("descricao", descricao)
            .append ("talhaoId", talhao == null ? null : talhao.getObjectId ("_id").toHexString ())
            .append ("talhaoNome", talhao == null ? null : talhao.getString ("nome"))
            .append ("safra", talhao == null ? null : talhao.getString ("safra"));
    }

    private static long versao (Document registro)
    {
        return ((Number) registro.get ("versao")).longValue ();
    }

    // Cada evento guarda os dados daquela etapa. Editar o cadastro não apaga a descrição original.
    private static Document evento (Document registro, String acao, String descricao)
    {
        return new Document ("_id", new ObjectId ()).append ("usuarioId", registro.getString ("usuarioId"))
            .append ("propriedadeId", registro.getString ("propriedadeId"))
            .append ("ocorrenciaId", registro.getObjectId ("_id").toHexString ())
            .append ("ordem", versao (registro)).append ("data", registro.getString ("atualizadaEm"))
            .append ("acao", acao).append ("status", registro.getString ("status")).append ("descricao", descricao)
            .append ("dados", new Document ("titulo", registro.getString ("titulo")).append ("tipo", registro.getString ("tipo"))
                .append ("data", registro.getString ("data")).append ("prioridade", registro.getString ("prioridade"))
                .append ("descricao", registro.getString ("descricao")).append ("talhaoId", registro.getString ("talhaoId"))
                .append ("talhaoNome", registro.getString ("talhaoNome")).append ("safra", registro.getString ("safra")));
    }

    private Resposta mutar (Requisicao r, String acao) throws Exception
    {
        // Coordena escrita com troca/exclusão da propriedade e invalidação da sessão.
        synchronized (this.sessoes)
        {
            Sessao atual = this.sessoes.buscar (r.getToken ());
            if (atual == null) return Resposta.erro (401, "Unauthorized");
            Document corpo = r.getCorpoJson ();
            String propriedade = atual.getPropriedadeId ();
            if (!propriedade.equals (r.getSessao ().getPropriedadeId ()) ||
                (corpo.containsKey ("contextoPropriedadeId") && !propriedade.equals (corpo.get ("contextoPropriedadeId"))))
                return Resposta.erro (409, "A propriedade ativa mudou. Recarregue antes de salvar.");
            Validacao v = new Validacao (corpo);
            String agora = this.relogio.instant ().toString ();
            if (acao.equals ("criar"))
            {
                if (corpo.containsKey ("status") && !"ABERTA".equals (corpo.get ("status")))
                    v.erro ("status", "Uma ocorrência deve iniciar aberta.");
                Document registro = this.lerCampos (corpo, v, propriedade);
                if (v.temErros ()) return v.resposta ();
                registro.append ("_id", new ObjectId ()).append ("usuarioId", atual.getUsuarioId ())
                    .append ("propriedadeId", propriedade).append ("statusInicial", "ABERTA").append ("status", "ABERTA")
                    .append ("criadaEm", agora).append ("atualizadaEm", agora).append ("resolvidaEm", null)
                    .append ("excluida", false).append ("versao", 1L);
                this.ocorrencias.criar (registro, evento (registro, "registro", registro.getString ("descricao")));
                return Resposta.json (201, publico (registro));
            }
            Document anterior = this.buscar (r);
            Object esperada = corpo.get ("versao");
            if (!(esperada instanceof Number) || ((Number) esperada).doubleValue () != versao (anterior))
                return Resposta.erro (409, "O registro mudou. Reabra o detalhe antes de tentar novamente.");
            if (Boolean.TRUE.equals (anterior.getBoolean ("excluida")))
                return Resposta.erro (409, "Ocorrência excluída: somente consulta do histórico disponível.");
            Document nova = new Document (anterior);
            String descricao = "";
            if (acao.equals ("editar"))
            {
                if (anterior.getString ("status").equals ("RESOLVIDA"))
                    return Resposta.erro (409, "Ocorrência resolvida: o desfecho deve ser preservado.");
                if (corpo.containsKey ("status") && !anterior.getString ("status").equals (corpo.get ("status")))
                    v.erro ("status", "Use a ação de acompanhamento ou resolução para alterar o status.");
                Document campos = this.lerCampos (corpo, v, propriedade);
                if (v.temErros ()) return v.resposta ();
                nova.putAll (campos);
                descricao = "Cadastro editado. Os dados de cada etapa permanecem no histórico.";
            }
            else if (acao.equals ("excluir"))
            {
                nova.append ("excluida", true).append ("excluidaEm", agora);
                descricao = "Ocorrência excluída da lista ativa. Histórico preservado para consulta.";
            }
            else
            {
                String operacao = v.texto ("acao", "Informe a ação.", 30);
                descricao = v.texto ("descricao", "Descreva o acompanhamento ou desfecho.", 6000);
                if (!List.of ("acompanhamento", "atualizacao", "resolucao").contains (operacao == null ? "" : operacao))
                    v.erro ("acao", "Ação inválida.");
                if (v.temErros ()) return v.resposta ();
                String status = anterior.getString ("status");
                if (status.equals ("RESOLVIDA") ||
                    (operacao.equals ("acompanhamento") && !status.equals ("ABERTA")) ||
                    (operacao.equals ("atualizacao") && !status.equals ("EM_ACOMPANHAMENTO")))
                    return Resposta.erro (409, "Transição inválida para o status atual.");
                if (operacao.equals ("acompanhamento")) nova.put ("status", "EM_ACOMPANHAMENTO");
                if (operacao.equals ("resolucao")) nova.append ("status", "RESOLVIDA").append ("resolvidaEm", agora);
                acao = operacao;
            }
            // O relógio não pode produzir um evento anterior ao último já confirmado.
            if (Instant.parse (agora).isBefore (Instant.parse (anterior.getString ("atualizadaEm"))))
                return Resposta.erro (409, "Data do servidor anterior ao último evento. Tente novamente mais tarde.");
            nova.append ("versao", versao (anterior) + 1).append ("atualizadaEm", agora);
            if (!this.ocorrencias.salvar (anterior, nova, evento (nova, acao, descricao)))
                return Resposta.erro (409, "Outra atualização foi salva. Reabra o detalhe para continuar.");
            return Resposta.json (200, publico (nova));
        }
    }
}
