// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.insumos.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.insumos.*;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

// Agrupa as verificações de DadosInsumo nesta classe.
public final class DadosInsumoTest {
    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Define titular e propriedade que serão usados neste cenário de teste.
        EscopoPropriedade escopo = new EscopoPropriedade(new ObjectId(), new ObjectId());
        // Cria insumo com unidade em maiúsculas e espaços para verificar sua normalização e mínimo decimal.
        Insumo i = new Insumo(new ObjectId(), escopo, "NPK", "Fertilizante", " KG ", new BigDecimal("10.25"));
        // Confere a expectativa do teste: catálogo e mínimo decimal preservados.
        verificar(Insumo.doDocumento(i.paraDocumento()).equals(i), "catálogo e mínimo decimal preservados");
        // Representa entrada de 500 unidades sem uma atividade consumidora.
        MovimentacaoInsumo entrada = movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.ENTRADA, "500", null);
        // Representa saída de 50 unidades vinculada a uma atividade.
        MovimentacaoInsumo saida = movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.SAIDA, "50", new ObjectId());
        // Representa ajuste negativo de 0,25 unidade, permitido como diferença de inventário.
        MovimentacaoInsumo ajuste = movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.AJUSTE, "-0.25", null);
        // Confere a expectativa do teste: consumo e vínculo preservados.
        verificar(MovimentacaoInsumo.doDocumento(saida.paraDocumento()).equals(saida), "consumo e vínculo preservados");
        // Confere a expectativa do teste: exemplo oficial 500 - 50.
        verificar(entrada.variacao().add(saida.variacao()).compareTo(new BigDecimal("450")) == 0, "exemplo oficial 500 - 50");
        // Confere a expectativa do teste: ajuste representa diferença assinada.
        verificar(ajuste.variacao().compareTo(new BigDecimal("-0.25")) == 0, "ajuste representa diferença assinada");
        // Confere que o estoque mínimo do catálogo não pode ser negativo.
        invalido(() -> new Insumo(null, escopo, "NPK", "Fertilizante", "kg", new BigDecimal("-1")));
        // Confere que a quantidade de uma saída deve ser positiva; o sinal é aplicado pela operação.
        invalido(() -> movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.SAIDA, "-1", null));
        // Confere que movimentações de entrada com quantidade zero são rejeitadas.
        invalido(() -> movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.ENTRADA, "0", null));
        // Confere que uma atividade consumidora não pode acompanhar uma entrada de estoque.
        invalido(() -> movimento(escopo, i.id(), MovimentacaoInsumo.Tipo.ENTRADA, "1", new ObjectId()));
        // Exibe o resultado desta etapa no console: DadosInsumoTest: OK.
        System.out.println("DadosInsumoTest: OK");
    }

    // Constrói uma movimentação fictícia com quantidade decimal e referências escolhidas pelo teste.
    private static MovimentacaoInsumo movimento(EscopoPropriedade e, ObjectId i, MovimentacaoInsumo.Tipo t, String q, ObjectId a) {
        // Reconstrói MovimentacaoInsumo com os identificadores e valores recebidos para este cenário.
        return new MovimentacaoInsumo(new ObjectId(), e, i, t, new BigDecimal(q), "kg", a, Instant.now(), "Teste fictício");
    }
}
