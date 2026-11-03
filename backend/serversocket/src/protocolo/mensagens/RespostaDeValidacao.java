package protocolo.mensagens;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import protocolo.Resposta;

/*
    Resultado de qualquer validacao: valido, ou o primeiro erro de cada campo.
    Quando valido, "normalizados" traz os valores prontos para gravar (texto sem espacos nas pontas,
    e-mail em minusculas, status padrao...). As chaves dos erros sao os nomes dos campos do front.
*/
public class RespostaDeValidacao extends Resposta
{
    private static final long serialVersionUID = 1L;

    private final Map<String, String> erros;
    private final Map<String, Object> normalizados;

    public RespostaDeValidacao (long idDoPedido, Map<String, String> erros, Map<String, Object> normalizados)
    {
        super (idDoPedido);

        this.erros        = new LinkedHashMap<String, String> (erros);
        this.normalizados = new LinkedHashMap<String, Object> (normalizados);
    }

    public boolean isValido ()
    {
        return this.erros.isEmpty ();
    }

    public Map<String, String> getErros ()
    {
        return Collections.unmodifiableMap (this.erros);
    }

    public Map<String, Object> getNormalizados ()
    {
        return Collections.unmodifiableMap (this.normalizados);
    }
}
