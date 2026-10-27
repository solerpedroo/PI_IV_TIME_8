package br.com.agrogestao.core.domain.operacao;

import java.math.BigDecimal;
import org.bson.types.Decimal128;

public final class RegrasDados {
    private RegrasDados() {}

    public static <T> T obrigatorio(T valor, String campo) {
        if (valor == null) throw new IllegalArgumentException(campo + " é obrigatório");
        return valor;
    }

    public static String texto(String valor, String campo) {
        obrigatorio(valor, campo);
        if (valor.isBlank()) throw new IllegalArgumentException(campo + " é obrigatório");
        return valor.trim();
    }

    public static BigDecimal decimal(BigDecimal valor, String campo, boolean aceitaZero) {
        obrigatorio(valor, campo);
        if (valor.signum() < 0 || (!aceitaZero && valor.signum() == 0)) {
            throw new IllegalArgumentException(campo + " deve ser " + (aceitaZero ? "não negativo" : "positivo"));
        }
        // Garante que o valor possa ser representado exatamente pelo BSON Decimal128.
        new Decimal128(valor);
        return valor;
    }
}
