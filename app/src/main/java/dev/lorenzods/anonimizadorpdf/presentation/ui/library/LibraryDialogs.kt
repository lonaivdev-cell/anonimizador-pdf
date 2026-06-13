package dev.lorenzods.anonimizadorpdf.presentation.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.Folder
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument

/** Generic single-line text prompt — reused for creating/renaming folders and renaming documents. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Generic confirm/cancel dialog (used for bulk delete). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Folder picker for moving documents: root, every folder, and a "new folder" shortcut. */
@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    onMove: (Long?) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(
                    text = stringResource(R.string.move_to_folder),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.size(12.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        FolderPickRow(
                            icon = Icons.Filled.FolderOpen,
                            label = stringResource(R.string.move_to_root),
                            onClick = { onMove(null) },
                        )
                    }
                    itemsIndexed(folders) { _, folder ->
                        FolderPickRow(
                            icon = Icons.Filled.Folder,
                            label = folder.name,
                            onClick = { onMove(folder.id) },
                        )
                    }
                    item {
                        FolderPickRow(
                            icon = Icons.Filled.CreateNewFolder,
                            label = stringResource(R.string.new_folder),
                            onClick = onCreateNew,
                            highlight = true,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun FolderPickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val tint =
            if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Combine prompt: name the output and arrange the chosen documents top-to-bottom (page order) with
 * up/down controls. The list starts in the order the documents were selected.
 */
@Composable
fun CombinePdfsDialog(
    documents: List<PdfDocument>,
    defaultName: String,
    onConfirm: (orderedIds: List<Long>, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var ordered by remember { mutableStateOf(documents) }
    var name by rememberSaveable { mutableStateOf(defaultName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.combine_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.combine_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.combine_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(ordered, key = { _, doc -> doc.id }) { index, doc ->
                        ReorderRow(
                            position = index + 1,
                            document = doc,
                            canMoveUp = index > 0,
                            canMoveDown = index < ordered.lastIndex,
                            onUp = { ordered = ordered.swap(index, index - 1) },
                            onDown = { ordered = ordered.swap(index, index + 1) },
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = { onConfirm(ordered.map { it.id }, name.trim()) },
                        enabled = name.isNotBlank() && ordered.size >= 2,
                    ) { Text(stringResource(R.string.combine_action)) }
                }
            }
        }
    }
}

@Composable
private fun ReorderRow(
    position: Int,
    document: PdfDocument,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.page_count, document.pageCount, document.pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
            }
            IconButton(onClick = onDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
            }
        }
    }
}

/** Offered right after a combine: share or export the new PDF (it is already in the library). */
@Composable
fun CombinedPdfReadyDialog(
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
        title = { Text(stringResource(R.string.combine_done_title)) },
        text = { Text(stringResource(R.string.combine_done_message)) },
        confirmButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.share)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onExport) { Text(stringResource(R.string.export_pdf)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
    )
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this
    return toMutableList().also { it[a] = this[b]; it[b] = this[a] }
}
