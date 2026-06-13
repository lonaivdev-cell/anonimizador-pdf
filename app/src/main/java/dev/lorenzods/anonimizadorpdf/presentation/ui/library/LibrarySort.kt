package dev.lorenzods.anonimizadorpdf.presentation.ui.library

import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument

/** How the library list is ordered. Favorites are always pinned above everything else. */
enum class SortMode {
    RECENT,
    OLDEST,
    NAME_ASC,
    NAME_DESC,
    PAGES_DESC,
    PAGES_ASC,
    ;

    companion object {
        fun fromName(value: String?): SortMode =
            entries.firstOrNull { it.name == value } ?: RECENT
    }
}

/**
 * Orders [docs] by [mode], with favorited documents pinned to the top (still ordered among
 * themselves by the same mode). Pure function — covered by unit tests.
 */
fun sortDocuments(docs: List<PdfDocument>, mode: SortMode): List<PdfDocument> {
    val byMode: Comparator<PdfDocument> = when (mode) {
        SortMode.RECENT -> compareByDescending { it.importTimestamp }
        SortMode.OLDEST -> compareBy { it.importTimestamp }
        SortMode.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        SortMode.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        SortMode.PAGES_DESC -> compareByDescending { it.pageCount }
        SortMode.PAGES_ASC -> compareBy { it.pageCount }
    }
    val favoritesFirst = compareByDescending<PdfDocument> { it.isFavorite }
        .then(byMode)
        // Stable tie-breaker so equal keys keep a deterministic order.
        .thenByDescending { it.id }
    return docs.sortedWith(favoritesFirst)
}
