package handlers.conta;

/* Autor principal: Pedro Henrique Contardi Soler - RA: 25005592 */
/* Autor secundário: Pedro Henrique Contardi Soler - 25005592 */

import dados.propriedade.RepositorioEquipe;
import dados.conta.RepositorioUsuarios;
import util.propriedade.PapelNaPropriedade;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import org.bson.Document;
import org.bson.types.ObjectId;
import seguranca.Senhas;
import seguranca.Sessao;
import seguranca.Sessoes;
import seguranca.LimitadorDeTaxa;
import dados.infra.ConflitoDeCadastro;
import servicos.conta.CodigosVerificacao;
import servicos.conta.ServicoEmail;
import servicos.integracao.ClienteDeAlgoritmos;
import util.comum.Validacao;

/*
    Autenticação da conta no AgroGestão.

    Quem chama: as telas de cadastro, login, confirmação de e-mail e
    redefinição de senha. GET /api/me é chamado pelo front já logado para
    montar o menu (quem é o usuário, qual propriedade está ativa e se ele
    é proprietário ou colaborador).

    Rotas públicas, sem token (adicionarPublica):
      POST /api/auth/register
      POST /api/auth/login
      POST /api/auth/logout
      POST /api/auth/confirm-email
      POST /api/auth/resend-confirmation
      POST /api/auth/forgot-password
      POST /api/auth/reset-password
    Rota autenticada: GET /api/me. Sem sessão válida o roteador responde
    401 antes de chegar neste handler.

    O que persiste: o documento do usuário (nome, e-mail, telefone, hash da
    senha, flag de e-mail confirmado e os tokens de confirmação ou de
    redefinição) e, no cadastro, a primeira propriedade. O token de sessão
    fica só em memória, na classe Sessoes.

    O código de 6 dígitos sai por ServicoEmail. Sem SMTP no ambiente, o
    serviço imprime o código no console do servidor.
*/
public class AuthHandler
{
    private static final long INTERVALO_REENVIO   = 30L * 1000;      // espera mínima entre dois reenvios do mesmo e-mail
    private static final long VALIDADE_REDEFINICAO = 30L * 60 * 1000; // o código de "esqueci a senha" vale 30 minutos
    private static final long JANELA_TAXA = 60L * 1000;               // janela do limitador: 1 minuto por IP e por rota
    private static final int  MAXIMO_TENTATIVAS = 60;                 // acima disso a rota responde 429

    private final RepositorioUsuarios usuarios;
    private final RepositorioEquipe   equipe;
    private final Sessoes             sessoes;
    private final ServicoEmail        email;
    private final LimitadorDeTaxa     limitador = new LimitadorDeTaxa ();

    // Trava das operações que leem um token e depois gravam outro
    // (reenvio, confirmação e redefinição). Sem ela, duas requisições
    // paralelas passariam juntas pela checagem e a segunda apagaria a primeira.
    private final Object trava = new Object ();

    /*
        Atalho de construção quando o repositório de equipe ainda não existe.
        Com equipe nula, GET /api/me não anexa a lista de telas do colaborador.
    */
    public AuthHandler (RepositorioUsuarios usuarios, Sessoes sessoes, ServicoEmail email) throws Exception
    {
        this (usuarios, sessoes, email, null);
    }

    /*
        Exige usuários, sessões e e-mail. Equipe é opcional e só entra em
        GET /api/me, para saber as telas que um colaborador pode abrir.
    */
    public AuthHandler (RepositorioUsuarios usuarios, Sessoes sessoes, ServicoEmail email,
                        RepositorioEquipe equipe) throws Exception
    {
        if (usuarios == null)
            throw new Exception ("Repositorio de usuarios ausente");

        if (sessoes == null)
            throw new Exception ("Sessoes ausentes");

        if (email == null)
            throw new Exception ("Servico de e-mail ausente");

        this.usuarios = usuarios;
        this.equipe   = equipe;
        this.sessoes  = sessoes;
        this.email    = email;
    }

    /*
        Liga cada caminho ao método correspondente.
        adicionarPublica não exige Bearer token. adicionar (só o /api/me)
        deixa o roteador recusar a chamada com 401 se a sessão não existir.
    */
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

    /*
        Monta o JSON do usuário que pode sair do servidor.
        Hash da senha, tokens e prazos internos ficam de fora.
    */
    private static Document usuarioPublico (Document usuario)
    {
        return util.conta.PerfilUsuario.publico (usuario);
    }

    /*
        Atalho das respostas 200 que só levam uma mensagem de texto.
        Usado na confirmação de e-mail, no "esqueci a senha" e na redefinição.
    */
    private static Resposta mensagem (String texto)
    {
        return Resposta.json (200, new Document ("message", texto));
    }

    /*
        Limite por IP e por nome de rota: no máximo 60 chamadas por minuto.
        Devolve null quando a chamada pode seguir.
        429, com Retry-After 60: o IP estourou a janela. Vale para cadastro,
        login e para os fluxos de código, que são públicos e fáceis de abusar.
    */
    private Resposta limitar (Requisicao requisicao, String rota) throws Exception
    {
        if (this.limitador.permitir (rota + ":" + requisicao.getEnderecoRemoto (),
                                    AuthHandler.MAXIMO_TENTATIVAS, AuthHandler.JANELA_TAXA))
            return null;

        Resposta ret = Resposta.erro (429, "Muitas requisições. Aguarde e tente novamente.");
        ret.setCabecalho ("Retry-After", "60");
        return ret;
    }

    /*
        POST /api/auth/register
        Cria o usuário e a primeira propriedade, ainda com e-mail não confirmado.
        429: limite de tentativas deste IP.
        400: o validador remoto recusou nome, e-mail, senha, fazenda ou área.
        409: e-mail ou nome da propriedade já existe (índice único do banco).
        201: conta gravada. Se o SMTP falhar, a conta permanece e o JSON avisa.
    */
    private Resposta cadastrar (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "register");
        if (limitada != null)
            return limitada;

        Document corpo = requisicao.getCorpoJson ();

        // Regras de campo ficam no ServerSocket (ValidadorDeConta).
        // respostaErro, quando vem preenchida, já é o 400 com os campos.
        ClienteDeAlgoritmos.ResultadoCadastro remoto = ClienteDeAlgoritmos.validarCadastro (corpo);

        if (remoto.respostaErro != null)
            return remoto.respostaErro;

        String name     = remoto.normalizados.getString ("name");
        String email    = remoto.normalizados.getString ("email");
        String phone    = remoto.normalizados.getString ("phone");
        String password = remoto.password;
        String farm     = remoto.normalizados.getString ("farm");
        String area     = remoto.normalizados.getString ("area");

        String agora = Instant.now ().toString ();
        String token = CodigosVerificacao.seisDigitos ();

        /*
            Documento que usuarios.criar grava na coleção usuarios.
            A senha em claro (password) NÃO entra. No lugar vai senhaHash.
            tokenConfirmacao é o código de 6 dígitos do e-mail; a resposta
            pública (usuarioPublico) tira esse campo antes de ir para o navegador.

            {
              "name", "email", "phone",
              "senhaHash": "pbkdf2$120000$salt$hash",
              "emailConfirmado": false,
              "tokenConfirmacao": "123456",
              "ultimoEnvioConfirmacao": milissegundos,  // o reenvio respeita um intervalo
              "onboardingConcluido": false,             // o painel ainda mostra o tour
              "criadoEm": "2026-...Z"
            }

            propriedade é outro documento, coleção propriedades, criado na mesma
            transação: { "farm", "area", "criadaEm" }. O repositório ainda
            preenche _id, usuarioId e o nome normalizado.
        */
        Document usuario = new Document ("name",                   name)
                               .append  ("email",                  email)
                               .append  ("phone",                  phone)
                               .append  ("senhaHash",              Senhas.gerar (password))
                               .append  ("emailConfirmado",        false)
                               .append  ("tokenConfirmacao",       token)
                               .append  ("ultimoEnvioConfirmacao", System.currentTimeMillis ())
                               .append  ("onboardingConcluido",    false)
                               .append  ("criadoEm",               agora);

        Document propriedade = new Document ("farm", farm).append ("area", area).append ("criadaEm", agora);

        Document criado;
        try
        {
            criado = this.usuarios.criar (usuario, propriedade);
        }
        catch (ConflitoDeCadastro conflito)
        {
            // 409 separado por campo: o front destaca fazenda ou e-mail.
            if ("farm".equals (conflito.getCampo ()))
                /*
                    JSON de erro, só na resposta HTTP, nada é gravado:

                    {
                      "error": "Já existe uma propriedade com esse nome.",
                      "fields": { "farm": "Este nome de propriedade já está em uso." }
                    }

                    O front usa fields.farm para pintar o input da fazenda.
                    O 409 de e-mail logo abaixo tem a mesma forma, com fields.email.
                */
                return Resposta.json (409, new Document ("error", "Já existe uma propriedade com esse nome.")
                                               .append ("fields", new Document ("farm", "Este nome de propriedade já está em uso.")));
            return Resposta.json (409, new Document ("error", "E-mail já cadastrado.")
                                               .append ("fields", new Document ("email", "Este e-mail já está cadastrado.")));
        }

        Document corpoResposta = new Document ("usuario", AuthHandler.usuarioPublico (criado));
        try
        {
            this.email.enviarCodigo (email, "Confirmação de e-mail", token);
        }
        catch (Exception erro)
        {
            // A conta já foi gravada: 201 mesmo assim, com aviso para reenviar.
            corpoResposta.append ("aviso",
                "Conta criada, mas o e-mail de confirmação não foi enviado. Corrija o SMTP ou use Reenviar confirmação.");
            corpoResposta.append ("detalheEnvio", erro.getMessage ());
        }

        return Resposta.json (201, corpoResposta);
    }

    /*
        POST /api/auth/login
        Confere e-mail e senha e devolve o token da sessão.
        429: limite de tentativas deste IP.
        400: e-mail ou senha em branco no corpo.
        401: e-mail inexistente ou senha errada (a mesma frase nos dois casos).
        403: senha certa, mas o e-mail ainda não foi confirmado.
        200: token novo e o usuário público.
    */
    private Resposta entrar (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "login");
        if (limitada != null)
            return limitada;

        Document corpo    = requisicao.getCorpoJson ();
        Object   email    = corpo.get ("email");
        Object   password = corpo.get ("password");

        Validacao validacao = new Validacao (corpo);
        // 400 por campo. Senha vazia e e-mail em branco param aqui, antes do banco.
        if (!(email instanceof String) || ((String) email).isBlank ())
            validacao.erro ("email", "Informe seu e-mail.");
        if (!(password instanceof String) || ((String) password).isEmpty ())
            validacao.erro ("password", "Informe sua senha.");
        if (validacao.temErros ())
            return validacao.resposta ();

        Document usuario = this.usuarios.buscarPor ("email", ((String) email).trim ().toLowerCase ());

        // 401 idêntico para e-mail inexistente e senha errada: não revela quem está cadastrado.
        if (!Senhas.conferir ((String) password, usuario == null ? null : usuario.getString ("senhaHash")))
            return Resposta.erro (401, "E-mail ou senha inválidos.");

        // A senha correta identifica o próprio titular; só então informamos
        // que falta ativar a conta. Assim não há enumeração de e-mails.
        // 403 é distinto do 401 de propósito: a credencial está certa.
        if (!Boolean.TRUE.equals (usuario.getBoolean ("emailConfirmado")))
            return Resposta.erro (403, "Confirme o e-mail antes de entrar.");

        String token;
        // Mesmo monitor das trocas de propriedade e da troca de senha.
        // Relê o usuário depois do hash: uma exclusão ou uma redefinição
        // paralela pode ter mudado hash, e-mail ou a propriedade padrão.
        synchronized (this.sessoes)
        {
            String hashConferido = usuario.getString ("senhaHash");
            String emailConferido = usuario.getString ("email");
            // Uma exclusão pode ter alterado a propriedade padrão durante o hash.
            usuario = this.usuarios.buscarPorId (((ObjectId) usuario.get ("_id")).toHexString ());
            // 401 de novo se a conta sumiu, a senha mudou, o e-mail mudou,
            // a confirmação foi desfeita ou a propriedade padrão não existe mais.
            if (usuario == null || !hashConferido.equals (usuario.getString ("senhaHash")) ||
                !emailConferido.equals (usuario.getString ("email")) || !Boolean.TRUE.equals (usuario.getBoolean ("emailConfirmado")) ||
                this.usuarios.buscarPropriedade (usuario.getString ("propriedadeId")) == null)
                return Resposta.erro (401, "E-mail ou senha inválidos.");
            token = this.sessoes.criar (((ObjectId) usuario.get ("_id")).toHexString (),
                                        usuario.getString ("propriedadeId"));
        }

        return Resposta.json (200, new Document ("token", token).append ("usuario", AuthHandler.usuarioPublico (usuario)));
    }

    /*
        POST /api/auth/logout
        Rota pública de propósito: encerra o token enviado, mesmo que já
        esteja inválido. 204 sem corpo. Não persiste nada no banco; só
        apaga a sessão em memória. Token ausente não gera erro.
    */
    private Resposta sair (Requisicao requisicao)
    {
        this.sessoes.encerrar (requisicao.getToken ());
        return Resposta.semConteudo ();
    }

    /*
        GET /api/me
        Devolve o usuário da sessão, o papel na propriedade ativa e,
        se for colaborador, as telas que a equipe liberou.
        401: o token passou no roteador, mas o usuário não existe mais.
        200: usuário público, papel e, quando houver, a propriedade ativa.
    */
    private Resposta eu (Requisicao requisicao) throws Exception
    {
        Sessao   sessao  = requisicao.getSessao ();
        Document usuario = this.usuarios.buscarPorId (sessao.getUsuarioId ());

        if (usuario == null)
            return Resposta.erro (401, "Não autorizado.");

        Document propriedade = this.usuarios.buscarPropriedade (sessao.getPropriedadeId ());
        Document usuarioPublico = AuthHandler.usuarioPublico (usuario)
                                      .append ("propriedadeId", sessao.getPropriedadeId ());

        String propriedadeId = sessao.getPropriedadeId ();
        String usuarioId     = sessao.getUsuarioId ();
        boolean dono = PapelNaPropriedade.ehProprietario (this.usuarios, propriedadeId, usuarioId);
        usuarioPublico.append ("papel", dono ? PapelNaPropriedade.PROPRIETARIO : PapelNaPropriedade.COLABORADOR);
        usuarioPublico.append ("equipeGerenciavel", dono);

        // Colaborador: as telas vêm do vínculo ativo. Dono não precisa dessa lista.
        if (!dono && this.equipe != null)
        {
            Document colaboracao = this.equipe.membroAtivoDoUsuario (propriedadeId, usuarioId);
            if (colaboracao != null)
                usuarioPublico.append ("telas", colaboracao.get ("telas"));
        }

        Document ret = new Document ("usuario", usuarioPublico);

        if (propriedade != null)
            ret.append ("propriedade", new Document ("id",   sessao.getPropriedadeId ())
                                           .append ("farm", propriedade.getString ("nome") == null ? propriedade.getString ("farm") : propriedade.getString ("nome"))
                                           .append ("area", propriedade.containsKey ("areaHectares") ? propriedade.get ("areaHectares") : propriedade.get ("area")));

        return Resposta.json (200, ret);
    }

    /*
        POST /api/auth/confirm-email  {token}
        Marca o e-mail como confirmado e apaga o código usado.
        429: limite de tentativas deste IP.
        400: código ausente, com formato errado, desconhecido ou já consumido.
        200: e-mail confirmado. Não devolve sessão; o front ainda precisa entrar.
    */
    private Resposta confirmarEmail (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "confirm-email");
        if (limitada != null)
            return limitada;

        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    token     = validacao.codigo6 ("token", "Informe o código de confirmação.");
        // 400: o código não tem exatamente 6 dígitos.
        if (validacao.temErros ())
            return validacao.resposta ();

        // Lê e apaga o token sob a mesma trava. A segunda requisição com o
        // mesmo código encontra o campo já nulo e cai no 400.
        synchronized (this.trava)
        {
            Document usuario = this.usuarios.buscarPor ("tokenConfirmacao", token);
            if (usuario == null)
                return Resposta.erro (400, "Código inválido ou expirado.");

            this.usuarios.atualizar (((ObjectId) usuario.get ("_id")).toHexString (),
                                     new Document ("emailConfirmado", true).append ("tokenConfirmacao", null));
        }

        return AuthHandler.mensagem ("E-mail confirmado.");
    }

    /*
        POST /api/auth/resend-confirmation  {email}
        Gera outro código se a conta existe e o e-mail ainda não foi confirmado.
        429: limite por IP, ou reenvio antes dos 30 segundos (Retry-After em segundos).
        400: e-mail ausente ou malformado.
        200: frase genérica, mesmo quando o e-mail não existe ou já foi confirmado,
        para não servir de consulta de cadastro.
    */
    private Resposta reenviarConfirmacao (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "resend-confirmation");
        if (limitada != null)
            return limitada;

        Validacao validacao = new Validacao (requisicao.getCorpoJson ());
        String    email     = validacao.email ("email");
        if (validacao.temErros ())
            return validacao.resposta ();

        Document corpoResposta = new Document ("message",
            "Se o e-mail estiver pendente de confirmação, um novo código foi enviado.");

        // A checagem dos 30 s e a troca do token precisam ser atômicas:
        // dois cliques juntos não podem os dois passar do intervalo.
        synchronized (this.trava)
        {
            Document usuario = this.usuarios.buscarPor ("email", email);

            // Conta inexistente ou já confirmada: silêncio, e o 200 genérico segue.
            if (usuario != null && !usuario.getBoolean ("emailConfirmado"))
            {
                long agora    = System.currentTimeMillis ();
                long ultimo   = usuario.get ("ultimoEnvioConfirmacao", 0L);
                long esperado = ultimo + AuthHandler.INTERVALO_REENVIO;

                // 429 próprio deste fluxo, com os segundos que ainda faltam.
                if (agora < esperado)
                {
                    long segundos = (esperado - agora + 999) / 1000;

                    Resposta ret = Resposta.json (429, new Document ("error", "Aguarde antes de reenviar.")
                                                           .append ("retryAfter", segundos));
                    ret.setCabecalho ("Retry-After", "" + segundos);
                    return ret;
                }

                String token = CodigosVerificacao.seisDigitos ();
                this.usuarios.atualizar (((ObjectId) usuario.get ("_id")).toHexString (),
                                         new Document ("tokenConfirmacao", token).append ("ultimoEnvioConfirmacao", agora));

                try
                {
                    this.email.enviarCodigo (email, "Confirmação de e-mail (reenvio)", token);
                }
                catch (Exception erro)
                {
                    // O token novo já foi gravado. O 200 avisa que o SMTP falhou.
                    corpoResposta.append ("aviso",
                        "Não foi possível enviar o e-mail agora. Confira o SMTP do servidor ou tente novamente em instantes.");
                    corpoResposta.append ("detalheEnvio", erro.getMessage ());
                }
            }
        }

        return Resposta.json (200, corpoResposta);
    }

    /*
        POST /api/auth/forgot-password  {email}
        Grava um código de redefinição com validade de 30 minutos.
        429: limite de tentativas deste IP.
        400: e-mail ausente ou malformado.
        200 sempre que o e-mail tem formato válido, exista a conta ou não.
        Falha de SMTP também fica em silêncio, para não revelar o cadastro.
    */
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

            // E-mail desconhecido: não grava token e não muda a resposta.
            if (usuario != null)
            {
                String token = CodigosVerificacao.seisDigitos ();
                this.usuarios.atualizar (((ObjectId) usuario.get ("_id")).toHexString (),
                                         new Document ("tokenRedefinicao", token)
                                             .append ("redefinicaoExpiraEm", System.currentTimeMillis () + AuthHandler.VALIDADE_REDEFINICAO));

                try
                {
                    this.email.enviarCodigo (email, "Redefinição de senha", token);
                }
                catch (Exception ignorado)
                {
                    // Resposta genérica 200: não revelar falha de SMTP ao cliente.
                }
            }
        }

        return AuthHandler.mensagem ("Se o e-mail estiver cadastrado, um código de redefinição foi enviado.");
    }

    /*
        POST /api/auth/reset-password  {token, password, password-confirm}
        Troca o hash, invalida o código e encerra todas as sessões da conta.
        429: limite de tentativas deste IP.
        400: código fora do formato, senha recusada pelo validador remoto,
        código inexistente, expirado ou já consumido por outra requisição.
        200: senha gravada. O front precisa fazer login de novo.
    */
    private Resposta redefinirSenha (Requisicao requisicao) throws Exception
    {
        Resposta limitada = this.limitar (requisicao, "reset-password");
        if (limitada != null)
            return limitada;

        Document  corpo     = requisicao.getCorpoJson ();
        Validacao validacao = new Validacao (corpo);
        String    token     = validacao.codigo6 ("token", "Informe o código de redefinição.");

        // 400 só do código. A senha nova é checada no bloco seguinte.
        if (validacao.temErros ())
            return validacao.resposta ();

        // Regras de senha no ServerSocket (ValidadorDeConta); token e persistência aqui.
        ClienteDeAlgoritmos.ResultadoSenha remoto = ClienteDeAlgoritmos.validarSenha (corpo, "password");

        if (remoto.respostaErro != null)
            return remoto.respostaErro;

        String password = remoto.password;
        String hash     = Senhas.gerar (password);
        String id;

        // trava: ninguém reenvia ou confirma token enquanto esta troca ocorre.
        // sessoes: o login não cria token novo no meio da invalidação.
        synchronized (this.trava)
        {
            synchronized (this.sessoes)
            {
                Document usuario = this.usuarios.buscarPor ("tokenRedefinicao", token);
                // 400: código desconhecido ou prazo de 30 minutos vencido.
                if (usuario == null || usuario.get ("redefinicaoExpiraEm", 0L) < System.currentTimeMillis ())
                    return Resposta.erro (400, "Código inválido ou expirado.");
                id = ((ObjectId) usuario.get ("_id")).toHexString ();
                // O token não pode ressuscitar depois de uma troca de senha/perfil.
                // atualizarCondicional só grava se o token e o hash ainda forem os lidos.
                // Também limpa uma troca de e-mail pendente, para o código antigo morrer junto.
                if (!this.usuarios.atualizarCondicional (id, new Document ("tokenRedefinicao", token)
                    .append ("senhaHash", usuario.getString ("senhaHash")), new Document ("senhaHash", hash)
                    .append ("tokenRedefinicao", null).append ("redefinicaoExpiraEm", null)
                    .append ("emailPendente", null).append ("tokenTrocaEmail", null).append ("trocaEmailExpiraEm", null)))
                    return Resposta.erro (400, "Código inválido ou expirado.");
                this.sessoes.encerrarDoUsuario (id);
            }
        }

        return AuthHandler.mensagem ("Senha redefinida.");
    }
}
