package handlers;

import dados.ConexaoMongo;
import http.Requisicao;
import http.Resposta;
import http.Roteador;

/*
    GET /health -> o processo esta de pe.
    GET /ready  -> o banco responde ao ping (sem banco, modo memoria, sempre pronto).
*/
public class SaudeHandler
{
    private static final String UP   = "{\"status\":\"UP\"}";
    private static final String DOWN = "{\"status\":\"DOWN\"}";

    private final ConexaoMongo conexao; // null no modo memoria

    public SaudeHandler (ConexaoMongo conexao)
    {
        this.conexao = conexao;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionarPublica ("GET", "/health", this::saude);
        roteador.adicionarPublica ("GET", "/ready",  this::pronto);
    }

    private Resposta saude (Requisicao requisicao)
    {
        return Resposta.json (200, SaudeHandler.UP);
    }

    private Resposta pronto (Requisicao requisicao)
    {
        if (this.conexao == null || this.conexao.pronto ())
            return Resposta.json (200, SaudeHandler.UP);

        return Resposta.json (503, SaudeHandler.DOWN);
    }
}
