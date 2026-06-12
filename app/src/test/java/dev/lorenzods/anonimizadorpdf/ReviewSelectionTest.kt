package dev.lorenzods.anonimizadorpdf

import dev.lorenzods.anonimizadorpdf.domain.usecase.LlmResponseParser
import dev.lorenzods.anonimizadorpdf.domain.usecase.ReviewSuggestionsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Revisar com IA" step sends a numbered candidate list and expects item numbers back.
 * These tests cover every answer shape a small on-device model actually produces.
 */
class ReviewSelectionTest {

    private val terms = listOf("Maria da Silva", "Hemograma Completo", "Dr. Carlos")

    @Test
    fun parsesIndexArray() {
        assertEquals(
            setOf("Maria da Silva", "Dr. Carlos"),
            LlmResponseParser.parseReviewSelection("[1, 3]", terms),
        )
    }

    @Test
    fun parsesQuotedIndexArray() {
        assertEquals(
            setOf("Maria da Silva", "Dr. Carlos"),
            LlmResponseParser.parseReviewSelection("[\"1\", \"3\"]", terms),
        )
    }

    @Test
    fun parsesIndexArrayInMarkdownFence() {
        assertEquals(
            setOf("Dr. Carlos"),
            LlmResponseParser.parseReviewSelection("```json\n[3]\n```", terms),
        )
    }

    @Test
    fun ignoresOutOfRangeIndices() {
        assertEquals(
            setOf("Maria da Silva"),
            LlmResponseParser.parseReviewSelection("[1, 9, 0]", terms),
        )
    }

    @Test
    fun acceptsEchoedTermsCaseInsensitive() {
        assertEquals(
            setOf("Maria da Silva", "Dr. Carlos"),
            LlmResponseParser.parseReviewSelection("[\"maria da silva\", \"dr. carlos\"]", terms),
        )
    }

    @Test
    fun acceptsEchoedNumberedEntries() {
        assertEquals(
            setOf("Dr. Carlos"),
            LlmResponseParser.parseReviewSelection("[\"3. Dr. Carlos\"]", terms),
        )
    }

    @Test
    fun fallsBackToEchoedNumberWhenTermWasAltered() {
        // The model rewrote the term but kept the list number — the number wins.
        assertEquals(
            setOf("Maria da Silva"),
            LlmResponseParser.parseReviewSelection("[\"1. Maria Silva\"]", terms),
        )
    }

    @Test
    fun acceptsPlainNumberedLinesWithoutJson() {
        assertEquals(
            setOf("Maria da Silva", "Dr. Carlos"),
            LlmResponseParser.parseReviewSelection("1. Maria da Silva\n3. Dr. Carlos", terms),
        )
    }

    @Test
    fun acceptsBareNumbersWhenReplyIsNumeric() {
        assertEquals(
            setOf("Maria da Silva", "Dr. Carlos"),
            LlmResponseParser.parseReviewSelection("1, 3", terms),
        )
    }

    @Test
    fun doesNotMineNumbersOutOfProse() {
        // A digit inside an explanation must never select an item.
        assertEquals(
            emptySet<String>(),
            LlmResponseParser.parseReviewSelection("Nenhum dos 3 itens é um dado pessoal.", terms),
        )
    }

    @Test
    fun returnsEmptyForEmptyArray() {
        assertEquals(emptySet<String>(), LlmResponseParser.parseReviewSelection("[]", terms))
    }

    @Test
    fun promptNumbersCandidatesAndEndsWithAnswerPrimer() {
        val prompt = ReviewSuggestionsUseCase.buildPrompt(terms)
        assertTrue(prompt.contains("1. Maria da Silva"))
        assertTrue(prompt.contains("2. Hemograma Completo"))
        assertTrue(prompt.contains("3. Dr. Carlos"))
        assertTrue(prompt.contains("Exemplo:"))
        assertTrue(prompt.trimEnd().endsWith("Resposta:"))
    }

    @Test
    fun promptFlattensNewlinesInsideTerms() {
        val prompt = ReviewSuggestionsUseCase.buildPrompt(listOf("Maria\nda Silva"))
        assertTrue(prompt.contains("1. Maria da Silva"))
    }
}
