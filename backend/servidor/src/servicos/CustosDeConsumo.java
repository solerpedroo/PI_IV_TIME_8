/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package servicos;

import dados.RepositorioCustos;
import dados.RepositorioEstoque;
import dados.RepositorioSafras;
import dados.RepositorioTalhoes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Custo gerado pelo consumo de estoque (#156).

    Toda saida de insumo vinculada a uma atividade vira um lancamento de custo na
    categoria Insumos, com valor = quantidade x custo unitario da saida, o talhao e a
    safra da atividade e a origem (movimentacao, insumo, atividade). A movimentacao
    e a chave: existe no maximo um custo por movimentacao, entao repetir o registro
    ou a sincronizacao nunca duplica o valor.

    A baixa do estoque continua sendo do EstoqueHandler. Se o custo nao puder ser
    gravado logo depois da baixa, sincronizar () cria os que faltam na proxima
    consulta de custos da propriedade.
*/
public class CustosDeConsumo
{
    public static final ZoneId FUSO = ZoneId.of ("America/Sao_Paulo");

    private static final int MAXIMO_DESCRICAO = 120;

    private final RepositorioCustos  custos;
    private final RepositorioEstoque estoque;
    private final RepositorioTalhoes talhoes;
    private final RepositorioSafras  safras;

    public CustosDeConsumo (RepositorioCustos custos, RepositorioEstoque estoque,
                            RepositorioTalhoes talhoes, RepositorioSafras safras) throws Exception
    {
        if (custos == null)
            throw new Exception ("Repositorio de custos ausente");

        if (estoque == null)
            throw new Exception ("Repositorio de estoque ausente");

        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        if (safras == null)
            throw new Exception ("Repositorio de safras ausente");

        this.custos  = custos;
        this.estoque = estoque;
        this.talhoes = talhoes;
        this.safras  = safras;
    }

    // ------------------------------------------------------------------ auxiliares

    private static String id (Object valor)
    {
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    private static double numero (Document documento, String campo)
    {
        Number valor = documento.get (campo, Number.class);

        return valor == null ? 0 : valor.doubleValue ();
    }

    // 50.0 -> "50"; 2.5 -> "2,5"
    private static String quantidade (double valor)
    {
        return new BigDecimal (Double.toString (valor)).stripTrailingZeros ().toPlainString ().replace ('.', ',');
    }

    private static String nomeDaAtividade (Document atividade)
    {
        String ret = atividade.getString ("nome");

        if (ret == null)
            ret = atividade.getString ("titulo");

        return ret == null ? "atividade" : ret;
    }

    // talhao da atividade (guardado pelo nome) na propriedade; null se nao existir mais
    private String talhaoId (String propriedadeId, Document atividade) throws Exception
    {
        String ret  = null;
        String nome = atividade.getString ("talhao");

        if (nome != null)
            for (Document talhao : this.talhoes.listar (propriedadeId))
                if (ret == null && nome.equalsIgnoreCase (talhao.getString ("nome")))
                    ret = CustosDeConsumo.id (talhao.get ("_id"));

        return ret;
    }

    // safra da atividade, se ainda existe, contem o talhao e nao foi finalizada
    private String safraId (String propriedadeId, Document atividade, String talhaoId) throws Exception
    {
        String   ret     = null;
        String   safraId = atividade.getString ("safraId");
        Document safra   = safraId == null || safraId.isBlank () ? null : this.safras.buscarPorId (propriedadeId, safraId);

        if (safra != null && !"Finaliza\u00e7\u00e3o".equals (safra.getString ("etapa")) &&
            (talhaoId == null || safra.getList ("talhoes", String.class).contains (talhaoId)))
            ret = safraId;

        return ret;
    }

    // ------------------------------------------------------------------ registro

    /*
        Grava o custo da saida. Devolve o custo gravado agora ou o que ja existia
        para a mesma movimentacao; null quando a movimentacao nao gera custo
        (nao e saida, nao tem atividade ou o valor fica abaixo de um centavo).
    */
    public Document registrar (String propriedadeId, Document movimento, Document insumo, Document atividade) throws Exception
    {
        if (propriedadeId == null || movimento == null || insumo == null)
            throw new Exception ("Consumo incompleto");

        if (!"saida".equals (movimento.getString ("tipo")) || atividade == null || movimento.get ("_id") == null)
            return null;

        // custo da propria saida; sem ele, o custo medio do insumo no momento da baixa
        double unitario = movimento.get ("custoUnitario") != null ? CustosDeConsumo.numero (movimento, "custoUnitario")
                                                                   : CustosDeConsumo.numero (insumo, "custoUnitario");
        double qtd      = CustosDeConsumo.numero (movimento, "quantidade");
        long   centavos = Math.round (qtd * unitario * 100);

        if (centavos < 1)
            return null;

        String movimentacaoId = CustosDeConsumo.id (movimento.get ("_id"));
        String unidade        = movimento.getString ("unidade") != null ? movimento.getString ("unidade") : insumo.getString ("unidade");
        String talhaoId       = this.talhaoId (propriedadeId, atividade);
        String descricao      = "Consumo de " + insumo.getString ("nome") + ": " + CustosDeConsumo.quantidade (qtd) + " " +
                                unidade + " (" + CustosDeConsumo.nomeDaAtividade (atividade) + ")";
        String criadoEm       = movimento.getString ("criadoEm") != null ? movimento.getString ("criadoEm") : Instant.now ().toString ();

        if (descricao.length () > CustosDeConsumo.MAXIMO_DESCRICAO)
            descricao = descricao.substring (0, CustosDeConsumo.MAXIMO_DESCRICAO - 3) + "...";

        Document origem = new Document ("tipo",           "consumo")
                              .append  ("movimentacaoId", movimentacaoId)
                              .append  ("insumoId",       movimento.getString ("insumoId"))
                              .append  ("atividadeId",    CustosDeConsumo.id (atividade.get ("_id")))
                              .append  ("quantidade",     qtd)
                              .append  ("unidade",        unidade)
                              .append  ("custoUnitario",  unitario);

        Document custo = new Document ("description",  descricao)
                             .append  ("category",     "Insumos")
                             .append  ("value",        centavos / 100.0)
                             .append  ("date",         Instant.parse (criadoEm).atZone (CustosDeConsumo.FUSO).toLocalDate ().toString ())
                             .append  ("talhaoId",     talhaoId)
                             .append  ("safraId",      this.safraId (propriedadeId, atividade, talhaoId))
                             .append  ("origem",       origem)
                             .append  ("propriedadeId", propriedadeId)
                             .append  ("criadoEm",     Instant.now ().toString ())
                             .append  ("atualizadoEm", Instant.now ().toString ());

        if (!this.custos.criarSeAusente (custo))
            custo = this.daMovimentacao (propriedadeId, movimentacaoId);

        return custo;
    }

    // custo ja gravado para a movimentacao; null se nao houver
    public Document daMovimentacao (String propriedadeId, String movimentacaoId) throws Exception
    {
        Document ret = null;

        for (Document custo : this.custos.listar (propriedadeId))
        {
            Document origem = custo.get ("origem", Document.class);

            if (ret == null && origem != null && movimentacaoId.equals (origem.getString ("movimentacaoId")))
                ret = custo;
        }

        return ret;
    }

    /*
        Cria os custos que faltam para as saidas com atividade da propriedade
        (gravacao interrompida depois da baixa, ou saida anterior a esta entrega).
        Devolve quantos foram criados. As atividades sao procuradas com o mesmo
        escopo de usuario que o estoque usou para aceitar o vinculo.
    */
    public int sincronizar (String usuarioId, String propriedadeId) throws Exception
    {
        int             ret      = 0;
        HashSet<String> comCusto = new HashSet<String> ();
        List<Document>  gravados = this.custos.listar (propriedadeId);

        for (Document custo : gravados)
            if (custo.get ("origem", Document.class) != null)
                comCusto.add (custo.get ("origem", Document.class).getString ("movimentacaoId"));

        for (Document movimento : this.estoque.listarMovimentacoes (propriedadeId, null))
        {
            String atividadeId = movimento.getString ("atividadeId");

            if ("saida".equals (movimento.getString ("tipo")) && atividadeId != null && !atividadeId.isBlank () &&
                !comCusto.contains (CustosDeConsumo.id (movimento.get ("_id"))))
            {
                Document insumo    = this.estoque.buscarPorId (propriedadeId, movimento.getString ("insumoId"));
                Document atividade = this.estoque.buscarAtividade (usuarioId, propriedadeId, atividadeId);

                if (insumo != null && atividade != null && this.registrar (propriedadeId, movimento, insumo, atividade) != null)
                    ret++; // corrida com outro registro devolve o custo existente, sem gravar outro
            }
        }

        return ret;
    }
}
