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
import dev.lorenzods.anonimizadorpdf.domain.usecase.OutputFormatter
import dev.lorenzods.anonimizadorpdf.domain.usecase.PiiDetector
import dev.lorenzods.anonimizadorpdf.domain.usecase.RedactionClassifier
import dev.lorenzods.anonimizadorpdf.domain.usecase.ReviewSuggestionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.SuggestRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.domain.usecase.TextChunker
import dev.lorenzods.anonimizadorpdf.presentation.navigation.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    val modelAvailable: Boolean = false,
    val detecting: Boolean = false,
    val generating: Boolean = false,
    val reviewing: Boolean = false,
    val progress: String? = null,
    val streamingText: String = "",
    val chips: List<RedactionChip> = emptyList(),
    /** The redacted text exactly as produced — always the source of truth. */
    val previewRaw: String? = null,
    /** The redacted text after OutputFormatter — shown/exported only while [formatEnabled]. */
    val previewFormatted: String? = null,
    val formatEnabled: Boolean = false,
    val saved: Boolean = false,
    val errorText: String? = null,
) {
    val selectedTerms: List<String> get() = chips.filter { it.selected }.map { it.term }
    val selectedCount: Int get() = chips.count { it.selected }
    val busy: Boolean get() = generating || reviewing
    val previewDisplay: String? get() = if (formatEnabled) previewFormatted ?: previewRaw else previewRaw
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

    private val _uiState = MutableStateFlow(AnonymizeUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AnonymizeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AnonymizeEvent> = _events.asSharedFlow()

    private var generateJob: Job? = null
    private var reviewJob: Job? = null

    init {
        viewModelScope.launch {
            val doc = pdfRepository.getDocument(docId)
            val available = llmRepository.isModelAvailable()
            val formatEnabled = preferences.formatOutput.first()
            _uiState.update {
                it.copy(document = doc, modelAvailable = available, formatEnabled = formatEnabled)
            }
            // Stage 1: instant, offline candidate detection.
            if (doc != null) runDetection()
        }
    }

    fun clearError() = _uiState.update { it.copy(errorText = null) }

    /** Runs the offline detector, merging any new candidates without disturbing existing chips. */
    fun runDetection() {
        val doc = _uiState.value.document ?: return
        if (_uiState.value.detecting) return
        _uiState.update { it.copy(detecting = true) }
        viewModelScope.launch {
            val learned = preferences.learnedTerms.first()
            val detections = withContext(Dispatchers.Default) {
                PiiDetector.detect(doc.extractedText, learned)
            }
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
                state.copy(detecting = false, chips = state.chips + additions)
            }
        }
    }

    /**
     * Stage 2a: the LLM reviews the candidate list and deselects (but keeps) non-sensitive terms.
     *
     * Only semantically ambiguous chips (names, organizations, unclassified terms) are sent — the
     * structured pattern hits (CPF, telefone, e-mail, datas…) are deterministic, so asking a small
     * model to re-judge them adds tokens and risk for zero gain. Long lists go out in small
     * batches; a batch whose answer can't be parsed is left exactly as the user had it.
     */
    fun reviewWithLlm() {
        if (_uiState.value.busy) return
        val reviewable = _uiState.value.chips
            .filter { it.category in REVIEWABLE_CATEGORIES }
            .map { it.term }
        if (reviewable.isEmpty()) {
            _events.tryEmit(AnonymizeEvent.Message(R.string.review_nothing))
            return
        }
        reviewJob?.cancel()
        _uiState.update { it.copy(reviewing = true, errorText = null) }
        reviewJob = viewModelScope.launch {
            try {
                val batches = reviewable.chunked(ReviewSuggestionsUseCase.BATCH_SIZE)
                val judged = mutableSetOf<String>()
                val sensitive = mutableSetOf<String>()
                for ((index, batch) in batches.withIndex()) {
                    if (batches.size > 1) {
                        _uiState.update { it.copy(progress = "${index + 1}/${batches.size}") }
                    }
                    val builder = StringBuilder()
                    reviewSuggestions.stream(batch).collect { builder.append(it) }
                    val keep = LlmResponseParser.parseReviewSelection(builder.toString(), batch)
                    // An empty answer is indistinguishable from a failed one — treat it as "no
                    // opinion" and keep the batch untouched rather than deselect everything.
                    if (keep.isNotEmpty()) {
                        judged += batch.map { it.lowercase() }
                        sensitive += keep.map { it.lowercase() }
                    }
                }
                if (sensitive.isEmpty()) {
                    // The model returned nothing usable — never silently wipe the user's list.
                    _uiState.update { it.copy(reviewing = false, progress = null) }
                    _events.emit(AnonymizeEvent.Message(R.string.review_no_result))
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        reviewing = false,
                        progress = null,
                        chips = state.chips.map { chip ->
                            val key = chip.term.lowercase()
                            if (key !in judged) {
                                chip
                            } else {
                                val isSensitive = key in sensitive
                                chip.copy(selected = isSensitive, filteredByAi = !isSensitive)
                            }
                        },
                    )
                }
                _events.emit(AnonymizeEvent.Message(R.string.review_done))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmNotReadyException) {
                _uiState.update { it.copy(reviewing = false, progress = null, modelAvailable = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        reviewing = false,
                        progress = null,
                        errorText = e.message ?: "Falha na revisão pelo modelo.",
                    )
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
                    // The prompt asks for verbatim copies; anything not present in the chunk is
                    // hallucinated and would only clutter the chip list.
                    val terms = LlmResponseParser.parseTerms(builder.toString())
                        .filter { chunk.contains(it, ignoreCase = true) }
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
        val terms = _uiState.value.selectedTerms
        viewModelScope.launch {
            val (raw, formatted) = withContext(Dispatchers.Default) {
                val redacted = applyRedactions(doc.extractedText, terms)
                redacted to OutputFormatter.organize(redacted)
            }
            _uiState.update {
                it.copy(previewRaw = raw, previewFormatted = formatted, saved = false)
            }
        }
    }

    /** Toggles the LLM-friendly formatting; the choice is remembered for the next document. */
    fun setFormatEnabled(enabled: Boolean) {
        _uiState.update { it.copy(formatEnabled = enabled, saved = false) }
        viewModelScope.launch { preferences.setFormatOutput(enabled) }
    }

    fun dismissPreview() = _uiState.update {
        it.copy(previewRaw = null, previewFormatted = null, saved = false)
    }

    fun save() {
        val doc = _uiState.value.document ?: return
        val text = _uiState.value.previewDisplay ?: return
        if (_uiState.value.saved) return
        viewModelScope.launch {
            pdfRepository.saveAnonymizedVersion(
                AnonymizedVersion(
                    parentDocumentId = doc.id,
                    anonymizedText = text,
                    redactedTerms = _uiState.value.selectedTerms,
                    createdTimestamp = System.currentTimeMillis(),
                ),
            )
            pdfRepository.updateStatus(doc.id, DocumentStatus.ANONYMIZED)
            // Learn the confirmed names/instituições so future documents auto-suggest them. Only
            // these categories generalise across documents — structured IDs (CPF, datas…) are
            // deterministic patterns and a single patient's number won't recur, so they're skipped.
            val learnable = _uiState.value.chips
                .filter { it.selected && it.category in LEARNABLE_CATEGORIES }
                .map { it.term }
            preferences.addLearnedTerms(learnable)
            _uiState.update { it.copy(saved = true) }
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

        // Chip categories where small-model judgement adds value (is this a name or a medical
        // term?). Everything else came from a deterministic pattern and is never sent for review.
        val REVIEWABLE_CATEGORIES = setOf(
            RedactionCategory.NAME,
            RedactionCategory.ORGANIZATION,
            RedactionCategory.OTHER,
        )

        // Categories worth remembering across documents: people and institutions recur, whereas a
        // CPF/RG/telefone is unique to one patient and already caught deterministically.
        val LEARNABLE_CATEGORIES = setOf(
            RedactionCategory.NAME,
            RedactionCategory.ORGANIZATION,
        )
    }
}
