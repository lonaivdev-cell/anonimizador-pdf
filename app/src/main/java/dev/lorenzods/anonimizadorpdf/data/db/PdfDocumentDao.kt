package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow

/**
 * List-screen projection of [PdfDocumentEntity] without `extractedText`. Loading the full text of
 * every document into each list emission is both a memory hazard and a hard crash risk: a single
 * row whose text exceeds the 2 MB CursorWindow throws `SQLiteBlobTooBigException`. List screens
 * only need metadata; the text is fetched per-document by id.
 */
data class PdfDocumentMetaRow(
    val id: Long,
    val originalFilename: String,
    val importTimestamp: Long,
    val internalPath: String,
    val pageCount: Int,
    val status: DocumentStatus,
    val customName: String?,
    val folderId: Long?,
    val isFavorite: Boolean,
)

@Dao
interface PdfDocumentDao {

    @Query(
        "SELECT id, originalFilename, importTimestamp, internalPath, pageCount, status, " +
            "customName, folderId, isFavorite FROM pdf_documents ORDER BY importTimestamp DESC",
    )
    fun observeAllMeta(): Flow<List<PdfDocumentMetaRow>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    fun observeById(id: Long): Flow<PdfDocumentEntity?>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    suspend fun getById(id: Long): PdfDocumentEntity?

    @Insert
    suspend fun insert(entity: PdfDocumentEntity): Long

    @Update
    suspend fun update(entity: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DocumentStatus)

    @Query("UPDATE pdf_documents SET customName = :name WHERE id = :id")
    suspend fun updateCustomName(id: Long, name: String?)

    @Query("UPDATE pdf_documents SET folderId = :folderId WHERE id = :id")
    suspend fun updateFolder(id: Long, folderId: Long?)

    @Query("UPDATE pdf_documents SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    /** Un-files every document in a folder — used before the folder itself is deleted. */
    @Query("UPDATE pdf_documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)

    @Delete
    suspend fun delete(entity: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents")
    suspend fun deleteAll()
}
