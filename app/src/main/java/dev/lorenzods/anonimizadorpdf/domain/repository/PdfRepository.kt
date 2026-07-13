package dev.lorenzods.anonimizadorpdf.domain.repository

import android.net.Uri
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
import dev.lorenzods.anonimizadorpdf.domain.model.Folder
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import kotlinx.coroutines.flow.Flow

interface PdfRepository {
    /**
     * All documents for list screens. **Metadata only**: [PdfDocument.extractedText] is empty in
     * these emissions (loading every text into each list update is a memory/crash hazard) — fetch
     * a single document via [observeDocument]/[getDocument] when the text is needed.
     */
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

    // --- Organization ---

    fun observeFolders(): Flow<List<Folder>>
    suspend fun createFolder(name: String): Long
    suspend fun renameFolder(folderId: Long, name: String)
    /** Deletes a folder; its documents are un-filed (moved back to the root), never deleted. */
    suspend fun deleteFolder(folderId: Long)

    /** Sets a custom display name. Blank/null clears it, reverting to the original filename. */
    suspend fun renameDocument(documentId: Long, name: String?)
    suspend fun moveDocuments(documentIds: List<Long>, folderId: Long?)
    suspend fun setFavorite(documentId: Long, favorite: Boolean)

    /**
     * Merges the given documents — in the order supplied — into a single new PDF, copies it into
     * app-internal storage as a fresh RAW document, and returns its id. Fully offline (pdfbox).
     */
    suspend fun combinePdfs(documentIds: List<Long>, outputName: String): Long

    suspend fun saveAnonymizedVersion(version: AnonymizedVersion): Long
    suspend fun deleteAnonymizedVersion(version: AnonymizedVersion)
    fun observeAnonymizedVersions(documentId: Long): Flow<List<AnonymizedVersion>>

    /**
     * Every saved anonymized version across all documents — backs the home dashboard stats.
     * **Metadata only**: [AnonymizedVersion.anonymizedText] is empty in these emissions; use
     * [observeAnonymizedVersions] for a document's full versions.
     */
    fun observeAllAnonymizedVersions(): Flow<List<AnonymizedVersion>>
}
