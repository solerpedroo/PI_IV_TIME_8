// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.domain.propriedades, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.domain.propriedades;

// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;

/**
 * Ponto obrigatório de autorização para recursos vinculados a uma propriedade.
 * Endpoints futuros devem receber o usuário autenticado do contexto de segurança,
 * e nunca um proprietarioId enviado pelo navegador.
 */
// Agrupa as operações de AcessoPropriedade nesta classe.
public final class AcessoPropriedade {
    // Mantém o contrato de consulta de propriedades recebido por injeção, sem acoplar a regra ao MongoDB.
    private final PropriedadeRepository propriedades;

    // Inicializa AcessoPropriedade com os valores recebidos e as validações detalhadas a seguir.
    public AcessoPropriedade(PropriedadeRepository propriedades) {
        // Guarda coleção de propriedades na instância para reutilização nas próximas operações.
        this.propriedades = propriedades;
    }

    // Exige que a propriedade pertença ao usuário autenticado antes de devolvê-la ao chamador.
    public Propriedade exigirDoProprietario(ObjectId propriedadeId, ObjectId usuarioAutenticadoId) {
        // Consulta simultaneamente a propriedade e o titular autenticado para não expor registros de outro usuário.
        return propriedades.buscarDoProprietario(propriedadeId, usuarioAutenticadoId)
                // Trata ausência e falta de titularidade com a mesma exceção, evitando revelar a existência de propriedade alheia.
                .orElseThrow(() -> new PropriedadeNaoEncontradaException(propriedadeId.toHexString()));
    }
}
