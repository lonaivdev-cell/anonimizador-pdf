package dev.lorenzods.anonimizadorpdf.domain.usecase

/**
 * Optional, deterministic post-formatter for the anonymized output. It reorganizes the raw PDF
 * extraction into something an external LLM reads more reliably:
 *
 *  - normalizes whitespace and drops page artifacts (page numbers, "Página 2 de 10", separator rows);
 *  - re-joins sentences that the PDF hard-wrapped mid-line (including hyphenated breaks);
 *  - promotes ALL-CAPS section titles to `## ` headings with breathing room;
 *  - collapses runs of blank lines and prepends a short header explaining the [ANONIMIZADO] marker.
 *
 * It is purely cosmetic and **lossy by design** (layout, not content) — that is why the UI exposes
 * it behind a toggle: turning it off always falls back to the untouched redacted text. Chat-style
 * lines ("João: …", timestamps) and list items are never merged, so conversation records keep their
 * per-message structure.
 *
 * Pure logic, no Android dependencies — exercised directly by unit tests.
 */
object OutputFormatter {

    private const val PLACEHOLDER = ApplyRedactionsUseCase.PLACEHOLDER

    // Space, tab and non-breaking space (frequent in PDF extractions).
    private val whitespaceRun = Regex("[ \\t\\u00A0]+")
    private val pageNumber = Regex("""^\d{1,4}$""")
    private val pageLabel = Regex("""(?i)^p[áa]g(?:ina)?\.?\s*\d+\s*(?:(?:de|/)\s*\d+)?$""")
    private val separatorRow = Regex("""^[-_=*·•.~]{3,}$""")

    private const val HEADER =
        "# DOCUMENTO ANONIMIZADO\n" +
            "# Dados pessoais foram substituídos por $PLACEHOLDER conforme a LGPD.\n\n"

    fun organize(text: String): String {
        if (text.isBlank()) return text

        val cleaned = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split("\n")
            .map { it.replace(whitespaceRun, " ").trim() }
            .filterNot { isPageArtifact(it) }

        // Re-join hard-wrapped sentences. A line is merged into the previous one only when the
        // previous line clearly didn't finish (no terminal punctuation) and the current one clearly
        // continues it (starts lowercase and is not a label/chat line).
        val reflowed = ArrayList<String>(cleaned.size)
        for (line in cleaned) {
            val previous = reflowed.lastOrNull()
            if (line.isNotBlank() && !previous.isNullOrBlank() && shouldMerge(previous, line)) {
                reflowed[reflowed.lastIndex] = joinWrapped(previous, line)
            } else {
                reflowed.add(line)
            }
        }

        val body = StringBuilder()
        var pendingBlank = false
        for (line in reflowed) {
            if (line.isBlank()) {
                pendingBlank = true
                continue
            }
            val heading = isHeading(line)
            if (body.isNotEmpty() && (heading || pendingBlank)) body.append('\n')
            body.append(if (heading) "## $line" else line).append('\n')
            // Headings get breathing room on both sides.
            pendingBlank = heading
        }

        if (body.isBlank()) return text
        return HEADER + body.toString().trimEnd('\n') + "\n"
    }

    private fun isPageArtifact(line: String): Boolean {
        if (line.isBlank()) return false
        return pageNumber.matches(line) || pageLabel.matches(line) || separatorRow.matches(line)
    }

    private fun shouldMerge(previous: String, current: String): Boolean {
        if (isHeading(previous)) return false
        val lastChar = previous.last()
        if (lastChar == '.' || lastChar == '!' || lastChar == '?' || lastChar == ':' || lastChar == ';') return false
        val first = current.first()
        if (!first.isLetter() || !first.isLowerCase()) return false
        // "joão: bom dia" / "obs: jejum 8h" — an early colon marks a labelled or chat line that
        // must keep its own row.
        val colon = current.indexOf(':')
        return colon !in 1..30
    }

    private fun joinWrapped(previous: String, current: String): String {
        // Hyphenated line break ("hiper-" + "tensão" → "hipertensão").
        return if (previous.length >= 2 && previous.last() == '-' && previous[previous.length - 2].isLetter()) {
            previous.dropLast(1) + current
        } else {
            "$previous $current"
        }
    }

    private fun isHeading(line: String): Boolean {
        if (line.length > 60 || line.contains(PLACEHOLDER)) return false
        if (line.any { it.isLowerCase() }) return false
        val letters = line.count { it.isLetter() }
        return letters >= 3 && letters * 2 >= line.length
    }
}
