package dev.lorenzods.anonimizadorpdf.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lorenzods.anonimizadorpdf.data.preferences.AppPreferences
import dev.lorenzods.anonimizadorpdf.di.IoDispatcher
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmNotReadyException
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.nehuatl.llamacpp.LlamaHelper
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM inference. Two engines are supported and chosen by the imported model's extension:
 *
 * - **`.task` / `.litertlm`** → MediaPipe LLM Inference Task API ([LlmInference]).
 * - **`.gguf`** → llama.cpp via the `llamacpp-kotlin` wrapper ([LlamaHelper]).
 *
 * Each engine is created lazily from the model path stored in preferences and reused across calls.
 * Tokens are streamed as incremental deltas, which the caller accumulates.
 */
@Singleton
class LlmRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LlmRepository {

    // --- MediaPipe (.task / .litertlm) engine ---
    @Volatile
    private var engine: LlmInference? = null

    @Volatile
    private var loadedModelPath: String? = null

    // --- llama.cpp (.gguf) engine ---
    private val llamaScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val llamaEvents = MutableSharedFlow<LlamaHelper.LLMEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Volatile
    private var llamaHelper: LlamaHelper? = null

    @Volatile
    private var llamaLoadedPath: String? = null

    override suspend fun isModelAvailable(): Boolean {
        val path = preferences.modelPath.first()
        return !path.isNullOrBlank() && File(path).exists()
    }

    override suspend fun importModel(uri: Uri): String = withContext(ioDispatcher) {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        // Single model slot — remove any previously imported model.
        dir.listFiles()?.forEach { it.delete() }
        val name = queryDisplayName(uri) ?: "model.task"
        val dest = File(dir, name.ifBlank { "model.task" })
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Não foi possível ler o arquivo do modelo")
        // Force re-init on next inference so the new model is loaded.
        close()
        Log.i(TAG, "model imported (${dest.length() / (1024 * 1024)} MB)")
        dest.absolutePath
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    override fun generate(prompt: String): Flow<String> = flow {
        val path = preferences.modelPath.first()
        if (path.isNullOrBlank() || !File(path).exists()) {
            throw LlmNotReadyException("Arquivo do modelo não encontrado")
        }
        val delegate = if (path.substringAfterLast('.').equals("gguf", ignoreCase = true)) {
            generateGguf(prompt, path)
        } else {
            generateMediaPipe(prompt)
        }
        emitAll(delegate)
    }

    // --- MediaPipe path ---

    private suspend fun ensureEngine(): LlmInference = withContext(ioDispatcher) {
        val path = preferences.modelPath.first()
        if (path.isNullOrBlank()) throw LlmNotReadyException("Nenhum modelo configurado")
        if (!File(path).exists()) throw LlmNotReadyException("Arquivo do modelo não encontrado")

        engine?.let { current ->
            if (loadedModelPath == path) return@withContext current
            runCatching { current.close() }
        }

        val options = LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(MAX_TOKENS)
            .build()
        val created = LlmInference.createFromOptions(context, options)
        engine = created
        loadedModelPath = path
        Log.i(TAG, "MediaPipe engine initialized")
        created
    }

    private fun generateMediaPipe(prompt: String): Flow<String> = callbackFlow {
        val activeEngine = ensureEngine()
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTopP(TOP_P)
            .setTemperature(TEMPERATURE)
            .build()
        val session = LlmInferenceSession.createFromOptions(activeEngine, sessionOptions)
        session.addQueryChunk(prompt)
        session.generateResponseAsync { partial, done ->
            trySend(partial)
            if (done) close()
        }
        awaitClose { runCatching { session.close() } }
    }.flowOn(ioDispatcher)

    // --- llama.cpp (.gguf) path ---

    private suspend fun ensureLlama(path: String): LlamaHelper = withContext(ioDispatcher) {
        llamaHelper?.let { current ->
            if (llamaLoadedPath == path) return@withContext current
            runCatching { current.release() }
            llamaHelper = null
            llamaLoadedPath = null
        }

        val helper = LlamaHelper(context.contentResolver, llamaScope, llamaEvents)
        val ready = CompletableDeferred<Unit>()
        // Loading is asynchronous: completion arrives via the load callback, failures via an Error
        // event. Watch both so a failed load surfaces instead of hanging until the timeout.
        val watcher = llamaScope.launch {
            llamaEvents.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Loaded -> ready.complete(Unit)
                    is LlamaHelper.LLMEvent.Error ->
                        ready.completeExceptionally(LlmNotReadyException(event.message))
                    else -> Unit
                }
            }
        }
        try {
            helper.load(path, CONTEXT_LENGTH) { ready.complete(Unit) }
            withTimeout(LOAD_TIMEOUT_MS) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            runCatching { helper.release() }
            throw LlmNotReadyException("Tempo esgotado ao carregar o modelo GGUF")
        } catch (e: LlmNotReadyException) {
            runCatching { helper.release() }
            throw e
        } catch (e: CancellationException) {
            // Caller cancelled (e.g. navigated away) — release and propagate, don't mask it.
            runCatching { helper.release() }
            throw e
        } catch (e: Exception) {
            runCatching { helper.release() }
            throw LlmNotReadyException(e.message ?: "Falha ao carregar o modelo GGUF")
        } finally {
            watcher.cancel()
        }

        llamaHelper = helper
        llamaLoadedPath = path
        Log.i(TAG, "llama.cpp (GGUF) engine initialized")
        helper
    }

    private fun generateGguf(prompt: String, path: String): Flow<String> = callbackFlow {
        val helper = ensureLlama(path)
        // Subscribe to the shared event stream first, then start prediction from onSubscription so
        // no early tokens are missed (the SharedFlow has no replay).
        val collector = launch {
            llamaEvents
                .onSubscription { helper.predict(prompt) }
                .collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> trySend(event.word)
                        is LlamaHelper.LLMEvent.Done -> close()
                        is LlamaHelper.LLMEvent.Error -> close(RuntimeException(event.message))
                        else -> Unit
                    }
                }
        }
        awaitClose {
            runCatching { helper.stopPrediction() }
            collector.cancel()
        }
    }.flowOn(ioDispatcher)

    override fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedModelPath = null
        runCatching { llamaHelper?.release() }
        llamaHelper = null
        llamaLoadedPath = null
    }

    companion object {
        private const val TAG = "LlmRepository"

        // Conservative context budget so the engine initializes across small models
        // (e.g. Gemma 3 1B with ~1280 ekv). The document is chunked to fit this.
        private const val MAX_TOKENS = 1024

        // llama.cpp context window (tokens). The document is already chunked small upstream; this
        // leaves comfortable room for the prompt plus the generated response.
        private const val CONTEXT_LENGTH = 2048

        // Upper bound for loading a GGUF model into memory before treating it as a failure.
        private const val LOAD_TIMEOUT_MS = 120_000L

        private const val TOP_K = 40
        private const val TOP_P = 0.95f

        // Low temperature for deterministic, structured (JSON) extraction.
        private const val TEMPERATURE = 0.3f
    }
}
