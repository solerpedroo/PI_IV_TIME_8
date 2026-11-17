// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core;

// Agrupa as verificações de CoreApplications nesta classe.
public final class CoreApplicationTests {
    // Impede criar instâncias desta classe, pois suas operações são estáticas.
    private CoreApplicationTests() {}

    // Executa a sequência de inicialização ou as verificações desta classe a partir da linha de comando.
    public static void main(String[] args) {
        // Confere a expectativa do teste: deve aceitar porta válida.
        verificar(CoreApplication.porta("8081") == 8081, "deve aceitar porta válida");

        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Exercita uma porta acima de 65535 para comprovar a rejeição da configuração inválida.
            CoreApplication.porta("70000");
            // Faz o teste falhar: deveria rejeitar porta fora do intervalo TCP.
            throw new AssertionError("deveria rejeitar porta fora do intervalo TCP");
        // Considera esta exceção o resultado esperado para o caso inválido exercitado pelo teste.
        } catch (IllegalArgumentException esperado) {
            // Resultado esperado.
        }

        // Exibe o resultado desta etapa no console: CoreApplicationTests: OK.
        System.out.println("CoreApplicationTests: OK");
    }

    // Verifica a condição recebida e lança AssertionError se a expectativa do teste não for atendida.
    private static void verificar(boolean condicao, String mensagem) {
        // Interrompe o teste com AssertionError quando a expectativa recebida não é verdadeira.
        if (!condicao) {
            // Faz o teste falhar com a mensagem da expectativa que não foi atendida.
            throw new AssertionError(mensagem);
        }
    }
}
