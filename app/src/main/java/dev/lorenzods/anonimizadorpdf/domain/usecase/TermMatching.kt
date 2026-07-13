package dev.lorenzods.anonimizadorpdf.domain.usecase

import java.text.Normalizer

/**
 * Shared matching rules for redaction terms. Every place that matches a confirmed/learned/manual
 * term against document text ([ApplyRedactionsUseCase], [PiiDetector]) must build its pattern here,
 * so the guarantee is single-sourced: a term matches **whole-word**, case-insensitively, and
 * **accent-insensitively in both directions** — "Joao" finds "João" and "João" finds "Joao".
 *
 * Accent-insensitivity matters because PDF extraction sometimes strips diacritics (see
 * [BrazilianNames]): a name learned as "João" from one document must still redact the "Joao" a
 * later PDF yields, otherwise the miss is silent. Patterns also tolerate decomposed (NFD) text,
 * where "ã" arrives as "a" + combining tilde.
 */
object TermMatching {

    /**
     * Canonical NFC form. Extracted text is normalized once at extraction time so stored text,
     * learned terms and manual terms all compare in the same composed form.
     */
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    // Diacritic families relevant to pt-BR text (plus the few extras that show up in names).
    // Each letter in a group matches any letter of the same group.
    private val variantGroups = listOf(
        "aáàâãä", "eéèêë", "iíìîï", "oóòôõö", "uúùûü", "cç", "nñ", "yýÿ",
    )
    private val variantsByLetter: Map<Char, String> = buildMap {
        for (group in variantGroups) for (ch in group) put(ch, group)
    }

    /**
     * Whole-word regex for [term]: case-insensitive ((?iu) Unicode case folding), accent-folded via
     * character classes, and NFD-tolerant (`\p{Mn}*` after foldable letters absorbs combining
     * marks). Whole-word means not glued to another letter/digit, so "Ana" never hits "Anamnese";
     * symbol/digit terms (CPF, e-mail, telefone) still match because their neighbours are naturally
     * non-alphanumeric.
     */
    fun wholeWordPattern(term: String): Regex {
        val body = buildString {
            for (ch in normalize(term)) {
                val variants = variantsByLetter[ch.lowercaseChar()]
                if (variants != null) {
                    append('[').append(variants).append(']').append("\\p{Mn}*")
                } else {
                    append(Regex.escape(ch.toString()))
                }
            }
        }
        return Regex("(?iu)(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])")
    }
}
