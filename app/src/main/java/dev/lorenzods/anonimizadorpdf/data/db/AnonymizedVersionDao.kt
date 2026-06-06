package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnonymizedVersionDao {

    @Query("SELECT * FROM anonymized_versions WHERE parentDocumentId = :documentId ORDER BY createdTimestamp DESC")
    fun observeForDocument(documentId: Long): Flow<List<AnonymizedVersionEntity>>

    @Insert
    suspend fun insert(entity: AnonymizedVersionEntity): Long

    @Query("DELETE FROM anonymized_versions")
    suspend fun deleteAll()
}
