package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalFilename: String,
    val importTimestamp: Long,
    val internalPath: String,
    val extractedText: String,
    val pageCount: Int,
    val status: DocumentStatus,
    /** User-chosen display name (rename). Null falls back to [originalFilename]. */
    val customName: String? = null,
    /** Owning folder, or null when the document is unfiled (lives at the library root). */
    val folderId: Long? = null,
    /** Pinned to the top of the library when true. */
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
)
