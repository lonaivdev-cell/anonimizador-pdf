package dev.lorenzods.anonimizadorpdf

import dev.lorenzods.anonimizadorpdf.domain.model.Confidence
import dev.lorenzods.anonimizadorpdf.domain.model.RedactionCategory
import dev.lorenzods.anonimizadorpdf.domain.usecase.PiiDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiDetectorTest {

    private fun detect(text: String) = PiiDetector.detect(text)

    private fun List<PiiDetector.Detection>.term(term: String) =
        firstOrNull { it.term.equals(term, ignoreCase = true) }

    @Test
    fun detectsEmailAsContact() {
        val d = detect("Contato: joao.silva@exemplo.com").term("joao.silva@exemplo.com")
        assertNotNull(d)
        assertEquals(RedactionCategory.CONTACT, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun detectsCpfAsDocumentHighConfidence() {
        val d = detect("CPF: 123.456.789-00").term("123.456.789-00")
        assertNotNull(d)
        assertEquals(RedactionCategory.DOCUMENT, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun detectsPhone() {
        val phone = detect("Telefone (11) 98765-4321").firstOrNull {
            it.category == RedactionCategory.CONTACT && it.term.contains("98765-4321")
        }
        assertNotNull(phone)
    }

    @Test
    fun detectsDate() {
        val d = detect("Data de nascimento: 12/05/1980").term("12/05/1980")
        assertNotNull(d)
        assertEquals(RedactionCategory.DATE, d!!.category)
    }

    @Test
    fun detectsTriggeredNameWithMediumConfidence() {
        val d = detect("Paciente: João Carlos da Silva").term("João Carlos da Silva")
        assertNotNull(d)
        assertEquals(RedactionCategory.NAME, d!!.category)
        assertEquals(Confidence.MEDIUM, d.confidence)
    }

    @Test
    fun ignoresClinicalHeadingPair() {
        val result = detect("Pressão Arterial: 120/80 mmHg")
        assertTrue(result.none { it.term.equals("Pressão Arterial", ignoreCase = true) })
        // The blood-pressure reading must not be misread as a date or phone.
        assertTrue(result.isEmpty())
    }

    @Test
    fun classifiesInstitutionAsOrganization() {
        val d = detect("Atendido no Hospital Santa Maria hoje.").term("Hospital Santa Maria")
        assertNotNull(d)
        assertEquals(RedactionCategory.ORGANIZATION, d!!.category)
    }

    @Test
    fun doesNotSweepAllCapsAcronymIntoName() {
        // "CPF" (all caps) must not be appended to the preceding name run.
        val result = detect("Paciente: Maria Souza CPF 111.222.333-44")
        assertNotNull(result.term("Maria Souza"))
        assertTrue(result.none { it.term.contains("CPF") })
    }

    @Test
    fun emptyTextYieldsNothing() {
        assertTrue(detect("   ").isEmpty())
    }

    @Test
    fun highConfidenceComesFirst() {
        val result = detect("Paciente: Ana Lima, CPF 123.456.789-00")
        assertFalse(result.isEmpty())
        assertEquals(Confidence.HIGH, result.first().confidence)
    }
}
