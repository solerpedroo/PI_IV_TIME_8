/* Autor: Pedro Andre do Carmo Chavier - RA: 25018639 */
package dados;

import java.util.List;
import org.bson.Document;

/*
    Manutencoes preventivas programadas dos equipamentos, sempre filtradas pela propriedade (isolamento entre usuarios).
    Implementacoes: RepositorioPreventivasMongo e RepositorioPreventivasMemoria (testes e modo --memoria).
*/
public interface RepositorioPreventivas
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    // grava e preenche o _id
    public void criar (Document preventiva) throws Exception;

    // $set dos campos; false se a preventiva nao existir na propriedade
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    // false se a preventiva nao existir na propriedade
    public boolean excluir (String propriedadeId, String id) throws Exception;
}
