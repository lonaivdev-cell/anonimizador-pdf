@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.lorenzods.anonimizadorpdf.presentation.ui.viewer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.presentation.theme.DocumentTextStyle
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.EmptyState
import kotlinx.coroutines.launch

@Composable
fun TextViewerPane(
    documentId: Long,
    showBack: Boolean,
    onBack: () -> Unit,
    onNavigateToAnonymize: (Long, Boolean) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    LaunchedEffect(documentId) { viewModel.setDocument(documentId) }
    val document by viewModel.document.collectAsStateWithLifecycle()
    val doc = document

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val copiedMsg = stringResource(R.string.copied)
    val exportedMsg = stringResource(R.string.exported)
    val exportFailedMsg = stringResource(R.string.export_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val current = doc
        if (uri != null && current != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(current.extractedText.toByteArray())
                }
            }.isSuccess
            scope.launch { snackbarHostState.showSnackbar(if (ok) exportedMsg else exportFailedMsg) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = doc?.originalFilename ?: stringResource(R.string.viewer_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (doc != null) {
                            Text(
                                text = pluralStringResource(R.plurals.page_count, doc.pageCount, doc.pageCount),
                                style = MaterialTheme.typography.labelSmall,
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
                    IconButton(
                        onClick = {
                            doc?.let {
                                clipboard.setText(AnnotatedString(it.extractedText))
                                scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                            }
                        },
                    ) { Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy)) }

                    IconButton(
                        onClick = { doc?.let { exportLauncher.launch(suggestTxtName(it.originalFilename)) } },
                    ) { Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.export_txt)) }
                },
            )
        },
        floatingActionButton = {
            if (doc != null && doc.extractedText.isNotBlank()) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { onNavigateToAnonymize(doc.id, true) },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        text = { Text(stringResource(R.string.mark_manually)) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(),
                    )
                    ExtendedFloatingActionButton(
                        onClick = { onNavigateToAnonymize(doc.id, false) },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                        text = { Text(stringResource(R.string.suggest_anonymization)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                doc == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                doc.extractedText.isBlank() -> EmptyState(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.empty_text),
                    message = stringResource(R.string.error_no_text),
                )

                else -> SelectionContainer(
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
        }
    }
}

private fun suggestTxtName(filename: String): String =
    filename.substringBeforeLast('.').ifBlank { "documento" } + ".txt"
