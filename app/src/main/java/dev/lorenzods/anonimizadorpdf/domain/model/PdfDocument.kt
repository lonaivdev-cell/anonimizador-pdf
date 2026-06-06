package dev.lorenzods.anonimizadorpdf.domain.model

data class PdfDocument(
    val id: Long = 0,
    val originalFilename: String,
    val importTimestamp: Long,
    val internalPath: String,
    val extractedText: String,
    val pageCount: Int,
    val status: DocumentStatus,
)
