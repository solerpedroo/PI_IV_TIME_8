/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package servicos.integracao;

import dados.cultivo.RepositorioTalhoes;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Espelha conclusao de atividade, consumo com custo e ocorrencias no historico
    do talhao (historicos_talhoes), para o painel e GET /api/safras/{id}/historico
    sem POST manual em /api/talhoes/{id}/historico (#162 / validacao #180-#181).
*/
public class EspelhoHistoricoTalhao
{
    private static final int MAX_DESCRICAO = 240;

    private final RepositorioTalhoes talhoes;

    public EspelhoHistoricoTalhao (RepositorioTalhoes talhoes) throws Exception
    {
        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");
        this.talhoes = talhoes;
    }

  public void atividadeConcluida (String propriedadeId, Document atividade, String detalhe) throws Exception
    {
        String talhaoId = this.talhaoDaAtividade (propriedadeId, atividade);
        if (talhaoId == null)
            return;
        String nome = atividade.getString ("nome");
        if (nome == null)
            nome = atividade.getString ("titulo");
        String descricao = "Concluida: " + (nome == null ? "atividade" : nome);
        if (detalhe != null && !detalhe.isBlank ())
            descricao = descricao + " — " + detalhe.trim ();
        this.gravar (propriedadeId, talhaoId, "atividade", descricao, chave ("atividade", "conclusao", atividade));
    }

    public void consumoDeInsumo (String propriedadeId, Document atividade, Document custo) throws Exception
    {
        if (custo == null)
            return;
        String talhaoId = custo.getString ("talhaoId");
        if (talhaoId == null)
            talhaoId = this.talhaoDaAtividade (propriedadeId, atividade);
        if (talhaoId == null)
            return;
        String descricao = custo.getString ("description");
        if (descricao == null || descricao.isBlank ())
            descricao = "Custo de consumo vinculado a atividade";
        Document origem = custo.get ("origem", Document.class);
        String movId = origem == null ? null : origem.getString ("movimentacaoId");
        this.gravar (propriedadeId, talhaoId, "custo", descricao, chave ("custo", "movimentacao", movId));
    }

    public void ocorrenciaRegistrada (String propriedadeId, Document registro) throws Exception
    {
        String talhaoId = registro.getString ("talhaoId");
        if (talhaoId == null || talhaoId.isBlank ())
            return;
        String titulo = registro.getString ("titulo");
        this.gravar (propriedadeId, talhaoId, "ocorrencia", "Registrada: " + (titulo == null ? "ocorrencia" : titulo),
                     chave ("ocorrencia", "registro", registro));
    }

    public void ocorrenciaAtualizada (String propriedadeId, Document registro, String acao, String descricao) throws Exception
    {
        String talhaoId = registro.getString ("talhaoId");
        if (talhaoId == null || talhaoId.isBlank ())
            return;
        if (!List.of ("acompanhamento", "atualizacao", "resolucao").contains (acao == null ? "" : acao))
            return;
        String titulo = registro.getString ("titulo");
        String texto = switch (acao)
        {
            case "acompanhamento" -> "Em acompanhamento";
            case "resolucao"      -> "Resolvida";
            default               -> "Atualizada";
        };
        String linha = texto + ": " + (titulo == null ? "ocorrencia" : titulo);
        if (descricao != null && !descricao.isBlank ())
            linha = linha + " — " + descricao.trim ();
        this.gravar (propriedadeId, talhaoId, "ocorrencia", linha, chave ("ocorrencia", acao, registro));
    }

    private String talhaoDaAtividade (String propriedadeId, Document atividade) throws Exception
    {
        if (atividade == null)
            return null;
        String nome = atividade.getString ("talhao");
        if (nome == null || nome.isBlank ())
            return null;
        for (Document talhao : this.talhoes.listar (propriedadeId))
            if (nome.equalsIgnoreCase (talhao.getString ("nome")))
                return id (talhao.get ("_id"));
        return null;
    }

    private void gravar (String propriedadeId, String talhaoId, String tipo, String descricao, String origemChave) throws Exception
    {
        try
        {
            if (origemChave != null)
                for (Document evento : this.talhoes.listarHistorico (propriedadeId, talhaoId))
                    if (origemChave.equals (evento.getString ("origemChave")))
                        return;
            Document evento = new Document ("tipo", tipo).append ("descricao", limitar (descricao))
                .append ("criadoEm", Instant.now ().toString ());
            if (origemChave != null)
                evento.append ("origemChave", origemChave);
            this.talhoes.criarHistorico (propriedadeId, talhaoId, evento);
        }
        catch (Exception ignorado)
        {
            // O fluxo principal (atividade, estoque, ocorrencia) nao pode falhar por espelho.
        }
    }

    private static String chave (String entidade, String acao, Document registro)
    {
        Object id = registro == null ? null : registro.get ("_id");
        if (id == null && registro != null)
            id = registro.get ("id");
        return entidade + ":" + acao + ":" + (id == null ? "" : id (id));
    }

    private static String chave (String entidade, String acao, String movimentacaoId)
    {
        return entidade + ":" + acao + ":" + (movimentacaoId == null ? "" : movimentacaoId);
    }

    private static String id (Object valor)
    {
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    private static String limitar (String texto)
    {
        if (texto == null)
            return "";
        String normalizado = texto.strip ();
        if (normalizado.length () <= EspelhoHistoricoTalhao.MAX_DESCRICAO)
            return normalizado;
        return normalizado.substring (0, EspelhoHistoricoTalhao.MAX_DESCRICAO - 3).trim () + "...";
    }
}