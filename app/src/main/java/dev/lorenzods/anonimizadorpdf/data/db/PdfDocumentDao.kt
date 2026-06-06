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

    @Delete
    suspend fun delete(entity: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents")
    suspend fun deleteAll()
}
