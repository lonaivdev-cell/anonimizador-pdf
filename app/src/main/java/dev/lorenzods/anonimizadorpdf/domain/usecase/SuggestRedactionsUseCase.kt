package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stage 2b (deep scan): streams the raw model output for one document chunk. The caller
 * accumulates the tokens and parses the final text with [LlmResponseParser].
 *
 * The chunk is framed exactly like the few-shot example in the system prompt ("TEXTO: …" then
 * "Resposta:"), so even a completion-style small model falls straight into the JSON array.
 */
class SuggestRedactionsUseCase(
    private val llmRepository: LlmRepository,
) {
    fun stream(systemPrompt: String, text: String): Flow<String> =
        llmRepository.generate(buildPrompt(systemPrompt, text))

    private fun buildPrompt(systemPrompt: String, text: String): String = buildString {
        append(systemPrompt.trim())
        append("\n\nTEXTO:\n")
        append(text)
        append("\nResposta:")
    }
}
