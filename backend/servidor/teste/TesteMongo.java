/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */

import dados.ConexaoMongo;
import dados.RepositorioUsuariosMongo;
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
        if (uri == null || !uri.contains ("_teste"))
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
            conferir (usuarios.criar (usuario, propriedade) == null,
                      "indice unico deve rejeitar e-mail duplicado");
            conferir (conexao.getBanco ().getCollection ("usuarios").countDocuments () == 1,
                      "a transacao nao deve duplicar usuario");
            conferir (conexao.getBanco ().getCollection ("propriedades").countDocuments () == 1,
                      "a transacao nao deve deixar propriedade orfa");
            System.out.println ("MongoDB: 5/5 testes ok");
        }
        finally
        {
            conexao.getBanco ().drop ();
            conexao.fechar ();
        }
    }
}
