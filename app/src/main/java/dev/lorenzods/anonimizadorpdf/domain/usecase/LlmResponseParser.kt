package dev.lorenzods.anonimizadorpdf.domain.usecase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses LLM responses into redaction terms. Built for *small* on-device models: the happy path is
 * a JSON array, but every common failure mode of a 1B-class model is tolerated — prose around the
 * array, markdown fences, trailing commas, objects instead of strings, and bullet/numbered lists
 * instead of JSON. It never throws; unusable input yields an empty result.
 */
object LlmResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** "- João" / "* João" / "• João" / "1. João" / "1) João" */
    private val bulletLine = Regex("""^(?:[-*•]|\d{1,3}[.)])\s+(.+)$""")

    /** A line that is just a quoted string, optionally with a trailing comma: "João Silva", */
    private val quotedLine = Regex("""^"([^"]+)"\s*,?$""")

    /** Leading "12." / "12)" enumeration the model may echo back in front of a term. */
    private val leadingIndex = Regex("""^(\d{1,3})[.)]\s*""")

    /** A standalone 1–3 digit number (not part of a longer digit run). */
    private val bareNumber = Regex("""(?<!\d)\d{1,3}(?!\d)""")

    /** Trailing comma before a closing bracket — invalid JSON that small models love to emit. */
    private val trailingComma = Regex(""",\s*([\]}])""")

    // Anything longer is model chatter ("Não há dados sensíveis neste trecho…"), not a term.
    private const val MAX_TERM_LENGTH = 80

    fun parseTerms(raw: String): List<String> {
        val candidates = extractArrayElements(raw) ?: parseListLines(raw)
        return candidates
            .map { cleanTerm(it) }
            .filter { it.isNotEmpty() && it.length <= MAX_TERM_LENGTH }
            .distinct()
    }

    /**
     * Resolves a review response against the numbered candidate list sent in the prompt. The model
     * is asked for item *numbers* ("[1, 3]") — echoing accented Portuguese terms verbatim is
     * exactly what a small model gets wrong — but echoed terms ("Maria", "3. Dr. Carlos") are
     * accepted as a fallback. Returns the matched terms exactly as they appear in [terms].
     */
    fun parseReviewSelection(raw: String, terms: List<String>): Set<String> {
        val byKey = terms.associateBy { it.trim().lowercase() }
        val selected = LinkedHashSet<String>()

        fun resolve(entry: String) {
            val content = cleanTerm(entry)
            if (content.isEmpty()) return
            content.toIntOrNull()?.let { index ->
                terms.getOrNull(index - 1)?.let { selected.add(it) }
                return
            }
            val indexed = leadingIndex.find(content)
            if (indexed != null) {
                // "3. Dr. Carlos" — prefer the echoed term, fall back to the leading number.
                val rest = content.removePrefix(indexed.value).trim().lowercase()
                val byTerm = byKey[rest]
                if (byTerm != null) {
                    selected.add(byTerm)
                } else {
                    terms.getOrNull(indexed.groupValues[1].toInt() - 1)?.let { selected.add(it) }
                }
                return
            }
            byKey[content.lowercase()]?.let { selected.add(it) }
        }

        val elements = extractArrayElements(raw)
        if (elements != null) {
            elements.forEach { resolve(it) }
            return selected
        }
        // No JSON array — treat each line as a potential item ("1. Maria", "- Maria", "3").
        raw.lines().forEach { line ->
            val item = line.trim().trimStart('-', '*', '•').trim()
            if (item.isNotEmpty()) resolve(item)
        }
        if (selected.isNotEmpty()) return selected
        // Last resort: accept standalone in-range numbers ("1, 3"), but only when the reply is
        // essentially numeric — digits inside prose ("nenhum dos 5 itens…") must not select items.
        if (raw.count { it.isLetter() } <= raw.length / 4) {
            bareNumber.findAll(raw).forEach { match ->
                terms.getOrNull(match.value.toInt() - 1)?.let { selected.add(it) }
            }
        }
        return selected
    }

    /** Parses the outermost `[ ... ]` into element strings, or null when no array can be read. */
    private fun extractArrayElements(raw: String): List<String>? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) return null
        val candidate = trailingComma.replace(raw.substring(start, end + 1), "$1")
        return try {
            val element = json.parseToJsonElement(candidate)
            if (element is JsonArray) {
                element.mapNotNull { item ->
                    when (item) {
                        is JsonPrimitive -> item.content
                        // e.g. [{"termo": "João"}] — take the first string value.
                        is JsonObject ->
                            item.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content }
                        else -> null
                    }
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Fallback for models that answer with a bullet/numbered/quoted list instead of JSON. */
    private fun parseListLines(raw: String): List<String> =
        raw.lines().mapNotNull { line ->
            val trimmed = line.trim()
            bulletLine.find(trimmed)?.groupValues?.get(1)
                ?: quotedLine.find(trimmed)?.groupValues?.get(1)
        }

    private fun cleanTerm(value: String): String = value.trim().trim('"', '\'', '`').trim()
}
