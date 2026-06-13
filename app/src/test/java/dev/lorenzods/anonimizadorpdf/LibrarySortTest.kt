package dev.lorenzods.anonimizadorpdf

import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.presentation.ui.library.SortMode
import dev.lorenzods.anonimizadorpdf.presentation.ui.library.sortDocuments
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {

    private fun doc(
        id: Long,
        name: String,
        ts: Long,
        pages: Int = 1,
        favorite: Boolean = false,
        custom: String? = null,
    ) = PdfDocument(
        id = id,
        originalFilename = name,
        importTimestamp = ts,
        internalPath = "/tmp/$id.pdf",
        extractedText = "",
        pageCount = pages,
        status = DocumentStatus.PROCESSED,
        customName = custom,
        isFavorite = favorite,
    )

    private val a = doc(1, "Banana.pdf", ts = 100, pages = 5)
    private val b = doc(2, "abacaxi.pdf", ts = 300, pages = 2)
    private val c = doc(3, "Caju.pdf", ts = 200, pages = 9)

    @Test
    fun recentOrdersNewestFirst() {
        val ids = sortDocuments(listOf(a, b, c), SortMode.RECENT).map { it.id }
        assertEquals(listOf(2L, 3L, 1L), ids)
    }

    @Test
    fun oldestOrdersOldestFirst() {
        val ids = sortDocuments(listOf(a, b, c), SortMode.OLDEST).map { it.id }
        assertEquals(listOf(1L, 3L, 2L), ids)
    }

    @Test
    fun nameAscIsCaseInsensitive() {
        // abacaxi < Banana < Caju regardless of letter case.
        val ids = sortDocuments(listOf(a, b, c), SortMode.NAME_ASC).map { it.id }
        assertEquals(listOf(2L, 1L, 3L), ids)
    }

    @Test
    fun nameDescIsCaseInsensitive() {
        val ids = sortDocuments(listOf(a, b, c), SortMode.NAME_DESC).map { it.id }
        assertEquals(listOf(3L, 1L, 2L), ids)
    }

    @Test
    fun nameSortUsesCustomDisplayName() {
        // A custom rename of "Aaa" must sort the document first even though its filename starts with Z.
        val renamed = doc(9, "Zzz.pdf", ts = 50, custom = "Aaa")
        val ids = sortDocuments(listOf(a, renamed), SortMode.NAME_ASC).map { it.id }
        assertEquals(listOf(9L, 1L), ids)
    }

    @Test
    fun pagesDescOrdersByPageCount() {
        val ids = sortDocuments(listOf(a, b, c), SortMode.PAGES_DESC).map { it.id }
        assertEquals(listOf(3L, 1L, 2L), ids)
    }

    @Test
    fun favoritesArePinnedAboveEveryone() {
        // b is the most recent, but a is favorited, so a must come first under RECENT.
        val favA = a.copy(isFavorite = true)
        val ids = sortDocuments(listOf(favA, b, c), SortMode.RECENT).map { it.id }
        assertEquals(listOf(1L, 2L, 3L), ids)
    }

    @Test
    fun favoritesKeepTheirOwnOrdering() {
        val favA = a.copy(isFavorite = true) // ts 100
        val favC = c.copy(isFavorite = true) // ts 200
        val ids = sortDocuments(listOf(favA, b, favC), SortMode.RECENT).map { it.id }
        // Both favorites pinned (newest favorite first), then the rest.
        assertEquals(listOf(3L, 1L, 2L), ids)
    }
}
