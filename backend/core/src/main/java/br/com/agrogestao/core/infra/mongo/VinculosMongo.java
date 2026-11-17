// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa a seleção das coleções no banco compartilhado.
import com.mongodb.client.MongoDatabase;
// Importa br.com.agrogestao.core.domain.operacao.EscopoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.EscopoPropriedade;
// Importa br.com.agrogestao.core.domain.operacao.ContextoOperacao para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.ContextoOperacao;
// Disponibiliza diretamente os utilitários de br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio, usados nas validações ou consultas abaixo.
import static br.com.agrogestao.core.domain.operacao.RegrasDados.obrigatorio;

/** Validação contextual: IDs existentes em outra propriedade também são referências inválidas. */
// Agrupa as operações de VinculosMongo nesta classe.
final class VinculosMongo {
    // Mantém banco compartilhado pelos repositórios como dependência ou dado imutável.
    private final MongoDatabase banco;

    // Recebe o banco compartilhado pelos repositórios que validarão as referências.
    VinculosMongo(MongoDatabase banco) { this.banco = banco; }

    // Confere a existência da propriedade para o titular do escopo.
    void exigirPropriedade(EscopoPropriedade escopo) {
        // Exige o preenchimento de escopo antes de utilizar esse componente.
        obrigatorio(escopo, "escopo");
        // Usa o repositório de propriedades para conferir o titular do escopo informado.
        if (new PropriedadeRepositorioMongo(banco)
                // Procura a propriedade junto do proprietário e considera inválida a ausência desse vínculo.
                .buscarDoProprietario(escopo.propriedadeId(), escopo.proprietarioId()).isEmpty()) {
            // Rejeita propriedade inexistente ou pertencente a outro titular.
            throw new IllegalArgumentException("propriedade não encontrada para o titular");
        }
    }

    // Valida propriedade, talhão e safra e a relação entre eles.
    void exigirContexto(ContextoOperacao contexto) {
        // Antes de validar talhão e safra, confirma a propriedade e seu titular.
        exigirPropriedade(contexto.escopo());
        // Quando existe talhão no contexto, exige que ele pertença à mesma propriedade e titular.
        if (contexto.talhaoId() != null) exigir("talhoes", contexto.escopo(), contexto.talhaoId());
        // Executa a conferência adicional somente se o contexto informa uma safra.
        if (contexto.safraId() != null) {
            // Carrega a safra com o mesmo filtro de isolamento usado nas outras referências.
            Document safra = exigir("safras", contexto.escopo(), contexto.safraId());
            // Confere se a lista de talhões da safra contém o talhão indicado pelo contexto.
            if (!safra.getList("talhaoIds", ObjectId.class).contains(contexto.talhaoId())) {
                // Impede associar uma operação a um talhão que não participa daquela safra.
                throw new IllegalArgumentException("talhão não pertence à safra informada");
            }
        }
    }

    // Monta um filtro que inclui titular, propriedade e identificador do registro.
    Document filtro(EscopoPropriedade escopo, ObjectId id) {
        // Combina titular e propriedade com o id obrigatório do registro consultado.
        return escopo.documento().append("_id", obrigatorio(id, "id"));
    }

    // Exige a existência de uma referência na coleção e no escopo solicitados.
    Document exigir(String colecao, EscopoPropriedade escopo, ObjectId id) {
        // Busca o documento usando todos os identificadores do escopo, não apenas o id isolado.
        Document d = banco.getCollection(colecao).find(filtro(escopo, id)).first();
        // Rejeita referência ausente no escopo mesmo que o mesmo id exista em outra propriedade.
        if (d == null) throw new IllegalArgumentException("referência não encontrada no escopo: " + colecao);
        // Entrega o documento encontrado para que o chamador confira as relações adicionais.
        return d;
    }
}
