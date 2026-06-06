package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import kotlinx.coroutines.flow.Flow

class ExtractTextUseCase(
    private val pdfRepository: PdfRepository,
) {
    operator fun invoke(documentId: Long): Flow<ExtractionProgress> =
        pdfRepository.extractText(documentId)
}
