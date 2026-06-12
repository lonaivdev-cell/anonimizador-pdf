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
    fun toleratesTrailingComma() {
        assertEquals(listOf("João", "Maria"), LlmResponseParser.parseTerms("[\"João\", \"Maria\",]"))
    }

    @Test
    fun extractsStringValuesFromObjectItems() {
        val raw = "[{\"termo\": \"João Souza\"}, {\"termo\": \"(11) 98888-7777\"}]"
        assertEquals(listOf("João Souza", "(11) 98888-7777"), LlmResponseParser.parseTerms(raw))
    }

    @Test
    fun fallsBackToBulletList() {
        val raw = "Dados pessoais encontrados:\n- João Souza\n- Hospital Santa Casa\n"
        assertEquals(listOf("João Souza", "Hospital Santa Casa"), LlmResponseParser.parseTerms(raw))
    }

    @Test
    fun fallsBackToNumberedList() {
        val raw = "1. Maria da Silva\n2) Dr. Carlos\n"
        assertEquals(listOf("Maria da Silva", "Dr. Carlos"), LlmResponseParser.parseTerms(raw))
    }

    @Test
    fun fallsBackToQuotedLines() {
        val raw = "\"João Souza\",\n\"Clínica Vida\"\n"
        assertEquals(listOf("João Souza", "Clínica Vida"), LlmResponseParser.parseTerms(raw))
    }

    @Test
    fun dropsOverlongChatterEntries() {
        val chatter = "Este trecho do documento não contém nenhum dado pessoal sensível que precise " +
            "ser removido segundo a LGPD"
        assertEquals(listOf("Ana"), LlmResponseParser.parseTerms("- Ana\n- $chatter"))
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
