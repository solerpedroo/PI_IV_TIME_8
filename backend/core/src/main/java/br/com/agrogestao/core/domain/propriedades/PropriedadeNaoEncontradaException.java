// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.propriedades, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.propriedades;

// Agrupa as operações de PropriedadeNaoEncontradaException nesta classe.
public class PropriedadeNaoEncontradaException extends RuntimeException {
    // Inicializa PropriedadeNaoEncontradaException com os valores recebidos e as validações detalhadas a seguir.
    public PropriedadeNaoEncontradaException(String propriedadeId) {
        // Inicializa a exceção de domínio com o identificador da propriedade que não foi encontrada no escopo.
        super("propriedade não encontrada: " + propriedadeId);
    }
}
