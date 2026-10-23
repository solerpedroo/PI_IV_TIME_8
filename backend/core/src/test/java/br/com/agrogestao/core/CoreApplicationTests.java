// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core;

public final class CoreApplicationTests {
    private CoreApplicationTests() {}

    public static void main(String[] args) {
        verificar(CoreApplication.porta("8081") == 8081, "deve aceitar porta válida");

        try {
            CoreApplication.porta("70000");
            throw new AssertionError("deveria rejeitar porta fora do intervalo TCP");
        } catch (IllegalArgumentException esperado) {
            // Resultado esperado.
        }

        System.out.println("CoreApplicationTests: OK");
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }
}
