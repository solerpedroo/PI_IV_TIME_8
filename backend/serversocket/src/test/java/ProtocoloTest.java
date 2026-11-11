import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import protocolo.Cliente;
import protocolo.Comunicado;
import protocolo.ComunicadoDeBoasVindas;
import protocolo.ComunicadoDeDesligamento;
import protocolo.Parceiro;
import protocolo.PedidoParaSair;
import protocolo.Protocolo;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeMovimentacaoDeEstoque;
import protocolo.mensagens.PedidoDePing;
import protocolo.mensagens.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.PedidoDeResumoDeTalhoes;
import protocolo.mensagens.PedidoDeValidacaoDeCadastro;
import protocolo.mensagens.PedidoDeValidacaoDeTalhao;
import protocolo.mensagens.RegistroDaSafra;
import protocolo.mensagens.RespostaDeMovimentacaoDeEstoque;
import protocolo.mensagens.RespostaDePing;
import protocolo.mensagens.RespostaDeRelatorioDeSafra;
import protocolo.mensagens.RespostaDeResumoDeTalhoes;
import protocolo.mensagens.RespostaDeValidacao;
import protocolo.mensagens.TalhaoDaSafra;
import protocolo.mensagens.TalhaoParaResumo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

// Classe fora do pacote protocolo: o filtro de classes do servidor tem que recusar.
class ComunicadoMalicioso extends Comunicado
{
    private static final long serialVersionUID = 1L;
}

/* Sobe o servidor de verdade (porta livre, 127.0.0.1) e conversa com ele pelo protocolo, como a API vai fazer. */
@TestMethodOrder (MethodOrderer.OrderAnnotation.class)
@TestInstance (Lifecycle.PER_CLASS)
class ProtocoloTest
{
    private static final String HOST = "127.0.0.1";
    private Servidor servidor;

    @BeforeAll
    void iniciarServidor () throws Exception
    {
        Servidor.registrarPedidos = false;
        servidor = new Servidor (HOST, 0, Servidor.MAXIMO_DE_CONEXOES, Servidor.OCIOSO_EM_MILISSEGUNDOS);
    }

    @AfterAll
    void encerrarServidor () throws Exception
    {
        servidor.desativar ();
    }


    private static void verificar (String nome, boolean ok)
    {
        assertTrue (ok, nome);
    }

    private static <T extends Resposta> T como (Resposta resposta, Class<T> tipo)
    {
        if (!tipo.isInstance (resposta))
            throw new AssertionError ("esperava " + tipo.getSimpleName () + " mas veio " + resposta +
                (resposta instanceof RespostaDeErro ? " " + ((RespostaDeErro) resposta).getMensagem () : ""));

        return tipo.cast (resposta);
    }

    private static boolean falha (Cliente cliente, protocolo.Pedido pedido)
    {
        try
        {
            cliente.chamar (pedido);
            return false;
        }
        catch (Exception erro)
        {
            return true;
        }
    }

    // conexao "crua" (sem o Cliente), com limite de 5 s: um defeito vira falha do teste, nao travamento
    private static Parceiro conectar (int porta) throws Exception
    {
        Socket conexao = new Socket (HOST, porta);
        conexao.setSoTimeout (5000);

        return new Parceiro (conexao);
    }

    // espera uma condicao que depende de outra thread do servidor (ate 3 s)
    private static boolean aguardar (java.util.function.BooleanSupplier condicao) throws Exception
    {
        for (int i = 0; i < 60; i++)
        {
            if (condicao.getAsBoolean ()) return true;
            Thread.sleep (50);
        }
        return false;
    }

    private static PedidoDeMovimentacaoDeEstoque entrada (int saldo)
    {
        return new PedidoDeMovimentacaoDeEstoque (saldo, 2.0, "entrada", 10.0, 4.0, null);
    }

    @Test
    @Order (1)
    void conversa () throws Exception
    {
        Cliente cliente = new Cliente (HOST, servidor.getPorta (), 3000);

        RespostaDePing ping = como (cliente.chamar (new PedidoDePing ()), RespostaDePing.class);
        verificar ("ping pelo socket devolve a versão do protocolo", Protocolo.VERSAO.equals (ping.getVersao ()));

        PedidoDeValidacaoDeCadastro cadastro = new PedidoDeValidacaoDeCadastro ("Ana", "ANA@x.com", "(11) 91234-5678", "Senha@123", "Senha@123", "Fazenda", "10 ha", true);
        RespostaDeValidacao validacao = como (cliente.chamar (cadastro), RespostaDeValidacao.class);
        verificar ("validação de cadastro atravessa o socket (e devolve o id do pedido)", validacao.isValido () && validacao.getIdDoPedido () == cadastro.getId () &&
                   "ana@x.com".equals (validacao.getNormalizados ().get ("email")));

        RespostaDeValidacao talhao = como (cliente.chamar (new PedidoDeValidacaoDeTalhao ("", "Banana", null, -1.0, null)), RespostaDeValidacao.class);
        verificar ("erros por campo atravessam o socket", talhao.getErros ().size () == 3 && talhao.getErros ().containsKey ("hectares"));

        RespostaDeResumoDeTalhoes resumo = como (cliente.chamar (new PedidoDeResumoDeTalhoes (List.of (new TalhaoParaResumo (12.5, "Ativo"), new TalhaoParaResumo (7.5, "Em preparo")))), RespostaDeResumoDeTalhoes.class);
        verificar ("resumo de talhões (lista imutável do lado da API vira ArrayList)", resumo.getTotalHectares () == 20 && resumo.getQuantidadeAtivos () == 1);

        RespostaDeMovimentacaoDeEstoque estoque = como (cliente.chamar (new PedidoDeMovimentacaoDeEstoque (10, 5, "entrada", 10.0, 7.0, null)), RespostaDeMovimentacaoDeEstoque.class);
        verificar ("estoque pelo socket", estoque.isOk () && estoque.getSaldoPosterior () == 20 && estoque.getCustoUnitario () == 6);

        TalhaoDaSafra norte = new TalhaoDaSafra ("t1", "Norte", 12.5);
        List<RegistroDaSafra> registros = new ArrayList<RegistroDaSafra> ();
        registros.add (new RegistroDaSafra ("custo", "c1", "Adubo", "2026-06-01", null, "t1", null, "Insumos", new java.math.BigDecimal ("0.10"), false));
        registros.add (new RegistroDaSafra ("custo", "c2", "Semente", "2026-06-02", null, null, null, "Insumos", new java.math.BigDecimal ("0.20"), false));
        registros.add (new RegistroDaSafra ("atividade", "a1", "Plantio", "2026-01-01", "Concluída", null, "norte", null, null, false));
        RespostaDeRelatorioDeSafra relatorio = como (cliente.chamar (new PedidoDeRelatorioDeSafra ("2026-01-01", "2026-12-31", List.of ("t1"), List.of (norte), registros)), RespostaDeRelatorioDeSafra.class);
        verificar ("relatório de safra pelo socket (BigDecimal, listas e mapas aninhados)", relatorio.getTotalCentavos () == 30 && relatorio.getAreaHectares () == 12.5 &&
                   relatorio.getRegistros ().size () == 3 && relatorio.getContagens ().get ("custo") == 2);

        RespostaDeErro erro = como (cliente.chamar (new PedidoDeRelatorioDeSafra ("2026-01-01", "2026-12-31", null, null, null)), RespostaDeErro.class);
        verificar ("erro de aplicação chega como RespostaDeErro, não como exceção", RespostaDeErro.PEDIDO_INVALIDO.equals (erro.getCodigo ()));

        verificar ("a conexão continua útil depois de um erro de aplicação", como (cliente.chamar (new PedidoDePing ()), RespostaDePing.class) != null);

        cliente.fechar ();
        verificar ("PedidoParaSair libera a conexão no servidor", aguardar ( () -> servidor.getConexoesAtivas () == 0));
    }

    // varios pedidos enviados sem esperar: o servidor responde um a um, na ordem, com os ids certos
    @Test
    @Order (2)
    void pipeline () throws Exception
    {
        Parceiro parceiro = ProtocoloTest.conectar (servidor.getPorta ());
        verificar ("o servidor fala primeiro: boas-vindas", parceiro.envie () instanceof ComunicadoDeBoasVindas);

        PedidoDePing a = new PedidoDePing (), b = new PedidoDePing (), c = new PedidoDePing ();
        parceiro.receba (a);
        parceiro.receba (b);
        parceiro.receba (c);

        Resposta ra = (Resposta) parceiro.envie (), rb = (Resposta) parceiro.envie (), rc = (Resposta) parceiro.envie ();
        verificar ("pedidos em sequência são respondidos na ordem, cada um com seu id",
                   ra.getIdDoPedido () == a.getId () && rb.getIdDoPedido () == b.getId () && rc.getIdDoPedido () == c.getId ());

        parceiro.receba (new PedidoParaSair ());
        verificar ("depois de PedidoParaSair o servidor fecha a conexão", parceiro.envie () == null);
        parceiro.adeus ();
    }

    @Test
    @Order (3)
    void seguranca () throws Exception
    {
        Parceiro parceiro = ProtocoloTest.conectar (servidor.getPorta ());
        parceiro.envie (); // boas-vindas

        parceiro.receba (new ComunicadoMalicioso ());
        Comunicado resposta = parceiro.envie ();
        verificar ("classe fora da lista de permissão é recusada pelo filtro",
                   resposta instanceof RespostaDeErro && RespostaDeErro.COMUNICADO_PROIBIDO.equals (((RespostaDeErro) resposta).getCodigo ()));

        boolean encerrou;
        try
        {
            encerrou = parceiro.envie () == null;
        }
        catch (Exception erro)
        {
            encerrou = true; // reset da conexao tambem significa que o servidor fechou
        }
        verificar ("depois de recusar, o servidor fecha aquela conexão", encerrou);
        parceiro.adeus ();

        Cliente outro = new Cliente (HOST, servidor.getPorta (), 3000);
        verificar ("a recusa de um cliente não derruba o servidor nem as outras conexões", como (outro.chamar (new PedidoDePing ()), RespostaDePing.class) != null);
        outro.fechar ();
    }

    // quem chama pelo Cliente: um pedido por vez; a API abre varias conexoes para atender em paralelo
    @Test
    @Order (4)
    void concorrencia () throws Exception
    {
        final int threads = 50, chamadas = 40;
        AtomicInteger certas = new AtomicInteger (), erradas = new AtomicInteger ();
        List<Thread>  lista  = new ArrayList<Thread> ();

        for (int t = 0; t < threads; t++)
        {
            final int saldo = t;
            Thread thread = new Thread (() ->
            {
                try
                {
                    Cliente cliente = new Cliente (HOST, servidor.getPorta (), 10000);

                    for (int i = 0; i < chamadas; i++)
                    {
                        // cada thread tem seus proprios numeros: resposta trocada entre conexoes seria detectada
                        RespostaDeMovimentacaoDeEstoque r = (RespostaDeMovimentacaoDeEstoque) cliente.chamar (entrada (saldo));
                        double custo = algoritmos.CalculadoraDeEstoque.arredondar ((saldo * 2.0 + 10 * 4.0) / (saldo + 10), 6);

                        RespostaDeValidacao v = (RespostaDeValidacao) cliente.chamar (new PedidoDeValidacaoDeTalhao ("Talhão " + saldo, "Soja", null, saldo + 1.0, null));

                        if (r.isOk () && r.getSaldoPosterior () == saldo + 10 && r.getCustoUnitario () == custo &&
                            v.isValido () && ((Double) v.getNormalizados ().get ("hectares")) == saldo + 1.0)
                            certas.incrementAndGet ();
                        else
                            erradas.incrementAndGet ();
                    }

                    cliente.fechar ();
                }
                catch (Throwable erro)
                {
                    erradas.incrementAndGet ();
                    System.err.println ("thread " + saldo + ": " + erro);
                }
            });

            lista.add (thread);
            thread.start ();
        }

        for (Thread thread : lista)
            thread.join ();

        verificar ("50 conexões simultâneas x 40 chamadas duplas: " + certas.get () + " respostas corretas, " + erradas.get () + " erradas",
                   certas.get () == threads * chamadas && erradas.get () == 0);
        verificar ("todas as conexões foram liberadas", aguardar ( () -> servidor.getConexoesAtivas () == 0));

        // uma unica conexao compartilhada por varias threads da API: o Cliente serializa as chamadas
        Cliente compartilhado = new Cliente (HOST, servidor.getPorta (), 10000);
        AtomicInteger corretas = new AtomicInteger ();
        List<Thread>  outras   = new ArrayList<Thread> ();

        for (int t = 0; t < 10; t++)
        {
            final int numero = t;
            Thread thread = new Thread (() ->
            {
                try
                {
                    for (int i = 0; i < 50; i++)
                    {
                        PedidoDeResumoDeTalhoes pedido = new PedidoDeResumoDeTalhoes (List.of (new TalhaoParaResumo (numero, "Ativo")));
                        RespostaDeResumoDeTalhoes r = (RespostaDeResumoDeTalhoes) compartilhado.chamar (pedido);

                        if (r.getIdDoPedido () == pedido.getId () && r.getTotalHectares () == numero && r.getQuantidadeAtivos () == 1)
                            corretas.incrementAndGet ();
                    }
                }
                catch (Throwable erro)
                {
                    System.err.println ("thread compartilhada " + numero + ": " + erro);
                }
            });

            outras.add (thread);
            thread.start ();
        }

        for (Thread thread : outras)
            thread.join ();

        verificar ("10 threads sobre uma só conexão: 500 respostas, cada uma para o seu pedido", corretas.get () == 500);
        compartilhado.fechar ();
    }

    // resposta atrasada de um pedido anterior (que estourou o tempo limite) nao pode ser confundida com a atual
    @Test
    @Order (5)
    void respostaAtrasada () throws Exception
    {
        ServerSocket falso = new ServerSocket (0, 1, java.net.InetAddress.getByName (HOST));
        falso.setSoTimeout (5000);

        Thread servidorFalso = new Thread (() ->
        {
            try
            {
                Socket aceito = falso.accept ();
                aceito.setSoTimeout (5000);

                Parceiro parceiro = new Parceiro (aceito);
                parceiro.receba (new ComunicadoDeBoasVindas (Protocolo.VERSAO));

                protocolo.Pedido pedido = (protocolo.Pedido) parceiro.envie ();
                parceiro.receba (new RespostaDePing (pedido.getId () - 1, "atrasada"));
                parceiro.receba (new RespostaDePing (pedido.getId (), "certa"));
                parceiro.envie (); // espera o PedidoParaSair
                parceiro.adeus ();
            }
            catch (Exception erro)
            {
                System.err.println ("servidor falso: " + erro);
            }
        });
        servidorFalso.start ();

        Cliente cliente = new Cliente (HOST, falso.getLocalPort (), 3000);
        RespostaDePing r = como (cliente.chamar (new PedidoDePing ()), RespostaDePing.class);
        verificar ("o Cliente descarta a resposta atrasada e devolve a do pedido atual", "certa".equals (r.getVersao ()));

        cliente.fechar ();
        servidorFalso.join ();
        falso.close ();
    }

    @Test
    @Order (6)
    void limites () throws Exception
    {
        Servidor pequeno = new Servidor (HOST, 0, 2, 5000);

        Cliente um = new Cliente (HOST, pequeno.getPorta (), 3000), dois = new Cliente (HOST, pequeno.getPorta (), 3000);

        try
        {
            new Cliente (HOST, pequeno.getPorta (), 3000);
            verificar ("terceira conexão deveria ser recusada", false);
        }
        catch (Exception erro)
        {
            verificar ("acima do limite de conexões o servidor recusa com SERVIDOR_LOTADO", erro.getMessage ().contains (RespostaDeErro.SERVIDOR_LOTADO));
        }

        verificar ("as conexões já aceitas seguem funcionando", como (um.chamar (new PedidoDePing ()), RespostaDePing.class) != null);

        dois.fechar ();
        verificar ("a vaga devolvida pode ser usada por outro cliente", aguardar ( () ->
        {
            try
            {
                new Cliente (HOST, pequeno.getPorta (), 3000).fechar ();
                return true;
            }
            catch (Exception erro)
            {
                return false;
            }
        }));

        um.fechar ();
        pequeno.desativar ();

        Servidor impaciente = new Servidor (HOST, 0, 10, 300);
        Cliente  ocioso     = new Cliente (HOST, impaciente.getPorta (), 3000);
        Thread.sleep (900);
        verificar ("conexão ociosa é fechada pelo servidor", falha (ocioso, new PedidoDePing ()));
        verificar ("a ociosa foi removida da lista de ativas", aguardar ( () -> impaciente.getConexoesAtivas () == 0));
        impaciente.desativar ();
    }

    @Test
    @Order (7)
    void desligamento () throws Exception
    {
        Servidor servidor = new Servidor (HOST, 0, 10, 60000);
        Parceiro conectado = ProtocoloTest.conectar (servidor.getPorta ());
        conectado.envie (); // boas-vindas
        verificar ("antes de desativar há uma conexão ativa", aguardar ( () -> servidor.getConexoesAtivas () == 1));

        servidor.desativar ();

        verificar ("desativar avisa o cliente conectado com ComunicadoDeDesligamento", conectado.envie () instanceof ComunicadoDeDesligamento);
        verificar ("e depois fecha a conexão", conectado.envie () == null);
        verificar ("não sobra conexão ativa", servidor.getConexoesAtivas () == 0);
        conectado.adeus ();

        verificar ("depois de desativado ninguém mais conecta", falhaAoConectar (servidor.getPorta ()));
    }

    private static boolean falhaAoConectar (int porta)
    {
        try
        {
            new Cliente (HOST, porta, 1000).fechar ();
            return false;
        }
        catch (Exception erro)
        {
            return true;
        }
    }
}
