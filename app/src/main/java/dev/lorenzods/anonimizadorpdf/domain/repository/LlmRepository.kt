package dev.lorenzods.anonimizadorpdf.domain.repository

import kotlinx.coroutines.flow.Flow

interface LlmRepository {
    /** True if a model path is configured and the model file exists. */
    suspend fun isModelAvailable(): Boolean

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
