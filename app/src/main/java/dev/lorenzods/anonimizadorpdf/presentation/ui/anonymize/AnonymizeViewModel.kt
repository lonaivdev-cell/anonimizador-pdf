package dev.lorenzods.anonimizadorpdf.presentation.ui.anonymize

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.data.preferences.AppPreferences
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.DocumentStatus
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmNotReadyException
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.LlmResponseParser
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

data class RedactionChip(val term: String, val selected: Boolean)

data class AnonymizeUiState(
    val document: PdfDocument? = null,
    val mode: AnonymizeMode = AnonymizeMode.AUTO,
    val modelAvailable: Boolean = false,
    val generating: Boolean = false,
    val progress: String? = null,
    val streamingText: String = "",
    val chips: List<RedactionChip> = emptyList(),
    val previewText: String? = null,
    val errorText: String? = null,
) {
    val selectedTerms: List<String> get() = chips.filter { it.selected }.map { it.term }
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

    init {
        viewModelScope.launch {
            val doc = pdfRepository.getDocument(docId)
            val available = llmRepository.isModelAvailable()
            _uiState.update { it.copy(document = doc, modelAvailable = available) }
        }
    }

    fun setMode(mode: AnonymizeMode) = _uiState.update { it.copy(mode = mode) }

    fun clearError() = _uiState.update { it.copy(errorText = null) }

    fun generate() {
        val doc = _uiState.value.document ?: return
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
        _uiState.update { it.copy(generating = false) }
    }

    fun toggleChip(term: String) = _uiState.update { state ->
        state.copy(chips = state.chips.map { if (it.term == term) it.copy(selected = !it.selected) else it })
    }

    fun addManualTerm(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { state ->
            if (state.chips.any { it.term.equals(trimmed, ignoreCase = true) }) {
                state
            } else {
                state.copy(chips = state.chips + RedactionChip(trimmed, selected = true))
            }
        }
    }

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
            if (key in seen) null else { seen.add(key); RedactionChip(term, selected = true) }
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
