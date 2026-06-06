package dev.lorenzods.anonimizadorpdf

import dev.lorenzods.anonimizadorpdf.domain.usecase.LlmResponseParser
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonParseTest {

    @Test
    fun parsesCleanJsonArray() {
        assertEquals(listOf("João", "CPF 123"), LlmResponseParser.parseTerms("[\"João\", \"CPF 123\"]"))
    }

    @Test
    fun parsesArrayWrappedInProse() {
        val raw = "Claro! Aqui estão os termos a remover: [\"Maria\", \"maria@x.com\"]. Espero ajudar."
        assertEquals(listOf("Maria", "maria@x.com"), LlmResponseParser.parseTerms(raw))
    }

    @Test
    fun parsesArrayInMarkdownFence() {
        val raw = "```json\n[\"Dr. Silva\", \"Hospital X\"]\n```"
        assertEquals(listOf("Dr. Silva", "Hospital X"), LlmResponseParser.parseTerms(raw))
    }

    @Test
    fun trimsAndDeduplicates() {
        assertEquals(listOf("a", "b"), LlmResponseParser.parseTerms("[\" a \", \"a\", \"b\"]"))
    }

    @Test
    fun returnsEmptyForMalformedInput() {
        assertEquals(emptyList<String>(), LlmResponseParser.parseTerms("isto não é json"))
    }

    @Test
    fun returnsEmptyForEmptyArray() {
        assertEquals(emptyList<String>(), LlmResponseParser.parseTerms("[]"))
    }

    @Test
    fun dropsEmptyStringEntries() {
        assertEquals(listOf("x"), LlmResponseParser.parseTerms("[\"x\", \"\", \"   \"]"))
    }
}
