package algoritmos;

import java.util.LinkedHashMap;
import protocolo.Resposta;
import protocolo.mensagens.PedidoDeValidacaoDeCadastro;
import protocolo.mensagens.PedidoDeValidacaoDeSenha;
import protocolo.mensagens.RespostaDeValidacao;

// Validacoes de conta: criar conta e redefinir senha. Nao consulta nada: so olha os campos recebidos.
public class ValidadorDeConta
{
    public static final String FORMATO_DO_TELEFONE = "\\(\\d{2}\\) 9\\d{4}-\\d{4}";

    public static Resposta validarCadastro (PedidoDeValidacaoDeCadastro pedido)
    {
        Validacao v = new Validacao ();

        String name     = v.texto ("name", pedido.getName (), "Informe seu nome.", 120);
        String email    = v.email ("email", pedido.getEmail ());
        String phone    = v.texto ("phone", pedido.getPhone (), "Informe seu telefone.", 20);
        String password = v.senha ("password", pedido.getPassword (), "Crie uma senha.");
        String farm     = v.texto ("farm", pedido.getFarm (), "Informe a propriedade.", 120);
        String area     = v.texto ("area", pedido.getArea (), "Informe a área.", 60);
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
            normalizados.put ("farm",  farm);
            normalizados.put ("area",  area);
        }

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), normalizados);
    }

    public static Resposta validarSenha (PedidoDeValidacaoDeSenha pedido)
    {
        Validacao v = new Validacao ();

        String password = v.senha ("password", pedido.getPassword (), "Crie uma nova senha.");
        v.confirmacao ("password-confirm", password, pedido.getPasswordConfirm ());

        return new RespostaDeValidacao (pedido.getId (), v.getErros (), new LinkedHashMap<String, Object> ());
    }
}
