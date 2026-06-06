package dev.lorenzods.anonimizadorpdf.domain.usecase

/**
 * Splits extracted text into chunks that fit a small on-device model's context window.
 *
 * On-device LLMs (e.g. Gemma 3 1B with ~1280-token context) cannot ingest a whole clinical
 * document at once, so Mode A analyzes the document chunk by chunk and aggregates the terms.
 * Splitting prefers line boundaries; overly long lines are hard-split.
 */
object TextChunker {

    fun chunk(text: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        if (text.isBlank()) return emptyList()
        if (text.length <= maxChars) return listOf(text)

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                chunks.add(current.toString())
                current.clear()
            }
        }

        for (line in text.split("\n")) {
            when {
                line.length > maxChars -> {
                    flush()
                    var i = 0
                    while (i < line.length) {
                        val end = minOf(i + maxChars, line.length)
                        chunks.add(line.substring(i, end))
                        i = end
                    }
                }

                current.isNotEmpty() && current.length + line.length + 1 > maxChars -> {
                    flush()
                    current.append(line)
                }

                else -> {
                    if (current.isNotEmpty()) current.append('\n')
                    current.append(line)
                }
            }
        }
        flush()
        return chunks
    }
}
