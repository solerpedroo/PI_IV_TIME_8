// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa as datas de calendário sem horário ou fuso.
import java.time.LocalDate;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.custos.Custo para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.custos.Custo;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.DadosCultivoTest.*;

// Agrupa as verificações de DadosCusto nesta classe.
public final class DadosCustoTest {
    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Define o escopo e os vínculos de talhão e safra que serão usados neste cenário de teste.
        ContextoOperacao contexto = new ContextoOperacao(new EscopoPropriedade(new ObjectId(), new ObjectId()), null, null);
        // Prepara um custo de dez centavos para testar a aritmética e a persistência exatas.
        Custo custo = custo(contexto, "0.10");
        // Confere a expectativa do teste: centavos e referências preservados no BSON.
        verificar(Custo.doDocumento(custo.paraDocumento()).equals(custo), "centavos e referências preservados no BSON");
        // Confere a expectativa do teste: soma monetária deve ser exata.
        verificar(custo.valor().add(custo(contexto, "0.20").valor()).compareTo(new BigDecimal("0.30")) == 0,
                // Identifica a expectativa deste caso: soma monetária deve ser exata.
                "soma monetária deve ser exata");
        // Confere a expectativa do teste: zeros adicionais podem ser normalizados.
        verificar(custo(contexto, "1.000").valor().equals(new BigDecimal("1.00")), "zeros adicionais podem ser normalizados");
        // Confere que uma fração de centavo não é arredondada nem aceita como custo em BRL.
        invalido(() -> custo(contexto, "0.001"));
        // Confere que um valor monetário negativo é rejeitado.
        invalido(() -> custo(contexto, "-1"));
        // Confere que um custo precisa ter valor maior que zero.
        invalido(() -> custo(contexto, "0"));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> new Custo(null, new ContextoOperacao(contexto.escopo(), new ObjectId(), new ObjectId()),
                // Omite a cultura mesmo informando safra para testar a referência obrigatória.
                null, null, null, Custo.Categoria.INSUMOS, "Sem cultura da safra", LocalDate.now(), BigDecimal.ONE));
        // Confere que referências inexistentes são rejeitadas em vez de produzir vínculos órfãos.
        invalido(() -> new Custo(null, contexto, null, null, new ObjectId(), Custo.Categoria.INSUMOS,
                // Informa consumo sem atividade consumidora para comprovar a rejeição desse vínculo incompleto.
                "Sem atividade do consumo", LocalDate.now(), BigDecimal.ONE));
        // Exibe o resultado desta etapa no console: DadosCustoTest: OK.
        System.out.println("DadosCustoTest: OK");
    }

    // Constrói um custo fictício para verificar centavos e vínculos com safra, atividade e consumo.
    private static Custo custo(ContextoOperacao contexto, String valor) {
        // Reconstrói Custo com os identificadores e valores recebidos para este cenário.
        return new Custo(new ObjectId(), contexto, null, null, null, Custo.Categoria.MANUTENCAO,
                // Completa o custo fictício com descrição, data conhecida e conversão decimal do valor recebido.
                "Manutenção fictícia", LocalDate.of(2026, 10, 23), new BigDecimal(valor));
    }
}
