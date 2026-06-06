package dev.lorenzods.anonimizadorpdf.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
    }

    val modelPath: Flow<String?> = context.dataStore.data.map { it[Keys.MODEL_PATH] }

    val systemPrompt: Flow<String> =
        context.dataStore.data.map { it[Keys.SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT }

    val exportFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.EXPORT_FOLDER_URI] }

    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setModelPath(path: String) =
        context.dataStore.edit { it[Keys.MODEL_PATH] = path }

    suspend fun setSystemPrompt(prompt: String) =
        context.dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }

    suspend fun setExportFolderUri(uri: String) =
        context.dataStore.edit { it[Keys.EXPORT_FOLDER_URI] = uri }

    suspend fun setOnboardingDone(done: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "Você é um assistente de anonimização de dados médicos. Revise o texto clínico abaixo " +
                "(registro de conversa ou resultado laboratorial de paciente) e retorne APENAS um array " +
                "JSON de strings que devem ser removidas ou substituídas para anonimização conforme a " +
                "LGPD. Inclua: nomes completos de pacientes, CPF, datas de nascimento, números de " +
                "telefone, endereços, e-mails, nomes de médicos e instituições mencionados. " +
                "Retorne SOMENTE JSON válido, sem texto adicional. Formato: [\"termo1\", \"termo2\", ...]"
    }
}
