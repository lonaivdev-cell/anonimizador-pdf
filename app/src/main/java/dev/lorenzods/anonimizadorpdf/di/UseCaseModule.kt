package dev.lorenzods.anonimizadorpdf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.ExtractTextUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.ImportPdfUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.ReviewSuggestionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.SuggestRedactionsUseCase

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideImportPdfUseCase(repository: PdfRepository): ImportPdfUseCase =
        ImportPdfUseCase(repository)

    @Provides
    fun provideExtractTextUseCase(repository: PdfRepository): ExtractTextUseCase =
        ExtractTextUseCase(repository)

    @Provides
    fun provideSuggestRedactionsUseCase(repository: LlmRepository): SuggestRedactionsUseCase =
        SuggestRedactionsUseCase(repository)

    @Provides
    fun provideReviewSuggestionsUseCase(repository: LlmRepository): ReviewSuggestionsUseCase =
        ReviewSuggestionsUseCase(repository)

    @Provides
    fun provideApplyRedactionsUseCase(): ApplyRedactionsUseCase = ApplyRedactionsUseCase()
}
