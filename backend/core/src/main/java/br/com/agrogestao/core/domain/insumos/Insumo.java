// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.insumos, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.insumos;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
// Importa a normalização textual independente do idioma do sistema.
import java.util.Locale;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa a representação decimal exata do BSON.
import org.bson.types.Decimal128;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.operacao.EscopoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

// Define o registro imutável Insumo com os identificadores e dados que serão validados no construtor.
public record Insumo(ObjectId id, EscopoPropriedade escopo, String nome, String categoria,
        // Define a unidade de medida do catálogo e o estoque mínimo decimal usado como referência.
        String unidade, BigDecimal estoqueMinimo) {
    // Valida os componentes de Insumo antes que uma instância imutável possa ser utilizada.
    public Insumo {
        // Exige o preenchimento de escopo antes de utilizar esse componente.
        obrigatorio(escopo, "escopo");
        // Exige nome não vazio e remove os espaços das extremidades.
        nome = texto(nome, "nome");
        // Exige categoria não vazio e remove os espaços das extremidades.
        categoria = texto(categoria, "categoria");
        // Exige unidade não vazio e remove os espaços das extremidades; normaliza também a unidade em minúsculas.
        unidade = texto(unidade, "unidade").toLowerCase(Locale.ROOT);
        // Valida estoqueMinimo como decimal não negativo, admitindo zero.
        decimal(estoqueMinimo, "estoqueMinimo", true);
    }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos nome, categoria.
        Document d = escopo.documento().append("nome", nome).append("categoria", categoria)
                // Preenche no BSON os campos unidade, estoqueMinimo e preserva os números decimais com Decimal128.
                .append("unidade", unidade).append("estoqueMinimo", new Decimal128(estoqueMinimo));
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) d.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return d;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Insumo doDocumento(Document d) {
        // Reconstrói Insumo com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new Insumo(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"),
                // Lê categoria, unidade, estoqueMinimo do BSON; converte o decimal para BigDecimal.
                d.getString("categoria"), d.getString("unidade"), d.get("estoqueMinimo", Decimal128.class).bigDecimalValue());
    }
}
