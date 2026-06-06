package dev.lorenzods.anonimizadorpdf

import dev.lorenzods.anonimizadorpdf.domain.usecase.TextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test
    fun blankTextYieldsNoChunks() {
        assertEquals(emptyList<String>(), TextChunker.chunk("", 100))
        assertEquals(emptyList<String>(), TextChunker.chunk("   ", 100))
    }

    @Test
    fun shortTextIsSingleChunk() {
        assertEquals(listOf("abc"), TextChunker.chunk("abc", 100))
    }

    @Test
    fun packsLinesWithoutExceedingLimitAndPreservesContent() {
        val text = "linha1\nlinha2\nlinha3"
        val chunks = TextChunker.chunk(text, 13)
        assertTrue(chunks.all { it.length <= 13 })
        assertEquals(text, chunks.joinToString("\n"))
    }

    @Test
    fun hardSplitsAnOverlongLine() {
        val text = "x".repeat(250)
        val chunks = TextChunker.chunk(text, 100)
        assertEquals(listOf(100, 100, 50), chunks.map { it.length })
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun everyChunkStaysWithinLimit() {
        val text = (1..50).joinToString("\n") { "Linha numero $it com texto clinico." }
        val chunks = TextChunker.chunk(text, 80)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= 80 })
    }
}
