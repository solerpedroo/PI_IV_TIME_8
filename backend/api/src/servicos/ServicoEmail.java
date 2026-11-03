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
        return valor == null ? "" : valor.trim ();
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
            throw new Exception ("SMTP recusou a operacao: " + linha);
    }

    private static String base64 (String texto)
    {
        return Base64.getEncoder ().encodeToString (texto.getBytes (StandardCharsets.UTF_8));
    }

    private void enviarSmtp (String destinatario, String assunto, String texto) throws Exception
    {
        SSLSocketFactory fabrica = (SSLSocketFactory) SSLSocketFactory.getDefault ();

        try (SSLSocket socket = (SSLSocket) fabrica.createSocket (this.host, this.porta);
             BufferedReader entrada = new BufferedReader (new InputStreamReader (socket.getInputStream (), StandardCharsets.US_ASCII));
             BufferedWriter saida = new BufferedWriter (new OutputStreamWriter (socket.getOutputStream (), StandardCharsets.US_ASCII)))
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

            // Assunto e corpo sao controlados pelo servidor; nao aceitamos CRLF no destinatario.
            saida.write ("From: " + this.remetente + "\r\n");
            saida.write ("To: " + destinatario + "\r\n");
            saida.write ("Subject: " + assunto + "\r\n");
            saida.write ("MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n");
            saida.write (texto.replace ("\n.", "\n.."));
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
            System.out.println ("[e-mail simulado] para " + destinatario + " | " + assunto + " | token: " + codigo);
            return;
        }

        this.enviarSmtp (destinatario, assunto,
                          "Use este codigo no AgroGestao: " + codigo + "\n\nSe voce nao solicitou esta acao, ignore esta mensagem.");
    }
}
