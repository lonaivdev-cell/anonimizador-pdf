package dev.lorenzods.anonimizadorpdf.domain.usecase

/**
 * Replaces every selected term in the text with [PLACEHOLDER] (case-insensitive).
 *
 * Terms are applied longest-first so that a shorter term that is a substring of a longer one does
 * not pre-empt the longer replacement.
 */
class ApplyRedactionsUseCase {

    operator fun invoke(text: String, terms: List<String>): String {
        val ordered = terms
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }

        var result = text
        for (term in ordered) {
            result = result.replace(term, PLACEHOLDER, ignoreCase = true)
        }
        return result
    }

    companion object {
        const val PLACEHOLDER = "[ANONIMIZADO]"
    }
}
