package protocolo;

/*
    Constantes do protocolo proprio entre a API e o ServerSocket.
    Nao ha metodo: a classe so fixa o que os dois lados precisam concordar sem se falar antes.

    Conversa (uma conexao TCP, varios pedidos, um por vez):
      1. a API conecta; os dois lados criam o ObjectOutputStream antes do ObjectInputStream e dao flush;
      2. o servidor fala primeiro: ComunicadoDeBoasVindas, ou RespostaDeErro SERVIDOR_LOTADO e fecha;
      3. a API envia um Pedido e espera exatamente uma Resposta com o mesmo id;
      4. a API termina com PedidoParaSair (sem resposta). No desligamento, o servidor manda
         ComunicadoDeDesligamento e fecha. Cinco minutos sem pedido tambem fecham a conexao.
*/
public class Protocolo
{
    // vai no ComunicadoDeBoasVindas e na RespostaDePing. Mudar isto e mudar a geracao do protocolo.
    public static final String VERSAO       = "1.0";

    // a API usa esta porta se SERVIDOR_PORT nao estiver definida; o argumento do main ganha dela
    public static final int    PORTA_PADRAO = 3000;

    // teto lido pelo LimiteDeLeitura em cada conexao. 1 MiB, o mesmo limite de corpo HTTP da API.
    public static final long   MAXIMO_BYTES_POR_MENSAGEM = 1048576L;
}
