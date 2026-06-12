package dev.lorenzods.anonimizadorpdf.domain.usecase

/**
 * Replaces every selected term in the text with [PLACEHOLDER] (case- and accent-case-insensitive).
 *
 * Matching is **whole-word**: an occurrence only counts when it is not glued to another letter or
 * digit, so redacting "Ana" never mangles "Anamnese". Terms made of symbols/digits (CPF, e-mail,
 * telefone) still match because their neighbours are naturally non-alphanumeric.
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
            // (?iu) = case-insensitive + Unicode case folding (JOÃO ↔ joão on the JVM as well).
            val pattern = Regex("(?iu)(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])")
            result = pattern.replace(result, Regex.escapeReplacement(PLACEHOLDER))
        }
        return result
    }

    companion object {
        const val PLACEHOLDER = "[ANONIMIZADO]"
    }
}
