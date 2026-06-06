package dev.lorenzods.anonimizadorpdf.presentation.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.data.preferences.AppPreferences
import dev.lorenzods.anonimizadorpdf.domain.repository.LlmRepository
import dev.lorenzods.anonimizadorpdf.domain.repository.PdfRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val pdfRepository: PdfRepository,
    private val llmRepository: LlmRepository,
) : ViewModel() {

    val modelPath: StateFlow<String?> =
        preferences.modelPath.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val systemPrompt: StateFlow<String> = preferences.systemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.DEFAULT_SYSTEM_PROMPT)

    val exportFolderUri: StateFlow<String?> =
        preferences.exportFolderUri.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _copying = MutableStateFlow(false)
    val copying: StateFlow<Boolean> = _copying.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _copying.value = true
            try {
                val path = llmRepository.importModel(uri)
                preferences.setModelPath(path)
            } catch (e: Exception) {
                emit(R.string.error_import)
            } finally {
                _copying.value = false
            }
        }
    }

    fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            preferences.setSystemPrompt(prompt)
            emit(R.string.prompt_saved)
        }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch {
            preferences.setSystemPrompt(AppPreferences.DEFAULT_SYSTEM_PROMPT)
            emit(R.string.prompt_saved)
        }
    }

    fun setExportFolder(uri: String) {
        viewModelScope.launch { preferences.setExportFolderUri(uri) }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            pdfRepository.deleteAll()
            emit(R.string.all_data_deleted)
        }
    }

    private suspend fun emit(@StringRes resId: Int) = _messages.emit(resId)
}
