package dev.lorenzods.anonimizadorpdf.presentation.ui.home

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.data.preferences.AppPreferences
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionError
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import dev.lorenzods.anonimizadorpdf.domain.usecase.ExtractTextUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.ImportPdfUseCase
import dev.lorenzods.anonimizadorpdf.presentation.ui.library.ExtractionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val totalDocuments: Int = 0,
    val anonymizedDocuments: Int = 0,
    val termsRemoved: Int = 0,
    val modelAvailable: Boolean = false,
    val recent: List<PdfDocument> = emptyList(),
    val loading: Boolean = true,
    val extraction: ExtractionState? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val preferences: AppPreferences,
    private val importPdf: ImportPdfUseCase,
    private val extractText: ExtractTextUseCase,
) : ViewModel() {

    private val extraction = MutableStateFlow<ExtractionState?>(null)

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val events: SharedFlow<Int> = _events.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        pdfRepository.observeDocuments(),
        pdfRepository.observeAllAnonymizedVersions(),
        preferences.modelPath.map { !it.isNullOrBlank() },
        extraction,
    ) { docs, versions, modelAvailable, extr ->
        HomeUiState(
            totalDocuments = docs.size,
            anonymizedDocuments = docs.count { it.status == DocumentStatus.ANONYMIZED },
            termsRemoved = versions.sumOf { it.redactedTerms.size },
            modelAvailable = modelAvailable,
            recent = docs.take(5),
            loading = false,
            extraction = extr,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun importAndExtract(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val total = uris.size
            uris.forEachIndexed { index, uri ->
                val docNumber = index + 1
                val id = try {
                    importPdf(uri, null)
                } catch (e: Exception) {
                    emit(R.string.error_import)
                    return@forEachIndexed
                }
                extractText(id).collect { progress ->
                    when (progress) {
                        is ExtractionProgress.InProgress ->
                            extraction.value = ExtractionState(
                                current = progress.currentPage,
                                total = progress.totalPages,
                                docIndex = docNumber,
                                docCount = total,
                            )

                        is ExtractionProgress.Success -> Unit

                        is ExtractionProgress.Error -> emit(
                            when (progress.type) {
                                ExtractionError.NO_TEXT -> R.string.error_no_text
                                ExtractionError.ENCRYPTED -> R.string.error_encrypted
                                ExtractionError.IO_ERROR -> R.string.error_extraction
                            },
                        )
                    }
                }
            }
            extraction.value = null
        }
    }

    private suspend fun emit(@StringRes resId: Int) = _events.emit(resId)
}
