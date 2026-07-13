@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.lorenzods.anonimizadorpdf.presentation.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.Folder
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.EmptyState
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.StatusPill
import dev.lorenzods.anonimizadorpdf.presentation.ui.viewer.DocumentViewer
import kotlinx.coroutines.launch
import java.io.File
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
    val combinedPdf by viewModel.combinedPdf.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val scope = rememberCoroutineScope()
    var selectedDocId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importAndExtract(uris)
    }

    // Exports the combined PDF to a user-chosen location (SAF). The source is the in-library copy.
    val exportedMsg = stringResource(R.string.exported)
    val exportFailedMsg = stringResource(R.string.export_failed)
    var pendingExportPath by remember { mutableStateOf<String?>(null) }
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val path = pendingExportPath
        if (uri != null && path != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    File(path).inputStream().use { it.copyTo(out) }
                }
            }.isSuccess
            scope.launch { snackbarHostState.showSnackbar(if (ok) exportedMsg else exportFailedMsg) }
        }
        pendingExportPath = null
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

    // Selection is confined to the current scope, so the back button should clear it first.
    BackHandler(enabled = uiState.selectionMode) { viewModel.clearSelection() }
    BackHandler(enabled = !uiState.selectionMode && uiState.currentFolder != null) { viewModel.exitFolder() }
    BackHandler(enabled = !uiState.selectionMode && uiState.currentFolder == null && navigator.canNavigateBack()) {
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
                    viewModel = viewModel,
                    onImportClick = { pickPdf.launch(arrayOf("application/pdf")) },
                    onOpen = { id ->
                        selectedDocId = id
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                    },
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

    combinedPdf?.let { combined ->
        CombinedPdfReadyDialog(
            onShare = {
                sharePdf(context, combined.shareName, File(combined.path))
                viewModel.dismissCombinedPdf()
            },
            onExport = {
                pendingExportPath = combined.path
                exportPdfLauncher.launch(combined.shareName)
                viewModel.dismissCombinedPdf()
            },
            onDismiss = { viewModel.dismissCombinedPdf() },
        )
    }
}

@Composable
private fun LibraryListPane(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: LibraryViewModel,
    onImportClick: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    // Dialog/menu state driven by list interactions.
    var showSortMenu by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameFolderTarget by remember { mutableStateOf<Folder?>(null) }
    var renameDocTarget by remember { mutableStateOf<PdfDocument?>(null) }
    var moveTarget by remember { mutableStateOf<List<Long>?>(null) }
    var createFolderForMove by remember { mutableStateOf<List<Long>?>(null) }
    var combineTarget by remember { mutableStateOf<List<PdfDocument>?>(null) }
    var deleteConfirm by remember { mutableStateOf<List<PdfDocument>?>(null) }

    val selectedDocs = remember(uiState.selectedIds, uiState.documents) {
        uiState.selectedIds.mapNotNull { id -> uiState.documents.firstOrNull { it.id == id } }
    }

    Scaffold(
        topBar = {
            if (uiState.selectionMode) {
                SelectionTopBar(
                    count = uiState.selectedIds.size,
                    canCombine = uiState.selectedIds.size >= 2,
                    onClose = viewModel::clearSelection,
                    onFavorite = {
                        val allFav = selectedDocs.isNotEmpty() && selectedDocs.all { it.isFavorite }
                        viewModel.setFavorite(uiState.selectedIds, !allFav)
                    },
                    onMove = { moveTarget = uiState.selectedIds },
                    onCombine = { combineTarget = selectedDocs },
                    onDelete = { deleteConfirm = selectedDocs },
                )
            } else {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = uiState.currentFolder?.name ?: stringResource(R.string.library_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            if (uiState.currentFolder != null) {
                                IconButton(onClick = viewModel::exitFolder) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.sort_by))
                                }
                                SortMenu(
                                    expanded = showSortMenu,
                                    current = uiState.sort,
                                    onSelect = {
                                        viewModel.onSortChange(it)
                                        showSortMenu = false
                                    },
                                    onDismiss = { showSortMenu = false },
                                )
                            }
                        },
                    )
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    FilterChipsRow(uiState.filter, viewModel::onFilterChange)
                }
            }
        },
        floatingActionButton = {
            if (!uiState.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onImportClick,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.import_pdf)) },
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
            val showFolders = uiState.currentFolder == null && !uiState.searching && !uiState.selectionMode
            when {
                uiState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                uiState.documents.isEmpty() && uiState.folders.isEmpty() &&
                    uiState.query.isBlank() && uiState.filter == StatusFilter.ALL &&
                    uiState.currentFolder == null ->
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.Description,
                        title = stringResource(R.string.empty_library_title),
                        message = stringResource(R.string.empty_library_message),
                        actionLabel = stringResource(R.string.import_pdf),
                        onAction = onImportClick,
                    )

                uiState.documents.isEmpty() && !showFolders ->
                    EmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = if (uiState.searching) Icons.Filled.SearchOff else Icons.Filled.Folder,
                        title = stringResource(
                            if (uiState.searching) R.string.empty_filter_title else R.string.empty_folder_title,
                        ),
                        message = stringResource(
                            if (uiState.searching) R.string.empty_filter_message else R.string.empty_folder_message,
                        ),
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showFolders) {
                        item(key = "folders") {
                            FoldersRow(
                                folders = uiState.folders,
                                counts = uiState.folderCounts,
                                onOpen = viewModel::openFolder,
                                onRename = { renameFolderTarget = it },
                                onDelete = viewModel::deleteFolder,
                                onNewFolder = { showNewFolder = true },
                            )
                        }
                    }
                    items(uiState.documents, key = { it.id }) { doc ->
                        DocumentRow(
                            document = doc,
                            versionCount = uiState.versionCounts[doc.id] ?: 0,
                            selectionMode = uiState.selectionMode,
                            selected = doc.id in uiState.selectedIds,
                            onOpen = { onOpen(doc.id) },
                            onToggleSelect = { viewModel.toggleSelection(doc.id) },
                            onStartSelection = { viewModel.startSelection(doc.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(doc) },
                            onRename = { renameDocTarget = doc },
                            onMove = { moveTarget = listOf(doc.id) },
                            onDelete = { viewModel.requestDelete(doc) },
                        )
                    }
                }
            }

            if (uiState.combining) {
                LoadingOverlay(stringResource(R.string.combining))
            }
            uiState.extraction?.let { ExtractionOverlay(it) }
        }
    }

    // --- Dialogs ---

    if (showNewFolder) {
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            label = stringResource(R.string.folder_name_label),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                viewModel.createFolder(it)
                showNewFolder = false
            },
            onDismiss = { showNewFolder = false },
        )
    }

    renameFolderTarget?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.folder_rename),
            label = stringResource(R.string.folder_name_label),
            initial = folder.name,
            confirmLabel = stringResource(R.string.rename),
            onConfirm = {
                viewModel.renameFolder(folder, it)
                renameFolderTarget = null
            },
            onDismiss = { renameFolderTarget = null },
        )
    }

    renameDocTarget?.let { doc ->
        TextInputDialog(
            title = stringResource(R.string.rename_document),
            label = stringResource(R.string.document_name_label),
            initial = doc.displayName,
            confirmLabel = stringResource(R.string.rename),
            onConfirm = {
                viewModel.renameDocument(doc.id, it)
                renameDocTarget = null
            },
            onDismiss = { renameDocTarget = null },
        )
    }

    moveTarget?.let { ids ->
        MoveToFolderDialog(
            folders = uiState.folders,
            onMove = { folderId ->
                viewModel.moveDocuments(ids, folderId)
                moveTarget = null
            },
            onCreateNew = {
                createFolderForMove = ids
                moveTarget = null
            },
            onDismiss = { moveTarget = null },
        )
    }

    createFolderForMove?.let { ids ->
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            label = stringResource(R.string.folder_name_label),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                viewModel.createFolderAndMove(it, ids)
                createFolderForMove = null
            },
            onDismiss = { createFolderForMove = null },
        )
    }

    combineTarget?.let { docs ->
        CombinePdfsDialog(
            documents = docs,
            defaultName = defaultCombineName(),
            onConfirm = { orderedIds, name ->
                viewModel.combineSelected(orderedIds, name)
                combineTarget = null
            },
            onDismiss = { combineTarget = null },
        )
    }

    deleteConfirm?.let { docs ->
        ConfirmDialog(
            title = stringResource(R.string.delete_documents_title),
            message = stringResource(R.string.delete_documents_message, docs.size),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.deleteDocuments(docs)
                deleteConfirm = null
            },
            onDismiss = { deleteConfirm = null },
        )
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    canCombine: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onCombine: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selection_count, count, count)) },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite))
            }
            IconButton(onClick = onMove) {
                Icon(Icons.Filled.Folder, contentDescription = stringResource(R.string.move))
            }
            IconButton(onClick = onCombine, enabled = canCombine) {
                Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.combine))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
        },
    )
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = listOf(
        SortMode.RECENT to R.string.sort_recent,
        SortMode.OLDEST to R.string.sort_oldest,
        SortMode.NAME_ASC to R.string.sort_name_asc,
        SortMode.NAME_DESC to R.string.sort_name_desc,
        SortMode.PAGES_DESC to R.string.sort_pages_desc,
        SortMode.PAGES_ASC to R.string.sort_pages_asc,
    )
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        labels.forEach { (mode, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                onClick = { onSelect(mode) },
                trailingIcon = {
                    if (mode == current) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    }
}

@Composable
private fun FoldersRow(
    folders: List<Folder>,
    counts: Map<Long, Int>,
    onOpen: (Long) -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
    onNewFolder: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(folders, key = { it.id }) { folder ->
            FolderChip(
                folder = folder,
                count = counts[folder.id] ?: 0,
                onOpen = { onOpen(folder.id) },
                onRename = { onRename(folder) },
                onDelete = { onDelete(folder) },
            )
        }
        item(key = "new_folder") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = onNewFolder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(stringResource(R.string.new_folder), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FolderChip(
    folder: Folder,
    count: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.folder_doc_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_rename)) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: PdfDocument,
    versionCount: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    onStartSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    if (selectionMode) {
        DocumentCard(
            document = document,
            versionCount = versionCount,
            selected = selected,
            onClick = onToggleSelect,
            onLongClick = onToggleSelect,
            trailing = {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            },
        )
        return
    }

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
        DocumentCard(
            document = document,
            versionCount = versionCount,
            selected = false,
            onClick = onOpen,
            onLongClick = onStartSelection,
            trailing = {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (document.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(
                            if (document.isFavorite) R.string.unfavorite else R.string.favorite,
                        ),
                        tint = if (document.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                DocumentOverflowMenu(onRename = onRename, onMove = onMove, onDelete = onDelete)
            },
        )
    }
}

@Composable
private fun DocumentCard(
    document: PdfDocument,
    versionCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    text = document.displayName,
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
            trailing()
        }
    }
}

@Composable
private fun DocumentOverflowMenu(
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_to_folder)) },
                onClick = { open = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = { open = false; onDelete() },
            )
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
private fun LoadingOverlay(label: String) {
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
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ExtractionOverlay(state: ExtractionState) {
    val label = when {
        state.docCount > 1 && state.total > 0 -> stringResource(
            R.string.extracting_multi_progress,
            state.docIndex, state.docCount, state.current, state.total,
        )

        state.docCount > 1 -> stringResource(R.string.extracting_multi, state.docIndex, state.docCount)
        state.total > 0 -> stringResource(R.string.extracting_progress, state.current, state.total)
        else -> stringResource(R.string.extracting)
    }
    LoadingOverlay(label)
}

private fun sharePdf(context: Context, filename: String, source: File) {
    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    // Staged copies are cleartext clinical data: drop anything left from earlier shares so the
    // only residue on disk is the file currently handed to the share sheet (swept again on app
    // start and by "Apagar todos os dados").
    dir.listFiles()?.forEach { runCatching { it.delete() } }
    // Stage a copy under the chosen name so the shared file carries a clean filename.
    val staged = File(dir, filename)
    runCatching { source.copyTo(staged, overwrite = true) }
    val file = if (staged.exists()) staged else source
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun defaultCombineName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale("pt", "BR")).format(Date())
    return "Combinado_$stamp.pdf"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(timestamp))
