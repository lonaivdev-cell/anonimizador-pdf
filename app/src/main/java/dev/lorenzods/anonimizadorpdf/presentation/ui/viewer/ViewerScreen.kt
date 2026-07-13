@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.lorenzods.anonimizadorpdf.presentation.ui.viewer

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.AnonymizedVersion
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.domain.usecase.ApplyRedactionsUseCase
import dev.lorenzods.anonimizadorpdf.presentation.theme.AppTheme
import dev.lorenzods.anonimizadorpdf.presentation.theme.DocumentTextStyle
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.EmptyState
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.copySensitiveText
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Standalone viewer route (opened from Home). */
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onNavigateToAnonymize: (Long) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    DocumentViewer(
        documentId = null,
        showBack = true,
        onBack = onBack,
        onNavigateToAnonymize = onNavigateToAnonymize,
        viewModel = viewModel,
    )
}

/**
 * The viewer body. Reused both as a full route and as the Library detail pane. When [documentId] is
 * non-null it is pushed into the ViewModel (pane usage); otherwise the id comes from nav args.
 */
@Composable
fun DocumentViewer(
    documentId: Long?,
    showBack: Boolean,
    onBack: () -> Unit,
    onNavigateToAnonymize: (Long) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    if (documentId != null) {
        LaunchedEffect(documentId) { viewModel.setDocument(documentId) }
    }
    val doc by viewModel.document.collectAsStateWithLifecycle()
    val versions by viewModel.versions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var previewVersion by remember { mutableStateOf<AnonymizedVersion?>(null) }

    val copiedMsg = stringResource(R.string.copied)
    val exportedMsg = stringResource(R.string.exported)
    val exportFailedMsg = stringResource(R.string.export_failed)

    // Single export launcher shared by the original text and any version preview.
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = pendingExport
        if (uri != null && text != null) {
            // "wt" truncates an existing document picked through SAF; plain "w" may leave a stale tail.
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            }.isSuccess
            scope.launch { snackbarHostState.showSnackbar(if (ok) exportedMsg else exportFailedMsg) }
        }
        pendingExport = null
    }

    Scaffold(
        topBar = {
            val current = doc
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = current?.displayName ?: stringResource(R.string.viewer_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (current != null) {
                            Text(
                                text = stringResource(
                                    R.string.viewer_meta,
                                    pluralStringResource(R.plurals.page_count, current.pageCount, current.pageCount),
                                    pluralStringResource(R.plurals.word_count, wordCount(current.extractedText), wordCount(current.extractedText)),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    val current2 = doc
                    if (current2 != null && selectedTab == 0) {
                        IconButton(onClick = {
                            copySensitiveText(context, current2.extractedText)
                            scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                        }) { Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy)) }
                        IconButton(onClick = {
                            pendingExport = current2.extractedText
                            exportLauncher.launch(suggestTxtName(current2.displayName))
                        }) { Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.export_txt)) }
                    }
                },
            )
        },
        floatingActionButton = {
            val current = doc
            if (current != null && current.extractedText.isNotBlank() && selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToAnonymize(current.id) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    text = { Text(stringResource(R.string.anonymize_action)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val current = doc
            when {
                current == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.viewer_tab_original)) },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                val label = stringResource(R.string.viewer_tab_versions)
                                Text(if (versions.isEmpty()) label else "$label (${versions.size})")
                            },
                        )
                    }
                    when (selectedTab) {
                        0 -> OriginalTab(current)
                        else -> VersionsTab(
                            versions = versions,
                            onOpen = { previewVersion = it },
                            onCopy = { v ->
                                copySensitiveText(context, v.anonymizedText)
                                scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                            },
                            onShare = { v -> scope.launch { shareText(context, versionFileName(v), v.anonymizedText) } },
                            onDelete = viewModel::deleteVersion,
                        )
                    }
                }
            }
        }
    }

    previewVersion?.let { version ->
        VersionPreviewDialog(
            version = version,
            onDismiss = { previewVersion = null },
            onCopy = {
                copySensitiveText(context, version.anonymizedText)
                scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
            },
            onExport = {
                pendingExport = version.anonymizedText
                exportLauncher.launch(versionFileName(version))
            },
            onShare = { scope.launch { shareText(context, versionFileName(version), version.anonymizedText) } },
        )
    }
}

@Composable
private fun OriginalTab(doc: PdfDocument) {
    if (doc.extractedText.isBlank()) {
        EmptyState(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Filled.Warning,
            title = stringResource(R.string.empty_text),
            message = stringResource(R.string.error_no_text),
        )
        return
    }
    SelectionContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = doc.extractedText,
            style = DocumentTextStyle,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun VersionsTab(
    versions: List<AnonymizedVersion>,
    onOpen: (AnonymizedVersion) -> Unit,
    onCopy: (AnonymizedVersion) -> Unit,
    onShare: (AnonymizedVersion) -> Unit,
    onDelete: (AnonymizedVersion) -> Unit,
) {
    if (versions.isEmpty()) {
        EmptyState(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Filled.HistoryEdu,
            title = stringResource(R.string.versions_empty_title),
            message = stringResource(R.string.versions_empty_message),
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        versions.forEach { version ->
            VersionCard(
                version,
                onOpen = { onOpen(version) },
                onCopy = { onCopy(version) },
                onShare = { onShare(version) },
                onDelete = { onDelete(version) },
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun VersionCard(
    version: AnonymizedVersion,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.version_created, formatDateTime(version.createdTimestamp)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.term_count,
                            version.redactedTerms.size,
                            version.redactedTerms.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = version.anonymizedText.take(160).replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.version_open)) }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.copy)) }
                TextButton(onClick = onShare) { Text(stringResource(R.string.version_share)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.version_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionPreviewDialog(
    version: AnonymizedVersion,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.version_preview_title)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                        actions = {
                            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy)) }
                            IconButton(onClick = onExport) { Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.export_txt)) }
                            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share)) }
                        },
                    )
                },
            ) { padding ->
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = highlightPlaceholders(version.anonymizedText),
                        style = DocumentTextStyle,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun highlightPlaceholders(text: String): AnnotatedString {
    val background = AppTheme.brand.redactionHighlight
    val foreground = AppTheme.brand.onRedactionHighlight
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

internal fun wordCount(text: String): Int =
    if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size

private fun suggestTxtName(filename: String): String =
    filename.substringBeforeLast('.').ifBlank { "documento" } + ".txt"

/**
 * Anonymized exports use a neutral, timestamped name. The original PDF is often named after the
 * patient, so deriving the export name from it would leak exactly what the redaction removed.
 */
private fun versionFileName(version: AnonymizedVersion): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale("pt", "BR")).format(Date(version.createdTimestamp))
    return "anonimizado_$stamp.txt"
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(Date(timestamp))

internal fun shareText(context: Context, filename: String, content: String) {
    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    // Staged copies are cleartext clinical data: drop anything left from earlier shares so the
    // only residue on disk is the file currently handed to the share sheet (swept again on app
    // start and by "Apagar todos os dados").
    dir.listFiles()?.forEach { runCatching { it.delete() } }
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
