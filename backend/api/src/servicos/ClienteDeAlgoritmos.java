/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 (ponte HTTP → ServerSocket)
   Cadastro: handlers/AuthHandler — Pedro Soler RA 25005592
   Talhão/resumo: handlers/TalhoesHandler — Miguel RA 25003110; Pedro Soler RA 25005592
   Estoque: handlers/EstoqueHandler — Pedro Soler RA 25005592; Tomas Michelon RA 25004211
   Relatório: servicos/RelatorioSafra — Miguel RA 25003110
   Senha: AuthHandler (reset) e PerfilHandler — Pedro Soler RA 25005592
*/
package servicos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import protocolo.Cliente;
import protocolo.Resposta;
import protocolo.RespostaDeErro;
import protocolo.mensagens.PedidoDeMovimentacaoDeEstoque;
import protocolo.mensagens.PedidoDeRelatorioDeSafra;
import protocolo.mensagens.PedidoDeResumoDeTalhoes;
import protocolo.mensagens.PedidoDeValidacaoDeCadastro;
import protocolo.mensagens.PedidoDeValidacaoDeSenha;
import protocolo.mensagens.PedidoDeValidacaoDeTalhao;
import protocolo.mensagens.RespostaDeMovimentacaoDeEstoque;
import protocolo.mensagens.RespostaDeRelatorioDeSafra;
import protocolo.mensagens.RespostaDeResumoDeTalhoes;
import protocolo.mensagens.RespostaDeValidacao;
import protocolo.mensagens.TalhaoParaResumo;

/*
    Ponte HTTP -> ServerSocket: a API delega validações e cálculos ao servidor de algoritmos (TCP, porta 3000).
    Variáveis: SERVIDOR_ALGORITMOS_HOST (127.0.0.1), SERVIDOR_ALGORITMOS_PORT (3000), SERVIDOR_ALGORITMOS_TIMEOUT_MS (5000).
*/
public class ClienteDeAlgoritmos
{
    public static final class ResultadoCadastro
    {
        public final http.Resposta respostaErro;
        public final Document      normalizados;
        public final String        password;

        private ResultadoCadastro (http.Resposta respostaErro, Document normalizados, String password)
        {
            this.respostaErro = respostaErro;
            this.normalizados = normalizados;
            this.password     = password;
        }
    }

    public static final class ResultadoTalhao
    {
        public final http.Resposta respostaErro;
        public final Document      campos;

        private ResultadoTalhao (http.Resposta respostaErro, Document campos)
        {
            this.respostaErro = respostaErro;
            this.campos       = campos;
        }
    }

    public static final class ResultadoMovimentacao
    {
        public final http.Resposta                 respostaErro;
        public final RespostaDeMovimentacaoDeEstoque calculo;

        private ResultadoMovimentacao (http.Resposta respostaErro, RespostaDeMovimentacaoDeEstoque calculo)
        {
            this.respostaErro = respostaErro;
            this.calculo      = calculo;
        }
    }

    public static final class ResultadoSenha
    {
        public final http.Resposta respostaErro;
        public final String        password;

        private ResultadoSenha (http.Resposta respostaErro, String password)
        {
            this.respostaErro = respostaErro;
            this.password     = password;
        }
    }

    private static final Object TRAVA = new Object ();
    private static Cliente        cliente;

    private static String variavel (String nome, String padrao)
    {
        String ret = System.getenv (nome);

        if (ret == null || ret.isBlank ())
            ret = padrao;

        return ret;
    }

    private static int variavelInt (String nome, int padrao)
    {
        String texto = System.getenv (nome);

        if (texto == null || texto.isBlank ())
            return padrao;

        try
        {
            return Integer.parseInt (texto.trim ());
        }
        catch (Exception erro)
        {
            return padrao;
        }
    }

    private static Cliente conectar () throws Exception
    {
        synchronized (ClienteDeAlgoritmos.TRAVA)
        {
            if (ClienteDeAlgoritmos.cliente != null)
                return ClienteDeAlgoritmos.cliente;

            String host = ClienteDeAlgoritmos.variavel ("SERVIDOR_ALGORITMOS_HOST", "127.0.0.1");
            int    porta = ClienteDeAlgoritmos.variavelInt ("SERVIDOR_ALGORITMOS_PORT", 3000);
            int    tempo = ClienteDeAlgoritmos.variavelInt ("SERVIDOR_ALGORITMOS_TIMEOUT_MS", 5000);

            ClienteDeAlgoritmos.cliente = new Cliente (host, porta, tempo);
            return ClienteDeAlgoritmos.cliente;
        }
    }

    public static void reiniciar ()
    {
        synchronized (ClienteDeAlgoritmos.TRAVA)
        {
            if (ClienteDeAlgoritmos.cliente != null)
            {
                try
                {
                    ClienteDeAlgoritmos.cliente.fechar ();
                }
                catch (Exception erro)
                {} // so tentando fechar

                ClienteDeAlgoritmos.cliente = null;
            }
        }
    }

    public static Resposta chamar (protocolo.Pedido pedido) throws Exception
    {
        try
        {
            return ClienteDeAlgoritmos.conectar ().chamar (pedido);
        }
        catch (Exception erro)
        {
            ClienteDeAlgoritmos.reiniciar ();
            throw erro;
        }
    }

    // --- Cadastro (AuthHandler) ---
    public static ResultadoCadastro validarCadastro (Document corpo) throws Exception
    {
        http.Resposta tipo = ClienteDeAlgoritmos.rejeitarTiposInvalidos (corpo);

        if (tipo != null)
            return new ResultadoCadastro (tipo, null, null);

        Object  terms  = corpo.get ("terms");
        boolean aceito = Boolean.TRUE.equals (terms) ||
                         "true".equalsIgnoreCase (String.valueOf (terms)) ||
                         "on".equals (String.valueOf (terms));
        String  senha  = ClienteDeAlgoritmos.texto (corpo, "password");
        String  confirm = ClienteDeAlgoritmos.confirmacao (corpo);

        PedidoDeValidacaoDeCadastro pedido = new PedidoDeValidacaoDeCadastro (
            ClienteDeAlgoritmos.texto (corpo, "name"),
            ClienteDeAlgoritmos.texto (corpo, "email"),
            ClienteDeAlgoritmos.texto (corpo, "phone"),
            senha,
            confirm,
            ClienteDeAlgoritmos.texto (corpo, "farm"),
            ClienteDeAlgoritmos.texto (corpo, "area"),
            aceito);

        return ClienteDeAlgoritmos.interpretarValidacao (corpo, ClienteDeAlgoritmos.chamar (pedido), senha, true);
    }

    // --- Senha (AuthHandler reset, PerfilHandler) — campoSenha: "password" ou "new" ---
    public static ResultadoSenha validarSenha (Document corpo, String campoSenha) throws Exception
    {
        String senha    = ClienteDeAlgoritmos.texto (corpo, campoSenha);
        String confirma = ClienteDeAlgoritmos.confirmacaoSenha (corpo);

        PedidoDeValidacaoDeSenha pedido = new PedidoDeValidacaoDeSenha (senha, confirma);
        ResultadoCadastro adaptado = ClienteDeAlgoritmos.interpretarValidacaoSenha (corpo, campoSenha,
            ClienteDeAlgoritmos.chamar (pedido), senha);

        return new ResultadoSenha (adaptado.respostaErro, adaptado.password);
    }

    // --- Talhão (TalhoesHandler) ---
    public static ResultadoTalhao validarTalhao (Document corpo) throws Exception
    {
        Object hectares = corpo.get ("hectares");
        Double ha = hectares instanceof Number ? Double.valueOf (((Number) hectares).doubleValue ()) : null;

        Object valorSafra = corpo.get ("safra");
        String safra = valorSafra == null ? null : (valorSafra instanceof String ? ((String) valorSafra).trim () : null);

        Object valorStatus = corpo.get ("status");
        String status = valorStatus == null || "".equals (valorStatus) ? null
                        : (valorStatus instanceof String ? (String) valorStatus : null);

        PedidoDeValidacaoDeTalhao pedido = new PedidoDeValidacaoDeTalhao (
            ClienteDeAlgoritmos.texto (corpo, "nome"),
            ClienteDeAlgoritmos.texto (corpo, "cultura"),
            safra,
            ha,
            status);

        Resposta resposta = ClienteDeAlgoritmos.chamar (pedido);
        ResultadoCadastro adaptado = ClienteDeAlgoritmos.interpretarValidacao (corpo, resposta, null, false);

        if (adaptado.respostaErro != null)
            return new ResultadoTalhao (adaptado.respostaErro, null);

        return new ResultadoTalhao (null, adaptado.normalizados);
    }

    public static http.Resposta resumoTalhoes (List<Document> talhoes) throws Exception
    {
        ArrayList<TalhaoParaResumo> lista = new ArrayList<TalhaoParaResumo> ();

        for (Document talhao : talhoes)
            lista.add (new TalhaoParaResumo (talhao.getDouble ("hectares"), talhao.getString ("status")));

        Resposta resposta = ClienteDeAlgoritmos.chamar (new PedidoDeResumoDeTalhoes (lista));

        if (resposta instanceof RespostaDeErro erro)
            return http.Resposta.json (503, new Document ("error", "Servidor de algoritmos indisponível")
                                           .append ("codigo", erro.getCodigo ()));

        if (!(resposta instanceof RespostaDeResumoDeTalhoes resumo))
            return http.Resposta.json (503, new Document ("error", "Resposta inesperada do servidor de algoritmos"));

        return http.Resposta.json (200, new Document ("totalHectares",    resumo.getTotalHectares ())
                                       .append ("quantidadeTotal",  resumo.getQuantidadeTotal ())
                                       .append ("quantidadeAtivos", resumo.getQuantidadeAtivos ()));
    }

    // --- Estoque (EstoqueHandler) ---
    public static ResultadoMovimentacao calcularMovimentacao (double saldoAtual, double custoUnitarioAtual,
                                                              String tipo, Double quantidade, Double custoUnitario,
                                                              String observacao) throws Exception
    {
        PedidoDeMovimentacaoDeEstoque pedido = new PedidoDeMovimentacaoDeEstoque (
            saldoAtual, custoUnitarioAtual, tipo, quantidade, custoUnitario, observacao);

        Resposta resposta = ClienteDeAlgoritmos.chamar (pedido);

        if (resposta instanceof RespostaDeErro erro)
            return new ResultadoMovimentacao (
                http.Resposta.json (503, new Document ("error", "Servidor de algoritmos indisponível")
                                               .append ("codigo", erro.getCodigo ())),
                null);

        if (!(resposta instanceof RespostaDeMovimentacaoDeEstoque calculo))
            return new ResultadoMovimentacao (
                http.Resposta.json (503, new Document ("error", "Resposta inesperada do servidor de algoritmos")),
                null);

        if (RespostaDeMovimentacaoDeEstoque.INVALIDO.equals (calculo.getSituacao ()))
        {
            Document campos = new Document ();
            for (Map.Entry<String, String> par : calculo.getErros ().entrySet ())
                campos.append (par.getKey (), par.getValue ());

            return new ResultadoMovimentacao (
                http.Resposta.json (400, new Document ("error", "Dados inválidos.").append ("fields", campos)),
                null);
        }

        if (RespostaDeMovimentacaoDeEstoque.SALDO_INSUFICIENTE.equals (calculo.getSituacao ()))
            return new ResultadoMovimentacao (
                http.Resposta.json (409, new Document ("error", "Saldo insuficiente.")
                                           .append ("fields", new Document ("quantidade", "A saída supera o saldo disponível."))),
                null);

        return new ResultadoMovimentacao (null, calculo);
    }

    // --- Relatório de safra (RelatorioSafra) ---
    public static RespostaDeRelatorioDeSafra consolidarRelatorio (PedidoDeRelatorioDeSafra pedido) throws Exception
    {
        Resposta resposta = ClienteDeAlgoritmos.chamar (pedido);

        if (resposta instanceof RespostaDeErro erro)
            throw new Exception ("Servidor de algoritmos: " + erro.getCodigo ());

        if (!(resposta instanceof RespostaDeRelatorioDeSafra relatorio))
            throw new Exception ("Resposta inesperada do servidor de algoritmos");

        return relatorio;
    }

    private static ResultadoCadastro interpretarValidacao (Document corpo, Resposta resposta, String senha,
                                                             boolean ajustarConfirm) throws Exception
    {
        if (resposta instanceof RespostaDeErro erro)
            return new ResultadoCadastro (
                http.Resposta.json (503, new Document ("error", "Servidor de algoritmos indisponível")
                                           .append ("codigo", erro.getCodigo ())),
                null,
                null);

        if (!(resposta instanceof RespostaDeValidacao validacao))
            return new ResultadoCadastro (
                http.Resposta.json (503, new Document ("error", "Resposta inesperada do servidor de algoritmos")),
                null,
                null);

        if (!validacao.isValido ())
        {
            Document campos = new Document ();

            for (Map.Entry<String, String> par : validacao.getErros ().entrySet ())
                campos.append (par.getKey (), par.getValue ());

            if (ajustarConfirm)
                ClienteDeAlgoritmos.ajustarCamposCadastro (corpo, campos);

            return new ResultadoCadastro (
                http.Resposta.json (400, new Document ("error", "Dados inválidos.").append ("fields", campos)),
                null,
                null);
        }

        Document normalizados = new Document ();

        for (Map.Entry<String, Object> par : validacao.getNormalizados ().entrySet ())
            normalizados.append (par.getKey (), par.getValue ());

        return new ResultadoCadastro (null, normalizados, senha);
    }

    private static ResultadoCadastro interpretarValidacaoSenha (Document corpo, String campoSenha, Resposta resposta,
                                                                String senha) throws Exception
    {
        ResultadoCadastro base = ClienteDeAlgoritmos.interpretarValidacao (corpo, resposta, senha, false);

        if (base.respostaErro != null && base.respostaErro.getCorpo () != null)
        {
            Document json = Document.parse (new String (base.respostaErro.getCorpo (), java.nio.charset.StandardCharsets.UTF_8));
            Document campos = json.get ("fields", Document.class);

            if (campos != null)
            {
                ClienteDeAlgoritmos.ajustarCamposSenha (corpo, campoSenha, campos);
                return new ResultadoCadastro (
                    http.Resposta.json (400, new Document ("error", "Dados inválidos.").append ("fields", campos)),
                    null,
                    null);
            }
        }

        return base;
    }

    private static String texto (Document corpo, String campo)
    {
        Object valor = corpo.get (campo);

        if (valor == null)
            return null;

        if (!(valor instanceof String))
            return null;

        return ((String) valor);
    }

    private static String confirmacao (Document corpo)
    {
        return ClienteDeAlgoritmos.confirmacaoSenha (corpo);
    }

    private static String confirmacaoSenha (Document corpo)
    {
        if (corpo.containsKey ("password-confirm"))
            return ClienteDeAlgoritmos.texto (corpo, "password-confirm");

        return ClienteDeAlgoritmos.texto (corpo, "confirm");
    }

    private static http.Resposta rejeitarTiposInvalidos (Document corpo)
    {
        Document campos = new Document ();
        String[] textos = { "name", "email", "phone", "password", "password-confirm", "confirm", "farm", "area" };

        for (String campo : textos)
        {
            Object valor = corpo.get (campo);

            if (valor != null && !(valor instanceof String))
                campos.append (campo, "Valor inválido.");
        }

        if (campos.isEmpty ())
            return null;

        return http.Resposta.json (400, new Document ("error", "Dados inválidos.").append ("fields", campos));
    }

    private static void ajustarCamposCadastro (Document corpo, Document campos)
    {
        if (!corpo.containsKey ("password-confirm") && campos.containsKey ("password-confirm"))
        {
            campos.append ("confirm", campos.getString ("password-confirm"));
            campos.remove ("password-confirm");
        }
    }

    // Perfil usa "new"/"confirm"; reset usa "password"/"password-confirm"
    private static void ajustarCamposSenha (Document corpo, String campoSenha, Document campos)
    {
        if ("new".equals (campoSenha))
        {
            if (campos.containsKey ("password"))
            {
                campos.append ("new", campos.getString ("password"));
                campos.remove ("password");
            }

            if (!corpo.containsKey ("password-confirm") && campos.containsKey ("password-confirm"))
            {
                campos.append ("confirm", campos.getString ("password-confirm"));
                campos.remove ("password-confirm");
            }
        }
    }
}
