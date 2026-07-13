package dev.lorenzods.anonimizadorpdf.domain.usecase

/**
 * Replaces every selected term in the text with [PLACEHOLDER]. Matching rules (whole-word,
 * case-insensitive, accent-insensitive in both directions, NFD-tolerant) come from [TermMatching],
 * shared with the detector's learned-term pass so what gets suggested is exactly what gets redacted.
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

        var result = TermMatching.normalize(text)
        for (term in ordered) {
            val pattern = TermMatching.wholeWordPattern(term)
            result = pattern.replace(result, Regex.escapeReplacement(PLACEHOLDER))
        }
        return result
    }

    companion object {
        const val PLACEHOLDER = "[ANONIMIZADO]"
    }
}
