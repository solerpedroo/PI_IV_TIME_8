/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package servicos.conta;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
    Envia os códigos de autenticação e o convite de equipe por SMTP.

    Quem chama: AuthHandler (confirmação de cadastro, reenvio e redefinição
    de senha), PerfilHandler (troca de e-mail) e o fluxo de equipe
    (enviarConviteEquipe). Não é um handler HTTP: não devolve 401, 400
    nem 409. Falha de rede ou de credencial sobe como Exception, e cada
    chamador decide se avisa o cliente ou esconde o erro.

    O que persiste: nada no banco. Com SMTP_* completo, abre TLS na porta
    465 (ou na SMTP_PORT) e fala o diálogo EHLO, AUTH LOGIN, MAIL, RCPT
    e DATA. Sem nenhuma variável SMTP_*, o modo simulado só imprime o
    código no console, para cadastro e recuperação funcionarem sem senha
    de aplicativo.
*/
public final class ServicoEmail
{
    private final String host;
    private final int    porta;
    private final String usuario;
    private final String senha;
    private final String remetente;

    /*
        Construtor privado. Só doAmbiente instancia, já com tudo preenchido
        ou com host nulo no modo simulado. Não há objeto "meio configurado".
    */
    private ServicoEmail (String host, int porta, String usuario, String senha, String remetente)
    {
        this.host      = host;
        this.porta     = porta;
        this.usuario   = usuario;
        this.senha     = senha;
        this.remetente = remetente;
    }

    /*
        Lê uma variável de ambiente já sem espaços nas pontas e sem aspas
        que o .env às vezes deixa em volta do valor. SMTP_PASSWORD ainda
        perde espaços internos: senha de app do Gmail é copiada em grupos.
    */
    private static String ambiente (String nome)
    {
        String valor = System.getenv (nome);
        if (valor == null)
            return "";

        valor = valor.trim ();
        if (valor.length () >= 2 &&
            ((valor.startsWith ("'") && valor.endsWith ("'")) || (valor.startsWith ("\"") && valor.endsWith ("\""))))
            valor = valor.substring (1, valor.length () - 1).trim ();

        if ("SMTP_PASSWORD".equals (nome))
            valor = valor.replace (" ", "");

        return valor;
    }

    /*
        Monta o serviço a partir do ambiente. Não é rota.
        Tudo vazio: modo simulado (host nulo), sem exceção.
        Alguma variável preenchida e outra faltando: exceção, para não
        subir achando que o e-mail sai quando o AUTH vai falhar.
        Porta vazia vira 465. Fora de 1..65535, exceção.
    */
    public static ServicoEmail doAmbiente () throws Exception
    {
        String host      = ServicoEmail.ambiente ("SMTP_HOST");
        String usuario   = ServicoEmail.ambiente ("SMTP_USERNAME");
        String senha     = ServicoEmail.ambiente ("SMTP_PASSWORD");
        String remetente = ServicoEmail.ambiente ("SMTP_FROM");
        String porta     = ServicoEmail.ambiente ("SMTP_PORT");

        if (host.isEmpty () && usuario.isEmpty () && senha.isEmpty () && remetente.isEmpty () && porta.isEmpty ())
            return new ServicoEmail (null, 0, null, null, null);

        if (host.isEmpty () || usuario.isEmpty () || senha.isEmpty () || remetente.isEmpty ())
            throw new Exception ("Configure SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD e SMTP_FROM juntos");

        int numeroPorta = porta.isEmpty () ? 465 : Integer.parseInt (porta);
        if (numeroPorta < 1 || numeroPorta > 65535)
            throw new Exception ("SMTP_PORT invalida");

        return new ServicoEmail (host, numeroPorta, usuario, senha, remetente);
    }

    /*
        true quando doAmbiente não achou SMTP_*. Os envios então escrevem
        no console em vez de abrir socket.
    */
    public boolean simulado ()
    {
        return this.host == null;
    }

    /*
        Avisa no console, na subida do servidor, se o código vai para o
        terminal ou para o SMTP. Configuração inválida não derruba o
        processo: só registra o motivo no erro padrão.
    */
    public static void logModo ()
    {
        try
        {
            ServicoEmail email = ServicoEmail.doAmbiente ();
            if (email.simulado ())
                System.out.println ("E-mail: modo simulado (defina SMTP_* no .env para envio real; codigos no console).");
            else
                System.out.println ("E-mail: SMTP " + email.host + ":" + email.porta + " remetente " + email.remetente);
        }
        catch (Exception erro)
        {
            System.err.println ("E-mail: configuracao invalida: " + erro.getMessage ());
        }
    }

    /*
        Recusa endereço nulo ou com quebra de linha. CR/LF no destinatário
        permitiria injetar cabeçalho SMTP (outro To, outro Subject).
    */
    private static void conferirEndereco (String endereco) throws Exception
    {
        if (endereco == null || endereco.contains ("\r") || endereco.contains ("\n"))
            throw new Exception ("Endereco de e-mail invalido");
    }

    /*
        Escreve um comando SMTP e termina com CRLF, como o protocolo exige.
        O flush é imediato: o servidor só responde depois de receber a linha.
    */
    private static void comando (BufferedWriter saida, String texto) throws Exception
    {
        saida.write (texto);
        saida.write ("\r\n");
        saida.flush ();
    }

    /*
        Lê a resposta SMTP até a última linha. Resposta de várias linhas
        usa hífen depois do código (250-); a última troca o hífen por espaço.
        Se o código não for o esperado, lança exceção. 535 ou badcredentials
        ganham a dica da senha de app, porque é a falha mais comum no Gmail.
    */
    private static void esperar (BufferedReader entrada, int esperado) throws Exception
    {
        String linha;
        do
        {
            linha = entrada.readLine ();
            if (linha == null || linha.length () < 3)
                throw new Exception ("Servidor SMTP encerrou a conexao");
        }
        while (linha.length () > 3 && linha.charAt (3) == '-');

        if (!linha.startsWith ("" + esperado))
        {
            if (linha.contains ("535") || linha.toLowerCase ().contains ("badcredentials"))
                throw new Exception ("SMTP recusou a operacao: " + linha
                    + " — confira senha de app do Gmail (16 caracteres, sem espacos) em SMTP_PASSWORD.");
            throw new Exception ("SMTP recusou a operacao: " + linha);
        }
    }

    /*
        AUTH LOGIN pede usuário e senha em Base64, não em texto puro.
    */
    private static String base64 (String texto)
    {
        return Base64.getEncoder ().encodeToString (texto.getBytes (StandardCharsets.UTF_8));
    }

    /*
        Assunto e nome do remetente com acento não cabem em ASCII puro.
        Se houver caractere acima de 127, embrulha no formato MIME
        =?UTF-8?B?...?= para o servidor não quebrar o cabeçalho.
    */
    private static String codificarUtf8Mime (String texto)
    {
        boolean soAscii = true;
        for (int i = 0; i < texto.length () && soAscii; i++)
            soAscii = texto.charAt (i) < 128;

        if (soAscii)
            return texto;

        return "=?UTF-8?B?" + ServicoEmail.base64 (texto) + "?=";
    }

    /*
        Escapa &, <, > e aspas antes de colocar texto do usuário no HTML.
        Sem isso, um nome de fazenda poderia alterar a marcação do convite.
    */
    private static String escapeHtml (String texto)
    {
        if (texto == null)
            return "";

        return texto.replace ("&", "&amp;")
                    .replace ("<", "&lt;")
                    .replace (">", "&gt;")
                    .replace ("\"", "&quot;");
    }

    /*
        Pasta dos modelos convite-equipe.txt e convite-equipe.html.
        SMTP_RECURSOS_EMAIL, se existir, substitui recursos/email sob o
        diretório de trabalho.
    */
    private static Path pastaRecursosEmail ()
    {
        String custom = System.getenv ("SMTP_RECURSOS_EMAIL");
        if (custom != null && !custom.isBlank ())
            return Path.of (custom);

        return Path.of (System.getProperty ("user.dir"), "recursos", "email");
    }

    /*
        Lê o modelo inteiro em UTF-8. Arquivo ausente vira exceção com o
        caminho, para o convite não sair com corpo vazio.
    */
    private static String carregarModelo (String nomeArquivo) throws Exception
    {
        Path arquivo = ServicoEmail.pastaRecursosEmail ().resolve (nomeArquivo);
        if (!Files.isRegularFile (arquivo))
            throw new Exception ("Modelo de e-mail ausente: " + arquivo);

        return Files.readString (arquivo, StandardCharsets.UTF_8);
    }

    /*
        Troca cada {{CHAVE}} pelo valor. No HTML o valor passa por
        escapeHtml; no texto puro, não, para o link do convite permanecer legível.
    */
    private static String aplicarModelo (String modelo, Map<String, String> valores, boolean html)
    {
        String saida = modelo;
        for (Map.Entry<String, String> entrada : valores.entrySet ())
        {
            String chave = "{{" + entrada.getKey () + "}}";
            String valor = entrada.getValue () == null ? "" : entrada.getValue ();
            if (html)
                valor = ServicoEmail.escapeHtml (valor);
            saida = saida.replace (chave, valor);
        }
        return saida;
    }

    /*
        Envia um e-mail com parte texto e parte HTML.
        Endereço com quebra de linha: exceção, antes de qualquer rede.
        Modo simulado: imprime assunto e texto no console e retorna.
        Modo real: completa o assunto com "— AgroGestão" se ainda não tiver
        e entrega o MIME a enviarSmtp.
    */
    public void enviarMultipart (String destinatario, String assunto, String textoPlano, String html) throws Exception
    {
        ServicoEmail.conferirEndereco (destinatario);

        if (this.host == null)
        {
            System.out.println ("[e-mail simulado] para " + destinatario + " | " + assunto);
            System.out.println (textoPlano);
            return;
        }

        String assuntoCompleto = assunto.endsWith ("— AgroGestão") ? assunto : assunto + " — AgroGestão";
        String boundary        = "----=_AgroGestao_" + System.currentTimeMillis ();
        String mime            = ServicoEmail.montarMime (boundary, textoPlano, html);
        this.enviarSmtp (destinatario, assuntoCompleto, boundary, mime);
    }

    /*
        Corpo texto do código de 6 dígitos. Quem não pede HTML ainda lê
        a mesma instrução.
    */
    private static String corpoTexto (String titulo, String codigo)
    {
        return titulo + "\n\n"
            + "Seu código: " + codigo + "\n\n"
            + "Digite esse código de 6 dígitos no AgroGestão para concluir a operação.\n"
            + "Se você não solicitou esta ação, ignore esta mensagem.";
    }

    /*
        Mesmo código em HTML, com o visual do AgroGestão. Título, código
        e instrução são escapados antes de entrar na marcação.
    */
    private static String corpoHtml (String titulo, String codigo, String instrucao)
    {
        String tituloEsc = ServicoEmail.escapeHtml (titulo);
        String codigoEsc = ServicoEmail.escapeHtml (codigo);
        String instEsc     = ServicoEmail.escapeHtml (instrucao);

        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + tituloEsc + "</title></head>"
            + "<body style=\"margin:0;padding:0;background:#f1f5f2;font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;color:#1a2e24;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f1f5f2;padding:32px 16px;\">"
            + "<tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"max-width:480px;background:#ffffff;border-radius:12px;border:1px solid #d8e5dc;overflow:hidden;\">"
            + "<tr><td style=\"background:#1a4d3a;padding:20px 24px;\">"
            + "<p style=\"margin:0;font-size:18px;font-weight:600;color:#ffffff;\">AgroGestão</p></td></tr>"
            + "<tr><td style=\"padding:28px 24px 8px;\">"
            + "<h1 style=\"margin:0 0 12px;font-size:20px;font-weight:600;color:#1a2e24;\">" + tituloEsc + "</h1>"
            + "<p style=\"margin:0 0 20px;font-size:15px;line-height:1.5;color:#3d5247;\">" + instEsc + "</p>"
            + "<p style=\"margin:0 0 8px;font-size:13px;text-transform:uppercase;letter-spacing:0.06em;color:#5c7368;\">Seu código</p>"
            + "<p style=\"margin:0 0 24px;font-size:32px;font-weight:700;letter-spacing:0.35em;color:#1a4d3a;\">"
            + codigoEsc + "</p>"
            + "<p style=\"margin:0;font-size:13px;line-height:1.5;color:#5c7368;\">"
            + "Este código expira em breve. Se você não solicitou esta ação, ignore este e-mail.</p>"
            + "</td></tr>"
            + "<tr><td style=\"padding:16px 24px 24px;border-top:1px solid #e8f0eb;\">"
            + "<p style=\"margin:0;font-size:12px;color:#8a9d94;\">Mensagem automática do AgroGestão.</p>"
            + "</td></tr></table></td></tr></table></body></html>";
    }

    /*
        Quebra o Base64 a cada 76 caracteres, com CRLF. Limite clássico
        de linha do MIME para o servidor não recusar o corpo.
    */
    private static String base64Dobrado (String texto)
    {
        String codificado = Base64.getEncoder ().encodeToString (texto.getBytes (StandardCharsets.UTF_8));
        StringBuilder   saida     = new StringBuilder (codificado.length () + (codificado.length () / 76) + 8);

        for (int i = 0; i < codificado.length (); i += 76)
            saida.append (codificado, i, Math.min (i + 76, codificado.length ())).append ("\r\n");

        return saida.toString ();
    }

    /*
        Junta texto puro e HTML num multipart/alternative. O cliente de
        e-mail mostra o HTML; quem não sabe HTML fica com o texto.
    */
    private static String montarMime (String boundary, String textoPlano, String html)
    {
        StringBuilder mime = new StringBuilder ();
        mime.append ("--").append (boundary).append ("\r\n");
        mime.append ("Content-Type: text/plain; charset=UTF-8\r\n");
        mime.append ("Content-Transfer-Encoding: base64\r\n\r\n");
        mime.append (ServicoEmail.base64Dobrado (textoPlano)).append ("\r\n");
        mime.append ("--").append (boundary).append ("\r\n");
        mime.append ("Content-Type: text/html; charset=UTF-8\r\n");
        mime.append ("Content-Transfer-Encoding: base64\r\n\r\n");
        mime.append (ServicoEmail.base64Dobrado (html)).append ("\r\n");
        mime.append ("--").append (boundary).append ("--\r\n");
        return mime.toString ();
    }

    /*
        No comando DATA, uma linha só com "." encerra a mensagem. Se o
        corpo tiver uma linha que começa com ponto, ela é dobrada ("..")
        para o servidor não cortar o e-mail no meio.
    */
    private static String dotStuff (String corpo)
    {
        return corpo.replace ("\r\n.", "\r\n..").replace ("\n.", "\n..");
    }

    /*
        Diálogo SMTP sobre TLS implícito: conecta, autentica, informa
        remetente e destinatário e envia o MIME. Timeout de 10 segundos
        no socket. Qualquer código de resposta diferente do esperado
        interrompe e a exceção volta para o handler.
    */
    private void enviarSmtp (String destinatario, String assunto, String boundary, String mimeCorpo) throws Exception
    {
        SSLSocketFactory fabrica = (SSLSocketFactory) SSLSocketFactory.getDefault ();
        String assuntoCodificado = ServicoEmail.codificarUtf8Mime (assunto);
        String remetenteExibicao = ServicoEmail.codificarUtf8Mime ("AgroGestão") + " <" + this.remetente + ">";

        try (SSLSocket socket = (SSLSocket) fabrica.createSocket (this.host, this.porta);
             BufferedReader entrada = new BufferedReader (new InputStreamReader (socket.getInputStream (), StandardCharsets.US_ASCII));
             BufferedWriter saida = new BufferedWriter (new OutputStreamWriter (socket.getOutputStream (), StandardCharsets.UTF_8)))
        {
            socket.setSoTimeout (10000);
            // 220: o servidor aceitou a conexão TLS.
            ServicoEmail.esperar (entrada, 220);
            ServicoEmail.comando (saida, "EHLO agrogestao.local");
            // 250: EHLO aceito. AUTH LOGIN em seguida pede usuário e senha.
            ServicoEmail.esperar (entrada, 250);
            ServicoEmail.comando (saida, "AUTH LOGIN");
            ServicoEmail.esperar (entrada, 334);
            ServicoEmail.comando (saida, ServicoEmail.base64 (this.usuario));
            ServicoEmail.esperar (entrada, 334);
            ServicoEmail.comando (saida, ServicoEmail.base64 (this.senha));
            // 235: autenticação ok. 535 cairia na dica de senha de app.
            ServicoEmail.esperar (entrada, 235);
            ServicoEmail.comando (saida, "MAIL FROM:<" + this.remetente + ">");
            ServicoEmail.esperar (entrada, 250);
            ServicoEmail.comando (saida, "RCPT TO:<" + destinatario + ">");
            ServicoEmail.esperar (entrada, 250);
            ServicoEmail.comando (saida, "DATA");
            // 354: o servidor espera o corpo, terminado por uma linha só com ponto.
            ServicoEmail.esperar (entrada, 354);

            saida.write ("From: " + remetenteExibicao + "\r\n");
            saida.write ("To: " + destinatario + "\r\n");
            saida.write ("Subject: " + assuntoCodificado + "\r\n");
            saida.write ("MIME-Version: 1.0\r\n");
            saida.write ("Content-Type: multipart/alternative; boundary=\"" + boundary + "\"\r\n\r\n");
            saida.write (ServicoEmail.dotStuff (mimeCorpo));
            saida.write ("\r\n.\r\n");
            saida.flush ();
            ServicoEmail.esperar (entrada, 250);
            ServicoEmail.comando (saida, "QUIT");
        }
    }

    /*
        Envia o código de 6 dígitos (cadastro, reenvio, senha ou e-mail novo).
        Endereço inválido: exceção.
        Modo simulado: imprime o código e retorna, sem chamar o multipart.
        A frase do HTML muda se o assunto fala de redefinição ou de confirmação.
        Quem chama trata a exceção de SMTP.
    */
    public void enviarCodigo (String destinatario, String assunto, String codigo) throws Exception
    {
        ServicoEmail.conferirEndereco (destinatario);

        if (this.host == null)
        {
            System.out.println ("[e-mail simulado] para " + destinatario + " | " + assunto + " | codigo: " + codigo);
            return;
        }

        String titulo = assunto.contains ("—") ? assunto.substring (0, assunto.indexOf ("—")).trim () : assunto.trim ();
        String instrucao = "Use o código abaixo na tela do AgroGestão para concluir a operação.";

        if (assunto.toLowerCase ().contains ("redefin"))
            instrucao = "Use o código abaixo na tela de redefinição de senha do AgroGestão.";
        else if (assunto.toLowerCase ().contains ("confirm"))
            instrucao = "Use o código abaixo na tela de confirmação de e-mail do AgroGestão.";

        String texto = ServicoEmail.corpoTexto (titulo, codigo);
        String html  = ServicoEmail.corpoHtml (titulo, codigo, instrucao);
        this.enviarMultipart (destinatario, assunto, texto, html);
    }

    /*
        Convite de equipe. Modelos em recursos/email/convite-equipe.txt
        e convite-equipe.html, com os marcadores {{CHAVE}}.
        Nome ou fazenda em branco ganham uma saudação e um texto genéricos.
        Devolve true só quando o SMTP real aceitou a mensagem. No modo
        simulado devolve false: o texto foi para o console, não para a caixa.
        Falha de envio é registrada e relançada para o chamador.
    */
    public boolean enviarConviteEquipe (String destinatario, String nomeConvidado, String nomePropriedade, String linkConvite) throws Exception
    {
        ServicoEmail.conferirEndereco (destinatario);

        String saudacao = (nomeConvidado == null || nomeConvidado.isBlank ())
            ? "Olá!"
            : "Olá, " + nomeConvidado.trim () + "!";

        String propriedade = (nomePropriedade == null || nomePropriedade.isBlank ())
            ? "uma propriedade rural"
            : nomePropriedade.trim ();

        java.util.HashMap<String, String> vars = new java.util.HashMap<String, String> ();
        vars.put ("TITULO",            "Convite para a equipe");
        vars.put ("SAUDACAO",          saudacao);
        vars.put ("NOME_PROPRIEDADE",  propriedade);
        vars.put ("LINK_CONVITE",      linkConvite);
        vars.put ("VALIDADE",          "7 dias");
        vars.put ("BOTAO_TEXTO",       "Aceitar convite");

        String modeloTxt  = ServicoEmail.carregarModelo ("convite-equipe.txt");
        String modeloHtml = ServicoEmail.carregarModelo ("convite-equipe.html");
        String texto      = ServicoEmail.aplicarModelo (modeloTxt, vars, false);
        String html       = ServicoEmail.aplicarModelo (modeloHtml, vars, true);

        try
        {
            this.enviarMultipart (destinatario, "Convite para a equipe", texto, html);
            return !this.simulado ();
        }
        catch (Exception erro)
        {
            System.err.println ("Falha ao enviar convite de equipe para " + destinatario + ": " + erro.getMessage ());
            throw erro;
        }
    }
}
