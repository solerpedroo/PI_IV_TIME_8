// Miguel — RA: 25003110
package br.com.agrogestao.core.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.cultivo.Cultura;
import br.com.agrogestao.core.domain.cultivo.Safra;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import br.com.agrogestao.core.infra.mongo.CultivoRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.AtividadeRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.OcorrenciaRepositorioMongo;
import br.com.agrogestao.core.infra.mongo.CustoRepositorioMongo;

/** API mínima de cultivo; o escopo obrigatório evita que IDs cruzem propriedades. */
public final class ApiCultivoHandler implements HttpHandler {
    private final CultivoRepositorioMongo cultivo;
    private final AtividadeRepositorioMongo atividades;
    private final OcorrenciaRepositorioMongo ocorrencias;
    private final CustoRepositorioMongo custos;

    public ApiCultivoHandler(CultivoRepositorioMongo cultivo, AtividadeRepositorioMongo atividades,
            OcorrenciaRepositorioMongo ocorrencias, CustoRepositorioMongo custos) {
        this.cultivo = cultivo;
        this.atividades = atividades;
        this.ocorrencias = ocorrencias;
        this.custos = custos;
    }

    @Override public void handle(HttpExchange troca) throws IOException {
        try {
            String caminho = troca.getRequestURI().getPath();
            EscopoPropriedade escopo = escopo(troca);
            if (caminho.startsWith("/api/safras")) safra(troca, escopo, caminho.substring("/api/safras".length()));
            else if (caminho.startsWith("/api/culturas")) cultura(troca, escopo, caminho.substring("/api/culturas".length()));
            else responder(troca, 404, erro("Rota não encontrada."));
        } catch (IllegalArgumentException erro) { responder(troca, 400, erro(erro.getMessage())); }
        catch (Exception erro) { responder(troca, 500, erro("Erro interno.")); }
    }

    private void safra(HttpExchange t, EscopoPropriedade e, String sufixo) throws IOException {
        String metodo = t.getRequestMethod();
        if (sufixo.isEmpty() && metodo.equals("GET")) { responder(t, 200, listaSafras(cultivo.listarSafras(e))); return; }
        if (sufixo.isEmpty() && metodo.equals("POST")) { Safra s = safra(corpo(t), e, null); responder(t, 201, id(cultivo.inserir(s))); return; }
        String[] partes = sufixo.split("/");
        if (partes.length < 2 || partes[1].isBlank()) { responder(t, 404, erro("Rota não encontrada.")); return; }
        ObjectId id = objeto(partes[1]);
        if (partes.length == 3 && "vinculos".equals(partes[2]) && metodo.equals("GET")) {
            responder(t, 200, vinculosSafra(e, id)); return;
        }
        if (partes.length == 3 && "fase".equals(partes[2]) && metodo.equals("POST")) {
            Safra.Status fase = Safra.Status.valueOf(corpo(t).getString("status"));
            responder(t, 200, cultivo.avancarSafra(e, id, fase).paraDocumento()); return;
        }
        if (partes.length == 2 && metodo.equals("GET")) { responder(t, 200, cultivo.buscarSafra(e, id).orElseThrow(() -> new IllegalArgumentException("safra não encontrada")).paraDocumento()); return; }
        if (partes.length == 2 && metodo.equals("PUT")) { responder(t, 200, cultivo.atualizarSafra(safra(corpo(t), e, id)).paraDocumento()); return; }
        if (partes.length == 2 && metodo.equals("DELETE")) { cultivo.excluirSafra(e, id); responder(t, 204, null); return; }
        responder(t, 404, erro("Rota não encontrada."));
    }

    private void cultura(HttpExchange t, EscopoPropriedade e, String sufixo) throws IOException {
        String metodo = t.getRequestMethod();
        if (sufixo.isEmpty() && metodo.equals("GET")) { responder(t, 200, listaCulturas(e)); return; }
        if (sufixo.isEmpty() && metodo.equals("POST")) { responder(t, 201, id(cultivo.inserir(cultura(corpo(t), e, null)))); return; }
        String[] partes = sufixo.split("/");
        if (partes.length < 2 || partes[1].isBlank()) { responder(t, 404, erro("Rota não encontrada.")); return; }
        ObjectId id = objeto(partes[1]);
        if (metodo.equals("GET")) { responder(t, 200, cultivo.buscarCultura(e, id).orElseThrow(() -> new IllegalArgumentException("cultura não encontrada")).paraDocumento()); return; }
        if (metodo.equals("PUT")) { responder(t, 200, cultivo.atualizarCultura(cultura(corpo(t), e, id)).paraDocumento()); return; }
        if (metodo.equals("DELETE")) { cultivo.excluirCultura(e, id); responder(t, 204, null); return; }
        responder(t, 404, erro("Rota não encontrada."));
    }

    private Safra safra(Document d, EscopoPropriedade e, ObjectId id) {
        List<ObjectId> talhoes = new ArrayList<>(); for (Object valor : d.getList("talhaoIds", Object.class)) talhoes.add(objeto(String.valueOf(valor)));
        return new Safra(id, e, d.getString("nome"), objeto(d.getString("culturaId")), talhoes,
                LocalDate.parse(d.getString("inicio")), LocalDate.parse(d.getString("fim")), new BigDecimal(String.valueOf(d.get("areaHectares"))),
                Safra.Status.valueOf(d.getString("status")));
    }
    private Cultura cultura(Document d, EscopoPropriedade e, ObjectId id) { return new Cultura(id, e, d.getString("nome")); }
    private EscopoPropriedade escopo(HttpExchange t) { Map<String, String> q = query(t); return new EscopoPropriedade(objeto(q.get("proprietarioId")), objeto(q.get("propriedadeId"))); }
    private Document corpo(HttpExchange t) throws IOException { return Document.parse(new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); }
    private Map<String, String> query(HttpExchange t) { Map<String, String> r = new java.util.HashMap<>(); String q = t.getRequestURI().getRawQuery(); if (q != null) for (String p : q.split("&")) { String[] kv = p.split("=", 2); if (kv.length == 2) r.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8)); } return r; }
    private ObjectId objeto(String valor) { if (valor == null || !ObjectId.isValid(valor)) throw new IllegalArgumentException("ObjectId inválido"); return new ObjectId(valor); }
    private Document id(ObjectId id) { return new Document("id", id); }
    private Document erro(String mensagem) { return new Document("erro", mensagem); }
    private Document listaSafras(List<Safra> safras) { return new Document("safras", safras.stream().map(Safra::paraDocumento).toList()); }
    private Document listaCulturas(EscopoPropriedade e) { return new Document("culturas", cultivo.listarCulturas(e).stream().map(Cultura::paraDocumento).toList()); }
    private Document vinculosSafra(EscopoPropriedade e, ObjectId id) {
        Document safra = cultivo.buscarSafra(e, id).orElseThrow(() -> new IllegalArgumentException("safra não encontrada")).paraDocumento();
        return new Document("safra", safra)
                .append("atividades", atividades.listarDaSafra(e, id).stream().map(a -> a.paraDocumento()).toList())
                .append("ocorrencias", ocorrencias.listarDaSafra(e, id).stream().map(o -> o.paraDocumento()).toList())
                .append("custos", custos.listarDaSafra(e, id).stream().map(c -> c.paraDocumento()).toList());
    }
    private void responder(HttpExchange t, int status, Document d) throws IOException { if (status == 204) { t.sendResponseHeaders(status, -1); t.close(); return; } byte[] b = d.toJson().getBytes(StandardCharsets.UTF_8); t.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); t.sendResponseHeaders(status, b.length); t.getResponseBody().write(b); t.close(); }
}
