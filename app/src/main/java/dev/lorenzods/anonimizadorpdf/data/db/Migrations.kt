package dev.lorenzods.anonimizadorpdf.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: organization features.
 *
 * Adds the `folders` table and three columns to `pdf_documents` (a custom display name for renaming,
 * a nullable folder reference, and a favorite flag). The DDL is written to match exactly what Room
 * derives from [FolderEntity] / [PdfDocumentEntity] — column types, nullability and the `isFavorite`
 * default — so Room's post-migration schema validation passes. Existing patient documents and
 * anonymized versions are preserved (no destructive fallback).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `folders` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdTimestamp` INTEGER NOT NULL)",
        )
        db.execSQL("ALTER TABLE `pdf_documents` ADD COLUMN `customName` TEXT")
        db.execSQL("ALTER TABLE `pdf_documents` ADD COLUMN `folderId` INTEGER")
        db.execSQL("ALTER TABLE `pdf_documents` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
    }
}
