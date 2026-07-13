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
    fun doesNotReplaceInsideOtherWords() {
        // "Ana" must never mangle "Anamnese" — whole-word matching only.
        val result = redact("Anamnese: Ana relatou dor. Reanálise adiada.", listOf("Ana"))
        assertEquals("Anamnese: $placeholder relatou dor. Reanálise adiada.", result)
    }

    @Test
    fun replacesWholeWordAtTextBoundaries() {
        val result = redact("Maria", listOf("Maria"))
        assertEquals(placeholder, result)
    }

    @Test
    fun replacesMultiWordTermsSurroundedByPunctuation() {
        val result = redact("Atendi (Maria Souza) ontem.", listOf("Maria Souza"))
        assertEquals("Atendi ($placeholder) ontem.", result)
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
    fun replacesCpfWithSeparators() {
        val result = redact("CPF do paciente: 123.456.789-00.", listOf("123.456.789-00"))
        assertEquals("CPF do paciente: $placeholder.", result)
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

    @Test
    fun matchesAccentedTextCaseInsensitively() {
        val result = redact("PACIENTE: JOSÉ ANTÔNIO.", listOf("José Antônio"))
        assertEquals("PACIENTE: $placeholder.", result)
    }

    @Test
    fun accentedTermMatchesTextWithStrippedDiacritics() {
        // PDF extraction sometimes strips accents: a term learned as "João" must still redact
        // the "Joao" a later document yields — a miss here would be silent.
        val result = redact("Paciente Joao compareceu.", listOf("João"))
        assertEquals("Paciente $placeholder compareceu.", result)
    }

    @Test
    fun unaccentedTermMatchesAccentedText() {
        val result = redact("Paciente João compareceu.", listOf("Joao"))
        assertEquals("Paciente $placeholder compareceu.", result)
    }

    @Test
    fun matchesDecomposedUnicodeText() {
        // NFD input: "João" with the tilde as a combining mark (a + U+0303).
        val nfd = "Paciente João compareceu."
        val result = redact(nfd, listOf("João"))
        assertEquals("Paciente $placeholder compareceu.", result)
    }

    @Test
    fun accentFoldingStaysWholeWord() {
        // "Ana" (or "Anã") must never mangle "Anamnese", accent-folded or not.
        val result = redact("Anamnese: Anã relatou dor.", listOf("Ana"))
        assertEquals("Anamnese: $placeholder relatou dor.", result)
    }
}
