package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stage 2a of the pipeline: the model never reads the document — it reviews only the short
 * **candidate list** produced by the offline detector and says which items are personal data.
 *
 * The prompt is tuned for very small on-device models:
 * - candidates are sent as a *numbered* list and the model answers with the item **numbers**
 *   ("[1, 3]") — echoing accented Portuguese terms verbatim is exactly what a 1B-class model
 *   gets wrong, and a couple of digits is the cheapest possible output;
 * - a one-line few-shot example shows the exact expected format, and the prompt ends with
 *   "Resposta:" so a completion-style model falls straight into the answer;
 * - long lists are reviewed in batches of [BATCH_SIZE] so each call stays tiny.
 *
 * Streams raw output; the caller accumulates and resolves it with
 * [LlmResponseParser.parseReviewSelection].
 */
class ReviewSuggestionsUseCase(
    private val llmRepository: LlmRepository,
) {
    fun stream(terms: List<String>): Flow<String> = llmRepository.generate(buildPrompt(terms))

    companion object {
        /** Candidates per call — keeps prompt + answer well inside a small context window. */
        const val BATCH_SIZE = 20

        internal fun buildPrompt(terms: List<String>): String = buildString {
            append("Tarefa: encontrar dados pessoais (LGPD) em uma lista numerada de termos de um documento clínico.\n")
            append("Marque um item somente se for: nome de pessoa (paciente, familiar, médico) ")
            append("ou nome de clínica, hospital, laboratório ou instituição.\n")
            append("NÃO marque: doenças, exames, medicamentos, resultados, valores, unidades, palavras comuns.\n")
            append("Responda APENAS com um array JSON com os números dos itens marcados. Se nenhum, responda [].\n")
            append("\nExemplo:\n")
            append("1. Maria da Silva\n2. Hemograma Completo\n3. Dr. Carlos\n")
            append("Resposta: [1, 3]\n")
            append("\nLISTA:\n")
            terms.forEachIndexed { index, term ->
                append(index + 1).append(". ").append(term.replace('\n', ' ')).append('\n')
            }
            append("Resposta:")
        }
    }
}
