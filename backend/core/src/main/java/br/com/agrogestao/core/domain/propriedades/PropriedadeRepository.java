// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.domain.propriedades;

import java.util.Optional;

import org.bson.types.ObjectId;

public interface PropriedadeRepository {
    Optional<Propriedade> buscarDoProprietario(ObjectId id, ObjectId proprietarioId);
}
