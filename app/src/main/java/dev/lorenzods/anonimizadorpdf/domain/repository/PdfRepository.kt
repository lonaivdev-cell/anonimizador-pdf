package dev.lorenzods.anonimizadorpdf.domain.repository

import android.net.Uri
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    fun observeDocuments(): Flow<List<PdfDocument>>
    fun observeDocument(id: Long): Flow<PdfDocument?>
    suspend fun getDocument(id: Long): PdfDocument?

    /** Copies the picked PDF into app-internal storage and inserts a RAW record. Returns the new id. */
    suspend fun importPdf(uri: Uri, displayName: String?): Long

    /** Extracts text from the imported document, emitting progress; updates the record on success. */
    fun extractText(documentId: Long): Flow<ExtractionProgress>

    suspend fun updateStatus(documentId: Long, status: DocumentStatus)
    suspend fun deleteDocument(document: PdfDocument)
    suspend fun deleteAll()

    suspend fun saveAnonymizedVersion(version: AnonymizedVersion): Long
    fun observeAnonymizedVersions(documentId: Long): Flow<List<AnonymizedVersion>>
}
