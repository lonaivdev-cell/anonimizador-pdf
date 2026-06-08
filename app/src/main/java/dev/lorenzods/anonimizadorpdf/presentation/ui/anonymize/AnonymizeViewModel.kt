package dev.lorenzods.anonimizadorpdf.presentation.ui.anonymize

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.data.preferences.AppPreferences
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.Confidence
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.model.RedactionCategory
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmNotReadyException
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.LlmResponseParser
import dev.lorenzods.anonimizadorpdf.domain.usecase.PiiDetector
import dev.lorenzods.anonimizadorpdf.domain.usecase.RedactionClassifier
import dev.lorenzods.anonimizadorpdf.domain.usecase.ReviewSuggestionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.SuggestRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.TextChunker
import dev.lorenzods.anonimizadorpdf.presentation.navigation.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AnonymizeMode { AUTO, MANUAL }

data class RedactionChip(
    val term: String,
    val selected: Boolean,
    val category: RedactionCategory = RedactionCategory.OTHER,
    val confidence: Confidence = Confidence.MEDIUM,
    /** True when the LLM review judged this term non-sensitive (kept visible but deselected). */
    val filteredByAi: Boolean = false,
)

data class AnonymizeUiState(
    val document: PdfDocument? = null,
    val mode: AnonymizeMode = AnonymizeMode.AUTO,
    val modelAvailable: Boolean = false,
    val generating: Boolean = false,
    val reviewing: Boolean = false,
    val progress: String? = null,
    val streamingText: String = "",
    val chips: List<RedactionChip> = emptyList(),
    val previewText: String? = null,
    val errorText: String? = null,
) {
    val selectedTerms: List<String> get() = chips.filter { it.selected }.map { it.term }
    val selectedCount: Int get() = chips.count { it.selected }
    val busy: Boolean get() = generating || reviewing
}

sealed interface AnonymizeEvent {
    data class Message(@StringRes val resId: Int) : AnonymizeEvent
}

@HiltViewModel
class AnonymizeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pdfRepository: PdfRepository,
    private val preferences: AppPreferences,
    private val suggestRedactions: SuggestRedactionsUseCase,
    private val reviewSuggestions: ReviewSuggestionsUseCase,
    private val applyRedactions: ApplyRedactionsUseCase,
    private val llmRepository: LlmRepository,
) : ViewModel() {

    private val docId: Long = checkNotNull(savedStateHandle.get<Long>(Screen.Anonymize.ARG_DOC_ID))
    private val initialManual: Boolean = savedStateHandle.get<Boolean>(Screen.Anonymize.ARG_MANUAL) ?: false

    private val _uiState = MutableStateFlow(
        AnonymizeUiState(mode = if (initialManual) AnonymizeMode.MANUAL else AnonymizeMode.AUTO),
    )
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AnonymizeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AnonymizeEvent> = _events.asSharedFlow()

    private var generateJob: Job? = null
    private var reviewJob: Job? = null

    init {
        viewModelScope.launch {
            val doc = pdfRepository.getDocument(docId)
            val available = llmRepository.isModelAvailable()
            _uiState.update { it.copy(document = doc, modelAvailable = available) }
            // Stage 1: instant, offline candidate detection.
            if (doc != null) applyDetections(PiiDetector.detect(doc.extractedText))
        }
    }

    fun setMode(mode: AnonymizeMode) = _uiState.update { it.copy(mode = mode) }

    fun clearError() = _uiState.update { it.copy(errorText = null) }

    /** Re-runs the offline detector, merging any new candidates without disturbing existing chips. */
    fun runDetection() {
        val doc = _uiState.value.document ?: return
        applyDetections(PiiDetector.detect(doc.extractedText))
    }

    private fun applyDetections(detections: List<PiiDetector.Detection>) {
        _uiState.update { state ->
            val seen = state.chips.map { it.term.lowercase() }.toMutableSet()
            val additions = detections.mapNotNull { d ->
                val key = d.term.lowercase()
                if (key in seen) {
                    null
                } else {
                    seen.add(key)
                    RedactionChip(
                        term = d.term,
                        selected = d.confidence != Confidence.LOW,
                        category = d.category,
                        confidence = d.confidence,
                    )
                }
            }
            state.copy(chips = state.chips + additions)
        }
    }

    /** Stage 2a: LLM reviews the candidate list and deselects (but keeps) non-sensitive terms. */
    fun reviewWithLlm() {
        val terms = _uiState.value.chips.map { it.term }
        if (terms.isEmpty() || _uiState.value.busy) return
        reviewJob?.cancel()
        _uiState.update { it.copy(reviewing = true, errorText = null) }
        reviewJob = viewModelScope.launch {
            try {
                val builder = StringBuilder()
                reviewSuggestions.stream(terms).collect { builder.append(it) }
                val kept = LlmResponseParser.parseTerms(builder.toString()).map { it.lowercase() }.toSet()
                if (kept.isEmpty()) {
                    // The model returned nothing usable — never silently wipe the user's list.
                    _uiState.update { it.copy(reviewing = false) }
                    _events.emit(AnonymizeEvent.Message(R.string.review_no_result))
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        reviewing = false,
                        chips = state.chips.map { chip ->
                            val sensitive = chip.term.lowercase() in kept
                            chip.copy(selected = sensitive, filteredByAi = !sensitive)
                        },
                    )
                }
                _events.emit(AnonymizeEvent.Message(R.string.review_done))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmNotReadyException) {
                _uiState.update { it.copy(reviewing = false, modelAvailable = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(reviewing = false, errorText = e.message ?: "Falha na revisão pelo modelo.")
                }
            }
        }
    }

    /** Stage 2b: optional full-text LLM deep scan to catch candidates the patterns missed. */
    fun deepScan() {
        val doc = _uiState.value.document ?: return
        if (_uiState.value.busy) return
        generateJob?.cancel()
        _uiState.update { it.copy(generating = true, streamingText = "", errorText = null) }
        generateJob = viewModelScope.launch {
            try {
                val systemPrompt = preferences.systemPrompt.first()
                // A small on-device model can't read a whole document at once — analyze it in
                // context-sized chunks and aggregate the suggested terms.
                val chunks = TextChunker.chunk(doc.extractedText, CHUNK_CHARS)
                for ((index, chunk) in chunks.withIndex()) {
                    _uiState.update { it.copy(progress = "${index + 1}/${chunks.size}", streamingText = "") }
                    val builder = StringBuilder()
                    suggestRedactions.stream(systemPrompt, chunk).collect { token ->
                        builder.append(token)
                        _uiState.update { it.copy(streamingText = builder.toString()) }
                    }
                    val terms = LlmResponseParser.parseTerms(builder.toString())
                    if (terms.isNotEmpty()) {
                        _uiState.update { it.copy(chips = mergeChips(it.chips, terms)) }
                    }
                }
                _uiState.update { it.copy(generating = false, progress = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmNotReadyException) {
                _uiState.update { it.copy(generating = false, progress = null, modelAvailable = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        generating = false,
                        progress = null,
                        errorText = e.message ?: "Falha na inferência do modelo.",
                    )
                }
            }
        }
    }

    fun stopGenerating() {
        generateJob?.cancel()
        reviewJob?.cancel()
        _uiState.update { it.copy(generating = false, reviewing = false) }
    }

    fun toggleChip(term: String) = _uiState.update { state ->
        state.copy(
            chips = state.chips.map {
                if (it.term == term) it.copy(selected = !it.selected, filteredByAi = false) else it
            },
        )
    }

    /** Tap-to-redact: tapping a word in the document toggles it as a selected term. */
    fun toggleWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state ->
            val existing = state.chips.firstOrNull { it.term.equals(trimmed, ignoreCase = true) }
            if (existing != null) {
                state.copy(
                    chips = state.chips.map {
                        if (it === existing) it.copy(selected = !it.selected, filteredByAi = false) else it
                    },
                )
            } else {
                state.copy(
                    chips = state.chips + RedactionChip(
                        term = trimmed,
                        selected = true,
                        category = RedactionClassifier.classify(trimmed),
                        confidence = Confidence.HIGH,
                    ),
                )
            }
        }
    }

    fun addManualTerm(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state ->
            if (state.chips.any { it.term.equals(trimmed, ignoreCase = true) }) {
                state
            } else {
                state.copy(
                    chips = state.chips + RedactionChip(
                        term = trimmed,
                        selected = true,
                        category = RedactionClassifier.classify(trimmed),
                        confidence = Confidence.HIGH,
                    ),
                )
            }
        }
    }

    fun selectAll() = _uiState.update { state ->
        state.copy(chips = state.chips.map { it.copy(selected = true, filteredByAi = false) })
    }

    fun clearAll() = _uiState.update { it.copy(chips = emptyList()) }

    fun applyRedactions() {
        val doc = _uiState.value.document ?: return
        val preview = applyRedactions(doc.extractedText, _uiState.value.selectedTerms)
        _uiState.update { it.copy(previewText = preview) }
    }

    fun dismissPreview() = _uiState.update { it.copy(previewText = null) }

    fun save() {
        val doc = _uiState.value.document ?: return
        val preview = _uiState.value.previewText ?: return
        viewModelScope.launch {
            pdfRepository.saveAnonymizedVersion(
                AnonymizedVersion(
                    parentDocumentId = doc.id,
                    anonymizedText = preview,
                    redactedTerms = _uiState.value.selectedTerms,
                    createdTimestamp = System.currentTimeMillis(),
                ),
            )
            pdfRepository.updateStatus(doc.id, DocumentStatus.ANONYMIZED)
            _events.emit(AnonymizeEvent.Message(R.string.anonymized_saved))
        }
    }

    private fun mergeChips(existing: List<RedactionChip>, newTerms: List<String>): List<RedactionChip> {
        val seen = existing.map { it.term.lowercase() }.toMutableSet()
        val additions = newTerms.mapNotNull { term ->
            val key = term.lowercase()
            if (key in seen) {
                null
            } else {
                seen.add(key)
                RedactionChip(
                    term = term,
                    selected = true,
                    category = RedactionClassifier.classify(term),
                    confidence = Confidence.MEDIUM,
                )
            }
        }
        return existing + additions
    }

    override fun onCleared() {
        super.onCleared()
        // Release native LLM resources held by the singleton engine.
        llmRepository.close()
    }

    private companion object {
        // Characters per chunk — keeps prompt + chunk + output within the model's context window
        // (see LlmRepositoryImpl.MAX_TOKENS). ~1500 chars ≈ a few hundred tokens.
        const val CHUNK_CHARS = 1500
    }
}
