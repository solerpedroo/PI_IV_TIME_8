/*
    Autor: Tomas de Paula Michelon Toniato
    RA: 25004211
*/
package dados;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;

/*
    Orcamentos na colecao "orcamentos" do MongoDB.
    Indice unico (propriedadeId, safraId): a safra tem no maximo um orcamento, mesmo entre servidores.
*/
public class RepositorioOrcamentosMongo implements RepositorioOrcamentos
{
    private final MongoCollection<Document> orcamentos;

    public RepositorioOrcamentosMongo (MongoDatabase banco) throws Exception
    {
        if (banco == null)
            throw new Exception ("Banco ausente");

        this.orcamentos = banco.getCollection ("orcamentos");

        this.orcamentos.createIndex (Indexes.ascending ("propriedadeId", "safraId"),
                                     new IndexOptions ().unique (true));
    }

    private static Bson filtro (String propriedadeId, String safraId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        if (safraId == null)
            throw new Exception ("Safra ausente");

        return Filters.and (Filters.eq ("propriedadeId", propriedadeId), Filters.eq ("safraId", safraId));
    }

    public List<Document> listar (String propriedadeId) throws Exception
    {
        if (propriedadeId == null)
            throw new Exception ("Propriedade ausente");

        return this.orcamentos.find (Filters.eq ("propriedadeId", propriedadeId))
                              .sort (Sorts.ascending ("_id"))
                              .into (new ArrayList<Document> ());
    }

    public Document buscarPorSafra (String propriedadeId, String safraId) throws Exception
    {
        return this.orcamentos.find (RepositorioOrcamentosMongo.filtro (propriedadeId, safraId)).first ();
    }

    // upsert: grava os campos e, so na criacao, o criadoEm
    private UpdateResult gravar (Bson filtro, Document orcamento)
    {
        Document campos   = new Document (orcamento);
        Object   criadoEm = campos.remove ("criadoEm");

        campos.remove ("_id");

        Document alteracao = new Document ("$set", campos);
        if (criadoEm != null)
            alteracao.append ("$setOnInsert", new Document ("criadoEm", criadoEm));

        return this.orcamentos.updateOne (filtro, alteracao, new UpdateOptions ().upsert (true));
    }

    public boolean salvar (Document orcamento) throws Exception
    {
        if (orcamento == null)
            throw new Exception ("Orcamento ausente");

        Bson         filtro = RepositorioOrcamentosMongo.filtro (orcamento.getString ("propriedadeId"),
                                                                 orcamento.getString ("safraId"));
        UpdateResult resultado;

        try
        {
            resultado = this.gravar (filtro, orcamento);
        }
        catch (MongoWriteException erro)
        {
            // dois upserts simultaneos: o indice unico barra o segundo, que vira alteracao
            if (erro.getError ().getCategory () != ErrorCategory.DUPLICATE_KEY)
                throw erro;

            resultado = this.gravar (filtro, orcamento);
        }

        boolean ret = resultado.getUpsertedId () != null;

        orcamento.put ("_id", ret ? resultado.getUpsertedId ().asObjectId ().getValue ()
                                  : this.buscarPorSafra (orcamento.getString ("propriedadeId"),
                                                         orcamento.getString ("safraId")).get ("_id"));

        return ret;
    }

    public boolean excluir (String propriedadeId, String safraId) throws Exception
    {
        return this.orcamentos.deleteOne (RepositorioOrcamentosMongo.filtro (propriedadeId, safraId)).getDeletedCount () == 1;
    }
}
