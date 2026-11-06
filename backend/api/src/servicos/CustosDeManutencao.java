/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package servicos;

import dados.RepositorioCustos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;

/*
    Custo gerado por uma intervencao de manutencao (#236), no molde de CustosDeConsumo.

    So a intervencao com valor gera custo (sem valor nao ha despesa zerada). O lancamento vai para a
    categoria Maquinario, com a data, o talhao e a safra da intervencao e a origem
    {tipo: "manutencao", intervencaoId, equipamentoId}. A intervencao e a chave: existe no maximo um
    custo por intervencao (indice unico parcial no MongoDB), entao repetir o registro nunca duplica.
    O custo so muda pela intervencao: PUT e DELETE em /api/custos/{id} devolvem 409.
    A regra de safra finalizada fica no ManutencaoHandler, que valida tudo antes de gravar.
*/
public class CustosDeManutencao
{
    public static final String CATEGORIA = "Maquin\u00e1rio";
    public static final String ORIGEM    = "manutencao";

    private static final int MAXIMO_DESCRICAO = 120;

    private final RepositorioCustos custos;

    public CustosDeManutencao (RepositorioCustos custos) throws Exception
    {
        if (custos == null)
            throw new Exception ("Repositorio de custos ausente");

        this.custos = custos;
    }

    // ------------------------------------------------------------------ auxiliares

    private static String id (Object valor)
    {
        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    // "Manutencao corretiva: Trator 01 (Troca da correia)", cortada em 120
    public static String descricao (Document intervencao, Document equipamento)
    {
        String ret = "Manuten\u00e7\u00e3o " + intervencao.getString ("tipo") + ": " + equipamento.getString ("nome") +
                     " (" + intervencao.getString ("descricao") + ")";

        if (ret.length () > CustosDeManutencao.MAXIMO_DESCRICAO)
            ret = ret.substring (0, CustosDeManutencao.MAXIMO_DESCRICAO - 3) + "...";

        return ret;
    }

    private static Double valor (Document intervencao)
    {
        Object valor = intervencao.get ("valor");
        Double ret   = null;

        if (valor instanceof Number)
        {
            BigDecimal reais = new BigDecimal (((Number) valor).toString ()).setScale (2, RoundingMode.HALF_UP);

            if (reais.compareTo (new BigDecimal ("0.01")) >= 0)
                ret = Double.valueOf (reais.doubleValue ());
        }

        return ret;
    }

    // os mesmos campos que o CustosHandler.lerCusto grava num lancamento manual
    private static Document campos (Document intervencao, Document equipamento, Double valor)
    {
        return new Document ("description",  CustosDeManutencao.descricao (intervencao, equipamento))
                   .append  ("category",     CustosDeManutencao.CATEGORIA)
                   .append  ("value",        valor)
                   .append  ("date",         intervencao.getString ("data"))
                   .append  ("talhaoId",     intervencao.getString ("talhaoId"))
                   .append  ("safraId",      intervencao.getString ("safraId"))
                   .append  ("atualizadoEm", Instant.now ().toString ());
    }

    // ------------------------------------------------------------------ registro

    // custo ja gravado para a intervencao; null se nao houver
    public Document daIntervencao (String propriedadeId, String intervencaoId) throws Exception
    {
        return this.custos.buscarPorIntervencao (propriedadeId, intervencaoId);
    }

    /*
        Grava o custo da intervencao. Devolve o custo gravado agora ou o que ja existia para a
        mesma intervencao; null quando a intervencao nao tem valor (nao gera custo).
    */
    public Document registrar (String propriedadeId, Document intervencao, Document equipamento) throws Exception
    {
        if (propriedadeId == null || intervencao == null || equipamento == null || intervencao.get ("_id") == null)
            throw new Exception ("Interven\u00e7\u00e3o incompleta");

        Double   valor = CustosDeManutencao.valor (intervencao);
        Document ret   = null;

        if (valor != null)
        {
            String   intervencaoId = CustosDeManutencao.id (intervencao.get ("_id"));
            Document origem        = new Document ("tipo",          CustosDeManutencao.ORIGEM)
                                         .append  ("intervencaoId", intervencaoId)
                                         .append  ("equipamentoId", intervencao.getString ("equipamentoId"));

            ret = CustosDeManutencao.campos (intervencao, equipamento, valor)
                      .append ("origem",        origem)
                      .append ("propriedadeId", propriedadeId)
                      .append ("criadoEm",      Instant.now ().toString ());

            if (!this.custos.criarSeAusente (ret))
                ret = this.daIntervencao (propriedadeId, intervencaoId);
        }

        return ret;
    }

    /*
        A intervencao mudou: valor novo atualiza o custo, valor removido exclui o custo e
        valor novo onde nao havia cria. Devolve o custo atual (null quando nao ha mais custo).
    */
    public Document atualizar (String propriedadeId, Document intervencao, Document equipamento) throws Exception
    {
        String   intervencaoId = CustosDeManutencao.id (intervencao.get ("_id"));
        Document existente     = this.daIntervencao (propriedadeId, intervencaoId);
        Double   valor         = CustosDeManutencao.valor (intervencao);
        Document ret           = null;

        if (valor == null)
        {
            if (existente != null)
                this.custos.excluir (propriedadeId, CustosDeManutencao.id (existente.get ("_id")));
        }
        else if (existente == null)
            ret = this.registrar (propriedadeId, intervencao, equipamento);
        else
        {
            String custoId = CustosDeManutencao.id (existente.get ("_id"));

            this.custos.atualizar (propriedadeId, custoId, CustosDeManutencao.campos (intervencao, equipamento, valor));
            ret = this.custos.buscarPorId (propriedadeId, custoId);
        }

        return ret;
    }

    // a intervencao foi excluida: o custo vai junto; false se ela nao tinha custo
    public boolean excluir (String propriedadeId, String intervencaoId) throws Exception
    {
        Document existente = this.daIntervencao (propriedadeId, intervencaoId);
        boolean  ret       = false;

        if (existente != null)
            ret = this.custos.excluir (propriedadeId, CustosDeManutencao.id (existente.get ("_id")));

        return ret;
    }
}
