package dados;

import java.util.List;
import org.bson.Document;

/*
    Talhoes, sempre filtrados pela propriedade (isolamento entre usuarios).
    O campo "nomeNormalizado" (nome em minusculas) garante nome unico por propriedade.
    Implementacoes: RepositorioTalhoesMongo e RepositorioTalhoesMemoria (testes).
*/
public interface RepositorioTalhoes
{
    // em ordem de cadastro
    public List<Document> listar (String propriedadeId) throws Exception;

    public Document buscarPorId (String propriedadeId, String id) throws Exception;

    // grava e preenche o _id; false se o nome ja existir na propriedade
    public boolean criar (Document talhao) throws Exception;

    // $set dos campos; false se o novo nome ja existir em outro talhao da propriedade
    public boolean atualizar (String propriedadeId, String id, Document campos) throws Exception;

    // Eventos de atividade, ocorrencia e custo permanecem vinculados ao talhao.
    public void criarHistorico (String propriedadeId, String talhaoId, Document evento) throws Exception;
    public List<Document> listarHistorico (String propriedadeId, String talhaoId) throws Exception;
}
