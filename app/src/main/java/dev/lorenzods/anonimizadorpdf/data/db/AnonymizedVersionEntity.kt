package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "anonymized_versions",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentDocumentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentDocumentId")],
)
data class AnonymizedVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentDocumentId: Long,
    val anonymizedText: String,
    val redactedTerms: List<String>,
    val createdTimestamp: Long,
)
