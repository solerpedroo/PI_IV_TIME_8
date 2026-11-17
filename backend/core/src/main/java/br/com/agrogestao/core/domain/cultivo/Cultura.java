// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.cultivo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.cultivo;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa br.com.agrogestao.core.domain.operacao.EscopoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.*, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.*;

/** Catálogo da propriedade; a safra referencia a cultura sem sobrescrever ciclos anteriores. */
// Define o registro imutável Cultura com os identificadores e dados que serão validados no construtor.
public record Cultura(ObjectId id, EscopoPropriedade escopo, String nome) {
    // Valida os componentes de Cultura antes que uma instância imutável possa ser utilizada.
    public Cultura {
        // Exige o preenchimento de escopo antes de utilizar esse componente.
        obrigatorio(escopo, "escopo");
        // Exige nome não vazio e remove os espaços das extremidades.
        nome = texto(nome, "nome");
    }

    // Converte o objeto de domínio em documento BSON, preservando os tipos exigidos na persistência.
    public Document paraDocumento() {
        // Preenche no BSON os campos nome.
        Document d = escopo.documento().append("nome", nome);
        // Inclui _id somente quando já existe identificador; caso contrário, a inserção poderá gerá-lo.
        if (id != null) d.put("_id", id);
        // Entrega o documento BSON completo ao chamador para persistência ou comparação.
        return d;
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static Cultura doDocumento(Document d) {
        // Reconstrói Cultura com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new Cultura(d.getObjectId("_id"), EscopoPropriedade.doDocumento(d), d.getString("nome"));
    }
}
