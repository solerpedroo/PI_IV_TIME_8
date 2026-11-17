// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;

// Importa a identificação de falhas de escrita, inclusive chaves duplicadas.
import com.mongodb.MongoWriteException;

// Importa br.com.agrogestao.core.domain.propriedades.AcessoPropriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.propriedades.AcessoPropriedade;
// Importa br.com.agrogestao.core.domain.propriedades.Propriedade para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.propriedades.Propriedade;
// Importa br.com.agrogestao.core.domain.propriedades.PropriedadeNaoEncontradaException para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.propriedades.PropriedadeNaoEncontradaException;
// Importa br.com.agrogestao.core.domain.usuarios.Usuario para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.usuarios.Usuario;

/**
 * Validação manual contra o Atlas. Insere dados fictícios e os remove ao final.
 */
// Agrupa as verificações de MongoAtlasValidacao nesta classe.
public final class MongoAtlasValidacaoTest {
    // Impede criar instâncias desta classe, pois suas operações são estáticas.
    private MongoAtlasValidacaoTest() {}

    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Reserva o id do produtor A para que a limpeza possa localizar apenas seus dados.
        ObjectId produtorAId = null;
        // Reserva o id do produtor B para os testes de isolamento e a limpeza final.
        ObjectId produtorBId = null;
        // Reserva o id da propriedade de A para removê-la ao finalizar a execução.
        ObjectId propriedadeAId = null;
        // Reserva o id da propriedade de B para removê-la ao finalizar a execução.
        ObjectId propriedadeBId = null;
        // Gera sufixo exclusivo para os emails e nomes fictícios desta execução.
        String sufixo = new ObjectId().toHexString();

        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Abre o recurso para esta operação e garante seu fechamento ao sair do bloco, inclusive em caso de falha.
            try (MongoConexao primeiraConexao = MongoConexao.paraTestes()) {
                // Executa ping no banco antes de continuar o cenário de persistência.
                primeiraConexao.verificar();
                // Cria o repositório UsuarioRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
                UsuarioRepositorioMongo usuarios = new UsuarioRepositorioMongo(primeiraConexao.banco());
                // Cria o repositório PropriedadeRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
                PropriedadeRepositorioMongo propriedades = new PropriedadeRepositorioMongo(primeiraConexao.banco());
                // Garante os índices de usuarios antes das gravações ou consultas do cenário.
                usuarios.criarIndices();
                // Garante os índices de propriedades antes das gravações ou consultas do cenário.
                propriedades.criarIndices();

                // Monta email único de A para não conflitar com outra execução da suíte.
                String emailA = "produtor-a-" + sufixo + "@teste.invalid";
                // Grava a primeira conta fictícia e guarda o identificador gerado.
                produtorAId = usuarios.inserir(new Usuario(emailA, "Produtor A", "hash-ficticio"));
                // Grava a segunda conta fictícia para testar isolamento entre titulares.
                produtorBId = usuarios.inserir(new Usuario(
                        // Usa email exclusivo de B e hash fictício, sem credencial real.
                        "produtor-b-" + sufixo + "@teste.invalid", "Produtor B", "hash-ficticio"));
                // Grava a propriedade de A com vínculo explícito ao titular criado.
                propriedadeAId = propriedades.inserir(new Propriedade(produtorAId, "Sítio Aurora " + sufixo));
                // Usa o mesmo nome sob B para comprovar que a unicidade do nome é por titular.
                propriedadeBId = propriedades.inserir(new Propriedade(produtorBId, "Sítio Aurora " + sufixo));

                // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
                try {
                    // Tenta reutilizar o email de A para provocar violação do índice único.
                    usuarios.inserir(new Usuario(emailA, "Duplicado", "hash-ficticio"));
                    // Faz o teste falhar: email duplicado deve ser rejeitado pelo índice único.
                    throw new AssertionError("email duplicado deve ser rejeitado pelo índice único");
                // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
                } catch (MongoWriteException esperado) {
                    // Confere a expectativa do teste: falha deve ser de chave duplicada.
                    verificar(esperado.getCode() == 11000, "falha deve ser de chave duplicada");
                }
                // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
                try {
                    // Tenta repetir nome de propriedade sob o mesmo titular para testar a chave composta.
                    propriedades.inserir(new Propriedade(produtorAId, "Sítio Aurora " + sufixo));
                    // Faz o teste falhar: nome duplicado para o mesmo titular deve ser rejeitado.
                    throw new AssertionError("nome duplicado para o mesmo titular deve ser rejeitado");
                // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
                } catch (MongoWriteException esperado) {
                    // Confere a expectativa do teste: falha deve ser de chave duplicada.
                    verificar(esperado.getCode() == 11000, "falha deve ser de chave duplicada");
                }

                // Usa o repositório real na regra de autorização da propriedade.
                AcessoPropriedade acesso = new AcessoPropriedade(propriedades);
                // Confere a expectativa do teste: o produtor A deve ler sua propriedade.
                verificar(acesso.exigirDoProprietario(propriedadeAId, produtorAId).getId().equals(propriedadeAId),
                        // Identifica a expectativa deste caso: o produtor A deve ler sua propriedade.
                        "o produtor A deve ler sua propriedade");
                // Confere a expectativa do teste: o produtor B deve ler sua propriedade.
                verificar(acesso.exigirDoProprietario(propriedadeBId, produtorBId).getId().equals(propriedadeBId),
                        // Identifica a expectativa deste caso: o produtor B deve ler sua propriedade.
                        "o produtor B deve ler sua propriedade");
                // Mantém propriedadeAId como dependência ou dado imutável.
                final ObjectId propriedadeDoA = propriedadeAId;
                // Mantém produtorBId como dependência ou dado imutável.
                final ObjectId produtorB = produtorBId;
                // Confere que a tentativa de consultar propriedade sem o titular correto é negada.
                deveNegar(() -> acesso.exigirDoProprietario(propriedadeDoA, produtorB),
                        // Identifica a expectativa deste caso: o produtor B não pode ler a propriedade do produtor A.
                        "o produtor B não pode ler a propriedade do produtor A");
                // Confere que a tentativa de consultar propriedade sem o titular correto é negada.
                deveNegar(() -> acesso.exigirDoProprietario(propriedadeDoA, new ObjectId()),
                        // Identifica a expectativa deste caso: um produtor inexistente não pode ler propriedade.
                        "um produtor inexistente não pode ler propriedade");

                // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
                try {
                    // Tenta gravar propriedade apontando para usuário inexistente para testar integridade da referência.
                    propriedades.inserir(new Propriedade(new ObjectId(), "Referência inválida " + sufixo));
                    // Faz o teste falhar: propriedade com produtor inexistente deve ser rejeitada.
                    throw new AssertionError("propriedade com produtor inexistente deve ser rejeitada");
                // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
                } catch (IllegalArgumentException esperado) {
                    // Referência obrigatória e existente.
                }
            }

            // Abre o recurso para esta operação e garante seu fechamento ao sair do bloco, inclusive em caso de falha.
            try (MongoConexao segundaConexao = MongoConexao.paraTestes()) {
                // Cria o repositório PropriedadeRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
                PropriedadeRepositorioMongo propriedades = new PropriedadeRepositorioMongo(segundaConexao.banco());
                // Cria o repositório UsuarioRepositorioMongo sobre a conexão atual, sem manter dados do cliente anterior.
                UsuarioRepositorioMongo usuarios = new UsuarioRepositorioMongo(segundaConexao.banco());
                // Confere a expectativa do teste: o produtor deve continuar disponível após reabrir a conexão.
                verificar(usuarios.buscarPorEmail("produtor-a-" + sufixo + "@teste.invalid").isPresent(),
                        // Identifica a expectativa deste caso: o produtor deve continuar disponível após reabrir a conexão.
                        "o produtor deve continuar disponível após reabrir a conexão");
                // Confere a expectativa do teste: a propriedade deve continuar disponível após reabrir a conexão.
                verificar(propriedades.buscarDoProprietario(propriedadeAId, produtorAId).isPresent(),
                        // Identifica a expectativa deste caso: a propriedade deve continuar disponível após reabrir a conexão.
                        "a propriedade deve continuar disponível após reabrir a conexão");
                // Confere a expectativa do teste: a consulta persistida deve manter o filtro por titular.
                verificar(propriedades.buscarDoProprietario(propriedadeAId, produtorBId).isEmpty(),
                        // Identifica a expectativa deste caso: a consulta persistida deve manter o filtro por titular.
                        "a consulta persistida deve manter o filtro por titular");
            }
        // Executa a limpeza mesmo se uma das operações anteriores lançar uma exceção.
        } finally {
            // Remove somente os ids criados por esta execução, mesmo após falha de uma verificação.
            limparDadosDeTeste(produtorAId, produtorBId, propriedadeAId, propriedadeBId);
        }

        // Exibe o resultado desta etapa no console: MongoAtlasValidacaoTest: OK.
        System.out.println("MongoAtlasValidacaoTest: OK");
    }

    // Remove somente os produtores e propriedades criados por esta execução usando seus identificadores.
    private static void limparDadosDeTeste(ObjectId produtorAId, ObjectId produtorBId,
            // Recebe também os ids das duas propriedades que precisam ser removidas.
            ObjectId propriedadeAId, ObjectId propriedadeBId) {
        // Abre o recurso para esta operação e garante seu fechamento ao sair do bloco, inclusive em caso de falha.
        try (MongoConexao conexao = MongoConexao.paraTestes()) {
            // Se a propriedade A chegou a ser criada, prepara sua remoção pela conexão exclusiva de limpeza.
            if (propriedadeAId != null) conexao.banco().getCollection("propriedades", Document.class)
                    // Remove a propriedade A pelo seu id exclusivo.
                    .deleteOne(new Document("_id", propriedadeAId));
            // Se a propriedade B chegou a ser criada, prepara sua remoção sem afetar outros registros.
            if (propriedadeBId != null) conexao.banco().getCollection("propriedades", Document.class)
                    // Remove a propriedade B pelo seu id exclusivo.
                    .deleteOne(new Document("_id", propriedadeBId));
            // Se o produtor A chegou a ser criado, prepara sua remoção depois das propriedades.
            if (produtorAId != null) conexao.banco().getCollection("usuarios", Document.class)
                    // Remove o produtor A pelo seu id exclusivo, depois de limpar as propriedades.
                    .deleteOne(new Document("_id", produtorAId));
            // Se o produtor B chegou a ser criado, prepara sua remoção sem limpar a coleção inteira.
            if (produtorBId != null) conexao.banco().getCollection("usuarios", Document.class)
                    // Remove o produtor B pelo seu id exclusivo, preservando contas fora do cenário.
                    .deleteOne(new Document("_id", produtorBId));
        }
    }

    // Exige que a tentativa de acesso termine como propriedade não encontrada, inclusive quando pertence a outro titular.
    private static void deveNegar(Runnable acao, String mensagem) {
        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Executa a operação que este caso de teste espera ver rejeitada.
            acao.run();
            // Faz o teste falhar com a mensagem da expectativa que não foi atendida.
            throw new AssertionError(mensagem);
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (PropriedadeNaoEncontradaException esperado) {
            // Mesmo resultado para registro inexistente ou que pertence a outro produtor.
        }
    }

    // Verifica a condição recebida e lança AssertionError se a expectativa do teste não for atendida.
    private static void verificar(boolean condicao, String mensagem) {
        // Interrompe o teste com AssertionError quando a expectativa recebida não é verdadeira.
        if (!condicao) throw new AssertionError(mensagem);
    }
}
