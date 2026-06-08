package dev.lorenzods.anonimizadorpdf.presentation.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import dev.lorenzods.anonimizadorpdf.presentation.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pdfRepository: PdfRepository,
) : ViewModel() {

    private val docId = MutableStateFlow<Long?>(savedStateHandle.get<Long>(Screen.Viewer.ARG_DOC_ID))

    val document: StateFlow<PdfDocument?> = docId
        .flatMapLatest { id -> if (id == null) flowOf(null) else pdfRepository.observeDocument(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val versions: StateFlow<List<AnonymizedVersion>> = docId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else pdfRepository.observeAnonymizedVersions(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Used by the Library detail pane, which supplies the id at runtime rather than via nav args. */
    fun setDocument(id: Long) {
        if (docId.value != id) docId.value = id
    }

    fun deleteVersion(version: AnonymizedVersion) {
        viewModelScope.launch { pdfRepository.deleteAnonymizedVersion(version) }
    }
}
