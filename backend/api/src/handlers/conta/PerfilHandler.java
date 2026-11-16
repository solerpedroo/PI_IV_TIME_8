/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package handlers.conta;

import dados.conta.RepositorioUsuarios;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import org.bson.Document;
import seguranca.LimitadorDeTaxa;
import seguranca.Senhas;
import seguranca.Sessoes;
import servicos.integracao.ClienteDeAlgoritmos;
import servicos.conta.CodigosVerificacao;
import servicos.conta.ServicoEmail;
import util.conta.PerfilUsuario;
import util.comum.Validacao;

/*
    Perfil do usuário que já está logado. O id vem sempre da sessão;
    o corpo não escolhe qual conta alterar.

    Quem chama: a tela de perfil (dados, preferências, senha, troca de
    e-mail e propriedade padrão) e o assistente de primeiro acesso.

    Rotas, todas autenticadas em /api/perfil:
      GET  /api/perfil
      PUT  /api/perfil
      PUT  /api/perfil/preferencias
      PUT  /api/perfil/propriedade-padrao
      PUT  /api/perfil/senha
      POST /api/perfil/email/confirmar
      POST /api/perfil/email/reenviar
      POST /api/perfil/onboarding
    Sem token, o roteador responde 401 antes destes métodos.

    O que persiste: campos do documento do usuário (nome, telefone,
    preferências, hash da senha, e-mail pendente e o código da troca).
    O e-mail atual só é substituído depois que o código do endereço novo
    é confirmado. Trocar a senha ou confirmar o e-mail novo encerra as
    sessões da conta.
*/
public class PerfilHandler
{
    private final RepositorioUsuarios usuarios;
    private final Sessoes sessoes;
    private final ServicoEmail email;
    private final LimitadorDeTaxa limitador = new LimitadorDeTaxa ();
    private static final long VALIDADE_EMAIL = 30 * 60 * 1000L; // código da troca de e-mail vale 30 minutos

    /*
        As três dependências são obrigatórias: sem usuários não há o que
        gravar, sem sessões não há de quem é o perfil, sem e-mail não há
        como mandar o código da troca de endereço.
    */
    public PerfilHandler (RepositorioUsuarios usuarios, Sessoes sessoes, ServicoEmail email) throws Exception
    {
        if (usuarios == null || sessoes == null || email == null) throw new Exception ("Dependência de perfil ausente");
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.email = email;
    }

    /*
        Registra as oito rotas. As escritas passam todas por gravar, que
        serializa a operação com o login e aplica o limite por usuário.
    */
    public void registrar (Roteador r) throws Exception
    {
        r.adicionar ("GET", "/api/perfil", this::consultar);
        r.adicionar ("PUT", "/api/perfil", q -> this.gravar (q, "perfil"));
        r.adicionar ("PUT", "/api/perfil/preferencias", q -> this.gravar (q, "preferencias"));
        r.adicionar ("PUT", "/api/perfil/propriedade-padrao", q -> this.gravar (q, "padrao"));
        r.adicionar ("PUT", "/api/perfil/senha", q -> this.gravar (q, "senha"));
        r.adicionar ("POST", "/api/perfil/email/confirmar", q -> this.gravar (q, "confirmar"));
        r.adicionar ("POST", "/api/perfil/email/reenviar", q -> this.gravar (q, "reenviar"));
        r.adicionar ("POST", "/api/perfil/onboarding", q -> this.gravar (q, "onboarding"));
    }

    /*
        GET /api/perfil
        Lê o documento da sessão e devolve só o que o front pode ver.
        401: a sessão aponta para um usuário que não existe mais.
        200: usuário público e o mapa de preferências.
    */
    private Resposta consultar (Requisicao q) throws Exception
    {
        Document usuario = this.usuarios.buscarPorId (q.getSessao ().getUsuarioId ());
        if (usuario == null) return Resposta.erro (401, "Sessão inválida.");
        return Resposta.json (200, new Document ("usuario", PerfilUsuario.publico (usuario))
            .append ("preferencias", PerfilUsuario.preferencias (usuario)));
    }

    /*
        Porta de todas as escritas do perfil.
        401: o token sumiu entre o roteador e esta trava, ou o usuário foi apagado.
        429: mais de 20 chamadas da mesma operação deste usuário em 1 minuto.
        Os demais status vêm do método escolhido no switch.

        synchronized (sessoes): a mesma trava do login, da seleção de
        propriedade e da invalidação de sessões. A comparação condicional
        no repositório ainda protege contra outra conexão Mongo.
    */
    private Resposta gravar (Requisicao q, String operacao) throws Exception
    {
        synchronized (this.sessoes)
        {
            // 401: outra requisição encerrou esta sessão enquanto esperávamos a trava.
            if (this.sessoes.buscar (q.getToken ()) == null) return Resposta.erro (401, "Sessão expirada.");
            // 429 por usuário e por tipo de operação, não por IP.
            if (!this.limitador.permitir (q.getSessao ().getUsuarioId () + ":" + operacao, 20, 60000L))
            {
                Resposta ret = Resposta.erro (429, "Muitas tentativas. Aguarde um minuto.");
                ret.setCabecalho ("Retry-After", "60");
                return ret;
            }
            Document usuario = this.usuarios.buscarPorId (q.getSessao ().getUsuarioId ());
            if (usuario == null) return Resposta.erro (401, "Sessão inválida.");
            return switch (operacao)
            {
                case "perfil" -> this.atualizar (q, usuario);
                case "preferencias" -> this.preferencias (q, usuario);
                case "padrao" -> this.padrao (q, usuario);
                case "senha" -> this.senha (q, usuario);
                case "confirmar" -> this.confirmar (q, usuario);
                case "onboarding" -> this.concluirOnboarding (q, usuario);
                default -> this.reenviar (q, usuario);
            };
        }
    }

    /*
        POST /api/perfil/onboarding
        Marca o primeiro acesso como concluído. Se já estava concluído,
        responde 200 sem gravar de novo.
        409: o hash da senha mudou desde a leitura (outra aba trocou a senha).
        200: flag gravada, ou já estava gravada.
    */
    private Resposta concluirOnboarding (Requisicao q, Document usuario) throws Exception
    {
        if (PerfilUsuario.onboardingConcluido (usuario))
            return Resposta.json (200, new Document ("onboardingConcluido", true));

        Document campos = new Document ("onboardingConcluido", true)
            .append ("onboardingConcluidoEm", Instant.now ().toString ());
        // Condição: o hash lido ainda é o do banco. Senão, 409 para recarregar.
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario), campos))
            return Resposta.erro (409, "A conta mudou. Recarregue e tente novamente.");

        return Resposta.json (200, new Document ("onboardingConcluido", true));
    }

    /*
        Filtro otimista das escritas: a atualização só vale se o hash da
        senha ainda for o que esta requisição leu. Não é uma rota.
    */
    private static Document versaoSenha (Document usuario)
    {
        return new Document ("senhaHash", usuario.getString ("senhaHash"));
    }

    /*
        Confere a senha atual digitada no corpo. Não devolve HTTP:
        em caso de falha, registra o erro no Validacao (o chamador responde 400)
        e devolve false. Recusa valor que não é texto ou que passa de 128 caracteres.
    */
    private boolean conferirSenha (Document usuario, Document corpo, String campo, Validacao v) throws Exception
    {
        Object valor = corpo.get (campo);
        if (!(valor instanceof String) || ((String) valor).length () > 128 ||
            !Senhas.conferir ((String) valor, usuario.getString ("senhaHash")))
        {
            v.erro (campo, "Informe sua senha atual corretamente.");
            return false;
        }
        return true;
    }

    /*
        PUT /api/perfil
        Atualiza nome e telefone. Se o e-mail mudou, não grava o endereço
        novo ainda: guarda emailPendente e manda um código.
        400: nome, e-mail, telefone ou senha atual inválidos, ou e-mail já em uso.
        429: outra confirmação de e-mail foi pedida há menos de 30 segundos.
        409: nome, e-mail ou token mudaram desde a leitura.
        200: perfil atualizado, ou confirmação pendente se o e-mail trocou.
    */
    private Resposta atualizar (Requisicao q, Document usuario) throws Exception
    {
        Document corpo = q.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        String nome = v.texto ("name", "Informe seu nome.", 120);
        String endereco = v.email ("email");
        Object telefone = corpo.get ("phone");
        // Telefone vazio é aceito. Preenchido, só no formato (DD) 9XXXX-XXXX.
        if (!(telefone instanceof String) || (!((String) telefone).isBlank () && !((String) telefone).matches ("\\(\\d{2}\\) 9\\d{4}-\\d{4}")))
            v.erro ("phone", "Use (DD) 9XXXX-XXXX ou deixe em branco.");
        boolean trocar = endereco != null && !endereco.equals (usuario.getString ("email"));
        if (trocar)
        {
            // Troca de e-mail exige a senha atual e um endereço que ninguém usa.
            this.conferirSenha (usuario, corpo, "senhaAtual", v);
            Document outro = this.usuarios.buscarPor ("email", endereco);
            if (outro != null) v.erro ("email", "Este e-mail já está em uso.");
        }
        if (v.temErros ()) return v.resposta ();
        /*
            campos é o $set parcial na coleção usuarios: só o que esta rota pode mudar.
            O restante do documento (senhaHash, e-mail atual, propriedadeId) fica intacto.

            Sem troca de e-mail:
            { "name", "phone", "atualizadoEm" }

            Com troca, os append de baixo entram no mesmo campos:
            {
              "emailPendente": endereço novo, ainda NÃO substitui "email",
              "tokenTrocaEmail": código de 6 dígitos,
              "trocaEmailExpiraEm": agora + validade, em milissegundos,
              "ultimoEnvioTrocaEmail": agora, para o intervalo de 30 segundos
            }

            esperado é o filtro otimista: a gravação só ocorre se senhaHash e email
            ainda forem os que lemos. Se outra aba trocou a senha no meio, vem 409.
        */
        Document campos = new Document ("name", nome).append ("phone", ((String) telefone).trim ())
            .append ("atualizadoEm", Instant.now ().toString ());
        Document esperado = versaoSenha (usuario).append ("email", usuario.getString ("email"));
        if (trocar)
        {
            // 429 local: protege o envio mesmo que o limitador geral ainda permita.
            if (usuario.getString ("emailPendente") != null && System.currentTimeMillis () - usuario.get ("ultimoEnvioTrocaEmail", 0L) < 30000)
                return Resposta.erro (429, "Aguarde 30 segundos antes de solicitar outra confirmação.");
            campos.append ("emailPendente", endereco).append ("tokenTrocaEmail", CodigosVerificacao.seisDigitos ())
                .append ("trocaEmailExpiraEm", System.currentTimeMillis () + VALIDADE_EMAIL)
                .append ("ultimoEnvioTrocaEmail", System.currentTimeMillis ());
            // A condição inclui o token anterior: dois pedidos paralelos não se atropelam.
            esperado.append ("tokenTrocaEmail", usuario.get ("tokenTrocaEmail"));
        }
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), esperado, campos))
            return Resposta.erro (409, "O perfil mudou. Recarregue e tente novamente.");
        if (trocar) return this.enviarConfirmacao (endereco, campos.getString ("tokenTrocaEmail"));
        return this.consultar (q);
    }

    /*
        PUT /api/perfil/preferencias
        Grava só os booleanos da lista fechada de PerfilUsuario.PREFERENCIAS.
        Campo extra do corpo é ignorado: não vira permissão nem mexe na conta.
        400: alguma chave obrigatória não veio como verdadeiro ou falso.
        409: o hash da senha mudou durante a gravação.
        200: o mapa que foi persistido.
    */
    private Resposta preferencias (Requisicao q, Document usuario) throws Exception
    {
        Document corpo = q.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        Document preferencias = new Document ();
        for (String chave : PerfilUsuario.PREFERENCIAS)
        {
            if (!(corpo.get (chave) instanceof Boolean)) v.erro (chave, "Informe verdadeiro ou falso.");
            else preferencias.put (chave, corpo.get (chave));
        }
        if (v.temErros ()) return v.resposta ();
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario), new Document ("preferencias", preferencias)))
            return Resposta.erro (409, "A conta mudou. Entre novamente.");
        return Resposta.json (200, new Document ("preferencias", preferencias));
    }

    /*
        PUT /api/perfil/propriedade-padrao
        Aponta a propriedade que o próximo login deve abrir.
        400: propriedadeId ausente ou longo demais.
        404: o id não está na lista desta conta (nem como vínculo legado).
        200: o id que passou a ser o padrão.
    */
    private Resposta padrao (Requisicao q, Document usuario) throws Exception
    {
        Validacao v = new Validacao (q.getCorpoJson ());
        String id = v.texto ("propriedadeId", "Selecione a propriedade padrão.", 24);
        if (v.temErros ()) return v.resposta ();
        // A listagem também faz a migração de vínculos legados comprovados.
        boolean pertence = false;
        for (Document p : this.usuarios.listarPropriedades (q.getSessao ().getUsuarioId ()))
            if (p.getObjectId ("_id").toHexString ().equals (id)) pertence = true;
        if (!pertence || !this.usuarios.definirPropriedadePadrao (q.getSessao ().getUsuarioId (), id))
            return Resposta.erro (404, "Propriedade não encontrada para esta conta.");
        return Resposta.json (200, new Document ("propriedadePadraoId", id));
    }

    /*
        PUT /api/perfil/senha
        Troca o hash, apaga tokens de redefinição e de troca de e-mail
        e encerra todas as sessões do usuário.
        400: senha nova recusada pelo validador remoto, senha atual errada,
        ou a nova igual à atual.
        409: outra requisição já trocou o hash.
        200: senha gravada; o cliente precisa entrar de novo.
    */
    private Resposta senha (Requisicao q, Document usuario) throws Exception
    {
        Document corpo = q.getCorpoJson ();

        // Nova senha validada no ServerSocket; senha atual e gravação permanecem na API.
        ClienteDeAlgoritmos.ResultadoSenha remoto = ClienteDeAlgoritmos.validarSenha (corpo, "new");

        if (remoto.respostaErro != null)
            return remoto.respostaErro;

        String    nova = remoto.password;
        Validacao v    = new Validacao (corpo);
        this.conferirSenha (usuario, corpo, "current", v);

        // A nova não pode repetir a atual, mesmo que as regras remotas aceitem.
        if (nova != null && nova.equals (corpo.get ("current")))
            v.erro ("new", "Escolha uma senha diferente da atual.");

        if (v.temErros ())
            return v.resposta ();
        Document campos = new Document ("senhaHash", Senhas.gerar (nova)).append ("tokenRedefinicao", null)
            .append ("redefinicaoExpiraEm", null).append ("tokenTrocaEmail", null).append ("emailPendente", null)
            .append ("trocaEmailExpiraEm", null);
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario), campos))
            return Resposta.erro (409, "A senha mudou durante a operação. Entre novamente.");
        this.sessoes.encerrarDoUsuario (q.getSessao ().getUsuarioId ());
        return Resposta.json (200, new Document ("message", "Senha atualizada. Entre novamente em todos os dispositivos."));
    }

    /*
        Dispara o código do e-mail novo. Não é rota.
        200 nos dois caminhos: o pedido já foi salvo. Se o SMTP falhar,
        o JSON traz aviso e o endereço atual continua valendo.
    */
    private Resposta enviarConfirmacao (String destino, String codigo)
    {
        try
        {
            this.email.enviarCodigo (destino, "Confirmação de novo e-mail", codigo);
            return Resposta.json (200, new Document ("confirmacaoPendente", true).append ("message", "Confirme o código enviado ao novo e-mail. O endereço atual continua válido até confirmar."));
        }
        catch (Exception erro)
        {
            return Resposta.json (200, new Document ("confirmacaoPendente", true).append ("aviso", "O pedido foi salvo, mas o envio do código falhou. Use Reenviar código. O e-mail atual foi preservado."));
        }
    }

    /*
        POST /api/perfil/email/reenviar
        Gera outro código para o emailPendente já gravado.
        400: não há troca de e-mail em andamento.
        429: o último envio tem menos de 30 segundos.
        409: o token ou o hash mudaram desde a leitura.
        200: código novo enviado (ou aviso se o SMTP falhar).
    */
    private Resposta reenviar (Requisicao q, Document usuario) throws Exception
    {
        if (usuario.getString ("emailPendente") == null) return Resposta.erro (400, "Não há troca de e-mail pendente.");
        if (System.currentTimeMillis () - usuario.get ("ultimoEnvioTrocaEmail", 0L) < 30000)
            return Resposta.erro (429, "Aguarde 30 segundos para reenviar.");
        Document campos = new Document ("tokenTrocaEmail", CodigosVerificacao.seisDigitos ())
            .append ("ultimoEnvioTrocaEmail", System.currentTimeMillis ()).append ("trocaEmailExpiraEm", System.currentTimeMillis () + VALIDADE_EMAIL);
        // Condição no token antigo: um reenvio paralelo não grava por cima do outro.
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario)
            .append ("tokenTrocaEmail", usuario.get ("tokenTrocaEmail")), campos)) return Resposta.erro (409, "O pedido mudou. Recarregue.");
        return this.enviarConfirmacao (usuario.getString ("emailPendente"), campos.getString ("tokenTrocaEmail"));
    }

    /*
        POST /api/perfil/email/confirmar
        Promove emailPendente a e-mail da conta e encerra as sessões.
        400: código fora do formato, diferente do gravado, expirado (30 min)
        ou sem troca pendente.
        409: o endereço já foi tomado ou o token mudou no meio da confirmação.
        200: e-mail novo gravado. O cliente entra de novo.
    */
    private Resposta confirmar (Requisicao q, Document usuario) throws Exception
    {
        Validacao v = new Validacao (q.getCorpoJson ());
        String codigo = v.codigo6 ("codigo", "Informe o código recebido.");
        if (v.temErros ()) return v.resposta ();
        // Código, prazo e existência do endereço pendente são uma condição só.
        if (!codigo.equals (usuario.getString ("tokenTrocaEmail")) || usuario.get ("trocaEmailExpiraEm", 0L) < System.currentTimeMillis () || usuario.getString ("emailPendente") == null)
            return Resposta.erro (400, "Código inválido ou expirado.");
        Document campos = new Document ("email", usuario.getString ("emailPendente")).append ("emailConfirmado", true)
            .append ("emailPendente", null).append ("tokenTrocaEmail", null).append ("trocaEmailExpiraEm", null)
            .append ("tokenRedefinicao", null).append ("redefinicaoExpiraEm", null);
        // A condição exige o mesmo código lido. O índice único do e-mail também pode falhar aqui.
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario)
            .append ("tokenTrocaEmail", codigo), campos)) return Resposta.erro (409, "Este endereço já está em uso ou o pedido mudou.");
        this.sessoes.encerrarDoUsuario (q.getSessao ().getUsuarioId ());
        return Resposta.json (200, new Document ("message", "Novo e-mail confirmado. Entre novamente."));
    }
}
