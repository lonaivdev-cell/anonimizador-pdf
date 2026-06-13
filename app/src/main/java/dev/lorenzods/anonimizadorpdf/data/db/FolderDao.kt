package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Insert
    suspend fun insert(entity: FolderEntity): Long

    @Update
    suspend fun update(entity: FolderEntity)

    @Delete
    suspend fun delete(entity: FolderEntity)
}
