package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stage 2 of the pipeline: instead of reading the whole document, the model reviews only the short
 * **candidate list** and returns the subset that is genuinely LGPD-sensitive. Operating on a small
 * list keeps inference fast and reliable even on a small on-device model. Streams raw output; the
 * caller accumulates and parses with [LlmResponseParser].
 */
class ReviewSuggestionsUseCase(
    private val llmRepository: LlmRepository,
) {
    fun stream(terms: List<String>): Flow<String> = llmRepository.generate(buildPrompt(terms))

    private fun buildPrompt(terms: List<String>): String = buildString {
        append(
            "Você é um revisor de anonimização de dados clínicos (LGPD). A seguir está uma lista de " +
                "termos candidatos extraídos de um documento. Responda APENAS com um array JSON " +
                "contendo exatamente os termos da lista que são dados pessoais sensíveis e devem ser " +
                "removidos (nomes de pessoas, médicos e familiares; CPF; RG; telefones; e-mails; " +
                "endereços; CEP; datas de nascimento; nomes de clínicas, hospitais e instituições). " +
                "NÃO inclua termos médicos, exames, valores, unidades nem palavras comuns. Use " +
                "exatamente os termos da lista, sem alterá-los e sem inventar novos. Se nenhum for " +
                "sensível, responda [].",
        )
        append("\n\nLISTA:\n[")
        append(terms.joinToString(", ") { "\"${it.replace("\"", "'")}\"" })
        append("]")
    }
}
