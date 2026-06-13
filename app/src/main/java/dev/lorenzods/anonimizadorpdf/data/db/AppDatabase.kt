package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PdfDocumentEntity::class, AnonymizedVersionEntity::class, FolderEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun anonymizedVersionDao(): AnonymizedVersionDao
    abstract fun folderDao(): FolderDao
}
