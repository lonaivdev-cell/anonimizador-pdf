package dev.lorenzods.anonimizadorpdf.domain.model

data class PdfDocument(
    val id: Long = 0,
    val originalFilename: String,
    val importTimestamp: Long,
    val internalPath: String,
    val extractedText: String,
    val pageCount: Int,
    val status: DocumentStatus,
    val customName: String? = null,
    val folderId: Long? = null,
    val isFavorite: Boolean = false,
) {
    /** User-facing name: the custom rename when set, otherwise the original filename. */
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: originalFilename
}
