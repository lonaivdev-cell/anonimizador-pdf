package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository

/**
 * Merges several documents into one PDF, in the given order, returning the new document's id.
 * The merge runs fully on-device (pdfbox) — no network, no shared storage.
 */
class CombinePdfsUseCase(
    private val pdfRepository: PdfRepository,
) {
    suspend operator fun invoke(documentIds: List<Long>, outputName: String): Long =
        pdfRepository.combinePdfs(documentIds, outputName)
}
