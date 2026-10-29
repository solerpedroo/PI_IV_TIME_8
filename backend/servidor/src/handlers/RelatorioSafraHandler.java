/* Miguel — RA: 25003110 */
package handlers;

import dados.*;
import http.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Sessao;
import seguranca.Sessoes;
import servicos.RelatorioSafra;

/* O relatório não grava dados e sempre consulta os repositórios da sessão. */
public class RelatorioSafraHandler implements Manipulador
{
    private final RepositorioUsuarios usuarios;
    private final RepositorioSafras safras;
    private final RepositorioTalhoes talhoes;
    private final RepositorioAtividades atividades;
    private final RepositorioOcorrencias ocorrencias;
    private final RepositorioCustos custos;
    private final Sessoes sessoes;

    public RelatorioSafraHandler (RepositorioUsuarios usuarios, RepositorioSafras safras, RepositorioTalhoes talhoes,
        RepositorioAtividades atividades, RepositorioOcorrencias ocorrencias, RepositorioCustos custos, Sessoes sessoes)
    {
        this.usuarios = usuarios; this.safras = safras; this.talhoes = talhoes;
        this.atividades = atividades; this.ocorrencias = ocorrencias; this.custos = custos; this.sessoes = sessoes;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionar ("GET", "/api/relatorios/safras/{id}", this);
    }

    public Resposta tratar (Requisicao requisicao) throws Exception
    {
        String id = requisicao.getParametro ("id");
        if (!ObjectId.isValid (id)) return Resposta.erro (400, "Identificador de safra inválido.");
        Sessao sessao = requisicao.getSessao ();
        String propriedadeId = sessao.getPropriedadeId ();
        Document propriedade = this.usuarios.buscarPropriedade (propriedadeId);
        if (propriedade == null || !sessao.getUsuarioId ().equals (propriedade.getString ("usuarioId")))
            return Resposta.erro (404, "Propriedade não encontrada.");
        Document safra = this.safras.buscarPorId (propriedadeId, id);
        if (safra == null) return Resposta.erro (404, "Safra não encontrada.");

        // Uma falha em qualquer fonte interrompe o relatório; nunca vira total zero.
        Document resultado = RelatorioSafra.compor (propriedade, safra, this.talhoes.listar (propriedadeId),
            this.atividades.listar (propriedadeId), this.ocorrencias.listar (sessao.getUsuarioId (), propriedadeId),
            this.custos.listar (propriedadeId));
        synchronized (this.sessoes)
        {
            Sessao atual = this.sessoes.buscar (requisicao.getToken ());
            if (atual == null) return Resposta.erro (401, "Sessão encerrada.");
            if (!atual.getPropriedadeId ().equals (propriedadeId))
                return Resposta.erro (409, "A propriedade ativa mudou. Consulte novamente.");
            Resposta resposta = Resposta.json (200, resultado);
            resposta.setCabecalho ("Cache-Control", "no-store");
            return resposta;
        }
    }
}
