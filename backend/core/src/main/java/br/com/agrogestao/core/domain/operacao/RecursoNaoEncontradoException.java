package br.com.agrogestao.core.domain.operacao;

/**
 * Recurso ausente no escopo (ex.: safra/cultura inexistente).
 * Separado de {@link IllegalArgumentException} (dado inválido) para o chamador HTTP
 * mapear 404 vs 400 sem inspecionar a mensagem.
 */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
