package dev.lorenzods.anonimizadorpdf.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lorenzods.anonimizadorpdf.data.db.AnonymizedVersionDao
import dev.lorenzods.anonimizadorpdf.data.db.PdfDocumentDao
import dev.lorenzods.anonimizadorpdf.data.db.PdfDocumentEntity
import dev.lorenzods.anonimizadorpdf.data.db.toDomain
import dev.lorenzods.anonimizadorpdf.data.db.toEntity
import dev.lorenzods.anonimizadorpdf.di.IoDispatcher
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionError
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
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
        } catch (e: IOException) {
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

    companion object {
        private const val TAG = "PdfRepository"
    }
}
