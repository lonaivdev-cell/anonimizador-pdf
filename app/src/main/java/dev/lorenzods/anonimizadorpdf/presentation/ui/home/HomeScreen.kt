@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.lorenzods.anonimizadorpdf.presentation.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.domain.model.PdfDocument
import dev.lorenzods.anonimizadorpdf.presentation.theme.AppTheme
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.PrivacyBadge
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.SectionHeader
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.StatusPill
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importAndExtract(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { resId -> snackbarHostState.showSnackbar(context.getString(resId)) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HeroHeader()

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    StatsRow(uiState)

                    ModelStatusCard(uiState.modelAvailable, onOpenSettings)

                    ImportCard(onClick = { pickPdf.launch(arrayOf("application/pdf")) })

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.home_recent),
                            actionLabel = if (uiState.recent.isNotEmpty()) stringResource(R.string.nav_library) else null,
                            onAction = if (uiState.recent.isNotEmpty()) onOpenLibrary else null,
                        )
                        if (uiState.recent.isEmpty() && !uiState.loading) {
                            Text(
                                text = stringResource(R.string.home_recent_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            uiState.recent.forEach { doc ->
                                RecentDocumentRow(doc, onClick = { onOpenDocument(doc.id) })
                            }
                        }
                    }
                }
            }

            uiState.extraction?.let { ExtractionOverlay(it.current, it.total, it.docIndex, it.docCount) }
        }
    }
}

@Composable
private fun HeroHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.brand.gradient, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                PrivacyBadge()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(26.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_privacy_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_privacy_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsRow(state: HomeUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Description,
            value = state.totalDocuments.toString(),
            label = stringResource(R.string.home_stat_documents),
            container = MaterialTheme.colorScheme.secondaryContainer,
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.VerifiedUser,
            value = state.anonymizedDocuments.toString(),
            label = stringResource(R.string.home_stat_anonymized),
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Shield,
            value = state.termsRemoved.toString(),
            label = stringResource(R.string.home_stat_terms),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    Surface(color = container, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = onContainer)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.8f),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ModelStatusCard(available: Boolean, onConfigure: () -> Unit) {
    val container = if (available) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(color = container, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            Icon(
                if (available) Icons.Filled.CheckCircle else Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (available) R.string.home_model_ready else R.string.home_model_missing),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(if (available) R.string.home_model_ready_desc else R.string.home_model_missing_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!available) {
                FilledTonalButton(onClick = onConfigure) { Text(stringResource(R.string.home_model_configure)) }
            }
        }
    }
}

@Composable
private fun ImportCard(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_action_import),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = stringResource(R.string.home_action_import_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun RecentDocumentRow(doc: PdfDocument, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = doc.originalFilename,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDate(doc.importTimestamp)} · " +
                        pluralStringResource(R.plurals.page_count, doc.pageCount, doc.pageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(doc.status)
        }
    }
}

@Composable
private fun ExtractionOverlay(current: Int, total: Int, docIndex: Int, docCount: Int) {
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
                    docCount > 1 && total > 0 ->
                        stringResource(R.string.extracting_multi_progress, docIndex, docCount, current, total)

                    docCount > 1 -> stringResource(R.string.extracting_multi, docIndex, docCount)
                    total > 0 -> stringResource(R.string.extracting_progress, current, total)
                    else -> stringResource(R.string.extracting)
                }
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun greeting(): String {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(timestamp))
