package algoritmos.conta;

import algoritmos.comum.Validacao;

import java.util.LinkedHashMap;
import protocolo.Resposta;
import protocolo.mensagens.conta.PedidoDeValidacaoDeCadastro;
import protocolo.mensagens.conta.PedidoDeValidacaoDeSenha;
import protocolo.mensagens.comum.RespostaDeValidacao;

// Validacoes de conta: criar conta e redefinir senha. Nao consulta nada: so olha os campos recebidos.
public class ValidadorDeConta
{
    // (DD) 9XXXX-XXXX: celular com nono digito. Fixo e numero sem mascara ficam para o if abaixo.
    public static final String FORMATO_DO_TELEFONE = "\\(\\d{2}\\) 9\\d{4}-\\d{4}";

    /*
        Campos do formulario de criar conta. Senha fraca nao gera segundo erro de confirmacao
        (confirmacao so compara quando a senha passou). Com erro, normalizados volta vazio:
        a API nao grava pedaco de cadastro.
    */
    public static Resposta validarCadastro (PedidoDeValidacaoDeCadastro pedido)
    {
        Validacao v = new Validacao ();

        String name     = v.texto ("name", pedido.getName (), "Informe seu nome.", 120);
        String email    = v.email ("email", pedido.getEmail ());
        String phone    = v.texto ("phone", pedido.getPhone (), "Informe seu telefone.", 20);
        String password = v.senha ("password", pedido.getPassword (), "Crie uma senha.");
        // Fazenda e área vão juntas. As duas ausentes é conta de quem só vai aceitar convite.
        // Uma sem a outra continua erro: o dono não grava fazenda pela metade.
        String farm = null;
        String area = null;
        if (!ValidadorDeConta.emBranco (pedido.getFarm ()) || !ValidadorDeConta.emBranco (pedido.getArea ()))
        {
            farm = v.texto ("farm", pedido.getFarm (), "Informe a propriedade.", 120);
            area = v.texto ("area", pedido.getArea (), "Informe a área.", 60);
        }
        v.confirmacao ("password-confirm", password, pedido.getPasswordConfirm ());
        v.marcado ("terms", pedido.isTerms (), "Aceite os termos para continuar.");

        if (phone != null && !phone.matches (ValidadorDeConta.FORMATO_DO_TELEFONE))
            v.erro ("phone", "Use o formato (DD) 9XXXX-XXXX.");

        LinkedHashMap<String, Object> normalizados = new LinkedHashMap<String, Object> ();

        if (!v.temErros ())
        {
            normalizados.put ("name",  name);
            normalizados.put ("email", email);
            normalizados.put ("phone", phone);
            if (farm != null)
            {
                normalizados.put ("farm", farm);
                normalizados.put ("area", area);
            }
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }

    private static boolean emBranco (String valor)
    {
        return valor == null || valor.isBlank ();
    }

    /*
        So a senha nova e a confirmacao. Nao devolve normalizados: a senha nao e reescrita,
        a API guarda o hash do valor original (com espacos, se houver).
    */
    public static Resposta validarSenha (PedidoDeValidacaoDeSenha pedido)
    {
        Validacao v = new Validacao ();

        String password = v.senha ("password", pedido.getPassword (), "Crie uma nova senha.");
        v.confirmacao ("password-confirm", password, pedido.getPasswordConfirm ());

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), new LinkedHashMap<String, Object> ());
    }
}
