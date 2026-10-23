package br.com.agrogestao.core.domain.operacao;

import java.math.BigDecimal;
import java.time.Instant;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.insumos.*;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

public final class DadosInsumoTest {
    public static void main(String[] args) {
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        Insumo i = new Insumo(new ObjectId(), escopo, "NPK", "Fertilizante", " KG ", new BigDecimal("10.25"));
        verificar(Insumo.doDocumento(i.paraDocumento()).equals(i), "catálogo e mínimo decimal preservados");
        MovimentacaoInsumo entrada = movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.ENTRADA, "500", null);
        MovimentacaoInsumo saida = movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.SAIDA, "50", new ObjectId());
        MovimentacaoInsumo ajuste = movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.AJUSTE, "-0.25", null);
        verificar(MovimentacaoInsumo.doDocumento(saida.paraDocumento()).equals(saida), "consumo e vínculo preservados");
        verificar(entrada.variacao().add(saida.variacao()).compareTo(new BigDecimal("450")) == 0, "exemplo oficial 500 - 50");
        verificar(ajuste.variacao().compareTo(new BigDecimal("-0.25")) == 0, "ajuste representa diferença assinada");
        invalido(() -> new Insumo(null, escopo, "NPK", "Fertilizante", "kg", new BigDecimal("-1")));
        invalido(() -> movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.SAIDA, "-1", null));
        invalido(() -> movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.ENTRADA, "0", null));
        invalido(() -> movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.ENTRADA, "1", new ObjectId()));
        System.out.println("DadosInsumoTest: OK");
    }

    private static MovimentacaoInsumo movimento(EscopoPropriedade e, ObjectId i, MovimentacaoInsumo.Tipo t, String q, ObjectId a) {
        return new MovimentacaoInsumo(new ObjectId(), e, i, t, new BigDecimal(q), "kg", a, Instant.now(), "Teste fictício");
    }
}
