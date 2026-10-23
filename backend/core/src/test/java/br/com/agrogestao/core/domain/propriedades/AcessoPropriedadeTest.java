// Autor: Vinícius Panutti Salgado — R.A. 25007329
package br.com.agrogestao.core.domain.propriedades;

import java.time.Instant;
import java.util.Optional;

import org.bson.types.ObjectId;

import br.com.agrogestao.core.domain.usuarios.Usuario;

public final class AcessoPropriedadeTest {
    private AcessoPropriedadeTest() {}

    public static void main(String[] args) {
        ObjectId usuarioA = new ObjectId();
        ObjectId usuarioB = new ObjectId();
        ObjectId propriedadeId = new ObjectId();
        Propriedade propriedade = new Propriedade(propriedadeId, usuarioA, "Sítio Aurora", Instant.EPOCH);

        PropriedadeRepository repositorio = (id, proprietarioId) ->
                id.equals(propriedadeId) && proprietarioId.equals(usuarioA)
                        ? Optional.of(propriedade)
                        : Optional.empty();
        AcessoPropriedade acesso = new AcessoPropriedade(repositorio);

        verificar(acesso.exigirDoProprietario(propriedadeId, usuarioA) == propriedade,
                "deve devolver a propriedade do usuário autenticado");
        verificar(Propriedade.doDocumento(propriedade.paraDocumento()).getProprietarioId().equals(usuarioA),
                "o documento persistido deve preservar o vínculo com o produtor");

        Usuario produtorA = new Usuario(usuarioA, " A@EXEMPLO.TEST ", "Produtor A", "hash-ficticio", Instant.EPOCH);
        verificar(Usuario.doDocumento(produtorA.paraDocumento()).getEmail().equals("a@exemplo.test"),
                "email deve ser normalizado no documento");

        try {
            acesso.exigirDoProprietario(propriedadeId, usuarioB);
            throw new AssertionError("não pode revelar propriedade de outro produtor");
        } catch (PropriedadeNaoEncontradaException esperado) {
            // O mesmo resultado de "não existe" evita revelar dados de outro produtor.
        }

        try {
            acesso.exigirDoProprietario(new ObjectId(), usuarioA);
            throw new AssertionError("propriedade inexistente não pode ser encontrada");
        } catch (PropriedadeNaoEncontradaException esperado) {
            // O mesmo resultado de uma propriedade de outro produtor.
        }

        try {
            new Propriedade(null, "Sem titular");
            throw new AssertionError("propriedade sem titular deve ser inválida");
        } catch (IllegalArgumentException esperado) {
            // Vínculo obrigatório.
        }

        System.out.println("AcessoPropriedadeTest: OK");
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }
}
