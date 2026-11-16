/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package handlers.conta;

import dados.infra.ConexaoMongo;
import http.Requisicao;
import http.Resposta;
import http.Roteador;

/*
    Sondas de processo, usadas por balanceador, script de subida ou pelo
    gateway Java que já consultava o Actuator do Spring.

    Quem chama: monitoramento e o próprio gateway. Não é tela do front.
    As três rotas são públicas, sem token.

      GET /health           o processo está de pé (não olha o banco)
      GET /ready            o Mongo responde ao ping
      GET /actuator/health  o mesmo que /ready, para o gateway existente

    O que persiste: nada. conexao nula significa modo memória, e nesse
    modo /ready responde pronto sem tentar ping.
*/
public class SaudeHandler
{
    private static final String UP   = "{\"status\":\"UP\"}";
    private static final String DOWN = "{\"status\":\"DOWN\"}";

    private final ConexaoMongo conexao; // null no modo memória: não há banco para pingar

    /*
        Aceita conexão nula de propósito. Quem sobe a API em memória
        passa null e as sondas continuam respondendo.
    */
    public SaudeHandler (ConexaoMongo conexao)
    {
        this.conexao = conexao;
    }

    /*
        As três rotas são públicas. /actuator/health existe só por
        compatibilidade com o gateway Java, sem dependência de Spring.
    */
    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionarPublica ("GET", "/health", this::saude);
        roteador.adicionarPublica ("GET", "/ready",  this::pronto);
        roteador.adicionarPublica ("GET", "/actuator/health", this::pronto);
    }

    /*
        GET /health
        Não consulta banco nem sessão. 200 com {"status":"UP"} enquanto
        o processo consegue responder. Não há 401: a rota é pública.
    */
    private Resposta saude (Requisicao requisicao)
    {
        return Resposta.json (200, SaudeHandler.UP);
    }

    /*
        GET /ready e GET /actuator/health
        200 UP: modo memória (conexao nula) ou o ping do Mongo passou.
        503 DOWN: há banco configurado e o ping falhou. O processo segue
        no ar; quem orquestra é que deve tirar a instância de tráfego.
    */
    private Resposta pronto (Requisicao requisicao)
    {
        if (this.conexao == null || this.conexao.pronto ())
            return Resposta.json (200, SaudeHandler.UP);

        return Resposta.json (503, SaudeHandler.DOWN);
    }
}
