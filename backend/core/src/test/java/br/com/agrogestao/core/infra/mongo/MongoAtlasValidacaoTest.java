// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.infra.mongo;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.MongoWriteException;

import br.com.agrogestao.core.domain.propriedades.AcessoPropriedade;
import br.com.agrogestao.core.domain.propriedades.Propriedade;
import br.com.agrogestao.core.domain.propriedades.PropriedadeNaoEncontradaException;
import br.com.agrogestao.core.domain.usuarios.Usuario;

/**
 * Validação manual contra o Atlas. Insere dados fictícios e os remove ao final.
 */
public final class MongoAtlasValidacaoTest {
    private MongoAtlasValidacaoTest() {}

    public static void main(String[] args) {
        ObjectId produtorAId = null;
        ObjectId produtorBId = null;
        ObjectId propriedadeAId = null;
        ObjectId propriedadeBId = null;
        String sufixo = new ObjectId().toHexString();

        try {
            try (MongoConexao primeiraConexao = MongoConexao.paraTestes()) {
                primeiraConexao.verificar();
                UsuarioRepositorioMongo usuarios = new UsuarioRepositorioMongo(primeiraConexao.banco());
                PropriedadeRepositorioMongo propriedades = new PropriedadeRepositorioMongo(primeiraConexao.banco());
                usuarios.criarIndices();
                propriedades.criarIndices();

                String emailA = "produtor-a-" + sufixo + "@teste.invalid";
                produtorAId = usuarios.inserir(new Usuario(emailA, "Produtor A", "hash-ficticio"));
                produtorBId = usuarios.inserir(new Usuario(
                        "produtor-b-" + sufixo + "@teste.invalid", "Produtor B", "hash-ficticio"));
                propriedadeAId = propriedades.inserir(new Propriedade(produtorAId, "Sítio Aurora " + sufixo));
                propriedadeBId = propriedades.inserir(new Propriedade(produtorBId, "Sítio Aurora " + sufixo));

                try {
                    usuarios.inserir(new Usuario(emailA, "Duplicado", "hash-ficticio"));
                    throw new AssertionError("email duplicado deve ser rejeitado pelo índice único");
                } catch (MongoWriteException esperado) {
                    verificar(esperado.getCode() == 11000, "falha deve ser de chave duplicada");
                }
                try {
                    propriedades.inserir(new Propriedade(produtorAId, "Sítio Aurora " + sufixo));
                    throw new AssertionError("nome duplicado para o mesmo titular deve ser rejeitado");
                } catch (MongoWriteException esperado) {
                    verificar(esperado.getCode() == 11000, "falha deve ser de chave duplicada");
                }

                AcessoPropriedade acesso = new AcessoPropriedade(propriedades);
                verificar(acesso.exigirDoProprietario(propriedadeAId, produtorAId).getId().equals(propriedadeAId),
                        "o produtor A deve ler sua propriedade");
                verificar(acesso.exigirDoProprietario(propriedadeBId, produtorBId).getId().equals(propriedadeBId),
                        "o produtor B deve ler sua propriedade");
                final ObjectId propriedadeDoA = propriedadeAId;
                final ObjectId produtorB = produtorBId;
                deveNegar(() -> acesso.exigirDoProprietario(propriedadeDoA, produtorB),
                        "o produtor B não pode ler a propriedade do produtor A");
                deveNegar(() -> acesso.exigirDoProprietario(propriedadeDoA, new ObjectId()),
                        "um produtor inexistente não pode ler propriedade");

                try {
                    propriedades.inserir(new Propriedade(new ObjectId(), "Referência inválida " + sufixo));
                    throw new AssertionError("propriedade com produtor inexistente deve ser rejeitada");
                } catch (IllegalArgumentException esperado) {
                    // Referência obrigatória e existente.
                }
            }

            try (MongoConexao segundaConexao = MongoConexao.paraTestes()) {
                PropriedadeRepositorioMongo propriedades = new PropriedadeRepositorioMongo(segundaConexao.banco());
                UsuarioRepositorioMongo usuarios = new UsuarioRepositorioMongo(segundaConexao.banco());
                verificar(usuarios.buscarPorEmail("produtor-a-" + sufixo + "@teste.invalid").isPresent(),
                        "o produtor deve continuar disponível após reabrir a conexão");
                verificar(propriedades.buscarDoProprietario(propriedadeAId, produtorAId).isPresent(),
                        "a propriedade deve continuar disponível após reabrir a conexão");
                verificar(propriedades.buscarDoProprietario(propriedadeAId, produtorBId).isEmpty(),
                        "a consulta persistida deve manter o filtro por titular");
            }
        } finally {
            limparDadosDeTeste(produtorAId, produtorBId, propriedadeAId, propriedadeBId);
        }

        System.out.println("MongoAtlasValidacaoTest: OK");
    }

    private static void limparDadosDeTeste(ObjectId produtorAId, ObjectId produtorBId,
            ObjectId propriedadeAId, ObjectId propriedadeBId) {
        try (MongoConexao conexao = MongoConexao.paraTestes()) {
            if (propriedadeAId != null) conexao.banco().getCollection("propriedades", Document.class)
                    .deleteOne(new Document("_id", propriedadeAId));
            if (propriedadeBId != null) conexao.banco().getCollection("propriedades", Document.class)
                    .deleteOne(new Document("_id", propriedadeBId));
            if (produtorAId != null) conexao.banco().getCollection("usuarios", Document.class)
                    .deleteOne(new Document("_id", produtorAId));
            if (produtorBId != null) conexao.banco().getCollection("usuarios", Document.class)
                    .deleteOne(new Document("_id", produtorBId));
        }
    }

    private static void deveNegar(Runnable acao, String mensagem) {
        try {
            acao.run();
            throw new AssertionError(mensagem);
        } catch (PropriedadeNaoEncontradaException esperado) {
            // Mesmo resultado para registro inexistente ou que pertence a outro produtor.
        }
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }
}
