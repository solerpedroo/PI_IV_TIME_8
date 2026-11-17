// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.cultivo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.cultivo;

// Importa a aritmética decimal sem as aproximações de ponto flutuante binário.
import java.math.BigDecimal;
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

// Define o registro imutável Talhao com os identificadores e dados que serão validados no construtor.
public record Talhao(ObjectId id, EscopoPropriedade escopo, String nome, BigDecimal areaHectares, Status status) {
    // Define o conjunto fechado de valores aceitos:  ATIVO, INATIVO.
    public enum Status { ATIVO, INATIVO }

    // Valida os componentes de Talhao antes que uma instância imutável possa ser utilizada.
    public Talhao {
        // Exige o preenchimento de escopo antes de utilizar esse componente.
        obrigatorio(escopo, "escopo");
        // Exige nome não vazio e remove os espaços das extremidades.
        nome = texto(nome, "nome");
        // Valida areaHectares como decimal estritamente positivo, sem perder a precisão exigida pelo BSON.
        decimal(areaHectares, "areaHectares", false);
        // Exige o preenchimento de status antes de utilizar esse componente.
        obrigatorio(status, "status");
    }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos nome.
        Document d = escopo.documento().append("nome", nome)
                // Preenche no BSON os campos areaHectares, status e preserva os números decimais com Decimal128; persiste o enum pelo seu nome.
                .append("areaHectares", new Decimal128(areaHectares)).append("status", status.name());
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) d.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return d;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Talhao doDocumento(Document d) {
        // Reconstrói Talhao com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new Talhao(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"),
                // Lê areaHectares, status do BSON; converte o decimal para BigDecimal; recupera o enum pelo nome.
                d.get("areaHectares", Decimal128.class).bigDecimalValue(), Status.valueOf(d.getString("status")));
    }
}
