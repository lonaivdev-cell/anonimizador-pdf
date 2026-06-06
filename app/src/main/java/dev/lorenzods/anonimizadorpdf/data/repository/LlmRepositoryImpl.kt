package dev.lorenzods.anonimizadorpdf.data.repository

import android.content.Context
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM inference via the MediaPipe LLM Inference Task API.
 *
 * The heavy [LlmInference] engine is created lazily from the model path stored in preferences and
 * reused across calls; a fresh [LlmInferenceSession] is created per generation. Tokens are streamed
 * through a [callbackFlow] bridging the MediaPipe progress listener.
 */
@Singleton
class LlmRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LlmRepository {

    @Volatile
    private var engine: LlmInference? = null

    @Volatile
    private var loadedModelPath: String? = null

    override suspend fun isModelAvailable(): Boolean {
        val path = preferences.modelPath.first()
        return !path.isNullOrBlank() && File(path).exists()
    }

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
            .setMaxTopK(TOP_K)
            .build()
        val created = LlmInference.createFromOptions(context, options)
        engine = created
        loadedModelPath = path
        Log.i(TAG, "LLM engine initialized")
        created
    }

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        val activeEngine = ensureEngine()
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
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

    override fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedModelPath = null
    }

    companion object {
        private const val TAG = "LlmRepository"
        private const val MAX_TOKENS = 2048
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.6f
    }
}
