/* Autor Principal: Pedro Henrique Contardi Soler - 25005592 */
package dados.operacao;

import java.util.List;
import org.bson.Document;

/*
    Contrato das ocorrências. A memória guarda mapas; o Mongo usa as coleções
    ocorrencias (o cadastro e o status) e ocorrencias_timeline (os eventos).

    O isolamento é o par usuarioId + propriedadeId. O registro e o evento da
    timeline são gravados juntos, inclusive na exclusão lógica: ou os dois
    confirmam, ou nenhum. salvar ainda exige que a versão lida seja a versão
    ainda armazenada (controle otimista).
*/
public interface RepositorioOcorrencias
{
    /* Todas as ocorrências do usuário nesta propriedade, excluídas inclusive. O filtro de lista fica no handler. */
    List<Document> listar (String usuarioId, String propriedadeId) throws Exception;

    /* Uma ocorrência deste usuário e desta fazenda. Fora do par, null. */
    Document buscar (String usuarioId, String propriedadeId, String id) throws Exception;

    /* Eventos da timeline em ordem crescente do campo ordem. */
    List<Document> timeline (String usuarioId, String propriedadeId, String id) throws Exception;

    /* Insere o cadastro (status ABERTA, versão 1) e o primeiro evento na mesma operação. */
    void criar (Document ocorrencia, Document evento) throws Exception;

    /*
        Troca o documento somente se a versão ainda for a de anterior e grava
        o evento novo. false significa que outra escrita passou na frente.
    */
    boolean salvar (Document anterior, Document nova, Document evento) throws Exception;

    /*
        Atualiza o registro com controle de versão sem inserir evento na timeline
        (ex.: anexo de foto #175, que não altera o texto do cadastro).
    */
    boolean substituirComVersao (Document anterior, Document nova) throws Exception;

    /* true se a propriedade já tem alguma ocorrência, usada para saber se a fazenda tem histórico. */
    boolean temRegistros (String propriedadeId) throws Exception;
}
