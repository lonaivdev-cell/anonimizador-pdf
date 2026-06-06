@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.lorenzods.anonimizadorpdf.presentation.ui.anonymize

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.presentation.theme.DocumentTextStyle
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AnonymizeScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: AnonymizeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AnonymizeEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.resId))
            }
        }
    }

    uiState.error?.let { res ->
        val message = stringResource(res)
        LaunchedEffect(res) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = uiState.previewText
        if (uri != null && text != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anonymize_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val preview = uiState.previewText
            if (preview != null) {
                PreviewContent(
                    previewText = preview,
                    onEdit = viewModel::dismissPreview,
                    onSave = viewModel::save,
                    onExport = { exportLauncher.launch(exportName()) },
                    onShare = { scope.launch { shareTextFile(context, exportName(), preview) } },
                )
            } else {
                EditContent(
                    uiState = uiState,
                    onSetMode = viewModel::setMode,
                    onGenerate = viewModel::generate,
                    onStop = viewModel::stopGenerating,
                    onToggleChip = viewModel::toggleChip,
                    onAddManual = viewModel::addManualTerm,
                    onApply = viewModel::applyRedactions,
                    onNavigateToSettings = onNavigateToSettings,
                )
            }
        }
    }
}

@Composable
private fun EditContent(
    uiState: AnonymizeUiState,
    onSetMode: (AnonymizeMode) -> Unit,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onToggleChip: (String) -> Unit,
    onAddManual: (String) -> Unit,
    onApply: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = uiState.mode == AnonymizeMode.AUTO,
                onClick = { onSetMode(AnonymizeMode.AUTO) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.mode_a_title)) }
            SegmentedButton(
                selected = uiState.mode == AnonymizeMode.MANUAL,
                onClick = { onSetMode(AnonymizeMode.MANUAL) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.mode_b_title)) }
        }

        when (uiState.mode) {
            AnonymizeMode.AUTO -> ModeAuto(uiState, onGenerate, onStop, onNavigateToSettings)
            AnonymizeMode.MANUAL -> ModeManual(uiState, onAddManual)
        }

        ChipsSection(uiState, onToggleChip)

        Button(
            onClick = onApply,
            enabled = uiState.selectedTerms.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.apply_selected)) }
    }
}

@Composable
private fun ModeAuto(
    uiState: AnonymizeUiState,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    if (!uiState.modelAvailable) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.no_model_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.no_model_message), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.go_to_settings))
                }
            }
        }
        return
    }

    Button(
        onClick = onGenerate,
        enabled = !uiState.generating,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (uiState.chips.isEmpty()) R.string.generate else R.string.regenerate))
    }

    if (uiState.generating) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.thinking), style = MaterialTheme.typography.titleSmall)
                }
                if (uiState.streamingText.isNotBlank()) {
                    Text(
                        text = uiState.streamingText,
                        style = DocumentTextStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stop))
                }
            }
        }
    }
}

@Composable
private fun ModeManual(uiState: AnonymizeUiState, onAddManual: (String) -> Unit) {
    val doc = uiState.document
    var fieldValue by remember(doc?.id) {
        mutableStateOf(TextFieldValue(doc?.extractedText.orEmpty()))
    }
    val hasSelection = !fieldValue.selection.collapsed

    Text(stringResource(R.string.selection_hint), style = MaterialTheme.typography.bodyMedium)

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { fieldValue = it },
        readOnly = true,
        textStyle = DocumentTextStyle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 320.dp),
    )

    Button(
        onClick = {
            val selection = fieldValue.selection
            if (!selection.collapsed) {
                onAddManual(fieldValue.text.substring(selection.min, selection.max))
            }
        },
        enabled = hasSelection,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.add_to_list))
    }
}

@Composable
private fun ChipsSection(uiState: AnonymizeUiState, onToggleChip: (String) -> Unit) {
    Text(stringResource(R.string.redaction_list), style = MaterialTheme.typography.titleMedium)
    if (uiState.chips.isEmpty()) {
        Text(
            text = stringResource(if (uiState.mode == AnonymizeMode.AUTO) R.string.no_suggestions else R.string.no_terms),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.chips.forEach { chip ->
                FilterChip(
                    selected = chip.selected,
                    onClick = { onToggleChip(chip.term) },
                    label = { Text(chip.term) },
                )
            }
        }
    }
}

@Composable
private fun PreviewContent(
    previewText: String,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.preview_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Text(
                text = highlightPlaceholders(previewText),
                style = DocumentTextStyle,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            FilledTonalButton(onClick = onExport) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_txt))
            }
            FilledTonalButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share))
            }
            Button(onClick = onSave) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun highlightPlaceholders(text: String): AnnotatedString {
    val background = MaterialTheme.colorScheme.tertiaryContainer
    val foreground = MaterialTheme.colorScheme.onTertiaryContainer
    val placeholder = ApplyRedactionsUseCase.PLACEHOLDER
    return remember(text, background, foreground) {
        buildAnnotatedString {
            var index = 0
            while (true) {
                val found = text.indexOf(placeholder, index)
                if (found < 0) {
                    append(text.substring(index))
                    break
                }
                append(text.substring(index, found))
                withStyle(SpanStyle(background = background, color = foreground)) {
                    append(placeholder)
                }
                index = found + placeholder.length
            }
        }
    }
}

private fun exportName(): String = "anonimizado_${System.currentTimeMillis()}.txt"

private fun shareTextFile(context: Context, filename: String, content: String) {
    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    val file = File(dir, filename)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
