/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package servicos.integracao;

import dados.cultivo.RepositorioTalhoes;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Copia fatos já confirmados para o histórico do talhão (coleção usada por
    RepositorioTalhoes, historicos_talhoes). O painel e GET /api/safras/{id}/historico
    passam a ver conclusão de atividade, consumo de insumo com custo e
    ocorrência sem um POST manual em /api/talhoes/{id}/historico
    (#162 / validação #180-#181).

    Este serviço não abre transação com estoque nem com ocorrência. Ele roda
    depois que o fluxo principal gravou. Se a cópia falhar, o erro fica no
    log e a atividade, a baixa ou a ocorrência continuam válidas.

    A origemChave evita duplicar o mesmo fato quando a chamada se repete.
    O isolamento é propriedadeId: o histórico só é criado no talhão daquela fazenda.
*/
public class EspelhoHistoricoTalhao
{
    private static final int MAX_DESCRICAO = 240;

    private final RepositorioTalhoes talhoes;

    /* O repositório de talhões é quem persiste o histórico; este serviço só monta o evento. */
    public EspelhoHistoricoTalhao (RepositorioTalhoes talhoes) throws Exception
    {
        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");
        this.talhoes = talhoes;
    }

    /*
        Conclusão de atividade. Sem talhão reconhecido pelo nome, não há o que copiar.
        A chave origem evita um segundo evento se a conclusão for reprocessada.
    */
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

    /*
        Custo de consumo já lançado no financeiro. Prefere o talhaoId do custo;
        se faltar, resolve o talhão pelo nome da atividade. A chave usa o id
        da movimentação, então a mesma baixa não entra duas vezes.
    */
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

    /* Ocorrência recém-criada (status ABERTA). Sem talhaoId não há histórico para espelhar. */
    public void ocorrenciaRegistrada (String propriedadeId, Document registro) throws Exception
    {
        String talhaoId = registro.getString ("talhaoId");
        if (talhaoId == null || talhaoId.isBlank ())
            return;
        String titulo = registro.getString ("titulo");
        this.gravar (propriedadeId, talhaoId, "ocorrencia", "Registrada: " + (titulo == null ? "ocorrencia" : titulo),
                     chave ("ocorrencia", "registro", registro));
    }

    /*
        Acompanhamento, atualização ou resolução. O texto do histórico acompanha
        a ação (Em acompanhamento, Atualizada, Resolvida). A chave é
        ocorrencia:acao:id. Ações diferentes não se apagam; repetir a mesma
        ação no mesmo registro não duplica a linha.
    */
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

    /* Acha o talhão da propriedade pelo nome gravado na atividade. Nome vazio não casa. */
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

    /*
        Grava o evento se a origemChave ainda não existir neste talhão.
        Qualquer falha é só log: o fluxo principal não pode falhar por causa do espelho.
    */
    private void gravar (String propriedadeId, String talhaoId, String tipo, String descricao, String origemChave) throws Exception
    {
        try
        {
            if (origemChave != null)
                for (Document evento : this.talhoes.listarHistorico (propriedadeId, talhaoId))
                    if (origemChave.equals (evento.getString ("origemChave")))
                        return;
            /*
                Evento do histórico do talhão. criarHistorico grava isto na coleção
                historicos_talhoes e ainda acrescenta propriedadeId e talhaoId.
                GET /api/talhoes/{id}/historico e o histórico da safra leem daí.

                {
                  "tipo": "atividade" | "ocorrencia" | "custo",
                  "descricao": texto já cortado em 240 caracteres,
                  "criadoEm": instante de agora,
                  "origemChave": "atividade:conclusao:<id>" ou equivalente
                }

                origemChave não aparece na tela. Serve só para este método
                não inserir o mesmo fato duas vezes.
            */
            Document evento = new Document ("tipo", tipo).append ("descricao", limitar (descricao))
                .append ("criadoEm", Instant.now ().toString ());
            if (origemChave != null)
                evento.append ("origemChave", origemChave);
            this.talhoes.criarHistorico (propriedadeId, talhaoId, evento);
        }
        catch (Exception erro)
        {
            // O fluxo principal (atividade, estoque, ocorrencia) nao pode falhar por espelho.
            System.err.println ("Historico do talhao nao espelhado: " + erro.getMessage ());
        }
    }

    /* Chave estável entidade:acao:id. O id pode vir como ObjectId ou como string. */
    private static String chave (String entidade, String acao, Document registro)
    {
        Object id = registro == null ? null : registro.get ("_id");
        if (id == null && registro != null)
            id = registro.get ("id");
        return entidade + ":" + acao + ":" + (id == null ? "" : id (id));
    }

    /* Mesma forma de chave, quando o identificador já é o da movimentação de estoque. */
    private static String chave (String entidade, String acao, String movimentacaoId)
    {
        return entidade + ":" + acao + ":" + (movimentacaoId == null ? "" : movimentacaoId);
    }

    /* ObjectId vira os 24 caracteres hexadecimais; outro valor vira texto. */
    private static String id (Object valor)
    {
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    /* Corta a descrição no máximo do histórico e marca o corte com reticências. */
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