package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.model.RedactionCategory

/**
 * Best-effort, fully on-device heuristic that buckets a redaction term into a [RedactionCategory].
 * Purely cosmetic — it drives chip grouping/colour so the user can scan suggestions by type. It is
 * never used to decide *whether* something is redacted (the user always confirms).
 */
object RedactionClassifier {

    private val cpfCnpj = Regex("""\d{3}\.?\d{3}\.?\d{3}-?\d{2}|\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}""")
    private val date = Regex("""\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b""")
    private val phone = Regex("""\(?\d{2}\)?[\s-]?\d{4,5}-?\d{4}""")
    private val email = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")

    private val orgWords = listOf(
        "hospital", "clinica", "clínica", "instituto", "laboratorio", "laboratório",
        "centro", "unidade", "ltda", "s.a", "santa", "são", "sao", "policlinica", "policlínica",
        "ame", "upa", "ubs", "maternidade",
    )
    private val addressWords = listOf(
        "rua", "avenida", "av.", "alameda", "travessa", "rodovia", "estrada",
        "bairro", "cep", "nº", "numero", "número", "apto", "bloco", "quadra",
    )

    fun classify(rawTerm: String): RedactionCategory {
        val term = rawTerm.trim()
        if (term.isEmpty()) return RedactionCategory.OTHER
        val lower = term.lowercase()

        return when {
            email.containsMatchIn(term) -> RedactionCategory.CONTACT
            cpfCnpj.containsMatchIn(term) -> RedactionCategory.DOCUMENT
            date.containsMatchIn(term) -> RedactionCategory.DATE
            phone.containsMatchIn(term) && term.count { it.isDigit() } >= 8 -> RedactionCategory.CONTACT
            addressWords.any { lower.contains(it) } -> RedactionCategory.ADDRESS
            orgWords.any { lower.contains(it) } -> RedactionCategory.ORGANIZATION
            looksLikePersonName(term) -> RedactionCategory.NAME
            // Single words tapped/typed by the user: a known Brazilian name buckets under "Nomes".
            BrazilianNames.isKnownNameWord(lower) -> RedactionCategory.NAME
            term.count { it.isDigit() } >= term.length / 2 && term.any { it.isDigit() } ->
                RedactionCategory.DOCUMENT
            else -> RedactionCategory.OTHER
        }
    }

    /** Two or more capitalised, mostly-alphabetic words → likely a personal/family name. */
    private fun looksLikePersonName(term: String): Boolean {
        val words = term.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2) return false
        val capitalised = words.count { w ->
            w.first().isUpperCase() && w.all { it.isLetter() || it == '.' || it == '\'' || it == '-' }
        }
        return capitalised >= 2
    }
}
