package dev.lorenzods.anonimizadorpdf.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lorenzods.anonimizadorpdf.data.db.AnonymizedVersionDao
import dev.lorenzods.anonimizadorpdf.data.db.FolderDao
import dev.lorenzods.anonimizadorpdf.data.db.FolderEntity
import dev.lorenzods.anonimizadorpdf.data.db.PdfDocumentDao
import dev.lorenzods.anonimizadorpdf.data.db.PdfDocumentEntity
import dev.lorenzods.anonimizadorpdf.data.db.toDomain
import dev.lorenzods.anonimizadorpdf.data.db.toEntity
import dev.lorenzods.anonimizadorpdf.di.IoDispatcher
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionError
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
import dev.lorenzods.anonimizadorpdf.domain.model.Folder
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: PdfDocumentDao,
    private val versionDao: AnonymizedVersionDao,
    private val folderDao: FolderDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PdfRepository {

    override fun observeDocuments(): Flow<List<PdfDocument>> =
        documentDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDocument(id: Long): Flow<PdfDocument?> =
        documentDao.observeById(id).map { it?.toDomain() }

    override suspend fun getDocument(id: Long): PdfDocument? = withContext(ioDispatcher) {
        documentDao.getById(id)?.toDomain()
    }

    override suspend fun importPdf(uri: Uri, displayName: String?): Long = withContext(ioDispatcher) {
        val name = displayName
            ?: queryDisplayName(uri)
            ?: "documento_${System.currentTimeMillis()}.pdf"
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val dest = File(dir, "${System.currentTimeMillis()}_${name.sanitizeFilename()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Não foi possível abrir o arquivo selecionado")

        val entity = PdfDocumentEntity(
            originalFilename = name,
            importTimestamp = System.currentTimeMillis(),
            internalPath = dest.absolutePath,
            extractedText = "",
            pageCount = 0,
            status = DocumentStatus.RAW,
        )
        val id = documentDao.insert(entity)
        Log.i(TAG, "imported pdf id=$id (copied to internal storage)")
        id
    }

    override fun extractText(documentId: Long): Flow<ExtractionProgress> = flow {
        val doc = documentDao.getById(documentId)
        if (doc == null) {
            emit(ExtractionProgress.Error(ExtractionError.IO_ERROR))
            return@flow
        }
        try {
            PDDocument.load(File(doc.internalPath)).use { pdf ->
                val pageCount = pdf.numberOfPages
                emit(ExtractionProgress.InProgress(0, pageCount))
                val stripper = PDFTextStripper()
                val builder = StringBuilder()
                for (page in 1..pageCount) {
                    stripper.startPage = page
                    stripper.endPage = page
                    builder.append(stripper.getText(pdf))
                    emit(ExtractionProgress.InProgress(page, pageCount))
                }
                val text = builder.toString()
                if (text.isBlank()) {
                    emit(ExtractionProgress.Error(ExtractionError.NO_TEXT))
                    return@use
                }
                documentDao.update(
                    doc.copy(
                        extractedText = text,
                        pageCount = pageCount,
                        status = DocumentStatus.PROCESSED,
                    ),
                )
                Log.i(TAG, "extraction complete, ${text.length} chars, $pageCount pages")
                emit(ExtractionProgress.Success(text, pageCount))
            }
        } catch (e: Exception) {
            // pdfbox throws RuntimeExceptions on malformed/encrypted PDFs, not just IOExceptions.
            // Never log content or filename — class name only.
            Log.e(TAG, "extraction failed: ${e.javaClass.simpleName}")
            emit(ExtractionProgress.Error(ExtractionError.IO_ERROR))
        }
    }.flowOn(ioDispatcher)

    override suspend fun updateStatus(documentId: Long, status: DocumentStatus) =
        withContext(ioDispatcher) { documentDao.updateStatus(documentId, status) }

    override suspend fun deleteDocument(document: PdfDocument) = withContext(ioDispatcher) {
        runCatching { File(document.internalPath).delete() }
        documentDao.delete(document.toEntity())
    }

    override suspend fun deleteAll() = withContext(ioDispatcher) {
        versionDao.deleteAll()
        documentDao.deleteAll()
        runCatching { File(context.filesDir, "documents").deleteRecursively() }
        Unit
    }

    // --- Organization ---

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun createFolder(name: String): Long = withContext(ioDispatcher) {
        folderDao.insert(FolderEntity(name = name.trim(), createdTimestamp = System.currentTimeMillis()))
    }

    override suspend fun renameFolder(folderId: Long, name: String) = withContext(ioDispatcher) {
        val folder = folderDao.getById(folderId) ?: return@withContext
        folderDao.update(folder.copy(name = name.trim()))
    }

    override suspend fun deleteFolder(folderId: Long) = withContext(ioDispatcher) {
        // Un-file the folder's documents first so they survive at the root, then drop the folder.
        documentDao.clearFolder(folderId)
        folderDao.getById(folderId)?.let { folderDao.delete(it) }
        Unit
    }

    override suspend fun renameDocument(documentId: Long, name: String?) = withContext(ioDispatcher) {
        documentDao.updateCustomName(documentId, name?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun moveDocuments(documentIds: List<Long>, folderId: Long?) =
        withContext(ioDispatcher) {
            documentIds.forEach { documentDao.updateFolder(it, folderId) }
        }

    override suspend fun setFavorite(documentId: Long, favorite: Boolean) =
        withContext(ioDispatcher) { documentDao.updateFavorite(documentId, favorite) }

    override suspend fun combinePdfs(documentIds: List<Long>, outputName: String): Long =
        withContext(ioDispatcher) {
            require(documentIds.isNotEmpty()) { "Nenhum documento selecionado" }
            // Preserve the requested order; skip any ids that no longer resolve.
            val sources = documentIds.mapNotNull { documentDao.getById(it) }
            if (sources.isEmpty()) throw IOException("Nenhum documento de origem encontrado")

            val dir = File(context.filesDir, "documents").apply { mkdirs() }
            val name = outputName.ensurePdfExtension()
            val dest = File(dir, "${System.currentTimeMillis()}_${name.sanitizeFilename()}")

            val merger = PDFMergerUtility().apply {
                destinationFileName = dest.absolutePath
                sources.forEach { addSource(File(it.internalPath)) }
            }
            try {
                // Stream through app-internal temp files so memory stays bounded for large merges,
                // and nothing ever touches shared external storage (offline guarantee).
                merger.mergeDocuments(
                    MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir),
                )
            } catch (e: Exception) {
                runCatching { dest.delete() }
                Log.e(TAG, "merge failed: ${e.javaClass.simpleName}")
                throw e
            }

            val entity = PdfDocumentEntity(
                originalFilename = name,
                importTimestamp = System.currentTimeMillis(),
                internalPath = dest.absolutePath,
                extractedText = "",
                pageCount = 0,
                status = DocumentStatus.RAW,
            )
            val id = documentDao.insert(entity)
            Log.i(TAG, "combined ${sources.size} documents into id=$id")
            id
        }

    override suspend fun saveAnonymizedVersion(version: AnonymizedVersion): Long =
        withContext(ioDispatcher) { versionDao.insert(version.toEntity()) }

    override suspend fun deleteAnonymizedVersion(version: AnonymizedVersion) =
        withContext(ioDispatcher) { versionDao.delete(version.toEntity()) }

    override fun observeAnonymizedVersions(documentId: Long): Flow<List<AnonymizedVersion>> =
        versionDao.observeForDocument(documentId).map { list -> list.map { it.toDomain() } }

    override fun observeAllAnonymizedVersions(): Flow<List<AnonymizedVersion>> =
        versionDao.observeAll().map { list -> list.map { it.toDomain() } }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun String.sanitizeFilename(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)

    private fun String.ensurePdfExtension(): String =
        if (endsWith(".pdf", ignoreCase = true)) this else "$this.pdf"

    companion object {
        private const val TAG = "PdfRepository"
    }
}
