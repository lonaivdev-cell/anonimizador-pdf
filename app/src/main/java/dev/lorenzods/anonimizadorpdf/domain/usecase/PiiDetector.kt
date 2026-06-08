package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.model.Confidence
import dev.lorenzods.anonimizadorpdf.domain.model.RedactionCategory

/**
 * Stage 1 of the anonymization pipeline: a fully offline, deterministic detector that scans clinical
 * text for likely LGPD-sensitive data. It is deliberately **high recall** — structured data (CPF,
 * e-mail, phone, …) is matched precisely, while open-class names are over-suggested via heuristics.
 * The optional LLM review (Stage 2) prunes the false positives; until then the user confirms.
 *
 * Pure logic, no Android/network dependencies — exercised directly by unit tests.
 */
object PiiDetector {

    data class Detection(
        val term: String,
        val category: RedactionCategory,
        val confidence: Confidence,
    )

    // ---- Structured patterns (high precision) ----
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val cpf = Regex("""(?<!\d)\d{3}\.?\d{3}\.?\d{3}-?\d{2}(?!\d)""")
    private val cnpj = Regex("""(?<!\d)\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}(?!\d)""")
    private val cep = Regex("""(?<!\d)\d{5}-\d{3}(?!\d)""")
    private val phone = Regex("""(?<!\d)\(?\d{2}\)?[\s.-]?9?\d{4}[\s.-]?\d{4}(?!\d)""")
    private val date = Regex("""(?<!\d)\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}(?!\d)""")

    private val connectors = setOf("da", "de", "do", "das", "dos", "e", "del", "von", "van", "du")

    // Trigger words that strongly precede a person's name in clinical records.
    private val nameTriggers = setOf(
        "paciente", "nome", "cliente", "sr", "sra", "dr", "dra", "doutor", "doutora",
        "mãe", "mae", "pai", "responsável", "responsavel", "portador", "portadora",
        "acompanhante", "filho", "filha", "esposo", "esposa", "médico", "medico",
    )

    // Common capitalised clinical/section words that are NOT names — a candidate made up entirely of
    // these is dropped to keep the offline list usable even before the LLM review runs.
    private val clinicalStopwords = setOf(
        "pressão", "pressao", "arterial", "frequência", "frequencia", "cardíaca", "cardiaca",
        "respiratória", "respiratoria", "temperatura", "exame", "exames", "resultado", "resultados",
        "data", "hemograma", "glicose", "colesterol", "observação", "observacao", "conclusão",
        "conclusao", "diagnóstico", "diagnostico", "prescrição", "prescricao", "histórico",
        "historico", "queixa", "conduta", "saturação", "saturacao", "peso", "altura",
    )

    fun detect(text: String): List<Detection> {
        if (text.isBlank()) return emptyList()
        val found = ArrayList<Detection>()

        fun addPattern(regex: Regex, category: RedactionCategory, confidence: Confidence) {
            regex.findAll(text).forEach { found.add(Detection(it.value.trim(), category, confidence)) }
        }

        addPattern(email, RedactionCategory.CONTACT, Confidence.HIGH)
        addPattern(cnpj, RedactionCategory.DOCUMENT, Confidence.HIGH)
        addPattern(cpf, RedactionCategory.DOCUMENT, Confidence.HIGH)
        addPattern(cep, RedactionCategory.ADDRESS, Confidence.MEDIUM)
        addPattern(phone, RedactionCategory.CONTACT, Confidence.MEDIUM)
        addPattern(date, RedactionCategory.DATE, Confidence.MEDIUM)

        found.addAll(detectNames(text))

        // Deduplicate case-insensitively, keeping the strongest confidence per term.
        return found
            .filter { it.term.isNotBlank() }
            .groupBy { it.term.lowercase() }
            .map { (_, group) -> group.minByOrNull { it.confidence.ordinal }!! }
            .sortedWith(compareBy({ it.confidence.ordinal }, { it.term.lowercase() }))
    }

    /** Heuristic name detection over a tokenised pass. */
    private fun detectNames(text: String): List<Detection> {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = ArrayList<Detection>()

        var i = 0
        while (i < tokens.size) {
            val triggered = isTrigger(tokens.getOrNull(i - 1))
            // Build the longest run of name-like words (connectors allowed only between names).
            val words = ArrayList<String>()
            var j = i
            while (j < tokens.size) {
                val clean = clean(tokens[j])
                when {
                    // A trigger label ("Paciente:", "Dr.") is capitalised but must not become part of
                    // the name itself — it ends the (empty) run and flags the next one.
                    clean.lowercase().trimEnd(':') in nameTriggers -> break
                    isNameWord(clean) -> words.add(clean)
                    clean.lowercase() in connectors && words.isNotEmpty() &&
                        j + 1 < tokens.size && isNameWord(clean(tokens[j + 1])) ->
                        words.add(clean.lowercase())
                    else -> break
                }
                j++
            }
            // Trim trailing connectors.
            while (words.isNotEmpty() && words.last() in connectors) words.removeAt(words.lastIndex)

            val nameWordCount = words.count { it !in connectors }
            val minWords = if (triggered) 1 else 2
            if (nameWordCount >= minWords) {
                val term = words.joinToString(" ")
                if (!isAllStopwords(words)) {
                    val classified = RedactionClassifier.classify(term)
                    val category = when (classified) {
                        RedactionCategory.ORGANIZATION, RedactionCategory.ADDRESS -> classified
                        else -> RedactionCategory.NAME
                    }
                    val confidence = if (triggered) Confidence.MEDIUM else Confidence.LOW
                    results.add(Detection(term, category, confidence))
                }
                i = j
            } else {
                i++
            }
        }
        return results
    }

    private fun isTrigger(token: String?): Boolean {
        if (token == null) return false
        return clean(token).lowercase().trimEnd(':') in nameTriggers
    }

    private fun isNameWord(token: String): Boolean {
        if (token.length < 2) return false
        val first = token[0]
        if (!first.isLetter() || !first.isUpperCase()) return false
        if (!token.all { it.isLetter() || it == '\'' || it == '-' || it == '’' }) return false
        // Require mixed case: rejects ALL-CAPS headings/acronyms (CPF, RESULTADO) that would
        // otherwise be swept into a name run.
        return token.any { it.isLowerCase() }
    }

    private fun isAllStopwords(words: List<String>): Boolean =
        words.filter { it !in connectors }.all { it.lowercase() in clinicalStopwords }

    private fun clean(token: String): String =
        token.trim().trim('.', ',', ';', ':', '(', ')', '[', ']', '"', '\'', '“', '”', '!', '?')
}
