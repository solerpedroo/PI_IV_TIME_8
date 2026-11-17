// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.custos, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.custos;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa a política que impede arredondamento silencioso de valores monetários.
import java.math.RoundingMode;
// Importa as datas de calendário sem horário ou fuso.
import java.time.LocalDate;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa a representação decimal exata do BSON.
import org.bson.types.Decimal128;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.operacao.ContextoOperacao para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.ContextoOperacao;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

// Define o registro imutável Custo com os identificadores e dados que serão validados no construtor.
public record Custo(ObjectId id, ContextoOperacao contexto, ObjectId culturaId, ObjectId atividadeId,
        // Completa a origem do gasto com consumo, categoria, descrição, data e valor decimal.
        ObjectId consumoId, Categoria categoria, String descricao, LocalDate data, BigDecimal valor) {
    // Define as categorias aceitas para classificar os gastos da propriedade.
    public enum Categoria { INSUMOS, COMBUSTIVEL, MANUTENCAO, MAO_DE_OBRA, OUTROS }

    // Valida os componentes de Custo antes que uma instância imutável possa ser utilizada.
    public Custo {
        // Exige o preenchimento de contexto antes de utilizar esse componente.
        obrigatorio(contexto, "contexto");
        // Se o custo pertence a uma safra, exige a cultura correspondente para manter a rastreabilidade.
        if (contexto.safraId() != null) obrigatorio(culturaId, "culturaId da safra");
        // Um custo vinculado ao consumo de insumo precisa identificar a atividade consumidora.
        if (consumoId != null) obrigatorio(atividadeId, "atividadeId do consumo");
        // Exige o preenchimento de categoria antes de utilizar esse componente.
        obrigatorio(categoria, "categoria");
        // Exige descricao não vazio e remove os espaços das extremidades.
        descricao = texto(descricao, "descricao");
        // Exige o preenchimento de data antes de utilizar esse componente.
        obrigatorio(data, "data");
        // Valida valor como decimal estritamente positivo, sem perder a precisão exigida pelo BSON.
        decimal(valor, "valor", false);
        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Ajusta a escala para dois centavos decimais sem permitir arredondamento do valor.
            valor = valor.setScale(2, RoundingMode.UNNECESSARY);
        // Intercepta a perda de precisão que ocorreria ao reduzir o valor a duas casas.
        } catch (ArithmeticException e) {
            // Rejeita valores que exigiriam frações de centavo em vez de arredondá-los silenciosamente.
            throw new IllegalArgumentException("valor em BRL deve representar centavos exatos");
        }
        // Verifica também se o valor cabe na representação decimal exata usada pelo MongoDB.
        new Decimal128(valor);
    }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos culturaId, atividadeId.
        Document d = contexto.documento().append("culturaId", culturaId).append("atividadeId", atividadeId)
                // Preenche no BSON os campos consumoId, categoria, descricao e persiste o enum pelo seu nome.
                .append("consumoId", consumoId).append("categoria", categoria.name()).append("descricao", descricao)
                // Preenche no BSON os campos data, valor, moeda e preserva os números decimais com Decimal128; representa a data ou o horário como texto ISO.
                .append("data", data.toString()).append("valor", new Decimal128(valor)).append("moeda", "BRL");
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) d.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return d;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Custo doDocumento(Document d) {
        // Recusa documentos em outra moeda, pois este modelo representa exclusivamente valores em BRL.
        if (!"BRL".equals(d.getString("moeda"))) throw new IllegalArgumentException("moeda não suportada");
        // Reconstrói Custo com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new Custo(d.getObjectId("_id"), ContextoOperacao.doDocumento(d), d.getObjectId("culturaId"),
                // Lê atividadeId, consumoId, categoria do BSON; recupera o enum pelo nome.
                d.getObjectId("atividadeId"), d.getObjectId("consumoId"), Categoria.valueOf(d.getString("categoria")),
                // Lê descricao, data, valor do BSON; converte o decimal para BigDecimal; interpreta data/horário ISO.
                d.getString("descricao"), LocalDate.parse(d.getString("data")), d.get("valor", Decimal128.class).bigDecimalValue());
    }
}
