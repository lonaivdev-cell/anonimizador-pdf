package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {

    @Query("SELECT * FROM pdf_documents ORDER BY importTimestamp DESC")
    fun observeAll(): Flow<List<PdfDocumentEntity>>

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
