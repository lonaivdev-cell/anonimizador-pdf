package dev.lorenzods.anonimizadorpdf.presentation.ui.library

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.data.preferences.AppPreferences
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionError
import dev.lorenzods.anonimizadorpdf.domain.model.ExtractionProgress
import dev.lorenzods.anonimizadorpdf.domain.model.Folder
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import dev.lorenzods.anonimizadorpdf.domain.usecase.CombinePdfsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.ExtractTextUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.ImportPdfUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatusFilter { ALL, RAW, PROCESSED, ANONYMIZED }

data class ExtractionState(
    val current: Int,
    val total: Int,
    val docIndex: Int = 1,
    val docCount: Int = 1,
)

/** A freshly combined PDF, surfaced so the UI can offer share/export right after merging. */
data class CombinedPdf(
    val documentId: Long,
    val path: String,
    val shareName: String,
)

data class LibraryUiState(
    val folders: List<Folder> = emptyList(),
    /** Document count per folder id (excludes documents pending an undo-delete). */
    val folderCounts: Map<Long, Int> = emptyMap(),
    /** The folder currently being browsed, or null at the library root. */
    val currentFolder: Folder? = null,
    val documents: List<PdfDocument> = emptyList(),
    val versionCounts: Map<Long, Int> = emptyMap(),
    val query: String = "",
    val filter: StatusFilter = StatusFilter.ALL,
    val sort: SortMode = SortMode.RECENT,
    /** Ids selected in multi-select mode, in the order they were tapped. */
    val selectedIds: List<Long> = emptyList(),
    val loading: Boolean = true,
    val extraction: ExtractionState? = null,
    val combining: Boolean = false,
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
    val searching: Boolean get() = query.isNotBlank()
}

sealed interface LibraryEvent {
    data class Message(@StringRes val resId: Int) : LibraryEvent
    data class UndoDelete(val document: PdfDocument) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val preferences: AppPreferences,
    private val importPdf: ImportPdfUseCase,
    private val extractText: ExtractTextUseCase,
    private val combinePdfs: CombinePdfsUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(StatusFilter.ALL)
    private val currentFolderId = MutableStateFlow<Long?>(null)
    private val selectedIds = MutableStateFlow<List<Long>>(emptyList())
    private val extraction = MutableStateFlow<ExtractionState?>(null)
    private val combining = MutableStateFlow(false)
    private val hiddenIds = MutableStateFlow<Set<Long>>(emptySet())

    private val sort: StateFlow<SortMode> = preferences.librarySort
        .map { SortMode.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.RECENT)

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    /** Non-null right after a combine, until the share/export prompt is dismissed. */
    val combinedPdf = MutableStateFlow<CombinedPdf?>(null)

    private data class LibraryData(
        val folders: List<Folder>,
        val docs: List<PdfDocument>,
        val versionCounts: Map<Long, Int>,
        val hidden: Set<Long>,
    )

    private data class ViewSettings(
        val query: String,
        val filter: StatusFilter,
        val sort: SortMode,
        val currentFolderId: Long?,
    )

    private data class Transient(
        val selectedIds: List<Long>,
        val extraction: ExtractionState?,
        val combining: Boolean,
    )

    private val content = combine(
        pdfRepository.observeFolders(),
        pdfRepository.observeDocuments(),
        pdfRepository.observeAllAnonymizedVersions(),
        hiddenIds,
    ) { folders, docs, versions, hidden ->
        LibraryData(folders, docs, versions.groupingBy { it.parentDocumentId }.eachCount(), hidden)
    }

    private val viewSettings = combine(query, filter, sort, currentFolderId) { q, f, s, cf ->
        ViewSettings(q, f, s, cf)
    }

    private val transient = combine(selectedIds, extraction, combining) { sel, extr, comb ->
        Transient(sel, extr, comb)
    }

    val uiState: StateFlow<LibraryUiState> = combine(content, viewSettings, transient) { d, v, t ->
        val live = d.docs.filter { it.id !in d.hidden }
        val folderCounts = live.mapNotNull { it.folderId }.groupingBy { it }.eachCount()
        val currentFolder = v.currentFolderId?.let { id -> d.folders.firstOrNull { it.id == id } }

        // Blank query browses by folder scope; a query searches the whole library.
        val scope = when {
            v.query.isNotBlank() -> live
            currentFolder != null -> live.filter { it.folderId == currentFolder.id }
            else -> live.filter { it.folderId == null }
        }
        val searched =
            if (v.query.isBlank()) scope
            else scope.filter { it.displayName.contains(v.query, ignoreCase = true) }
        val statusFiltered =
            searched.filter { v.filter == StatusFilter.ALL || it.status.name == v.filter.name }

        LibraryUiState(
            folders = d.folders,
            folderCounts = folderCounts,
            currentFolder = currentFolder,
            documents = sortDocuments(statusFiltered, v.sort),
            versionCounts = d.versionCounts,
            query = v.query,
            filter = v.filter,
            sort = v.sort,
            selectedIds = t.selectedIds,
            loading = false,
            extraction = t.extraction,
            combining = t.combining,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: StatusFilter) {
        filter.value = value
    }

    fun onSortChange(mode: SortMode) {
        viewModelScope.launch { preferences.setLibrarySort(mode.name) }
    }

    // --- Folder navigation & CRUD ---

    fun openFolder(folderId: Long) {
        clearSelection()
        currentFolderId.value = folderId
    }

    fun exitFolder() {
        clearSelection()
        currentFolderId.value = null
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { pdfRepository.createFolder(trimmed) }
    }

    fun renameFolder(folder: Folder, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { pdfRepository.renameFolder(folder.id, trimmed) }
    }

    fun deleteFolder(folder: Folder) {
        if (currentFolderId.value == folder.id) currentFolderId.value = null
        viewModelScope.launch { pdfRepository.deleteFolder(folder.id) }
    }

    /** Creates a folder and immediately moves the given documents into it. */
    fun createFolderAndMove(name: String, documentIds: List<Long>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = pdfRepository.createFolder(trimmed)
            pdfRepository.moveDocuments(documentIds, id)
            clearSelection()
        }
    }

    // --- Document organization ---

    fun renameDocument(documentId: Long, name: String) {
        viewModelScope.launch { pdfRepository.renameDocument(documentId, name) }
    }

    fun toggleFavorite(document: PdfDocument) {
        viewModelScope.launch { pdfRepository.setFavorite(document.id, !document.isFavorite) }
    }

    fun setFavorite(documentIds: List<Long>, favorite: Boolean) {
        viewModelScope.launch {
            documentIds.forEach { pdfRepository.setFavorite(it, favorite) }
            clearSelection()
        }
    }

    fun moveDocuments(documentIds: List<Long>, folderId: Long?) {
        viewModelScope.launch {
            pdfRepository.moveDocuments(documentIds, folderId)
            clearSelection()
        }
    }

    // --- Multi-select ---

    fun startSelection(documentId: Long) {
        selectedIds.update { if (documentId in it) it else it + documentId }
    }

    fun toggleSelection(documentId: Long) {
        selectedIds.update { if (documentId in it) it - documentId else it + documentId }
    }

    fun clearSelection() {
        selectedIds.value = emptyList()
    }

    // --- Combine ---

    /** Merges [orderedIds] (in the given order) into a new PDF, extracts it, then offers share/export. */
    fun combineSelected(orderedIds: List<Long>, outputName: String) {
        if (orderedIds.size < 2) return
        viewModelScope.launch {
            combining.value = true
            val newId = try {
                combinePdfs(orderedIds, outputName)
            } catch (e: Exception) {
                combining.value = false
                _events.emit(LibraryEvent.Message(R.string.combine_failed))
                return@launch
            }
            combining.value = false
            clearSelection()
            // Reuse the normal extraction pipeline so the combined PDF gets text + page count.
            extractOne(newId, docIndex = 1, docCount = 1)
            extraction.value = null
            val doc = pdfRepository.getDocument(newId)
            if (doc != null) {
                combinedPdf.value = CombinedPdf(doc.id, doc.internalPath, outputName.ensurePdf())
            }
            _events.emit(LibraryEvent.Message(R.string.combine_done))
        }
    }

    fun dismissCombinedPdf() {
        combinedPdf.value = null
    }

    // --- Delete ---

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

    /** Bulk delete (no per-item undo — the UI confirms first). */
    fun deleteDocuments(documents: List<PdfDocument>) {
        viewModelScope.launch {
            documents.forEach { pdfRepository.deleteDocument(it) }
            clearSelection()
        }
    }

    fun importAndExtract(uri: Uri) = importAndExtract(listOf(uri))

    /**
     * Imports and extracts one or more PDFs **sequentially** — each file is fully parsed before the
     * next begins — so memory and CPU stay bounded regardless of how many are selected at once. A
     * single file that fails to import/extract is reported but does not abort the rest of the batch.
     * New imports land at the root (or the folder currently being browsed).
     */
    fun importAndExtract(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val targetFolder = currentFolderId.value
        viewModelScope.launch {
            val total = uris.size
            uris.forEachIndexed { index, uri ->
                val id = try {
                    importPdf(uri, null)
                } catch (e: Exception) {
                    _events.emit(LibraryEvent.Message(R.string.error_import))
                    return@forEachIndexed
                }
                if (targetFolder != null) pdfRepository.moveDocuments(listOf(id), targetFolder)
                extractOne(id, docIndex = index + 1, docCount = total)
            }
            extraction.value = null
        }
    }

    /** Collects [extractText] for one document, driving the extraction overlay and error events. */
    private suspend fun extractOne(id: Long, docIndex: Int, docCount: Int) {
        extractText(id).collect { progress ->
            when (progress) {
                is ExtractionProgress.InProgress ->
                    extraction.value = ExtractionState(
                        current = progress.currentPage,
                        total = progress.totalPages,
                        docIndex = docIndex,
                        docCount = docCount,
                    )

                is ExtractionProgress.Success -> Unit

                is ExtractionProgress.Error -> _events.emit(
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

    private fun String.ensurePdf(): String =
        if (endsWith(".pdf", ignoreCase = true)) this else "$this.pdf"
}
