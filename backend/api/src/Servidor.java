/* Miguel — RA: 25003110 */
/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import dados.infra.ConexaoMongo;
import dados.operacao.RepositorioAtividades;
import dados.operacao.RepositorioAtividadesMemoria;
import dados.operacao.RepositorioAtividadesMongo;
import dados.cultivo.RepositorioColheitas;
import dados.cultivo.RepositorioCulturas;
import dados.cultivo.RepositorioCulturasMemoria;
import dados.cultivo.RepositorioCulturasMongo;
import dados.propriedade.RepositorioEquipe;
import dados.propriedade.RepositorioEquipeMemoria;
import dados.propriedade.RepositorioEquipeMongo;
import dados.cultivo.RepositorioColheitasMemoria;
import dados.cultivo.RepositorioColheitasMongo;
import dados.operacao.RepositorioOcorrencias;
import dados.operacao.RepositorioOcorrenciasMemoria;
import dados.operacao.RepositorioOcorrenciasMongo;
import dados.financeiro.RepositorioCustos;
import dados.financeiro.RepositorioCustosMemoria;
import dados.financeiro.RepositorioCustosMongo;
import dados.financeiro.RepositorioOrcamentos;
import dados.financeiro.RepositorioOrcamentosMemoria;
import dados.financeiro.RepositorioOrcamentosMongo;
import dados.cultivo.RepositorioSafras;
import dados.cultivo.RepositorioSafrasMemoria;
import dados.cultivo.RepositorioSafrasMongo;
import dados.cultivo.RepositorioTalhoes;
import dados.cultivo.RepositorioTalhoesMemoria;
import dados.cultivo.RepositorioTalhoesMongo;
import dados.estoque.RepositorioEstoque;
import dados.estoque.RepositorioEstoqueMemoria;
import dados.estoque.RepositorioEstoqueMongo;
import dados.manutencao.RepositorioEquipamentos;
import dados.manutencao.RepositorioEquipamentosMemoria;
import dados.manutencao.RepositorioEquipamentosMongo;
import dados.manutencao.RepositorioIntervencoes;
import dados.manutencao.RepositorioIntervencoesMemoria;
import dados.manutencao.RepositorioIntervencoesMongo;
import dados.manutencao.RepositorioPreventivas;
import dados.manutencao.RepositorioPreventivasMemoria;
import dados.manutencao.RepositorioPreventivasMongo;
import dados.conta.RepositorioUsuarios;
import dados.conta.RepositorioUsuariosMemoria;
import dados.conta.RepositorioUsuariosMongo;
import handlers.conta.AuthHandler;
import handlers.cultivo.ColheitasHandler;
import handlers.operacao.AtividadesHandler;
import handlers.operacao.CalendarioHandler;
import handlers.cultivo.CulturasHandler;
import handlers.propriedade.EquipeHandler;
import handlers.financeiro.CustosHandler;
import handlers.conta.EstaticosHandler;
import handlers.estoque.EstoqueHandler;
import handlers.manutencao.ManutencaoHandler;
import handlers.financeiro.OrcamentosHandler;
import handlers.propriedade.PropriedadesHandler;
import handlers.conta.PerfilHandler;
import handlers.cultivo.SafrasHandler;
import handlers.relatorios.RelatorioSafraHandler;
import handlers.relatorios.RelatorioPropriedadeHandler;
import handlers.estoque.RelatorioEstoqueHandler;
import handlers.relatorios.RelatorioAtividadesHandler;
import handlers.conta.SaudeHandler;
import handlers.cultivo.TalhoesHandler;
import handlers.cultivo.GeorreferenciamentoHandler;
import handlers.operacao.OcorrenciasHandler;
import http.Roteador;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import seguranca.Sessoes;
import servicos.financeiro.CustosDeConsumo;
import servicos.integracao.EspelhoHistoricoTalhao;
import servicos.financeiro.CustosDeManutencao;
import servicos.conta.ServicoEmail;

/*
    Servidor HTTP do AgroGestao feito na mao sobre ServerSocket.
    Uso: java -cp "bin;lib/*" Servidor [porta]

    Variaveis de ambiente (todas com padrao):
    SERVIDOR_HOST  127.0.0.1
    SERVIDOR_PORT  8080        (o argumento da linha de comando tem prioridade)
    MONGODB_URI    obrigatoria (ou use o argumento --memoria, sem banco)
    SERVIDOR_RAIZ  ../../frontend       (pasta do front)

    Digite "desativar" no terminal para encerrar.
*/
public class Servidor
{
    public static final int MAXIMO_DE_CONEXOES = 100;

    private final ServerSocket        pedido;
    private final AceitadoraDeConexao aceitadora;
    private final ConexaoMongo        conexao; // null no modo memoria

    public Servidor (String host, int porta, Roteador roteador, ConexaoMongo conexao, int maximoDeConexoes) throws Exception
    {
        if (host == null)
            throw new Exception ("Host ausente");

        if (roteador == null)
            throw new Exception ("Roteador ausente");

        this.pedido     = new ServerSocket (porta, 50, InetAddress.getByName (host));
        this.aceitadora = new AceitadoraDeConexao (this.pedido, roteador, maximoDeConexoes);
        this.conexao    = conexao;

        this.aceitadora.start ();
    }

    public int getPorta ()
    {
        return this.pedido.getLocalPort ();
    }

    // fecha o ServerSocket, espera as conexoes em andamento e fecha o Mongo
    public void desativar () throws Exception
    {
        this.pedido.close ();
        this.aceitadora.join ();
        this.aceitadora.aguardarAtivas ();

        if (this.conexao != null)
            this.conexao.fechar ();
    }

    // conexao pode ser null (modo memoria / testes)
    public static Roteador montarRoteador (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, RepositorioEstoque estoque,
                                           RepositorioSafras safras, RepositorioCustos custos,
                                           ConexaoMongo conexao, Path raiz) throws Exception
    {
        RepositorioAtividades atividades = conexao == null
            ? new RepositorioAtividadesMemoria ()
            : new RepositorioAtividadesMongo (conexao);
        return Servidor.montarRoteador (usuarios, talhoes, atividades, estoque, safras, custos, conexao, raiz);
    }

    // A sobrecarga permite que a execução principal reutilize o mesmo repositório
    // de atividades durante toda a vida do servidor.
    public static Roteador montarRoteador (RepositorioUsuarios usuarios, RepositorioTalhoes talhoes, RepositorioAtividades atividades,
                                           RepositorioEstoque estoque, RepositorioSafras safras, RepositorioCustos custos,
                                           ConexaoMongo conexao, Path raiz) throws Exception
    {
        RepositorioOcorrencias ocorrencias = conexao == null ? new RepositorioOcorrenciasMemoria () : new RepositorioOcorrenciasMongo (conexao);
        Sessoes  sessoes  = new Sessoes ();
        Roteador roteador = new Roteador (sessoes);

        new SaudeHandler   (conexao).registrar           (roteador);
        RepositorioEquipe equipe = conexao == null
            ? new RepositorioEquipeMemoria ()
            : new RepositorioEquipeMongo (conexao.getBanco ());
        new AuthHandler    (usuarios, sessoes, ServicoEmail.doAmbiente (), equipe).registrar (roteador);
        new PerfilHandler (usuarios, sessoes, ServicoEmail.doAmbiente ()).registrar (roteador);
        RepositorioCulturas culturas = conexao == null
            ? new RepositorioCulturasMemoria ()
            : new RepositorioCulturasMongo (conexao.getBanco ());

        PropriedadesHandler propriedades = new PropriedadesHandler (usuarios, talhoes, custos, sessoes, ocorrencias);
        propriedades.setEquipe (equipe);
        propriedades.setEstoque (estoque);
        propriedades.setAtividades (atividades);
        propriedades.setSafras (safras);
        propriedades.registrar (roteador);
        new CulturasHandler (culturas, talhoes, safras).registrar (roteador);
        ServicoEmail servicoEmailEquipe = ServicoEmail.doAmbiente ();
        new EquipeHandler (equipe, usuarios, servicoEmailEquipe).registrar (roteador);
        TalhoesHandler handlerTalhoes = new TalhoesHandler (talhoes, atividades, culturas, sessoes);
        handlerTalhoes.registrar (roteador);
        EspelhoHistoricoTalhao espelhoHistorico = new EspelhoHistoricoTalhao (talhoes);
        AtividadesHandler handlerAtividades = new AtividadesHandler (atividades, talhoes, safras, sessoes);
        handlerAtividades.setEspelhoHistorico (espelhoHistorico);
        handlerAtividades.registrar (roteador);
        CustosDeConsumo consumo = new CustosDeConsumo (custos, estoque, talhoes, safras);
        EstoqueHandler handlerEstoque = new EstoqueHandler (estoque, sessoes);
        handlerEstoque.setConsumo (consumo);
        handlerEstoque.setEspelhoHistorico (espelhoHistorico);
        handlerEstoque.registrar (roteador);
        OcorrenciasHandler handlerOcorrencias = new OcorrenciasHandler (ocorrencias, talhoes, sessoes);
        handlerOcorrencias.setEspelhoHistorico (espelhoHistorico);
        handlerOcorrencias.registrar (roteador);
        new GeorreferenciamentoHandler (talhoes, ocorrencias, sessoes).registrar (roteador);
        SafrasHandler handlerSafras = new SafrasHandler (safras, talhoes, custos, atividades, culturas, sessoes);
        handlerSafras.registrar (roteador);
        RepositorioColheitas colheitas = conexao == null ? new RepositorioColheitasMemoria () : new RepositorioColheitasMongo (conexao.getBanco ());
        handlerSafras.setColheitas (colheitas);
        new ColheitasHandler (colheitas, safras, talhoes, sessoes).registrar (roteador);
        CustosHandler handlerCustos = new CustosHandler (custos, safras, talhoes, sessoes);
        handlerCustos.setConsumo (consumo);
        handlerCustos.registrar (roteador);
        RepositorioOrcamentos orcamentos = conexao == null ? new RepositorioOrcamentosMemoria () : new RepositorioOrcamentosMongo (conexao.getBanco ());
        handlerSafras.setOrcamentos (orcamentos);
        new OrcamentosHandler (orcamentos, safras, talhoes, custos, sessoes).registrar (roteador);
        RelatorioSafraHandler handlerRelatorioSafra = new RelatorioSafraHandler (usuarios, safras, talhoes, atividades, ocorrencias, custos, sessoes);
        handlerRelatorioSafra.setColheitas (colheitas);
        handlerRelatorioSafra.registrar (roteador);
        new RelatorioPropriedadeHandler (usuarios, talhoes, safras, estoque, atividades, ocorrencias, custos, sessoes).registrar (roteador);
        new RelatorioEstoqueHandler (usuarios, estoque, sessoes).registrar (roteador);
        new RelatorioAtividadesHandler (usuarios, atividades, sessoes).registrar (roteador);
        new CalendarioHandler (atividades).registrar (roteador);
        RepositorioEquipamentos equipamentos = conexao == null ? new RepositorioEquipamentosMemoria () : new RepositorioEquipamentosMongo (conexao.getBanco ());
        RepositorioPreventivas  preventivas  = conexao == null ? new RepositorioPreventivasMemoria ()  : new RepositorioPreventivasMongo (conexao.getBanco ());
        RepositorioIntervencoes intervencoes = conexao == null ? new RepositorioIntervencoesMemoria () : new RepositorioIntervencoesMongo (conexao.getBanco ());
        propriedades.setEquipamentos (equipamentos);
        new ManutencaoHandler (equipamentos, preventivas, intervencoes, talhoes, safras, custos,
                               new CustosDeManutencao (custos), sessoes).registrar (roteador);

        roteador.setEstaticos (new EstaticosHandler (raiz)::tratar);

        return roteador;
    }

    private static String variavel (String nome, String padrao)
    {
        String ret = System.getenv (nome);

        if (ret == null || ret.isBlank ())
            ret = padrao;

        return ret;
    }

    public static int lerPorta (String texto) throws Exception
    {
        int ret;

        try
        {
            ret = Integer.parseInt (texto.trim ());
        }
        catch (Exception erro)
        {
            throw new Exception ("Porta invalida: " + texto);
        }

        if (ret < 1 || ret > 65535)
            throw new Exception ("Porta invalida: " + texto);

        return ret;
    }

    public static void main (String[] args)
    {
        Servidor servidor;

        try
        {
            boolean memoria = false;
            String  porta   = Servidor.variavel ("SERVIDOR_PORT", "8080");

            for (String arg : args)
            {
                if (arg.equals ("--memoria"))
                    memoria = true;
                else
                    porta = arg;
            }

            String host = Servidor.variavel ("SERVIDOR_HOST", "127.0.0.1");
            Path   raiz = Path.of (Servidor.variavel ("SERVIDOR_RAIZ", "../../frontend"));

            ConexaoMongo        conexao  = null;
            RepositorioUsuarios usuarios = new RepositorioUsuariosMemoria ();
            RepositorioTalhoes  talhoes  = new RepositorioTalhoesMemoria  ();
            RepositorioAtividades atividades = new RepositorioAtividadesMemoria ();
            RepositorioEstoque  estoque  = new RepositorioEstoqueMemoria  (atividades);
            RepositorioSafras   safras   = new RepositorioSafrasMemoria   ();
            RepositorioCustos   custos   = new RepositorioCustosMemoria   ();

            if (!memoria)
            {
                conexao = new ConexaoMongo (System.getenv ("MONGODB_URI"));

                // criar os indices ja exige o banco no ar; se nao estiver, para aqui
                try
                {
                    usuarios = new RepositorioUsuariosMongo (conexao);
                    talhoes  = new RepositorioTalhoesMongo  (conexao.getBanco ());
                    atividades = new RepositorioAtividadesMongo (conexao);
                    estoque  = new RepositorioEstoqueMongo  (conexao);
                    safras   = new RepositorioSafrasMongo   (conexao.getBanco ());
                    custos   = new RepositorioCustosMongo   (conexao.getBanco ());
                }
                catch (Exception erro)
                {
                    conexao.fechar ();
                    throw new Exception ("MongoDB indisponivel (" + erro.getClass ().getSimpleName () + ")");
                }
            }

            // A sobrecarga abaixo usa o repositório de atividades da execução.
            Roteador roteador = Servidor.montarRoteador (usuarios, talhoes, atividades, estoque, safras, custos, conexao, raiz);
            servidor = new Servidor (host, Servidor.lerPorta (porta), roteador, conexao, Servidor.MAXIMO_DE_CONEXOES);

            ServicoEmail.logModo ();
            System.out.println ("Servidor ouvindo em http://" + host + ":" + servidor.getPorta () +
                                (memoria ? " (modo memoria, sem banco)" : ""));
            System.out.println ("Digite \"desativar\" para encerrar.");
        }
        catch (Exception erro)
        {
            System.err.println ("Nao foi possivel iniciar: " + erro.getMessage ());
            System.exit (1);
            return;
        }

        try
        {
            BufferedReader teclado = new BufferedReader (new InputStreamReader (System.in));
            for (;;)
            {
                String comando = teclado.readLine ();

                // fim da entrada (ex.: rodando em segundo plano) -> continua no ar
                if (comando == null)
                {
                    Thread.currentThread ().join ();
                    return;
                }

                if (comando.trim ().equalsIgnoreCase ("desativar"))
                    break;

                System.out.println ("Comando invalido. Use \"desativar\".");
            }

            System.out.println ("Desativando...");
            servidor.desativar ();
            System.out.println ("Servidor desativado.");
        }
        catch (Exception erro)
        {
            System.err.println ("Erro ao desativar: " + erro.getMessage ());
            System.exit (1);
        }
    }
}
