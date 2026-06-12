@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class,
)

package dev.lorenzods.anonimizadorpdf.presentation.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.EmptyState
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.StatusPill
import dev.lorenzods.anonimizadorpdf.presentation.ui.viewer.DocumentViewer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    isExpanded: Boolean,
    sharedPdfUri: Uri?,
    onSharedConsumed: () -> Unit,
    onNavigateToAnonymize: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val scope = rememberCoroutineScope()
    var selectedDocId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Multi-select: the picked PDFs are copied into internal storage immediately, so the temporary
    // read grant from the picker is sufficient — no persistable permission needed.
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAndExtract(uris)
        }
    }

    LaunchedEffect(sharedPdfUri) {
        if (sharedPdfUri != null) {
            viewModel.importAndExtract(sharedPdfUri)
            onSharedConsumed()
        }
    }

    val deletedText = stringResource(R.string.deleted_snackbar)
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.Message -> snackbarHostState.showSnackbar(context.getString(event.resId))
                is LibraryEvent.UndoDelete -> {
                    val result = snackbarHostState.showSnackbar(message = deletedText, actionLabel = undoText)
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.document)
                    } else {
                        viewModel.confirmDelete(event.document)
                    }
                }
            }
        }
    }

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                LibraryListPane(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onQueryChange = viewModel::onQueryChange,
                    onFilterChange = viewModel::onFilterChange,
                    onImportClick = { pickPdf.launch(arrayOf("application/pdf")) },
                    onOpen = { id ->
                        selectedDocId = id
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                    },
                    onDelete = viewModel::requestDelete,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val id = selectedDocId
                if (id != null) {
                    DocumentViewer(
                        documentId = id,
                        showBack = navigator.canNavigateBack(),
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onNavigateToAnonymize = onNavigateToAnonymize,
                    )
                } else {
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = stringResource(R.string.select_document),
                        message = stringResource(R.string.select_document_message),
                    )
                }
            }
        },
    )
}

@Composable
private fun LibraryListPane(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (StatusFilter) -> Unit,
    onImportClick: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (PdfDocument) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.library_title)) })
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                FilterChipsRow(uiState.filter, onFilterChange)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImportClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.import_pdf)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.documents.isEmpty() && uiState.query.isBlank() && uiState.filter == StatusFilter.ALL ->
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.Description,
                        title = stringResource(R.string.empty_library_title),
                        message = stringResource(R.string.empty_library_message),
                        actionLabel = stringResource(R.string.import_pdf),
                        onAction = onImportClick,
                    )

                uiState.documents.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.SearchOff,
                    title = stringResource(R.string.empty_filter_title),
                    message = stringResource(R.string.empty_filter_message),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.documents, key = { it.id }) { doc ->
                        SwipeableDocumentRow(
                            document = doc,
                            versionCount = uiState.versionCounts[doc.id] ?: 0,
                            onClick = { onOpen(doc.id) },
                            onDelete = { onDelete(doc) },
                        )
                    }
                }
            }

            uiState.extraction?.let { ExtractionOverlay(it) }
        }
    }
}

@Composable
private fun FilterChipsRow(selected: StatusFilter, onFilterChange: (StatusFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val labels = listOf(
            StatusFilter.ALL to R.string.filter_all,
            StatusFilter.RAW to R.string.filter_raw,
            StatusFilter.PROCESSED to R.string.filter_processed,
            StatusFilter.ANONYMIZED to R.string.filter_anonymized,
        )
        labels.forEach { (filter, labelRes) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun SwipeableDocumentRow(
    document: PdfDocument,
    versionCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        DocumentCard(document, versionCount, onClick)
    }
}

@Composable
private fun DocumentCard(document: PdfDocument, versionCount: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = document.originalFilename,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val pages = pluralStringResource(R.plurals.page_count, document.pageCount, document.pageCount)
                Text(
                    text = "${formatDate(document.importTimestamp)} · $pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(document.status)
                    if (versionCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                Icons.Filled.HistoryEdu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = versionCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractionOverlay(state: ExtractionState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator()
                val label = when {
                    state.docCount > 1 && state.total > 0 -> stringResource(
                        R.string.extracting_multi_progress,
                        state.docIndex, state.docCount, state.current, state.total,
                    )

                    state.docCount > 1 ->
                        stringResource(R.string.extracting_multi, state.docIndex, state.docCount)

                    state.total > 0 ->
                        stringResource(R.string.extracting_progress, state.current, state.total)

                    else -> stringResource(R.string.extracting)
                }
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(timestamp))
