package br.com.agrogestao.core.domain.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.cultivo.*;

public final class DadosCultivoTest {
    public static void main(String[] args) {
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        ObjectId talhaoId = new ObjectId();
        ObjectId culturaId = new ObjectId();
        Talhao talhao = new Talhao(talhaoId, escopo, " Talhão A ", new BigDecimal("12.75"), Talhao.Status.ATIVO);
        verificar(Talhao.doDocumento(talhao.paraDocumento()).equals(talhao), "área decimal e titular devem sobreviver ao BSON");
        Cultura cultura = new Cultura(culturaId, escopo, "Soja");
        verificar(Cultura.doDocumento(cultura.paraDocumento()).equals(cultura), "cultura deve preservar seu escopo");
        List<ObjectId> talhoes = new ArrayList<>(List.of(talhaoId));
        Safra safra = safra(escopo, culturaId, talhoes, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        talhoes.clear();
        verificar(safra.talhaoIds().size() == 1, "lista original não pode alterar uma safra já validada");
        verificar(Safra.doDocumento(safra.paraDocumento()).equals(safra), "período e vínculos devem sobreviver ao BSON");
        invalido(() -> new EscopoPropriedade(null, new ObjectId()));
        invalido(() -> new Talhao(null, escopo, "A", BigDecimal.ZERO, Talhao.Status.ATIVO));
        invalido(() -> new Talhao(null, escopo, " ", BigDecimal.ONE, Talhao.Status.ATIVO));
        invalido(() -> safra(escopo, culturaId, List.of(talhaoId), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        invalido(() -> safra(escopo, culturaId, List.of(), LocalDate.now(), LocalDate.now()));
        invalido(() -> safra(escopo, culturaId, List.of(talhaoId, talhaoId), LocalDate.now(), LocalDate.now()));
        System.out.println("DadosCultivoTest: OK");
    }

    private static Safra safra(EscopoPropriedade escopo, ObjectId culturaId, List<ObjectId> talhoes,
            LocalDate inicio, LocalDate fim) {
        return new Safra(new ObjectId(), escopo, "Soja 2026", culturaId, talhoes, inicio, fim,
                new BigDecimal("12.75"), Safra.Status.MANEJO);
    }

    public static void verificar(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }

    public static void invalido(Runnable acao) {
        try {
            acao.run();
            throw new AssertionError("a operação inválida deveria ter sido rejeitada");
        } catch (IllegalArgumentException esperado) { }
    }
}
