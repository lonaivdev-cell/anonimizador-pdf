package dev.lorenzods.anonimizadorpdf.domain.model

/** Progress events emitted while extracting text from a PDF. */
sealed interface ExtractionProgress {
    data class InProgress(val currentPage: Int, val totalPages: Int) : ExtractionProgress
    data class Success(val text: String, val pageCount: Int) : ExtractionProgress
    data class Error(val type: ExtractionError) : ExtractionProgress
}

enum class ExtractionError {
    /** PDF parsed but contained no extractable text (likely scanned-only). */
    NO_TEXT,

    /** PDF is password-protected and cannot be opened without the password. */
    ENCRYPTED,

    /** I/O or parsing failure. */
    IO_ERROR,
}
