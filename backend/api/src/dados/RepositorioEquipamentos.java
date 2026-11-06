/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package dados;

import java.util.List;
import org.bson.Document;

/*
    Equipamentos agricolas, sempre filtrados pela propriedade (isolamento entre usuarios).
    O campo "nomeNormalizado" (nome em minusculas) garante nome unico por propriedade.
    Sem exclusao nesta entrega: o equipamento sai de uso com status "Inativo" (o historico fica).
    Implementacoes: RepositorioEquipamentosMongo e RepositorioEquipamentosMemoria (testes e modo --memoria).
*/
public interface RepositorioEquipamentos
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    // grava e preenche o _id; false se o nome ja existir na propriedade
    public boolean criar (Document equipamento) throws Exception;

    // $set dos campos; false se o novo nome ja existir em outro equipamento da propriedade
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;
}
