package dev.lorenzods.anonimizadorpdf.data.db

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
)
