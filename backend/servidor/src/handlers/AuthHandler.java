package handlers;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */

import dados.RepositorioUsuarios;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Senhas;
import seguranca.Sessao;
import seguranca.Sessoes;
import seguranca.LimitadorDeTaxa;
import servicos.ServicoEmail;
import util.Validacao;

/*
    Cadastro, login, sessao, confirmacao de e-mail e redefinicao de senha.
    E-mail e simulado: os tokens sao impressos no console do servidor.
*/
public class AuthHandler
{
    private static final long INTERVALO_REENVIO   = 30L * 1000;      // 30 s
    private static final long VALIDADE_REDEFINICAO = 30L * 60 * 1000; // 30 min
    private static final long JANELA_TAXA = 60L * 1000;
    private static final int  MAXIMO_TENTATIVAS = 60;

    private final RepositorioUsuarios usuarios;
    private final Sessoes             sessoes;
    private final ServicoEmail        email;
    private final LimitadorDeTaxa     limitador = new LimitadorDeTaxa ();

    // protege as operacoes que leem e depois gravam tokens (reenvio, confirmacao, redefinicao)
    private final Object trava = new Object ();

    public AuthHandler (RepositorioUsuarios usuarios, Sessoes sessoes, ServicoEmail email) throws Exception
    {
        if (usuarios == null)
            throw new Exception ("Repositorio de usuarios ausente");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        if (email == null)
            throw new Exception ("Servico de e-mail ausente");

        this.usuarios = usuarios;
        this.sessoes  = sessoes;
        this.email    = email;
    }

    public void registrar (Roteador roteador) throws Exception
    {
        roteador.adicionarPublica ("POST", "/api/auth/register",            this::cadastrar);
        roteador.adicionarPublica ("POST", "/api/auth/login",               this::entrar);
        roteador.adicionarPublica ("POST", "/api/auth/logout",              this::sair);
        roteador.adicionarPublica ("POST", "/api/auth/confirm-email",       this::confirmarEmail);
        roteador.adicionarPublica ("POST", "/api/auth/resend-confirmation", this::reenviarConfirmacao);
        roteador.adicionarPublica ("POST", "/api/auth/forgot-password",     this::esqueciSenha);
        roteador.adicionarPublica ("POST", "/api/auth/reset-password",      this::redefinirSenha);
        roteador.adicionar        ("GET",  "/api/me",                       this::eu);
    }

    // so o que pode sair do servidor (nada de hash ou token)
    private static Document usuarioPublico (Document usuario)
    {
        return new Document ("id",              ((ObjectId) usuario.get ("_id")).toHexString ())
                   .append  ("name",            usuario.getString  ("name"))
                   .append  ("email",           usuario.getString  ("email"))
                   .append  ("phone",           usuario.getString  ("phone"))
                   .append  ("emailConfirmado", usuario.getBoolean ("emailConfirmado"))
                   .append  ("propriedadeId",   usuario.getString  ("propriedadeId"));
    }

    private static Resposta mensagem (String texto)
    {
        return Resposta.json (200, new Document ("message", texto));
    }

    private Resposta limitar (Requisicao requisicao, String rota) throws Exception
    {
        if (this.limitador.permitir (rota + ":" + requisicao.getEnderecoRemoto (),
                                    AuthHandler.MAXIMO_TENTATIVAS, AuthHandler.JANELA_TAXA))
            return null;

        Resposta ret = Resposta.erro (429, "Too many requests");
        ret.setCabecalho ("Retry-After", "60");
        return ret;
    }

    // POST /api/auth/register
    private Resposta cadastrar (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "register");
        if (limitada != null)
            return limitada;

        Document  corpo     = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);

        String name     = validacao.texto ("name", "Informe seu nome.", 120);
        String email    = validacao.email ("email");
        String phone    = validacao.texto ("phone", "Informe seu telefone.", 20);
        String password = validacao.senha ("password", "Crie uma senha.");
        String farm     = validacao.texto ("farm", "Informe a propriedade.", 120);
        String area     = validacao.texto ("area", "Informe a área.", 60);
        validacao.confirmacao (password);
        validacao.marcado ("terms", "Aceite os termos para continuar.");

        if (phone != null && !phone.matches ("\\(\\d{2}\\) 9\\d{4}-\\d{4}"))
            validacao.erro ("phone", "Use o formato (DD) 9XXXX-XXXX.");

        if (validacao.temErros ())
            return validacao.resposta ();

        String agora = Instant.now ().toString ();
        String token = UUID.randomUUID ().toString ();

        Document usuario = new Document ("name",                   name)
                               .append  ("email",                  email)
                               .append  ("phone",                  phone)
                               .append  ("senhaHash",              Senhas.gerar (password))
                               .append  ("emailConfirmado",        false)
                               .append  ("tokenConfirmacao",       token)
                               .append  ("ultimoEnvioConfirmacao", System.currentTimeMillis ())
                               .append  ("criadoEm",               agora);

        Document propriedade = new Document ("farm", farm).append ("area", area).append ("criadaEm", agora);

        Document criado = this.usuarios.criar (usuario, propriedade);
        if (criado == null)
            return Resposta.json (409, new Document ("error", "Email already registered")
                                           .append ("fields", new Document ("email", "Este e-mail já está cadastrado.")));

        this.email.enviarCodigo (email, "Confirmação de e-mail", token);

        return Resposta.json (201, new Document ("usuario", AuthHandler.usuarioPublico (criado)));
    }

    // POST /api/auth/login
    private Resposta entrar (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "login");
        if (limitada != null)
            return limitada;

        Document corpo    = requisicao.getCorpoJson ();
        Object   email    = corpo.get ("email");
        Object   password = corpo.get ("password");

        Validacao validacao = new Validacao (corpo);
        if (!(email instanceof String) || ((String) email).isBlank ())
            validacao.erro ("email", "Informe seu e-mail.");
        if (!(password instanceof String) || ((String) password).isEmpty ())
            validacao.erro ("password", "Informe sua senha.");
        if (validacao.temErros ())
            return validacao.resposta ();

        Document usuario = this.usuarios.buscarPor ("email", ((String) email).trim ().toLowerCase ());

        // mesma resposta para e-mail inexistente e senha errada
        if (!Senhas.conferir ((String) password, usuario == null ? null : usuario.getString ("senhaHash")))
            return Resposta.erro (401, "Invalid email or password");

        // A senha correta identifica o próprio titular; só então informamos
        // que falta ativar a conta. Assim não há enumeração de e-mails.
        if (!Boolean.TRUE.equals (usuario.getBoolean ("emailConfirmado")))
            return Resposta.erro (403, "Email confirmation required");

        String token;
        synchronized (this.sessoes)
        {
            // Uma exclusão pode ter alterado a propriedade padrão durante o hash.
            usuario = this.usuarios.buscarPorId (((ObjectId) usuario.get ("_id")).toHexString ());
            if (usuario == null || this.usuarios.buscarPropriedade (usuario.getString ("propriedadeId")) == null)
                return Resposta.erro (401, "Invalid email or password");
            token = this.sessoes.criar (((ObjectId) usuario.get ("_id")).toHexString (),
                                        usuario.getString ("propriedadeId"));
        }

        return Resposta.json (200, new Document ("token", token).append ("usuario", AuthHandler.usuarioPublico (usuario)));
    }

    // POST /api/auth/logout
    private Resposta sair (Requisicao requisicao)
    {
        this.sessoes.encerrar (requisicao.getToken ());
        return Resposta.semConteudo ();
    }

    // GET /api/me
    private Resposta eu (Requisicao requisicao) throws Exception
    {
        Sessao   sessao  = requisicao.getSessao ();
        Document usuario = this.usuarios.buscarPorId (sessao.getUsuarioId ());

        if (usuario == null)
            return Resposta.erro (401, "Unauthorized");

        Document propriedade = this.usuarios.buscarPropriedade (sessao.getPropriedadeId ());
        Document ret         = new Document ("usuario", AuthHandler.usuarioPublico (usuario)
                                         .append ("propriedadeId", sessao.getPropriedadeId ()));

        if (propriedade != null)
            ret.append ("propriedade", new Document ("id",   sessao.getPropriedadeId ())
                                           .append ("farm", propriedade.getString ("nome") == null ? propriedade.getString ("farm") : propriedade.getString ("nome"))
                                           .append ("area", propriedade.containsKey ("areaHectares") ? propriedade.get ("areaHectares") : propriedade.get ("area")));

        return Resposta.json (200, ret);
    }

    // POST /api/auth/confirm-email  {token}
    private Resposta confirmarEmail (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "confirm-email");
        if (limitada != null)
            return limitada;

        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    token     = validacao.texto ("token", "Informe o código de confirmação.", 64);
        if (validacao.temErros ())
            return validacao.resposta ();

        synchronized (this.trava)
        {
            Document usuario = this.usuarios.buscarPor ("tokenConfirmacao", token);
            if (usuario == null)
                return Resposta.erro (400, "Invalid or expired token");

            this.usuarios.atualizar (((ObjectId) usuario.get ("_id")).toHexString (),
                                     new Document ("emailConfirmado", true).append ("tokenConfirmacao", null));
        }

        return AuthHandler.mensagem ("Email confirmed");
    }

    // POST /api/auth/resend-confirmation  {email}
    private Resposta reenviarConfirmacao (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "resend-confirmation");
        if (limitada != null)
            return limitada;

        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    email     = validacao.email ("email");
        if (validacao.temErros ())
            return validacao.resposta ();

        synchronized (this.trava)
        {
            Document usuario = this.usuarios.buscarPor ("email", email);

            if (usuario != null && !usuario.getBoolean ("emailConfirmado"))
            {
                long agora    = System.currentTimeMillis ();
                long ultimo   = usuario.get ("ultimoEnvioConfirmacao", 0L);
                long esperado = ultimo + AuthHandler.INTERVALO_REENVIO;

                if (agora < esperado)
                {
                    long segundos = (esperado - agora + 999) / 1000;

                    Resposta ret = Resposta.json (429, new Document ("error", "Wait before resending")
                                                           .append ("retryAfter", segundos));
                    ret.setCabecalho ("Retry-After", "" + segundos);
                    return ret;
                }

                String token = UUID.randomUUID ().toString ();
                this.usuarios.atualizar (((ObjectId) usuario.get ("_id")).toHexString (),
                                         new Document ("tokenConfirmacao", token).append ("ultimoEnvioConfirmacao", agora));

                this.email.enviarCodigo (email, "Confirmação de e-mail (reenvio)", token);
            }
        }

        return AuthHandler.mensagem ("If the email is pending confirmation, a new code was sent");
    }

    // POST /api/auth/forgot-password  {email} -> sempre 200
    private Resposta esqueciSenha (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "forgot-password");
        if (limitada != null)
            return limitada;

        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    email     = validacao.email ("email");
        if (validacao.temErros ())
            return validacao.resposta ();

        synchronized (this.trava)
        {
            Document usuario = this.usuarios.buscarPor ("email", email);

            if (usuario != null)
            {
                String token = UUID.randomUUID ().toString ();
                this.usuarios.atualizar (((ObjectId) usuario.get ("_id")).toHexString (),
                                         new Document ("tokenRedefinicao", token)
                                             .append ("redefinicaoExpiraEm", System.currentTimeMillis () + AuthHandler.VALIDADE_REDEFINICAO));

                this.email.enviarCodigo (email, "Redefinição de senha", token);
            }
        }

        return AuthHandler.mensagem ("If the email is registered, a reset code was sent");
    }

    // POST /api/auth/reset-password  {token, password, password-confirm}
    private Resposta redefinirSenha (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "reset-password");
        if (limitada != null)
            return limitada;

        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    token     = validacao.texto ("token", "Informe o código de redefinição.", 64);
        String    password  = validacao.senha ("password", "Crie uma nova senha.");
        validacao.confirmacao (password);
        if (validacao.temErros ())
            return validacao.resposta ();

        String hash = Senhas.gerar (password);
        String id;

        synchronized (this.trava)
        {
            Document usuario = this.usuarios.buscarPor ("tokenRedefinicao", token);

            if (usuario == null || usuario.get ("redefinicaoExpiraEm", 0L) < System.currentTimeMillis ())
                return Resposta.erro (400, "Invalid or expired token");

            id = ((ObjectId) usuario.get ("_id")).toHexString ();
            this.usuarios.atualizar (id, new Document ("senhaHash", hash)
                                             .append ("tokenRedefinicao",    null)
                                             .append ("redefinicaoExpiraEm", null));
        }

        // senha nova: quem estava logado precisa entrar de novo
        this.sessoes.encerrarDoUsuario (id);

        return AuthHandler.mensagem ("Password reset");
    }
}
