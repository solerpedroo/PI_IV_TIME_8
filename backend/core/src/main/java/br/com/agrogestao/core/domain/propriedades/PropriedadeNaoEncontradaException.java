// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.domain.propriedades;

public class PropriedadeNaoEncontradaException extends RuntimeException {
    public PropriedadeNaoEncontradaException(String propriedadeId) {
        super("propriedade não encontrada: " + propriedadeId);
    }
}
