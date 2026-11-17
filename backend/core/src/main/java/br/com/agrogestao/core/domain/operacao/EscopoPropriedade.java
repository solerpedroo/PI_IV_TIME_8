// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio;

/** Titular e propriedade devem acompanhar toda gravação e consulta operacional. */
// Define o registro imutável EscopoPropriedade com os identificadores e dados que serão validados no construtor.
public record EscopoPropriedade(ObjectId proprietarioId, ObjectId propriedadeId) {
    // Valida os componentes de EscopoPropriedade antes que uma instância imutável possa ser utilizada.
    public EscopoPropriedade {
        // Exige o preenchimento de proprietarioId antes de utilizar esse componente.
        obrigatorio(proprietarioId, "proprietarioId");
        // Exige o preenchimento de propriedadeId antes de utilizar esse componente.
        obrigatorio(propriedadeId, "propriedadeId");
    }

    // Produz os campos que identificam este escopo/contexto nas gravações e nos filtros do MongoDB.
    public Document documento() {
        // Reconstrói Document com os identificadores e valores recebidos para este cenário.
        return new Document("proprietarioId", proprietarioId).append("propriedadeId", propriedadeId);
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static EscopoPropriedade doDocumento(Document documento) {
        // Reconstrói EscopoPropriedade com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new EscopoPropriedade(documento.getObjectId("proprietarioId"), documento.getObjectId("propriedadeId"));
    }
}
