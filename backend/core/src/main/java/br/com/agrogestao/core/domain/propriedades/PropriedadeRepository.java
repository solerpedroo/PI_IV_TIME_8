// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.propriedades, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.propriedades;

// Importa a representação explícita de uma busca sem resultado.
import java.util.Optional;

// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;

// Define o contrato de consulta de propriedades, separando autorização da implementação de persistência.
public interface PropriedadeRepository {
    // Exige uma busca conjunta por propriedade e titular; Optional vazio representa ausência nesse escopo.
    Optional<Propriedade> buscarDoProprietario(ObjectId id, ObjectId proprietarioId);
}
