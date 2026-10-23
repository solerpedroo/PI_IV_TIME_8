/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import dados.ConexaoMongo;
import dados.RepositorioUsuariosMongo;
import com.mongodb.ConnectionString;
import org.bson.Document;

/* Integracao contra MongoDB real; exige banco cujo nome contenha _teste. */
public class TesteMongo
{
    private static void conferir (boolean condicao, String mensagem) throws Exception
    {
        if (!condicao)
            throw new Exception (mensagem);
    }

    public static void main (String[] args) throws Exception
    {
        String uri = System.getenv ("MONGODB_URI");
        String banco = uri == null ? null : new ConnectionString (uri).getDatabase ();
        if (banco == null || !banco.endsWith ("_teste"))
            throw new Exception ("Use MONGODB_URI com banco terminado em _teste");

        ConexaoMongo conexao = new ConexaoMongo (uri);
        try
        {
            conexao.getBanco ().drop ();
            RepositorioUsuariosMongo usuarios = new RepositorioUsuariosMongo (conexao);
            Document usuario = new Document ("email", "integracao@agro.com").append ("name", "Integração");
            Document propriedade = new Document ("farm", "Fazenda teste").append ("area", "1 ha");
            Document criado = usuarios.criar (usuario, propriedade);

            conferir (criado != null, "cadastro Mongo deveria criar o usuario");
            conferir (usuarios.buscarPropriedade (criado.getString ("propriedadeId")) != null,
                      "a propriedade deve existir junto com o usuario");
            Document segunda = usuarios.criarPropriedade (((org.bson.types.ObjectId) criado.get ("_id")).toHexString (),
                                                          new Document ("nome", "Fazenda dois"));
            conferir (segunda != null && usuarios.listarPropriedades (((org.bson.types.ObjectId) criado.get ("_id")).toHexString ()).size () == 2,
                      "CRUD Mongo deve listar a segunda propriedade do mesmo usuario");
            conferir (usuarios.atualizarPropriedade (((org.bson.types.ObjectId) criado.get ("_id")).toHexString (),
                                                     ((org.bson.types.ObjectId) segunda.get ("_id")).toHexString (), new Document ("nome", "Fazenda alterada")),
                      "CRUD Mongo deve atualizar propriedade do dono");
            conferir (usuarios.excluirPropriedade (((org.bson.types.ObjectId) criado.get ("_id")).toHexString (),
                                                   ((org.bson.types.ObjectId) segunda.get ("_id")).toHexString ()),
                      "CRUD Mongo deve excluir propriedade sem vinculos");
            conferir (usuarios.criar (usuario, propriedade) == null,
                      "indice unico deve rejeitar e-mail duplicado");
            conferir (conexao.getBanco ().getCollection ("usuarios").countDocuments () == 1,
                      "a transacao nao deve duplicar usuario");
            conferir (conexao.getBanco ().getCollection ("propriedades").countDocuments () == 1,
                      "a transacao nao deve deixar propriedade orfa");
            String uid = criado.getObjectId ("_id").toHexString ();
            String principal = criado.getString ("propriedadeId");
            conexao.getBanco ().getCollection ("propriedades").updateOne (
                new Document ("_id", new org.bson.types.ObjectId (principal)),
                new Document ("$unset", new Document ("usuarioId", "")));
            conferir (usuarios.listarPropriedades (uid).size () == 1,
                      "propriedade legada deve ser vinculada somente ao usuario comprovado");
            Document alternativa = usuarios.criarPropriedade (uid, new Document ("nome", "Alternativa"));
            conferir (usuarios.excluirPropriedade (uid, principal), "padrao sem vinculos pode ser excluida");
            conferir (usuarios.buscarPorId (uid).getString ("propriedadeId").equals (alternativa.getObjectId ("_id").toHexString ()),
                      "exclusao deve atualizar referencia padrao na mesma transacao");
            conferir (!usuarios.excluirPropriedade (uid, alternativa.getObjectId ("_id").toHexString ()),
                      "repositorio deve preservar ultima propriedade");
            System.out.println ("MongoDB: testes de cadastro, propriedades, legado e transacao ok");
        }
        finally
        {
            conexao.getBanco ().drop ();
            conexao.fechar ();
        }
    }
}
