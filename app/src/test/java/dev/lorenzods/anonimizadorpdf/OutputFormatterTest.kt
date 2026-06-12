package dev.lorenzods.anonimizadorpdf

import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.OutputFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputFormatterTest {

    private fun body(text: String): String {
        val organized = OutputFormatter.organize(text)
        // Strip the fixed two-line header + blank line for body-focused assertions.
        return organized.trimEnd('\n').lines().drop(3).joinToString("\n")
    }

    @Test
    fun prependsExplanatoryHeader() {
        val organized = OutputFormatter.organize("Paciente estável.")
        assertTrue(organized.startsWith("# DOCUMENTO ANONIMIZADO"))
        assertTrue(organized.contains(ApplyRedactionsUseCase.PLACEHOLDER))
        assertTrue(organized.endsWith("Paciente estável.\n"))
    }

    @Test
    fun removesPageArtifacts() {
        val out = body("Primeira linha.\n12\nPágina 2 de 10\n-----\nSegunda linha.")
        assertEquals("Primeira linha.\nSegunda linha.", out)
    }

    @Test
    fun mergesHardWrappedSentences() {
        val out = body("O paciente relatou dor\nno peito ao subir escadas.")
        assertEquals("O paciente relatou dor no peito ao subir escadas.", out)
    }

    @Test
    fun joinsHyphenatedLineBreaks() {
        val out = body("Paciente apresentou hiper-\ntensão arterial grave.")
        assertEquals("Paciente apresentou hipertensão arterial grave.", out)
    }

    @Test
    fun doesNotMergeAfterSentenceEnd() {
        val out = body("Primeira frase completa.\nsegunda linha independente")
        assertEquals("Primeira frase completa.\nsegunda linha independente", out)
    }

    @Test
    fun keepsChatLinesSeparate() {
        val out = body("maria: tudo bem com o remédio\njoão: tudo sim, obrigado")
        assertEquals("maria: tudo bem com o remédio\njoão: tudo sim, obrigado", out)
    }

    @Test
    fun promotesAllCapsHeadings() {
        val out = body("RESULTADOS\nHemograma dentro da normalidade.")
        assertEquals("## RESULTADOS\n\nHemograma dentro da normalidade.", out)
    }

    @Test
    fun placeholderLineIsNotAHeading() {
        val out = body("${ApplyRedactionsUseCase.PLACEHOLDER}\ncompareceu à consulta.")
        assertFalse(out.contains("##"))
        assertEquals("${ApplyRedactionsUseCase.PLACEHOLDER} compareceu à consulta.", out)
    }

    @Test
    fun collapsesBlankLineRuns() {
        val out = body("Primeira parte.\n\n\n\nSegunda parte.")
        assertEquals("Primeira parte.\n\nSegunda parte.", out)
    }

    @Test
    fun normalizesInternalWhitespace() {
        val out = body("Hemoglobina    13,5      g/dL.")
        assertEquals("Hemoglobina 13,5 g/dL.", out)
    }

    @Test
    fun blankInputIsReturnedUnchanged() {
        assertEquals("   ", OutputFormatter.organize("   "))
    }
}
