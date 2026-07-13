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

    private fun detect(text: String, learnedTerms: List<String>) =
        PiiDetector.detect(text, learnedTerms)

    private fun List<PiiDetector.Detection>.term(term: String) =
        firstOrNull { it.term.equals(term, ignoreCase = true) }

    // ---- structured data ----

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
    fun detectsWrittenDate() {
        val d = detect("Paciente nascida em 12 de maio de 1980, sem comorbidades.")
            .term("12 de maio de 1980")
        assertNotNull(d)
        assertEquals(RedactionCategory.DATE, d!!.category)
    }

    @Test
    fun detectsRgNumber() {
        val d = detect("RG: 12.345.678-9, emitido em SP").term("12.345.678-9")
        assertNotNull(d)
        assertEquals(RedactionCategory.DOCUMENT, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun detectsCrmNumber() {
        val result = detect("Dr. Carlos Mendes - CRM/SP 123456")
        val crm = result.term("123456")
        assertNotNull(crm)
        assertEquals(RedactionCategory.DOCUMENT, crm!!.category)
        assertEquals(Confidence.HIGH, crm.confidence)
        // The physician name is triggered by "Dr." and boosted by the dictionary.
        val name = result.term("Carlos Mendes")
        assertNotNull(name)
        assertEquals(Confidence.HIGH, name!!.confidence)
    }

    @Test
    fun detectsCrbmNumber() {
        // Biomedicine professionals sign reports with a CRBM registration, like a physician's CRM.
        val result = detect("Responsável técnico: Ana Biomédica - CRBM/SP 12345")
        val crbm = result.term("12345")
        assertNotNull(crbm)
        assertEquals(RedactionCategory.DOCUMENT, crbm!!.category)
        assertEquals(Confidence.HIGH, crbm.confidence)
    }

    @Test
    fun detectsCrbmNumberWithRegionAndBareLabel() {
        // Region number ("CRBM-6") and a spelled-out region must not swallow the registration, and a
        // bare "CRBM 98765" with no region must capture the full number rather than a truncation.
        assertEquals("123456", detect("CRBM-6 123456").term("123456")?.term)
        assertEquals("445566", detect("CRBM 1ª Região 445566").term("445566")?.term)
        assertEquals("98765", detect("CRBM 98765").term("98765")?.term)
    }

    @Test
    fun detectsProntuarioNumber() {
        val d = detect("Prontuário nº 456789").term("456789")
        assertNotNull(d)
        assertEquals(RedactionCategory.DOCUMENT, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun detectsCnsNumber() {
        val d = detect("CNS 700501234567890").term("700501234567890")
        assertNotNull(d)
        assertEquals(RedactionCategory.DOCUMENT, d!!.category)
    }

    @Test
    fun detectsStreetAddressWithNumber() {
        val d = detect("Mora na Rua das Flores, 123 desde 2019.")
        assertNotNull(d.firstOrNull { it.category == RedactionCategory.ADDRESS && it.term.startsWith("Rua das Flores") })
    }

    @Test
    fun doesNotFlagStreetMentionWithoutNumber() {
        val result = detect("a rua estava molhada quando ele chegou")
        assertTrue(result.none { it.category == RedactionCategory.ADDRESS })
    }

    // ---- names: triggers, dictionary, ALL CAPS ----

    @Test
    fun detectsTriggeredDictionaryNameWithHighConfidence() {
        val d = detect("Paciente: João Carlos da Silva").term("João Carlos da Silva")
        assertNotNull(d)
        assertEquals(RedactionCategory.NAME, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun detectsAllCapsTriggeredName() {
        val d = detect("PACIENTE: MARIA APARECIDA DOS SANTOS").term("MARIA APARECIDA DOS SANTOS")
        assertNotNull(d)
        assertEquals(RedactionCategory.NAME, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun detectsStandaloneKnownFirstNameMidSentence() {
        val d = detect("Conversei com Maria sobre o exame.").term("Maria")
        assertNotNull(d)
        assertEquals(RedactionCategory.NAME, d!!.category)
        assertEquals(Confidence.MEDIUM, d.confidence)
    }

    @Test
    fun skipsAmbiguousNameAtSentenceStart() {
        // "Clara" doubles as an adjective; capitalised at a sentence start it carries no signal.
        val result = detect("Clara melhora do quadro clínico.")
        assertTrue(result.none { it.term.equals("Clara", ignoreCase = true) })
    }

    @Test
    fun nameRunsDoNotCrossLineBreaks() {
        val result = detect("Falei com Maria\nResultado do exame normal")
        assertNotNull(result.term("Maria"))
        assertTrue(result.none { it.term.contains("Resultado", ignoreCase = true) })
    }

    @Test
    fun nameRunsStopAtSentencePunctuation() {
        val result = detect("Atendi a Bruna. Depois examinei o joelho.")
        assertNotNull(result.term("Bruna"))
        assertTrue(result.none { it.term.contains("Depois") })
    }

    @Test
    fun ignoresClinicalHeadingPair() {
        val result = detect("Pressão Arterial: 120/80 mmHg")
        assertTrue(result.none { it.term.equals("Pressão Arterial", ignoreCase = true) })
        // The blood-pressure reading must not be misread as a date or phone.
        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresBirthDateLabelAsName() {
        // "Nascimento" is also a surname; the label must not surface as a person.
        val result = detect("Data de Nascimento: 12/05/1980")
        assertTrue(result.none { it.category == RedactionCategory.NAME })
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

    // ---- chat records ----

    @Test
    fun detectsChatSenderWithTimestamp() {
        val d = detect("12/05/2024 14:32 - João: Bom dia, doutora").term("João")
        assertNotNull(d)
        assertEquals(RedactionCategory.NAME, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun detectsWhatsAppBracketTimestampSender() {
        val d = detect("[12/05/24, 14:32:05] Ana Paula: cheguei no laboratório").term("Ana Paula")
        assertNotNull(d)
        assertEquals(Confidence.HIGH, d!!.confidence)
    }

    @Test
    fun detectsChatSenderWithoutTimestampViaDictionary() {
        val d = detect("Maria: tomei o remédio hoje cedo").term("Maria")
        assertNotNull(d)
        assertEquals(Confidence.HIGH, d!!.confidence)
    }

    @Test
    fun ignoresLabAnalyteLineAsSender() {
        val result = detect("Hemoglobina: 13,5 g/dL")
        assertTrue(result.none { it.category == RedactionCategory.NAME })
    }

    @Test
    fun ignoresSectionLabelLineAsSender() {
        val result = detect("Diagnóstico Principal: dor torácica atípica")
        // May surface as a LOW (unchecked) capitalised run, but never as an auto-selected name.
        assertTrue(result.none { it.category == RedactionCategory.NAME && it.confidence != Confidence.LOW })
    }

    // ---- learned terms (offline learning) ----

    @Test
    fun detectsLearnedTermAsHighConfidence() {
        // A name confirmed on a previous document is suggested automatically here, even though no
        // trigger word or chat prefix accompanies it.
        val d = detect("Encaminhado por solicitação externa para Valdomiro avaliar o caso.", listOf("Valdomiro"))
            .term("Valdomiro")
        assertNotNull(d)
        assertEquals(RedactionCategory.NAME, d!!.category)
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun learnedTermMatchesRegardlessOfCase() {
        val d = detect("RELATÓRIO ASSINADO POR VALDOMIRO PEREIRA.", listOf("Valdomiro Pereira"))
            .term("Valdomiro Pereira")
        assertNotNull(d)
        assertEquals(Confidence.HIGH, d!!.confidence)
    }

    @Test
    fun learnedTermDoesNotMatchInsideLongerWord() {
        // Whole-word only: a learned "Ana" must not redact "Anamnese".
        val result = detect("Anamnese completa sem alterações.", listOf("Ana"))
        assertTrue(result.none { it.term.equals("Ana", ignoreCase = true) })
    }

    @Test
    fun learnedTermMatchesTextWithStrippedDiacritics() {
        // PDF extraction sometimes strips accents: a learned "João" must still hit "Joao".
        val d = detect("Paciente Joao compareceu à consulta.", listOf("João"))
            .term("João")
        assertNotNull(d)
        assertEquals(Confidence.HIGH, d!!.confidence)
    }

    @Test
    fun unaccentedLearnedTermMatchesAccentedText() {
        val d = detect("Paciente João compareceu à consulta.", listOf("Joao"))
            .term("Joao")
        assertNotNull(d)
    }

    @Test
    fun absentLearnedTermIsNotSuggested() {
        val result = detect("Hemograma dentro dos parâmetros normais.", listOf("Valdomiro"))
        assertTrue(result.none { it.term.equals("Valdomiro", ignoreCase = true) })
    }

    // ---- general behaviour ----

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

    @Test
    fun deduplicatesCaseInsensitivelyKeepingStrongest() {
        val result = detect("Paciente: Bruna Costa. bruna costa retornou ao consultório.")
        val matches = result.filter { it.term.equals("Bruna Costa", ignoreCase = true) }
        assertEquals(1, matches.size)
        assertEquals(Confidence.HIGH, matches.first().confidence)
    }
}
