package dev.lorenzods.anonimizadorpdf.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lorenzods.anonimizadorpdf.data.repository.LlmRepositoryImpl
import dev.lorenzods.anonimizadorpdf.data.repository.PdfRepositoryImpl
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPdfRepository(impl: PdfRepositoryImpl): PdfRepository

    @Binds
    @Singleton
    abstract fun bindLlmRepository(impl: LlmRepositoryImpl): LlmRepository
}
