package dev.lorenzods.anonimizadorpdf.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface LlmRepository {
    /** True if a model path is configured and the model file exists. */
    suspend fun isModelAvailable(): Boolean

    /**
     * Copies a picked model (`.task`/`.litertlm` for MediaPipe, or `.gguf` for llama.cpp) into
     * app-internal storage — the engines require a filesystem path, not a SAF content Uri — and
     * returns the absolute path. The file extension selects the inference engine. Replaces any
     * previous model.
     */
    suspend fun importModel(uri: Uri): String

    /**
     * Streams generated tokens for [prompt]. The returned [Flow] emits partial results as they are
     * produced and completes when generation finishes.
     *
     * Collecting throws [LlmNotReadyException] if no model is configured / the file is missing.
     */
    fun generate(prompt: String): Flow<String>

    /** Releases native resources held by the inference engine. */
    fun close()
}

/** Thrown when inference is requested but no usable model is configured. */
class LlmNotReadyException(message: String) : Exception(message)
