// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.domain.propriedades;

import org.bson.types.ObjectId;

/**
 * Ponto obrigatório de autorização para recursos vinculados a uma propriedade.
 * Endpoints futuros devem receber o usuário autenticado do contexto de segurança,
 * e nunca um proprietarioId enviado pelo navegador.
 */
public final class AcessoPropriedade {
    private final PropriedadeRepository propriedades;

    public AcessoPropriedade(PropriedadeRepository propriedades) {
        this.propriedades = propriedades;
    }

    public Propriedade exigirDoProprietario(ObjectId propriedadeId, ObjectId usuarioAutenticadoId) {
        return propriedades.buscarDoProprietario(propriedadeId, usuarioAutenticadoId)
                .orElseThrow(() -> new PropriedadeNaoEncontradaException(propriedadeId.toHexString()));
    }
}
