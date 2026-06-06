package dev.lorenzods.anonimizadorpdf.domain.usecase

import android.net.Uri
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository

class ImportPdfUseCase(
    private val pdfRepository: PdfRepository,
) {
    suspend operator fun invoke(uri: Uri, displayName: String?): Long =
        pdfRepository.importPdf(uri, displayName)
}
