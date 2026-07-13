package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Dashboard projection of [AnonymizedVersionEntity] without `anonymizedText` — the all-versions
 * observers only need counts and term lists, and pulling every anonymized text into memory risks
 * `SQLiteBlobTooBigException` on large documents (see [PdfDocumentMetaRow]).
 */
data class AnonymizedVersionMetaRow(
    val id: Long,
    val parentDocumentId: Long,
    val redactedTerms: List<String>,
    val createdTimestamp: Long,
)

@Dao
interface AnonymizedVersionDao {

    @Query("SELECT * FROM anonymized_versions WHERE parentDocumentId = :documentId ORDER BY createdTimestamp DESC")
    fun observeForDocument(documentId: Long): Flow<List<AnonymizedVersionEntity>>

    @Query(
        "SELECT id, parentDocumentId, redactedTerms, createdTimestamp FROM anonymized_versions " +
            "ORDER BY createdTimestamp DESC",
    )
    fun observeAllMeta(): Flow<List<AnonymizedVersionMetaRow>>

    @Insert
    suspend fun insert(entity: AnonymizedVersionEntity): Long

    @Delete
    suspend fun delete(entity: AnonymizedVersionEntity)

    @Query("DELETE FROM anonymized_versions")
    suspend fun deleteAll()
}
