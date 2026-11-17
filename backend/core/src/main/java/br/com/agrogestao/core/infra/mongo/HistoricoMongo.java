// Autor: Vinícius Panutti Salgado — R.A. 25007329
// Define o pacote br.com.agrogestao.core.infra.mongo, que organiza e identifica esta classe no projeto.
package br.com.agrogestao.core.infra.mongo;

// Importa as listas mutáveis usadas ao reunir resultados das consultas.
import java.util.ArrayList;
// Importa as listas de vínculos, eventos e resultados.
import java.util.List;
// Importa a regra de transição recebida como função de dois estados.
import java.util.function.BiPredicate;
// Importa os documentos BSON usados na comunicação com o MongoDB.
import org.bson.Document;
// Importa os identificadores BSON usados nos registros e em suas referências.
import org.bson.types.ObjectId;
// Importa a identificação de falhas de escrita, inclusive chaves duplicadas.
import com.mongodb.MongoWriteException;
// Importa o acesso tipado às coleções de documentos.
import com.mongodb.client.MongoCollection;
// Importa a seleção das coleções no banco compartilhado.
import com.mongodb.client.MongoDatabase;
// Importa as opções de unicidade e nome dos índices.
import com.mongodb.client.model.IndexOptions;
// Importa br.com.agrogestao.core.domain.operacao.* para utilizar os tipos deste módulo nas operações abaixo.
import br.com.agrogestao.core.domain.operacao.*;

/** Histórico é a fonte do estado atual; evita gravar status e histórico em operações separadas. */
// Agrupa as operações de HistoricoMongo nesta classe.
final class HistoricoMongo {
    // Mantém coleção dos eventos do histórico como dependência ou dado imutável.
    private final MongoCollection<Document> eventos;
    // Guarda o nome do campo que liga cada evento ao registro principal deste histórico.
    private final String campoRegistro;

    // Inicializa HistoricoMongo com os valores recebidos e as validações detalhadas a seguir.
    HistoricoMongo(MongoDatabase banco, String colecao, String campoRegistro) {
        // Guarda coleção dos eventos do histórico na instância para reutilização nas próximas operações.
        this.eventos = banco.getCollection(colecao);
        // Permite reutilizar o mesmo repositório para atividadeId e ocorrenciaId.
        this.campoRegistro = campoRegistro;
    }

    // Cria os índices usados nas consultas e nas restrições de unicidade desta coleção.
    void criarIndices() {
        // Indexa titular, propriedade, registro e sequência para localizar e ordenar seus eventos.
        eventos.createIndex(com.mongodb.client.model.Indexes.ascending("proprietarioId", "propriedadeId", campoRegistro, "ordem"),
                // Impõe uma única gravação por posição do histórico, detectando escritores concorrentes.
                new IndexOptions().unique(true).name("registro_ordem_unica"));
    }

    // Reconstitui o histórico com o evento inicial e os eventos persistidos em ordem.
    List<EventoHistorico> listar(EscopoPropriedade escopo, ObjectId id, Document original) {
        // Prepara a lista mutável que receberá o evento zero e os eventos consultados.
        List<EventoHistorico> resultado = new ArrayList<>();
        // Inclui virtualmente o evento zero usando a data de criação do documento principal.
        resultado.add(new EventoHistorico(0, original.getDate("criadaEm").toInstant(),
                // Completa o evento inicial com o estado de criação, sem inserir outro documento no banco.
                original.getString("statusInicial"), "Registro criado"));
        // Busca somente os eventos deste registro e escopo, ordenados pela sequência crescente.
        eventos.find(escopo.documento().append(campoRegistro, id)).sort(com.mongodb.client.model.Sorts.ascending("ordem"))
                // Converte cada documento em evento e o acrescenta depois do evento inicial.
                .map(EventoHistorico::doDocumento).into(resultado);
        // Entrega uma cópia imutável da sequência para impedir alterações externas na lista retornada.
        return List.copyOf(resultado);
    }

    // Obtém o evento mais recente ou reconstrói o estado inicial quando ainda não há eventos persistidos.
    EventoHistorico ultimo(EscopoPropriedade escopo, ObjectId id, Document original) {
        // Consulta o último evento somente dentro da propriedade e do registro solicitados.
        Document evento = eventos.find(escopo.documento().append(campoRegistro, id))
                // Ordena da maior sequência para a menor e lê apenas o primeiro resultado.
                .sort(com.mongodb.client.model.Sorts.descending("ordem")).limit(1).first();
        // Se não houver evento persistido, reconstrói o estado inicial a partir do documento principal.
        return evento == null ? new EventoHistorico(0, original.getDate("criadaEm").toInstant(),
                // Usa o evento zero como alternativa; quando há documento, converte o último evento persistido.
                original.getString("statusInicial"), "Registro criado") : EventoHistorico.doDocumento(evento);
    }

    // Valida e grava um novo evento sem atualizar separadamente o estado do registro principal.
    void adicionar(EscopoPropriedade escopo, ObjectId id, Document original, EventoHistorico evento,
            // Recebe a regra de transição própria do tipo de registro, sem fixá-la neste repositório genérico.
            BiPredicate<String, String> permiteTransicao) {
        // Lê o evento mais recente para conferir sequência, data e transição do novo evento.
        EventoHistorico ultimo = ultimo(escopo, id, original);
        // Exige a posição imediatamente seguinte à última sequência conhecida.
        if (evento.ordem() != ultimo.ordem() + 1) {
            // Rejeita uma gravação baseada em histórico desatualizado em vez de sobrescrever outro evento.
            throw new IllegalArgumentException("ordem desatualizada; consulte o histórico antes de gravar");
        }
        // Impede que a data do novo evento seja anterior à última data registrada.
        if (evento.registradoEm().isBefore(ultimo.registradoEm())) {
            // Informa a quebra da ordem temporal do histórico.
            throw new IllegalArgumentException("evento não pode anteceder o histórico existente");
        }
        // Aplica a regra de transição recebida ao estado anterior e ao estado proposto.
        if (!permiteTransicao.test(ultimo.status(), evento.status())) {
            // Rejeita a mudança de estado que o modelo de domínio não permite.
            throw new IllegalArgumentException("transição de status inválida");
        }
        // Inicia o documento do evento com titular, propriedade e referência ao registro principal.
        Document d = escopo.documento().append(campoRegistro, id);
        // Acrescenta ordem, instante, estado e descrição produzidos pelo objeto de domínio.
        d.putAll(evento.paraDocumento());
        // Inicia o trecho cujas falhas serão tratadas pelos blocos de captura correspondentes.
        try {
            // Insere o documento validado na coleção; o driver pode completar seu _id quando ele estiver ausente.
            eventos.insertOne(d);
        // Encaminha esta falha ao tratamento definido abaixo sem continuar o caminho de sucesso.
        } catch (MongoWriteException e) {
            // Converte chave duplicada em conflito de histórico para orientar uma nova consulta.
            if (e.getCode() == 11000) throw new IllegalArgumentException("histórico alterado por outra gravação; consulte novamente");
            // Propaga os demais erros de escrita sem confundi-los com uma disputa pela sequência.
            throw e;
        }
    }
}
