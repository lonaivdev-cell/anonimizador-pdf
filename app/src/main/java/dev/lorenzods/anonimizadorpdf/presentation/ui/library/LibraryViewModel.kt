package dev.lorenzods.anonimizadorpdf.presentation.ui.library

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionError
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import dev.lorenzods.anonimizadorpdf.domain.usecase.ExtractTextUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.ImportPdfUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatusFilter { ALL, RAW, PROCESSED, ANONYMIZED }

data class ExtractionState(val current: Int, val total: Int)

data class LibraryUiState(
    val documents: List<PdfDocument> = emptyList(),
    val query: String = "",
    val filter: StatusFilter = StatusFilter.ALL,
    val loading: Boolean = true,
    val extraction: ExtractionState? = null,
)

sealed interface LibraryEvent {
    data class Message(@StringRes val resId: Int) : LibraryEvent
    data class UndoDelete(val document: PdfDocument) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val importPdf: ImportPdfUseCase,
    private val extractText: ExtractTextUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(StatusFilter.ALL)
    private val extraction = MutableStateFlow<ExtractionState?>(null)
    private val hiddenIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        pdfRepository.observeDocuments(),
        query,
        filter,
        extraction,
        hiddenIds,
    ) { docs, q, f, extr, hidden ->
        val filtered = docs.filter { doc ->
            doc.id !in hidden &&
                (q.isBlank() || doc.originalFilename.contains(q, ignoreCase = true)) &&
                (f == StatusFilter.ALL || doc.status.name == f.name)
        }
        LibraryUiState(documents = filtered, query = q, filter = f, loading = false, extraction = extr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: StatusFilter) {
        filter.value = value
    }

    fun importAndExtract(uri: Uri) {
        viewModelScope.launch {
            val id = try {
                importPdf(uri, null)
            } catch (e: Exception) {
                _events.emit(LibraryEvent.Message(R.string.error_import))
                return@launch
            }
            extractText(id).collect { progress ->
                when (progress) {
                    is ExtractionProgress.InProgress ->
                        extraction.value = ExtractionState(progress.currentPage, progress.totalPages)

                    is ExtractionProgress.Success -> extraction.value = null

                    is ExtractionProgress.Error -> {
                        extraction.value = null
                        _events.emit(
                            LibraryEvent.Message(
                                if (progress.type == ExtractionError.NO_TEXT) {
                                    R.string.error_no_text
                                } else {
                                    R.string.error_extraction
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Hides the row immediately and asks the UI to show an undo snackbar. */
    fun requestDelete(document: PdfDocument) {
        hiddenIds.update { it + document.id }
        viewModelScope.launch { _events.emit(LibraryEvent.UndoDelete(document)) }
    }

    fun undoDelete(document: PdfDocument) {
        hiddenIds.update { it - document.id }
    }

    fun confirmDelete(document: PdfDocument) {
        viewModelScope.launch {
            pdfRepository.deleteDocument(document)
            hiddenIds.update { it - document.id }
        }
    }
}
