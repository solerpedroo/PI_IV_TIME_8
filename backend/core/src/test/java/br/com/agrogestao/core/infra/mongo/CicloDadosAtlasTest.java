package br.com.agrogestao.core.infra.mongo;

import java.math.BigDecimal;
import java.util.List;
import org.bson.types.ObjectId;
import br.com.agrogestao.core.domain.cultivo.*;
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

public final class CicloDadosAtlasTest {
    public static void main(String[] args) {
        try (CenarioDadosAtlas c = new CenarioDadosAtlas()) {
            c.preparar();
            validarCultivo(c);
        }
        System.out.println("CicloDadosAtlasTest: OK (#101)");
    }

    private static void validarCultivo(CenarioDadosAtlas c) {
        CultivoRepositorioMongo r = new CultivoRepositorioMongo(c.conexao.banco());
        invalido(() -> r.inserir(c.safra(c.a, c.culturaB, List.of(c.talhaoA))));
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(c.talhaoB))));
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(c.talhaoExtra))));
        invalido(() -> r.inserir(c.safra(c.a, new ObjectId(), List.of(c.talhaoA))));
        invalido(() -> r.inserir(c.safra(c.a, c.culturaA, List.of(new ObjectId()))));
        invalido(() -> r.inserir(new Cultura(null, new EscopoPropriedade(c.produtorB, c.propriedadeA), "Alheia")));
        invalido(() -> r.inserir(new Talhao(null, new EscopoPropriedade(c.produtorA, new ObjectId()),
                "Sem propriedade", BigDecimal.ONE, Talhao.Status.ATIVO)));
        c.reabrir();
        CultivoRepositorioMongo persistido = new CultivoRepositorioMongo(c.conexao.banco());
        verificar(persistido.buscarTalhao(c.a, c.talhaoA).orElseThrow().areaHectares().compareTo(new BigDecimal("12.75")) == 0,
                "área deve permanecer exata após reconectar");
        verificar(persistido.buscarCultura(c.a, c.culturaA).orElseThrow().nome().equals("Soja"), "cultura persistida");
        verificar(persistido.listarSafrasDoTalhao(c.a, c.talhaoA).size() == 1, "consulta do ciclo por talhão");
        verificar(persistido.buscarSafra(c.b, c.safraA).isEmpty(), "safra não pode vazar entre produtores");
        verificar(persistido.buscarTalhao(c.extra, c.talhaoA).isEmpty(), "talhão não pode vazar entre propriedades");
        verificar(persistido.buscarCultura(c.b, c.culturaA).isEmpty(), "cultura não pode vazar entre produtores");
    }
}
