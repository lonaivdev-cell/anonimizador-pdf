package dev.lorenzods.anonimizadorpdf.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.lorenzods.anonimizadorpdf.data.db.AnonymizedVersionDao
import dev.lorenzods.anonimizadorpdf.data.db.AppDatabase
import dev.lorenzods.anonimizadorpdf.data.db.FolderDao
import dev.lorenzods.anonimizadorpdf.data.db.MIGRATION_1_2
import dev.lorenzods.anonimizadorpdf.data.db.PdfDocumentDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "anonimizador.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun providePdfDocumentDao(database: AppDatabase): PdfDocumentDao = database.pdfDocumentDao()

    @Provides
    fun provideAnonymizedVersionDao(database: AppDatabase): AnonymizedVersionDao =
        database.anonymizedVersionDao()

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()
}
