/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package handlers;

import dados.RepositorioUsuarios;
import http.Requisicao;
import http.Resposta;
import http.Roteador;
import java.time.Instant;
import java.util.UUID;
import org.bson.Document;
import seguranca.LimitadorDeTaxa;
import seguranca.Senhas;
import seguranca.Sessoes;
import servicos.ServicoEmail;
import util.PerfilUsuario;
import util.Validacao;

/* Perfil do próprio usuário: não aceita ID de usuário enviado pelo navegador.
   E-mail novo só substitui o atual após confirmar o código recebido. */
public class PerfilHandler
{
    private final RepositorioUsuarios usuarios;
    private final Sessoes sessoes;
    private final ServicoEmail email;
    private final LimitadorDeTaxa limitador = new LimitadorDeTaxa ();
    private static final long VALIDADE_EMAIL = 30 * 60 * 1000L;

    public PerfilHandler (RepositorioUsuarios usuarios, Sessoes sessoes, ServicoEmail email) throws Exception
    {
        if (usuarios == null || sessoes == null || email == null) throw new Exception ("Dependência de perfil ausente");
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.email = email;
    }

    public void registrar (Roteador r) throws Exception
    {
        r.adicionar ("GET", "/api/perfil", this::consultar);
        r.adicionar ("PUT", "/api/perfil", q -> this.gravar (q, "perfil"));
        r.adicionar ("PUT", "/api/perfil/preferencias", q -> this.gravar (q, "preferencias"));
        r.adicionar ("PUT", "/api/perfil/propriedade-padrao", q -> this.gravar (q, "padrao"));
        r.adicionar ("PUT", "/api/perfil/senha", q -> this.gravar (q, "senha"));
        r.adicionar ("POST", "/api/perfil/email/confirmar", q -> this.gravar (q, "confirmar"));
        r.adicionar ("POST", "/api/perfil/email/reenviar", q -> this.gravar (q, "reenviar"));
    }

    private Resposta consultar (Requisicao q) throws Exception
    {
        Document usuario = this.usuarios.buscarPorId (q.getSessao ().getUsuarioId ());
        if (usuario == null) return Resposta.erro (401, "Sessão inválida.");
        return Resposta.json (200, new Document ("usuario", PerfilUsuario.publico (usuario))
            .append ("preferencias", PerfilUsuario.preferencias (usuario)));
    }

    // Serializa com login, seleção de propriedade e invalidação das sessões.
    // A comparação no repositório ainda protege contra outra conexão Mongo.
    private Resposta gravar (Requisicao q, String operacao) throws Exception
    {
        synchronized (this.sessoes)
        {
            if (this.sessoes.buscar (q.getToken ()) == null) return Resposta.erro (401, "Sessão expirada.");
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
                default -> this.reenviar (q, usuario);
            };
        }
    }

    private static Document versaoSenha (Document usuario)
    {
        return new Document ("senhaHash", usuario.getString ("senhaHash"));
    }

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

    private Resposta atualizar (Requisicao q, Document usuario) throws Exception
    {
        Document corpo = q.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        String nome = v.texto ("name", "Informe seu nome.", 120);
        String endereco = v.email ("email");
        Object telefone = corpo.get ("phone");
        if (!(telefone instanceof String) || (!((String) telefone).isBlank () && !((String) telefone).matches ("\\(\\d{2}\\) 9\\d{4}-\\d{4}")))
            v.erro ("phone", "Use (DD) 9XXXX-XXXX ou deixe em branco.");
        boolean trocar = endereco != null && !endereco.equals (usuario.getString ("email"));
        if (trocar)
        {
            this.conferirSenha (usuario, corpo, "senhaAtual", v);
            Document outro = this.usuarios.buscarPor ("email", endereco);
            if (outro != null) v.erro ("email", "Este e-mail já está em uso.");
        }
        if (v.temErros ()) return v.resposta ();
        Document campos = new Document ("name", nome).append ("phone", ((String) telefone).trim ())
            .append ("atualizadoEm", Instant.now ().toString ());
        Document esperado = versaoSenha (usuario).append ("email", usuario.getString ("email"));
        if (trocar)
        {
            if (usuario.getString ("emailPendente") != null && System.currentTimeMillis () - usuario.get ("ultimoEnvioTrocaEmail", 0L) < 30000)
                return Resposta.erro (429, "Aguarde 30 segundos antes de solicitar outra confirmação.");
            campos.append ("emailPendente", endereco).append ("tokenTrocaEmail", UUID.randomUUID ().toString ())
                .append ("trocaEmailExpiraEm", System.currentTimeMillis () + VALIDADE_EMAIL)
                .append ("ultimoEnvioTrocaEmail", System.currentTimeMillis ());
            esperado.append ("tokenTrocaEmail", usuario.get ("tokenTrocaEmail"));
        }
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), esperado, campos))
            return Resposta.erro (409, "O perfil mudou. Recarregue e tente novamente.");
        if (trocar) return this.enviarConfirmacao (endereco, campos.getString ("tokenTrocaEmail"));
        return this.consultar (q);
    }

    // Preferências são booleanos de uma lista fechada. Campos extras não viram
    // permissões nem alteram outras informações da conta.
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

    private Resposta senha (Requisicao q, Document usuario) throws Exception
    {
        Document corpo = q.getCorpoJson ();
        Validacao v = new Validacao (corpo);
        String nova = v.senha ("new", "Informe a nova senha.");
        v.confirmacao (nova);
        this.conferirSenha (usuario, corpo, "current", v);
        if (nova != null && nova.equals (corpo.get ("current"))) v.erro ("new", "Escolha uma senha diferente da atual.");
        if (v.temErros ()) return v.resposta ();
        Document campos = new Document ("senhaHash", Senhas.gerar (nova)).append ("tokenRedefinicao", null)
            .append ("redefinicaoExpiraEm", null).append ("tokenTrocaEmail", null).append ("emailPendente", null)
            .append ("trocaEmailExpiraEm", null);
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario), campos))
            return Resposta.erro (409, "A senha mudou durante a operação. Entre novamente.");
        this.sessoes.encerrarDoUsuario (q.getSessao ().getUsuarioId ());
        return Resposta.json (200, new Document ("message", "Senha atualizada. Entre novamente em todos os dispositivos."));
    }

    private Resposta enviarConfirmacao (String destino, String codigo)
    {
        try
        {
            this.email.enviarCodigo (destino, "Confirmacao de novo e-mail", codigo);
            return Resposta.json (200, new Document ("confirmacaoPendente", true).append ("message", "Confirme o código enviado ao novo e-mail. O endereço atual continua válido até confirmar."));
        }
        catch (Exception erro)
        {
            return Resposta.json (200, new Document ("confirmacaoPendente", true).append ("aviso", "O pedido foi salvo, mas o envio do código falhou. Use Reenviar código. O e-mail atual foi preservado."));
        }
    }

    private Resposta reenviar (Requisicao q, Document usuario) throws Exception
    {
        if (usuario.getString ("emailPendente") == null) return Resposta.erro (400, "Não há troca de e-mail pendente.");
        if (System.currentTimeMillis () - usuario.get ("ultimoEnvioTrocaEmail", 0L) < 30000)
            return Resposta.erro (429, "Aguarde 30 segundos para reenviar.");
        Document campos = new Document ("tokenTrocaEmail", UUID.randomUUID ().toString ())
            .append ("ultimoEnvioTrocaEmail", System.currentTimeMillis ()).append ("trocaEmailExpiraEm", System.currentTimeMillis () + VALIDADE_EMAIL);
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario)
            .append ("tokenTrocaEmail", usuario.get ("tokenTrocaEmail")), campos)) return Resposta.erro (409, "O pedido mudou. Recarregue.");
        return this.enviarConfirmacao (usuario.getString ("emailPendente"), campos.getString ("tokenTrocaEmail"));
    }

    private Resposta confirmar (Requisicao q, Document usuario) throws Exception
    {
        Validacao v = new Validacao (q.getCorpoJson ());
        String codigo = v.texto ("codigo", "Informe o código recebido.", 64);
        if (v.temErros ()) return v.resposta ();
        if (!codigo.equals (usuario.getString ("tokenTrocaEmail")) || usuario.get ("trocaEmailExpiraEm", 0L) < System.currentTimeMillis () || usuario.getString ("emailPendente") == null)
            return Resposta.erro (400, "Código inválido ou expirado.");
        Document campos = new Document ("email", usuario.getString ("emailPendente")).append ("emailConfirmado", true)
            .append ("emailPendente", null).append ("tokenTrocaEmail", null).append ("trocaEmailExpiraEm", null)
            .append ("tokenRedefinicao", null).append ("redefinicaoExpiraEm", null);
        if (!this.usuarios.atualizarCondicional (q.getSessao ().getUsuarioId (), versaoSenha (usuario)
            .append ("tokenTrocaEmail", codigo), campos)) return Resposta.erro (409, "Este endereço já está em uso ou o pedido mudou.");
        this.sessoes.encerrarDoUsuario (q.getSessao ().getUsuarioId ());
        return Resposta.json (200, new Document ("message", "Novo e-mail confirmado. Entre novamente."));
    }
}
