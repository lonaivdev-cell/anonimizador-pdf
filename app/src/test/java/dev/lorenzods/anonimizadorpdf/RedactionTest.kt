package dev.lorenzods.anonimizadorpdf

import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class RedactionTest {

    private val redact = ApplyRedactionsUseCase()
    private val placeholder = ApplyRedactionsUseCase.PLACEHOLDER

    @Test
    fun replacesTermCaseInsensitively() {
        val result = redact("O paciente JOÃO compareceu; joão estava bem.", listOf("João"))
        assertEquals("O paciente $placeholder compareceu; $placeholder estava bem.", result)
    }

    @Test
    fun appliesLongestTermsFirstToAvoidPartialReplacement() {
        // If "Ana" were applied before "Ana Maria", the longer term would never match.
        val result = redact("Ana Maria e Ana", listOf("Ana", "Ana Maria"))
        assertEquals("$placeholder e $placeholder", result)
    }

    @Test
    fun replacesMultipleDistinctTerms() {
        val result = redact(
            "Contato: maria@x.com, telefone 11999998888.",
            listOf("maria@x.com", "11999998888"),
        )
        assertEquals("Contato: $placeholder, telefone $placeholder.", result)
    }

    @Test
    fun ignoresBlankAndDuplicateTerms() {
        val result = redact("CPF 123.456.789-00", listOf("  ", "", "123.456.789-00", "123.456.789-00"))
        assertEquals("CPF $placeholder", result)
    }

    @Test
    fun returnsTextUnchangedWhenNoTerms() {
        val text = "Nada a anonimizar aqui."
        assertEquals(text, redact(text, emptyList()))
    }

    @Test
    fun leavesTextUnchangedWhenTermAbsent() {
        val text = "Resultado laboratorial normal."
        assertEquals(text, redact(text, listOf("Inexistente")))
    }
}
