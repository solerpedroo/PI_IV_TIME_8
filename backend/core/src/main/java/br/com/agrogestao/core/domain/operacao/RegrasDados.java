// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa a representação decimal exata do BSON.
import org.bson.types.Decimal128;

// Agrupa as operações de RegrasDados nesta classe.
public final class RegrasDados {
    // Impede criar instâncias desta classe, pois suas operações são estáticas.
    private RegrasDados() {}

    // Valida genericamente a presença de um valor e preserva seu tipo no retorno.
    public static <T> T obrigatorio(T valor, String campo) {
        // Rejeita null identificando qual campo obrigatório faltou.
        if (valor == null) throw new IllegalArgumentException(campo + " é obrigatório");
        // Devolve o mesmo valor depois de garantir que ele não é null.
        return valor;
    }

    // Centraliza a validação de textos obrigatórios e a remoção de espaços nas extremidades.
    public static String texto(String valor, String campo) {
        // Primeiro rejeita null para que a verificação de texto vazio seja segura.
        obrigatorio(valor, campo);
        // Rejeita strings vazias ou compostas somente por espaços.
        if (valor.isBlank()) throw new IllegalArgumentException(campo + " é obrigatório");
        // Devolve o texto sem os espaços iniciais e finais.
        return valor.trim();
    }

    // Valida o sinal do decimal conforme a regra do campo e sua compatibilidade com BSON.
    public static BigDecimal decimal(BigDecimal valor, String campo, boolean aceitaZero) {
        // Rejeita a ausência do decimal antes de verificar o sinal.
        obrigatorio(valor, campo);
        // Rejeita negativos e também zero quando o campo exige quantidade estritamente positiva.
        if (valor.signum() < 0 || (!aceitaZero && valor.signum() == 0)) {
            // Explica a restrição de sinal correspondente ao parâmetro aceitaZero.
            throw new IllegalArgumentException(campo + " deve ser " + (aceitaZero ? "não negativo" : "positivo"));
        }
        // Garante que o valor possa ser representado exatamente pelo BSON Decimal128.
        // Tenta representar o número como Decimal128 para detectar valores incompatíveis com BSON.
        new Decimal128(valor);
        // Devolve o decimal original, sem arredondar nem modificar sua precisão.
        return valor;
    }
}
