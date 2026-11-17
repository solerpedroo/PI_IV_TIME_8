// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.operacao, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.operacao;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio;

/** O talhão e a safra são opcionais para registros gerais da propriedade. */
// Define o registro imutável ContextoOperacao com os identificadores e dados que serão validados no construtor.
public record ContextoOperacao(EscopoPropriedade escopo, ObjectId talhaoId, ObjectId safraId) {
    // Valida os componentes de ContextoOperacao antes que uma instância imutável possa ser utilizada.
    public ContextoOperacao {
        // Exige o preenchimento de escopo antes de utilizar esse componente.
        obrigatorio(escopo, "escopo");
        // Exige um talhão quando há safra; registros gerais podem omitir ambos os identificadores.
        if (safraId != null && talhaoId == null) throw new IllegalArgumentException("safra exige talhaoId");
    }

    // Produz os campos que identificam este escopo/contexto nas gravações e nos filtros do MongoDB.
    public Document documento() {
        // Preenche no BSON os campos talhaoId, safraId.
        return escopo.documento().append("talhaoId", talhaoId).append("safraId", safraId);
    }

    // Reconstrói o objeto de domínio a partir do BSON, passando novamente pelas validações do construtor.
    public static ContextoOperacao doDocumento(Document d) {
        // Reconstrói ContextoOperacao com os campos lidos do documento BSON, submetendo-os às validações do modelo.
        return new ContextoOperacao(EscopoPropriedade.doDocumento(d), d.getObjectId("talhaoId"), d.getObjectId("safraId"));
    }
}
