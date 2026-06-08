@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.lorenzods.anonimizadorpdf.presentation.ui.anonymize

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.RedactionCategory
import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.presentation.theme.AppTheme
import dev.lorenzods.anonimizadorpdf.presentation.theme.DocumentTextStyle
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.InfoBanner
import dev.lorenzods.anonimizadorpdf.presentation.ui.viewer.highlightPlaceholders
import dev.lorenzods.anonimizadorpdf.presentation.ui.viewer.shareText
import kotlinx.coroutines.launch

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

    uiState.errorText?.let { message ->
        LaunchedEffect(message) {
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

    val preview = uiState.previewText

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
        bottomBar = {
            if (preview == null && uiState.document != null) {
                ApplyBar(count = uiState.selectedCount, onApply = viewModel::applyRedactions)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (preview != null) {
                PreviewContent(
                    previewText = preview,
                    selectedCount = uiState.selectedCount,
                    onEdit = viewModel::dismissPreview,
                    onSave = viewModel::save,
                    onExport = { exportLauncher.launch(exportName()) },
                    onShare = { scope.launch { shareText(context, exportName(), preview) } },
                )
            } else {
                EditContent(
                    uiState = uiState,
                    onSetMode = viewModel::setMode,
                    onDetect = viewModel::runDetection,
                    onReview = viewModel::reviewWithLlm,
                    onDeepScan = viewModel::deepScan,
                    onStop = viewModel::stopGenerating,
                    onToggleChip = viewModel::toggleChip,
                    onToggleWord = viewModel::toggleWord,
                    onAddManual = viewModel::addManualTerm,
                    onSelectAll = viewModel::selectAll,
                    onClearAll = viewModel::clearAll,
                    onNavigateToSettings = onNavigateToSettings,
                )
            }
        }
    }
}

@Composable
private fun ApplyBar(count: Int, onApply: () -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Button(
            onClick = onApply,
            enabled = count > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                if (count > 0) {
                    stringResource(R.string.apply_selected) + "  ·  $count"
                } else {
                    stringResource(R.string.apply_selected)
                },
            )
        }
    }
}

@Composable
private fun EditContent(
    uiState: AnonymizeUiState,
    onSetMode: (AnonymizeMode) -> Unit,
    onDetect: () -> Unit,
    onReview: () -> Unit,
    onDeepScan: () -> Unit,
    onStop: () -> Unit,
    onToggleChip: (String) -> Unit,
    onToggleWord: (String) -> Unit,
    onAddManual: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
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
            AnonymizeMode.AUTO -> ModeAuto(uiState, onDetect, onReview, onDeepScan, onStop, onNavigateToSettings)
            AnonymizeMode.MANUAL -> ModeManual(onAddManual)
        }

        ChipsSection(uiState, onToggleChip, onSelectAll, onClearAll)

        DocumentSection(uiState, onToggleWord)

        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun ModeAuto(
    uiState: AnonymizeUiState,
    onDetect: () -> Unit,
    onReview: () -> Unit,
    onDeepScan: () -> Unit,
    onStop: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    // Stage 1 summary — always available, fully offline.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = if (uiState.chips.isEmpty()) {
                    stringResource(R.string.auto_summary_empty)
                } else {
                    stringResource(R.string.auto_summary, uiState.chips.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDetect, enabled = !uiState.busy) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.detect_again))
        }
        FilledTonalButton(onClick = onReview, enabled = uiState.modelAvailable && !uiState.busy && uiState.chips.isNotEmpty()) {
            Icon(Icons.Filled.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.review_with_ai))
        }
        FilledTonalButton(onClick = onDeepScan, enabled = uiState.modelAvailable && !uiState.busy) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.deep_scan))
        }
    }

    if (!uiState.modelAvailable) {
        InfoBanner(
            icon = Icons.Filled.Settings,
            text = stringResource(R.string.ai_needs_model),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        TextButton(onClick = onNavigateToSettings) { Text(stringResource(R.string.go_to_settings)) }
    }

    if (uiState.reviewing) {
        BusyCard(stringResource(R.string.reviewing), onStop)
    }

    if (uiState.generating) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    val thinking = stringResource(R.string.thinking)
                    Text(
                        text = uiState.progress?.let { "$thinking ($it)" } ?: thinking,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (uiState.streamingText.isNotBlank()) {
                    Text(
                        text = uiState.streamingText,
                        style = DocumentTextStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
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
private fun BusyCard(label: String, onStop: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onStop) { Text(stringResource(R.string.stop)) }
        }
    }
}

@Composable
private fun ModeManual(onAddManual: (String) -> Unit) {
    var term by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = term,
            onValueChange = { term = it },
            label = { Text(stringResource(R.string.term_to_remove)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                onAddManual(term)
                term = ""
            },
            enabled = term.isNotBlank(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
private fun ChipsSection(
    uiState: AnonymizeUiState,
    onToggleChip: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.redaction_list),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (uiState.chips.isNotEmpty()) {
            Text(
                text = stringResource(R.string.redactions_selected, uiState.selectedCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (uiState.chips.isEmpty()) {
        Text(
            text = stringResource(if (uiState.mode == AnonymizeMode.AUTO) R.string.no_suggestions else R.string.no_terms),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.select_all_suggestions)) }
        TextButton(onClick = onClearAll) { Text(stringResource(R.string.clear_all)) }
    }

    if (uiState.chips.any { it.filteredByAi }) {
        Text(
            text = stringResource(R.string.filtered_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val grouped = uiState.chips.groupBy { it.category }
    categoryOrder.forEach { category ->
        val items = grouped[category] ?: return@forEach
        Text(
            text = categoryLabel(category),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { chip ->
                FilterChip(
                    selected = chip.selected,
                    onClick = { onToggleChip(chip.term) },
                    label = { Text(if (chip.filteredByAi) "✦ ${chip.term}" else chip.term) },
                )
            }
        }
    }
}

@Composable
private fun DocumentSection(uiState: AnonymizeUiState, onToggleWord: (String) -> Unit) {
    InfoBanner(
        icon = Icons.Filled.TouchApp,
        text = stringResource(R.string.tap_to_redact_hint),
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        RedactableText(
            text = uiState.document?.extractedText.orEmpty(),
            selectedTerms = uiState.selectedTerms,
            highlightBackground = AppTheme.brand.redactionHighlight,
            highlightForeground = AppTheme.brand.onRedactionHighlight,
            style = DocumentTextStyle,
            onToggleWord = onToggleWord,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        )
    }
}

@Composable
private fun PreviewContent(
    previewText: String,
    selectedCount: Int,
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

        val occurrences = countOccurrences(previewText, ApplyRedactionsUseCase.PLACEHOLDER)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    R.string.preview_summary,
                    pluralStringResource(R.plurals.term_count, selectedCount, selectedCount),
                    occurrences,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp),
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
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

private val categoryOrder = listOf(
    RedactionCategory.NAME,
    RedactionCategory.DOCUMENT,
    RedactionCategory.CONTACT,
    RedactionCategory.DATE,
    RedactionCategory.ADDRESS,
    RedactionCategory.ORGANIZATION,
    RedactionCategory.OTHER,
)

@Composable
private fun categoryLabel(category: RedactionCategory): String = stringResource(
    when (category) {
        RedactionCategory.NAME -> R.string.category_name
        RedactionCategory.DOCUMENT -> R.string.category_document
        RedactionCategory.CONTACT -> R.string.category_contact
        RedactionCategory.DATE -> R.string.category_date
        RedactionCategory.ADDRESS -> R.string.category_address
        RedactionCategory.ORGANIZATION -> R.string.category_organization
        RedactionCategory.OTHER -> R.string.category_other
    },
)

private fun countOccurrences(text: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var index = text.indexOf(needle)
    while (index >= 0) {
        count++
        index = text.indexOf(needle, index + needle.length)
    }
    return count
}

private fun exportName(): String = "anonimizado_${System.currentTimeMillis()}.txt"
