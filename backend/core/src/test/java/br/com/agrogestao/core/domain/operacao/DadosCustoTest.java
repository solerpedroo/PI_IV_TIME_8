package br.com.agrogestao.core.domain.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.custos.Custo;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

public final class DadosCustoTest {
    public static void main(String[] args) {
        ContextoOperacao contexto = new ContextoOperacao(new EscopoPropriedade(new ObjectId(), new ObjectId()), null, null);
        Custo custo = custo(contexto, "0.10");
        verificar(Custo.doDocumento(custo.paraDocumento()).equals(custo), "centavos e referências preservados no BSON");
        verificar(custo.valor().add(custo(contexto, "0.20").valor()).compareTo(new BigDecimal("0.30")) == 0,
                "soma monetária deve ser exata");
        verificar(custo(contexto, "1.000").valor().equals(new BigDecimal("1.00")), "zeros adicionais podem ser normalizados");
        invalido(() -> custo(contexto, "0.001"));
        invalido(() -> custo(contexto, "-1"));
        invalido(() -> custo(contexto, "0"));
        invalido(() -> new Custo(null, new ContextoOperacao(contexto.escopo(), new ObjectId(), new ObjectId()),
                null, null, null, Custo.Categoria.INSUMOS, "Sem cultura da safra", LocalDate.now(), BigDecimal.ONE));
        invalido(() -> new Custo(null, contexto, null, null, new ObjectId(), Custo.Categoria.INSUMOS,
                "Sem atividade do consumo", LocalDate.now(), BigDecimal.ONE));
        System.out.println("DadosCustoTest: OK");
    }

    private static Custo custo(ContextoOperacao contexto, String valor) {
        return new Custo(new ObjectId(), contexto, null, null, null, Custo.Categoria.MANUTENCAO,
                "Manutenção fictícia", LocalDate.of(2026, 10, 23), new BigDecimal(valor));
    }
}
