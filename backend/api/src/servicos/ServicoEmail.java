/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

package servicos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
    Envia os codigos de autenticacao por SMTP sobre TLS implicito (porta 465).
    Sem as variaveis SMTP_* o modo local imprime o codigo no terminal para que
    cadastro, confirmacao e recuperacao possam ser testados sem credenciais.
*/
public final class ServicoEmail
{
    private final String host;
    private final int    porta;
    private final String usuario;
    private final String senha;
    private final String remetente;

    private ServicoEmail (String host, int porta, String usuario, String senha, String remetente)
    {
        this.host      = host;
        this.porta     = porta;
        this.usuario   = usuario;
        this.senha     = senha;
        this.remetente = remetente;
    }

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

    // Centraliza a configuracao e impede iniciar parcialmente configurado.
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

    public boolean simulado ()
    {
        return this.host == null;
    }

  // Avisa no console se o codigo vai para o terminal ou para o SMTP (facilita debug local).
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

    private static void conferirEndereco (String endereco) throws Exception
    {
        if (endereco == null || endereco.contains ("\r") || endereco.contains ("\n"))
            throw new Exception ("Endereco de e-mail invalido");
    }

    private static void comando (BufferedWriter saida, String texto) throws Exception
    {
        saida.write (texto);
        saida.write ("\r\n");
        saida.flush ();
    }

    // Respostas SMTP podem ter varias linhas; a ultima tem espaco apos o codigo.
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

    private static String base64 (String texto)
    {
        return Base64.getEncoder ().encodeToString (texto.getBytes (StandardCharsets.UTF_8));
    }

    private static String codificarUtf8Mime (String texto)
    {
        boolean soAscii = true;
        for (int i = 0; i < texto.length () && soAscii; i++)
            soAscii = texto.charAt (i) < 128;

        if (soAscii)
            return texto;

        return "=?UTF-8?B?" + ServicoEmail.base64 (texto) + "?=";
    }

    private static String escapeHtml (String texto)
    {
        if (texto == null)
            return "";

        return texto.replace ("&", "&amp;")
                    .replace ("<", "&lt;")
                    .replace (">", "&gt;")
                    .replace ("\"", "&quot;");
    }

    private static String corpoTexto (String titulo, String codigo)
    {
        return titulo + "\n\n"
            + "Seu código: " + codigo + "\n\n"
            + "Digite esse código de 6 dígitos no AgroGestão para concluir a operação.\n"
            + "Se você não solicitou esta ação, ignore esta mensagem.";
    }

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

    private static String base64Dobrado (String texto)
    {
        String codificado = Base64.getEncoder ().encodeToString (texto.getBytes (StandardCharsets.UTF_8));
        StringBuilder   saida     = new StringBuilder (codificado.length () + (codificado.length () / 76) + 8);

        for (int i = 0; i < codificado.length (); i += 76)
            saida.append (codificado, i, Math.min (i + 76, codificado.length ())).append ("\r\n");

        return saida.toString ();
    }

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

    private static String dotStuff (String corpo)
    {
        return corpo.replace ("\r\n.", "\r\n..").replace ("\n.", "\n..");
    }

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
            ServicoEmail.esperar (entrada, 220);
            ServicoEmail.comando (saida, "EHLO agrogestao.local");
            ServicoEmail.esperar (entrada, 250);
            ServicoEmail.comando (saida, "AUTH LOGIN");
            ServicoEmail.esperar (entrada, 334);
            ServicoEmail.comando (saida, ServicoEmail.base64 (this.usuario));
            ServicoEmail.esperar (entrada, 334);
            ServicoEmail.comando (saida, ServicoEmail.base64 (this.senha));
            ServicoEmail.esperar (entrada, 235);
            ServicoEmail.comando (saida, "MAIL FROM:<" + this.remetente + ">");
            ServicoEmail.esperar (entrada, 250);
            ServicoEmail.comando (saida, "RCPT TO:<" + destinatario + ">");
            ServicoEmail.esperar (entrada, 250);
            ServicoEmail.comando (saida, "DATA");
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

        String assuntoCompleto = assunto.endsWith ("— AgroGestão") ? assunto : assunto + " — AgroGestão";
        String boundary      = "----=_AgroGestao_" + System.currentTimeMillis ();
        String texto = ServicoEmail.corpoTexto (titulo, codigo);
        String html  = ServicoEmail.corpoHtml (titulo, codigo, instrucao);
        String mime  = ServicoEmail.montarMime (boundary, texto, html);

        this.enviarSmtp (destinatario, assuntoCompleto, boundary, mime);
    }
}
