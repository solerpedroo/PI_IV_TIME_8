/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package handlers;

import dados.RepositorioOcorrencias;
import dados.RepositorioTalhoes;
import http.Manipulador;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import protocolo.mensagens.RespostaDeGeometriaDoMapa;
import seguranca.Sessao;
import seguranca.Sessoes;
import servicos.AlgoritmosDeGeo;
import util.Json;

/*
    Georreferenciamento simplificado dos talhoes (#230, #231, #232). Sem satelite, sem camadas, sem GIS.

    PUT    /api/talhoes/{id}/localizacao   grava {tipo: ponto|poligono, pontos: [{lat, lng}]} (validado no socket)
    DELETE /api/talhoes/{id}/localizacao   remove (409 se houver ocorrencia marcada dentro do poligono)
    GET    /api/mapa?cultura=&ocorrencias=abertas|todas

    Validacao, area, centro e ponto no poligono sao calculados no servidor de algoritmos (GeometriaDeTalhao).
    Aqui ficam o banco, a sessao e as regras que dependem dele: o talhao e da propriedade, e nenhuma
    ocorrencia marcada pode ficar fora do poligono (trocar o poligono ou remover a localizacao responde 409).
    Talhao sem localizacao nao e desenhado (vai para "semLocalizacao"): nada de geometria inventada.
*/
public class GeorreferenciamentoHandler implements Manipulador
{
    private static final String RESOLVIDA = "RESOLVIDA";

    private final RepositorioTalhoes     talhoes;
    private final RepositorioOcorrencias ocorrencias;
    private final Sessoes                sessoes;

    public GeorreferenciamentoHandler (RepositorioTalhoes talhoes, RepositorioOcorrencias ocorrencias, Sessoes sessoes) throws Exception
    {
        if (talhoes == null)
            throw new Exception ("Repositorio de talhoes ausente");

        if (ocorrencias == null)
            throw new Exception ("Repositorio de ocorrencias ausente");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        this.talhoes     = talhoes;
        this.ocorrencias = ocorrencias;
        this.sessoes     = sessoes;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("PUT",    "/api/talhoes/{id}/localizacao", this);
        roteador.adicionar ("DELETE", "/api/talhoes/{id}/localizacao", this);
        roteador.adicionar ("GET",    "/api/mapa",                     this);
    }

    @Override
    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        Resposta ret;
        String   metodo = requisicao.getMetodo ().equals ("HEAD") ? "GET" : requisicao.getMetodo ();

        if (requisicao.getCaminho ().equals ("/api/mapa"))
            ret = this.mapa (requisicao);
        else if (metodo.equals ("PUT"))
            ret = this.definir (requisicao);
        else
            ret = this.remover (requisicao);

        return ret;
    }

    // ------------------------------------------------------------------ auxiliares

    private static String id (Document documento)
    {
        Object valor = documento.get ("_id");

        return valor instanceof ObjectId ? ((ObjectId) valor).toHexString () : String.valueOf (valor);
    }

    private static Document publico (Document talhao)
    {
        return Json.publico (talhao, "propriedadeId", "nomeNormalizado");
    }

    // a escrita usa a propriedade da sessao; se ela mudou no meio (outra aba selecionou outra), recusa
    private Resposta contextoMudou (Requisicao requisicao)
    {
        Resposta ret   = null;
        Sessao   atual = this.sessoes.buscar (requisicao.getToken ());

        if (atual == null)
            ret = Resposta.erro (401, "Não autorizado.");
        else if (!atual.getPropriedadeId ().equals (requisicao.getSessao ().getPropriedadeId ()))
            ret = Resposta.erro (409, "Property context changed; reload before saving");

        return ret;
    }

    // ocorrencias nao excluidas do talhao que tem posicao propria (mesma regra de dono do OcorrenciasHandler)
    private ArrayList<Document> marcadas (Requisicao requisicao, String talhaoId) throws Exception
    {
        ArrayList<Document> ret = new ArrayList<Document> ();
        Sessao              s   = requisicao.getSessao ();

        for (Document ocorrencia : this.ocorrencias.listar (s.getUsuarioId (), s.getPropriedadeId ()))
            if (!Boolean.TRUE.equals (ocorrencia.get ("excluida")) && talhaoId.equals (ocorrencia.getString ("talhaoId")) &&
                ocorrencia.get ("localizacao") instanceof Document)
                ret.add (ocorrencia);

        return ret;
    }

    private static Resposta conflito (String mensagem, Document ocorrencia)
    {
        Document corpo = new Document ("error", mensagem);

        if (ocorrencia != null)
            corpo.append ("ocorrenciaId", GeorreferenciamentoHandler.id (ocorrencia))
                 .append ("ocorrenciaTitulo", ocorrencia.getString ("titulo"));

        return Resposta.json (409, corpo);
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

    // ------------------------------------------------------------------ rotas

    // PUT /api/talhoes/{id}/localizacao
    private Resposta definir (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String   propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String   id            = requisicao.getParametro ("id");
            Document talhao        = this.talhoes.buscarPorId (propriedadeId, id);

            if (talhao == null)
                return Resposta.erro (404, "Não encontrado.");

            AlgoritmosDeGeo.ResultadoLocalizacao nova = AlgoritmosDeGeo.validarLocalizacao (requisicao.getCorpoJson ());

            if (nova.respostaErro != null)
                return nova.respostaErro;

            // nenhuma ocorrencia marcada pode ficar fora da area nova
            for (Document ocorrencia : this.marcadas (requisicao, id))
            {
                if (!AlgoritmosDeGeo.ehPoligono (nova.localizacao))
                    return GeorreferenciamentoHandler.conflito (
                        "H\u00e1 ocorr\u00eancia marcada neste pol\u00edgono. Mantenha um pol\u00edgono que a contenha ou exclua a marca\u00e7\u00e3o antes.",
                        ocorrencia);

                AlgoritmosDeGeo.ResultadoPonto ponto = AlgoritmosDeGeo.pontoNoTalhao (ocorrencia.get ("localizacao"), nova.localizacao);

                if (ponto.erro != null)
                    return GeorreferenciamentoHandler.conflito (
                        "H\u00e1 ocorr\u00eancia que ficaria fora do novo pol\u00edgono. Ajuste a \u00e1rea ou remova a marca\u00e7\u00e3o antes.",
                        ocorrencia);
            }

            this.talhoes.atualizar (propriedadeId, id, new Document ("localizacao",  nova.localizacao)
                                                           .append ("atualizadoEm", Instant.now ().toString ()));

            return Resposta.json (200, GeorreferenciamentoHandler.publico (this.talhoes.buscarPorId (propriedadeId, id))
                                           .append ("areaCalculadaHectares", nova.areaHectares)
                                           .append ("centro",                nova.centro));
        }
    }

    // DELETE /api/talhoes/{id}/localizacao
    private Resposta remover (Requisicao requisicao) throws Exception
    {
        synchronized (this.sessoes)
        {
            Resposta ret = this.contextoMudou (requisicao);
            if (ret != null)
                return ret;

            String              propriedadeId = requisicao.getSessao ().getPropriedadeId ();
            String              id            = requisicao.getParametro ("id");
            Document            talhao        = this.talhoes.buscarPorId (propriedadeId, id);
            ArrayList<Document> marcadas      = talhao == null ? null : this.marcadas (requisicao, id);

            if (talhao == null)
                ret = Resposta.erro (404, "Não encontrado.");
            else if (!marcadas.isEmpty ())
                ret = GeorreferenciamentoHandler.conflito ("H\u00e1 ocorr\u00eancias marcadas neste pol\u00edgono.", marcadas.get (0));
            else
            {
                this.talhoes.atualizar (propriedadeId, id, new Document ("localizacao",  null)
                                                               .append ("atualizadoEm", Instant.now ().toString ()));
                ret = Resposta.semConteudo ();
            }

            return ret;
        }
    }

    // GET /api/mapa?cultura=&ocorrencias=abertas|todas
    private Resposta mapa (Requisicao requisicao) throws Exception
    {
        String  propriedadeId = requisicao.getSessao ().getPropriedadeId ();
        String  cultura       = GeorreferenciamentoHandler.filtro (requisicao, "cultura");
        String  situacao      = requisicao.getConsulta ("ocorrencias");
        boolean todas         = "todas".equals (situacao);

        if (situacao != null && !situacao.isEmpty () && !todas && !"abertas".equals (situacao))
            return Resposta.json (400, new Document ("error", "Dados inválidos.")
                                           .append ("fields", new Document ("ocorrencias", "Use abertas ou todas.")));

        ArrayList<Document> localizados = new ArrayList<Document> ();
        ArrayList<String>   ids         = new ArrayList<String> ();
        ArrayList<Document> semLocal    = new ArrayList<Document> ();

        for (Document talhao : this.talhoes.listar (propriedadeId))
        {
            if (cultura != null && !cultura.equalsIgnoreCase (talhao.getString ("cultura")))
                continue;

            if (talhao.get ("localizacao") instanceof Document)
            {
                localizados.add (talhao);
                ids.add (GeorreferenciamentoHandler.id (talhao));
            }
            else
                semLocal.add (talhao);
        }

        // ocorrencias por talhao (excluidas nunca entram; resolvidas so com ocorrencias=todas)
        LinkedHashMap<String, ArrayList<Document>> porTalhao = new LinkedHashMap<String, ArrayList<Document>> ();
        LinkedHashMap<String, Integer>             abertas   = new LinkedHashMap<String, Integer> ();
        int                                        semTalhao = 0;
        Sessao                                     sessao    = requisicao.getSessao ();

        for (Document ocorrencia : this.ocorrencias.listar (sessao.getUsuarioId (), propriedadeId))
        {
            if (Boolean.TRUE.equals (ocorrencia.get ("excluida")))
                continue;

            boolean resolvida = GeorreferenciamentoHandler.RESOLVIDA.equals (ocorrencia.getString ("status"));
            String  talhaoId  = ocorrencia.getString ("talhaoId");

            if (!resolvida && talhaoId != null)
                abertas.put (talhaoId, Integer.valueOf (abertas.containsKey (talhaoId) ? abertas.get (talhaoId).intValue () + 1 : 1));

            if (resolvida && !todas)
                continue;

            if (talhaoId == null || talhaoId.isEmpty ())
                semTalhao++;
            else
            {
                if (!porTalhao.containsKey (talhaoId))
                    porTalhao.put (talhaoId, new ArrayList<Document> ());
                porTalhao.get (talhaoId).add (ocorrencia);
            }
        }

        RespostaDeGeometriaDoMapa geometria = null;

        if (!localizados.isEmpty ())
        {
            AlgoritmosDeGeo.ResultadoMapa resultado = AlgoritmosDeGeo.geometriaDoMapa (localizados, ids);

            if (resultado.respostaErro != null)
                return resultado.respostaErro;

            geometria = resultado.geometria;
        }

        ArrayList<Document> listaTalhoes = new ArrayList<Document> ();

        for (int i = 0; i < localizados.size (); i++)
        {
            Document            talhao    = localizados.get (i);
            String              talhaoId  = ids.get (i);
            Double              centroLat = geometria.getCentroLat ().get (talhaoId);
            Double              centroLng = geometria.getCentroLng ().get (talhaoId);
            ArrayList<Document> marcas    = new ArrayList<Document> ();
            ArrayList<Document> doTalhao  = porTalhao.get (talhaoId);

            if (doTalhao != null)
                for (Document ocorrencia : doTalhao)
                {
                    Object  posicao = ocorrencia.get ("localizacao");
                    boolean propria = posicao instanceof Document;

                    marcas.add (new Document ("id",         GeorreferenciamentoHandler.id (ocorrencia))
                                    .append  ("titulo",     ocorrencia.getString ("titulo"))
                                    .append  ("tipo",       ocorrencia.getString ("tipo"))
                                    .append  ("prioridade", ocorrencia.getString ("prioridade"))
                                    .append  ("status",     ocorrencia.getString ("status"))
                                    .append  ("data",       ocorrencia.getString ("data"))
                                    .append  ("posicao",    propria ? "propria" : "talhao")
                                    .append  ("lat",        propria ? ((Document) posicao).get ("lat") : centroLat)
                                    .append  ("lng",        propria ? ((Document) posicao).get ("lng") : centroLng));
                }

            listaTalhoes.add (new Document ("id",                    talhaoId)
                                  .append  ("nome",                  talhao.getString ("nome"))
                                  .append  ("cultura",               talhao.getString ("cultura"))
                                  .append  ("hectares",              talhao.get ("hectares"))
                                  .append  ("status",                talhao.getString ("status"))
                                  .append  ("localizacao",           talhao.get ("localizacao", Document.class))
                                  .append  ("centro",                new Document ("lat", centroLat).append ("lng", centroLng))
                                  .append  ("areaCalculadaHectares", geometria.getAreaHectares ().get (talhaoId))
                                  .append  ("ocorrencias",           marcas));
        }

        ArrayList<Document> listaSem = new ArrayList<Document> ();

        for (Document talhao : semLocal)
        {
            String  talhaoId = GeorreferenciamentoHandler.id (talhao);
            Integer quantas  = abertas.get (talhaoId);

            listaSem.add (new Document ("id",                 talhaoId)
                              .append  ("nome",               talhao.getString ("nome"))
                              .append  ("cultura",            talhao.getString ("cultura"))
                              .append  ("hectares",           talhao.get ("hectares"))
                              .append  ("ocorrenciasAbertas", quantas == null ? 0 : quantas.intValue ()));
        }

        Document limites = null;

        if (geometria != null && geometria.getLatMin () != null)
            limites = new Document ("latMin", geometria.getLatMin ()).append ("latMax", geometria.getLatMax ())
                          .append  ("lngMin", geometria.getLngMin ()).append ("lngMax", geometria.getLngMax ());

        return Resposta.json (200, new Document ("talhoes",              listaTalhoes)
                                       .append  ("semLocalizacao",       listaSem)
                                       .append  ("ocorrenciasSemTalhao", semTalhao)
                                       .append  ("limites",              limites));
    }
}
