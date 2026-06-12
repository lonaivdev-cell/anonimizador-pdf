@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.lorenzods.anonimizadorpdf.presentation.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.BuildConfig
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.ThemeMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val modelPath by viewModel.modelPath.collectAsStateWithLifecycle()
    val persistedPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val exportFolder by viewModel.exportFolderUri.collectAsStateWithLifecycle()
    val copying by viewModel.copying.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val learnedTermsCount by viewModel.learnedTermsCount.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var promptDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val promptText = promptDraft ?: persistedPrompt

    LaunchedEffect(Unit) {
        viewModel.messages.collect { resId -> snackbarHostState.showSnackbar(context.getString(resId)) }
    }

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.importModel(uri)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setExportFolder(uri.toString())
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Appearance
            SettingsCard(stringResource(R.string.settings_section_appearance)) {
                Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    )
                    modes.forEachIndexed { index, (mode, labelRes) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(stringResource(labelRes)) }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.dynamic_color_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                }
            }

            // Model
            SettingsCard(stringResource(R.string.settings_section_model)) {
                if (modelPath != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.model_loaded), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    text = modelPath ?: stringResource(R.string.no_model_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pickModel.launch(arrayOf("*/*")) }, enabled = !copying) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pick_model))
                    }
                    if (copying) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            // Prompt
            SettingsCard(stringResource(R.string.settings_section_prompt)) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptDraft = it },
                    label = { Text(stringResource(R.string.settings_prompt_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        viewModel.resetSystemPrompt()
                        promptDraft = null
                    }) { Text(stringResource(R.string.reset_prompt)) }
                    Button(onClick = { viewModel.saveSystemPrompt(promptText) }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }

            // Export
            SettingsCard(stringResource(R.string.settings_section_export)) {
                Text(
                    text = exportFolder ?: stringResource(R.string.no_folder_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { pickFolder.launch(null) }) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pick_folder))
                }
            }

            // Learned terms (offline learning)
            SettingsCard(stringResource(R.string.settings_section_learning)) {
                Text(
                    stringResource(R.string.learned_terms_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.learned_terms_count,
                        learnedTermsCount,
                        learnedTermsCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (learnedTermsCount > 0) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = viewModel::clearLearnedTerms) {
                        Text(stringResource(R.string.learned_terms_clear))
                    }
                }
            }

            // Data
            SettingsCard(stringResource(R.string.settings_section_data)) {
                OutlinedButton(onClick = viewModel::replayOnboarding) {
                    Icon(Icons.Filled.Replay, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.replay_onboarding))
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete_all_data)) }
            }

            // About
            SettingsCard(stringResource(R.string.settings_section_about)) {
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.about_license_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.about_license_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val repoUrl = stringResource(R.string.about_repo_url)
                TextButton(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, repoUrl.toUri())) } },
                    contentPadding = PaddingValues(0.dp),
                ) { Text(stringResource(R.string.about_repo)) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_all_data_title)) },
            text = { Text(stringResource(R.string.delete_all_data_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAllData()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}
