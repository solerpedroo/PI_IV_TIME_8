/* Miguel — RA: 25003110 */
package handlers;

import dados.*;
import http.*;
import java.time.LocalDate;
import java.util.*;
import org.bson.Document;
import seguranca.Sessao;
import seguranca.Sessoes;
import servicos.RelatorioSafra;

/* Consulta somente a propriedade da sessão; o período nunca altera os saldos atuais. */
public class RelatorioPropriedadeHandler implements Manipulador
{
    private final RepositorioUsuarios usuarios;
    private final RepositorioTalhoes talhoes;
    private final RepositorioSafras safras;
    private final RepositorioEstoque estoque;
    private final RepositorioAtividades atividades;
    private final RepositorioOcorrencias ocorrencias;
    private final RepositorioCustos custos;
    private final Sessoes sessoes;

    public RelatorioPropriedadeHandler (RepositorioUsuarios u, RepositorioTalhoes t, RepositorioSafras s, RepositorioEstoque e,
        RepositorioAtividades a, RepositorioOcorrencias o, RepositorioCustos c, Sessoes sessoes)
    { usuarios=u; talhoes=t; safras=s; estoque=e; atividades=a; ocorrencias=o; custos=c; this.sessoes=sessoes; }

    public void registrar (Roteador r) throws Exception { r.adicionar ("GET", "/api/relatorios/propriedade", this); }

    private static void adicionar (List<Document> registros, List<Document> pendencias, Document origem,
        String tipo, String descricao, String data, LocalDate de, LocalDate ate) throws ErroHttp
    {
        Document item = new Document ("tipo", tipo).append ("id", RelatorioSafra.id (origem))
            .append ("descricao", descricao).append ("data", data);
        if (tipo.equals ("custo")) item.append ("valorCentavos", RelatorioSafra.centavos (origem));
        LocalDate dia = RelatorioSafra.dia (data);
        if (dia == null) pendencias.add (item.append ("motivo", "Data inválida ou sem ano; não incluído nos totais."));
        else if (!dia.isBefore (de) && !dia.isAfter (ate)) registros.add (item);
    }

    public Resposta tratar (Requisicao r) throws Exception
    {
        LocalDate de=RelatorioSafra.dia (r.getConsulta ("de")), ate=RelatorioSafra.dia (r.getConsulta ("ate"));
        if (de == null || ate == null || ate.isBefore (de)) return Resposta.erro (400, "Informe um período válido, com início até o fim.");
        Sessao sessao=r.getSessao (); String pid=sessao.getPropriedadeId ();
        Document propriedade=usuarios.buscarPropriedade (pid);
        if (propriedade == null || !sessao.getUsuarioId ().equals (propriedade.getString ("usuarioId")))
            return Resposta.erro (404, "Propriedade não encontrada.");
        List<Document> terrenos=talhoes.listar (pid), ciclos=safras.listar (pid), insumos=estoque.listar (pid);
        List<Document> registros=new ArrayList<> (), pendencias=new ArrayList<> ();
        for (Document a : atividades.listar (pid)) adicionar (registros, pendencias, a, "atividade", a.getString ("nome"), a.getString ("data"), de, ate);
        for (Document o : ocorrencias.listar (sessao.getUsuarioId (), pid))
            if (!Boolean.TRUE.equals (o.get ("excluida"))) adicionar (registros, pendencias, o, "ocorrencia", o.getString ("titulo"), o.getString ("data"), de, ate);
        for (Document c : custos.listar (pid)) adicionar (registros, pendencias, c, "custo", c.getString ("description"), c.getString ("date"), de, ate);
        long total=0; Document contagens=new Document ("atividade", 0).append ("ocorrencia", 0).append ("custo", 0);
        for (Document item : registros)
        {
            String tipo=item.getString ("tipo"); contagens.put (tipo, contagens.getInteger (tipo)+1);
            if (tipo.equals ("custo")) total=Math.addExact (total, item.getLong ("valorCentavos"));
        }
        double area=0; for (Document t : terrenos) area+=SafrasHandler.hectares (t);
        long produtos=insumos.stream ().filter (i -> !Boolean.TRUE.equals (i.get ("excluido"))).count ();
        Document resultado=new Document ("propriedade", new Document ("id", pid).append ("nome", propriedade.getString ("nome") == null ? propriedade.getString ("farm") : propriedade.getString ("nome")))
            .append ("periodo", new Document ("inicio", de.toString ()).append ("fim", ate.toString ()))
            .append ("atuais", new Document ("talhoes", terrenos.size ()).append ("areaHectares", area).append ("safras", ciclos.size ()).append ("produtos", produtos))
            .append ("contagens", contagens).append ("totalCentavos", total).append ("registros", registros).append ("pendencias", pendencias);
        synchronized (sessoes)
        {
            Sessao atual=sessoes.buscar (r.getToken ());
            if (atual == null) return Resposta.erro (401, "Sessão encerrada.");
            if (!pid.equals (atual.getPropriedadeId ())) return Resposta.erro (409, "A propriedade ativa mudou. Consulte novamente.");
            Resposta resposta=Resposta.json (200, resultado); resposta.setCabecalho ("Cache-Control", "no-store"); return resposta;
        }
    }
}
