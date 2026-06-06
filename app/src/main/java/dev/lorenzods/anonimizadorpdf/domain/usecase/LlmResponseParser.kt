package dev.lorenzods.anonimizadorpdf.domain.usecase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses the LLM response into a list of redaction terms.
 *
 * The model is asked to return a JSON array of strings, but in practice it may wrap the array in
 * prose or markdown code fences. This extractor locates the outermost `[ ... ]` and parses it
 * leniently. It never throws — malformed input yields an empty list.
 */
object LlmResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseTerms(raw: String): List<String> {
        val candidate = extractJsonArray(raw) ?: return emptyList()
        return try {
            val element = json.parseToJsonElement(candidate)
            if (element is JsonArray) {
                element.mapNotNull { item ->
                    (item as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
                }.distinct()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Returns the substring from the first '[' to the last ']' inclusive, or null if absent. */
    private fun extractJsonArray(raw: String): String? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) return null
        return raw.substring(start, end + 1)
    }
}
