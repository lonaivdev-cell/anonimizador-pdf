package dev.lorenzods.anonimizadorpdf.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lorenzods.anonimizadorpdf.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "anonimizador_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val MODEL_PATH = stringPreferencesKey("model_path")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val EXPORT_FOLDER_URI = stringPreferencesKey("export_folder_uri")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FORMAT_OUTPUT = booleanPreferencesKey("format_output")
    }

    val modelPath: Flow<String?> = context.dataStore.data.map { it[Keys.MODEL_PATH] }

    val systemPrompt: Flow<String> =
        context.dataStore.data.map { it[Keys.SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT }

    val exportFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.EXPORT_FOLDER_URI] }

    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { ThemeMode.fromName(it[Keys.THEME_MODE]) }

    /** Material You dynamic color. Defaults to false so the clinical palette is deterministic. */
    val dynamicColor: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: false }

    /**
     * Whether the anonymized output is post-formatted for LLM reading (OutputFormatter). Defaults
     * to false: the untouched redacted text is always the safe baseline.
     */
    val formatOutput: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FORMAT_OUTPUT] ?: false }

    suspend fun setModelPath(path: String) =
        context.dataStore.edit { it[Keys.MODEL_PATH] = path }

    suspend fun setSystemPrompt(prompt: String) =
        context.dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }

    suspend fun setExportFolderUri(uri: String) =
        context.dataStore.edit { it[Keys.EXPORT_FOLDER_URI] = uri }

    suspend fun setOnboardingDone(done: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setFormatOutput(enabled: Boolean) =
        context.dataStore.edit { it[Keys.FORMAT_OUTPUT] = enabled }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "Você é um assistente de anonimização de dados médicos (LGPD). Analise o trecho de texto " +
                "clínico fornecido e identifique todos os dados pessoais que devem ser removidos: nomes " +
                "completos de pacientes e familiares, CPF, RG, datas de nascimento, números de telefone, " +
                "endereços, e-mails, nomes de médicos e nomes de instituições, clínicas ou hospitais. " +
                "Copie do texto exatamente os trechos a remover. Responda APENAS com um array JSON de " +
                "strings, sem explicações e sem texto adicional. Se não houver nada a remover, responda []."
    }
}
