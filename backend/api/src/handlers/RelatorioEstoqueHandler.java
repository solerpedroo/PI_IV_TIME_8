/* Miguel — RA: 25003110 */
package handlers;

import dados.RepositorioEstoque;
import dados.RepositorioUsuarios;
import http.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import seguranca.Sessao;
import seguranca.Sessoes;
import servicos.RelatorioSafra;

/* Saldo atual é distinto das movimentações do período, inclusive ajustes. */
public class RelatorioEstoqueHandler implements Manipulador
{
    private final RepositorioUsuarios usuarios;
    private final RepositorioEstoque estoque;
    private final Sessoes sessoes;
    public RelatorioEstoqueHandler (RepositorioUsuarios u, RepositorioEstoque e, Sessoes s) { usuarios=u; estoque=e; sessoes=s; }
    public void registrar (Roteador r) throws Exception { r.adicionar ("GET", "/api/relatorios/estoque", this); }
    private static String texto (Object valor) { return valor instanceof String ? (String) valor : null; }
    private static boolean numero (Object valor) { return valor instanceof Number && Double.isFinite (((Number) valor).doubleValue ()) && ((Number) valor).doubleValue () >= 0; }
    public Resposta tratar (Requisicao r) throws Exception
    {
        LocalDate de=RelatorioSafra.dia (r.getConsulta ("de")), ate=RelatorioSafra.dia (r.getConsulta ("ate"));
        if (de==null || ate==null || ate.isBefore (de)) return Resposta.erro (400, "Informe um período válido.");
        Sessao sessao=r.getSessao (); String pid=sessao.getPropriedadeId ();
        // Mesmo lock usado pela escrita nesta instância evita misturar saldo antigo e movimento novo.
        synchronized (sessoes) {
        Document propriedade=usuarios.buscarPropriedade (pid);
        if (propriedade==null || !sessao.getUsuarioId ().equals (propriedade.getString ("usuarioId"))) return Resposta.erro (404, "Propriedade não encontrada.");
        Map<String,Document> produtos=new HashMap<> ();
        List<Document> saldos=new ArrayList<> (), movimentos=new ArrayList<> (), pendencias=new ArrayList<> ();
        for (Document item : estoque.listar (pid))
        {
            String id=RelatorioSafra.id (item); produtos.put (id,item);
            Document saldo=new Document ("id",id).append ("produtoId",id).append ("produto",texto (item.get ("nome")))
                .append ("saldo",item.get ("saldo")).append ("unidade",texto (item.get ("unidade")));
            if (texto (item.get ("nome"))==null || texto (item.get ("unidade"))==null || !numero (item.get ("saldo")))
                pendencias.add (saldo.append ("motivo","Produto com saldo ou dados inválidos."));
            else saldos.add (new Document ("id",id).append ("nome",texto (item.get ("nome"))).append ("saldo",item.get ("saldo")).append ("unidade",texto (item.get ("unidade"))));
        }
        // Um histórico pode pertencer a produto arquivado; sua unidade pertence ao movimento.
        for (Document mov : estoque.listarMovimentacoes (pid,null))
        {
            String produtoId=texto (mov.get ("insumoId"));
            Document produto=produtos.get (produtoId);
            if (produto==null && produtoId!=null) { produto=estoque.buscarPorId (pid,produtoId); if (produto!=null) produtos.put (produtoId,produto); }
            Document item=new Document ("id",RelatorioSafra.id (mov)).append ("produtoId",produtoId)
                .append ("produto",produto==null ? null : texto (produto.get ("nome")))
                .append ("arquivado",produto!=null && Boolean.TRUE.equals (produto.get ("excluido")))
                .append ("tipo",texto (mov.get ("tipo"))).append ("quantidade",mov.get ("quantidade"))
                .append ("unidade",texto (mov.get ("unidade"))).append ("saldoAnterior",mov.get ("saldoAnterior"))
                .append ("saldoPosterior",mov.get ("saldoPosterior")).append ("criadoEm",texto (mov.get ("criadoEm")));
            LocalDate dia=null;
            try { dia=Instant.parse (texto (mov.get ("criadoEm"))).atZone (ZoneId.of ("America/Sao_Paulo")).toLocalDate (); } catch (Exception ignorada) { }
            if (dia==null) { pendencias.add (item.append ("motivo","Data inválida; movimento fora do recorte.")); continue; }
            if (dia.isBefore (de) || dia.isAfter (ate)) continue;
            item.append ("data",dia.toString ());
            String tipo=texto (mov.get ("tipo")), unidade=texto (mov.get ("unidade"));
            if (produto==null || texto (produto.get ("nome"))==null) pendencias.add (item.append ("motivo","Produto vinculado indisponível."));
            else if (tipo==null || !List.of ("entrada","saida","ajuste").contains (tipo) || unidade==null || unidade.isBlank () ||
                !numero (mov.get ("quantidade")) || !numero (mov.get ("saldoAnterior")) || !numero (mov.get ("saldoPosterior")))
                pendencias.add (item.append ("motivo","Movimentação com dados inválidos."));
            else movimentos.add (item);
        }
        Document resultado=new Document ("propriedade",new Document ("id",pid).append ("nome",propriedade.getString ("nome")==null ? propriedade.getString ("farm") : propriedade.getString ("nome")))
            .append ("periodo",new Document ("inicio",de.toString ()).append ("fim",ate.toString ()))
            .append ("fuso","America/Sao_Paulo").append ("saldos",saldos).append ("movimentos",movimentos).append ("pendencias",pendencias);
        synchronized (sessoes)
        {
            Sessao atual=sessoes.buscar (r.getToken ());
            if (atual==null) return Resposta.erro (401,"Sessão encerrada.");
            if (!pid.equals (atual.getPropriedadeId ())) return Resposta.erro (409,"A propriedade ativa mudou. Consulte novamente.");
            Resposta resposta=Resposta.json (200,resultado); resposta.setCabecalho ("Cache-Control","no-store"); return resposta;
        }
        }
    }
}
