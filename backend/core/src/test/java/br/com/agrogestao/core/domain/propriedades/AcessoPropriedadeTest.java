// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.propriedades, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.propriedades;

// Importa os instantes de criação e dos eventos.
import java.time.Instant;
// Importa a representação explícita de uma busca sem resultado.
import java.util.Optional;

// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;

// Importa br.com.agrogestao.core.domain.usuarios.Usuario para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.usuarios.Usuario;

// Agrupa as verificações de AcessoPropriedade nesta classe.
public final class AcessoPropriedadeTest {
    // Impede criar instâncias desta classe, pois suas operações são estáticas.
    private AcessoPropriedadeTest() {}

    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Reserva um identificador exclusivo para usuarioA, evitando colisão com dados de outras execuções.
        ObjectId usuarioA = new ObjectId();
        // Reserva um identificador exclusivo para usuarioB, evitando colisão com dados de outras execuções.
        ObjectId usuarioB = new ObjectId();
        // Reserva um identificador exclusivo para propriedadeId, evitando colisão com dados de outras execuções.
        ObjectId propriedadeId = new ObjectId();
        // Prepara uma propriedade do usuário A com identificador e data determinísticos.
        Propriedade propriedade = new Propriedade(propriedadeId, usuarioA, "Sítio Aurora", Instant.EPOCH);

        // Substitui o banco por um repositório em memória definido como lambda para isolar a regra de acesso.
        PropriedadeRepository repositorio = (id, proprietarioId) ->
                // Só considera encontrado o par formado pela propriedade conhecida e seu titular A.
                id.equals(propriedadeId) && proprietarioId.equals(usuarioA)
                        // Entrega a propriedade quando os dois identificadores correspondem ao cenário.
                        ? Optional.of(propriedade)
                        // Representa qualquer outro par de propriedade e titular como resultado ausente.
                        : Optional.empty();
        // Injeta o repositório simulado no serviço de autorização.
        AcessoPropriedade acesso = new AcessoPropriedade(repositorio);

        // Confere a expectativa do teste: deve devolver a propriedade do usuário autenticado.
        verificar(acesso.exigirDoProprietario(propriedadeId, usuarioA) == propriedade,
                // Identifica a expectativa deste caso: deve devolver a propriedade do usuário autenticado.
                "deve devolver a propriedade do usuário autenticado");
        // Confere a expectativa do teste: o documento persistido deve preservar o vínculo com o produtor.
        verificar(Propriedade.doDocumento(propriedade.paraDocumento()).getProprietarioId().equals(usuarioA),
                // Identifica a expectativa deste caso: o documento persistido deve preservar o vínculo com o produtor.
                "o documento persistido deve preservar o vínculo com o produtor");

        // Usa email com espaços e maiúsculas para exercitar a normalização da conta.
        Usuario produtorA = new Usuario(usuarioA, " A@EXEMPLO.TEST ", "Produtor A", "hash-ficticio", Instant.EPOCH);
        // Confere a expectativa do teste: email deve ser normalizado no documento.
        verificar(Usuario.doDocumento(produtorA.paraDocumento()).getEmail().equals("a@exemplo.test"),
                // Identifica a expectativa deste caso: email deve ser normalizado no documento.
                "email deve ser normalizado no documento");

        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Tenta criar conta com domínio ausente para confirmar a validação mínima do email.
            new Usuario("sem-dominio@", "X", "hash");
            // Faz o teste falhar: email sem domínio válido deve ser rejeitado.
            throw new AssertionError("email sem domínio válido deve ser rejeitado");
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (IllegalArgumentException esperado) {
            // Formato mínimo: local@domínio.com
        }

        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Tenta acessar a propriedade de A usando o usuário B; a operação deve ser negada.
            acesso.exigirDoProprietario(propriedadeId, usuarioB);
            // Faz o teste falhar: não pode revelar propriedade de outro produtor.
            throw new AssertionError("não pode revelar propriedade de outro produtor");
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (PropriedadeNaoEncontradaException esperado) {
            // O mesmo resultado de "não existe" evita revelar dados de outro produtor.
        }

        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Consulta um id inexistente com titular válido para conferir o mesmo resultado de ausência.
            acesso.exigirDoProprietario(new ObjectId(), usuarioA);
            // Faz o teste falhar: propriedade inexistente não pode ser encontrada.
            throw new AssertionError("propriedade inexistente não pode ser encontrada");
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (PropriedadeNaoEncontradaException esperado) {
            // O mesmo resultado de uma propriedade de outro produtor.
        }

        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Tenta criar propriedade sem titular para verificar a obrigatoriedade do vínculo.
            new Propriedade(null, "Sem titular");
            // Faz o teste falhar: propriedade sem titular deve ser inválida.
            throw new AssertionError("propriedade sem titular deve ser inválida");
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (IllegalArgumentException esperado) {
            // Vínculo obrigatório.
        }

        // Exibe o resultado desta etapa no console: AcessoPropriedadeTest: OK.
        System.out.println("AcessoPropriedadeTest: OK");
    }

    // Verifica a condição recebida e lança AssertionError se a expectativa do teste não for atendida.
    private static void verificar(boolean condicao, String mensagem) {
        // Interrompe o teste com AssertionError quando a expectativa recebida não é verdadeira.
        if (!condicao) throw new AssertionError(mensagem);
    }
}
