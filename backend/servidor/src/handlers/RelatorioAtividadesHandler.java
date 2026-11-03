/* Miguel — RA: 25003110 */
package handlers;

import dados.RepositorioAtividades;
import dados.RepositorioUsuarios;
import http.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import seguranca.Sessao;
import seguranca.Sessoes;
import servicos.RelatorioSafra;

/* Data é a data planejada; o status e o atraso são os observados na consulta. */
public class RelatorioAtividadesHandler implements Manipulador
{
    private final RepositorioUsuarios usuarios;
    private final RepositorioAtividades atividades;
    private final Sessoes sessoes;
    private final Clock relogio;
    public RelatorioAtividadesHandler (RepositorioUsuarios u, RepositorioAtividades a, Sessoes s)
    { this (u,a,s,Clock.system (ZoneId.of ("America/Sao_Paulo"))); }
    public RelatorioAtividadesHandler (RepositorioUsuarios u, RepositorioAtividades a, Sessoes s, Clock c)
    { usuarios=u; atividades=a; sessoes=s; relogio=c; }
    public void registrar (Roteador r) throws Exception { r.adicionar ("GET","/api/relatorios/atividades",this); }
    private static String texto (Object v) { return v instanceof String ? (String) v : null; }
    public Resposta tratar (Requisicao r) throws Exception
    {
        LocalDate de=RelatorioSafra.dia (r.getConsulta ("de")),ate=RelatorioSafra.dia (r.getConsulta ("ate"));
        if(de==null||ate==null||ate.isBefore(de))return Resposta.erro (400,"Informe um período válido.");
        Sessao sessao=r.getSessao ();String pid=sessao.getPropriedadeId ();
        Document propriedade=usuarios.buscarPropriedade (pid);
        if(propriedade==null || !sessao.getUsuarioId ().equals (propriedade.getString ("usuarioId")))return Resposta.erro (404,"Propriedade não encontrada.");
        LocalDate hoje=LocalDate.now (relogio.withZone (ZoneId.of ("America/Sao_Paulo")));
        List<Document> registros=new ArrayList<> (),pendencias=new ArrayList<> ();
        int concluidas=0,pendentes=0,atrasadas=0;
        for(Document atividade:atividades.listar (pid))
        {
            String status=texto (atividade.get ("status")),data=texto (atividade.get ("data"));
            Document item=new Document ("id",RelatorioSafra.id (atividade)).append ("nome",texto (atividade.get ("nome")))
                .append ("talhao",texto (atividade.get ("talhao"))).append ("safraId",texto (atividade.get ("safraId")))
                .append ("data",data).append ("status",status);
            if(status==null || !AtividadesHandler.STATUS_VALIDOS.contains (status)) {pendencias.add(item.append("motivo","Status inválido."));continue;}
            if(status.equals("Cancelada"))continue;
            LocalDate dia=RelatorioSafra.dia (data);
            if(dia==null){pendencias.add(item.append("motivo","Data inválida ou sem ano; fora do recorte."));continue;}
            if(dia.isBefore(de)||dia.isAfter(ate))continue;
            boolean concluida=status.equals("Concluída"),pendente=status.equals("Pendente")||status.equals("Agendada");
            boolean atrasada=!concluida && dia.isBefore(hoje);
            if(!concluida&&!pendente&&!atrasada)continue;
            item.append("concluida",concluida).append("pendente",pendente).append("atrasada",atrasada);
            registros.add(item);if(concluida)concluidas++;if(pendente)pendentes++;if(atrasada)atrasadas++;
        }
        Document resultado=new Document("propriedade",new Document("id",pid).append("nome",propriedade.getString("nome")==null ? propriedade.getString("farm") : propriedade.getString("nome")))
            .append("periodo",new Document("inicio",de.toString()).append("fim",ate.toString()))
            .append("dataConsulta",hoje.toString()).append("fuso","America/Sao_Paulo")
            .append("contagens",new Document("concluidas",concluidas).append("pendentes",pendentes).append("atrasadas",atrasadas))
            .append("registros",registros).append("pendencias",pendencias);
        synchronized(sessoes)
        {
            Sessao atual=sessoes.buscar(r.getToken());if(atual==null)return Resposta.erro(401,"Sessão encerrada.");
            if(!pid.equals(atual.getPropriedadeId()))return Resposta.erro(409,"A propriedade ativa mudou. Consulte novamente.");
            Resposta resposta=Resposta.json(200,resultado);resposta.setCabecalho("Cache-Control","no-store");return resposta;
        }
    }
}
