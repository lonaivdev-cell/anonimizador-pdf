package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams the raw model output for an anonymization suggestion. The caller accumulates the tokens
 * and parses the final text with [LlmResponseParser].
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
    }
}
