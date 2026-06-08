package dev.lorenzods.anonimizadorpdf.presentation.ui.onboarding

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.presentation.theme.AppTheme
import dev.lorenzods.anonimizadorpdf.presentation.ui.common.PrivacyBadge
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val linkRes: Int? = null,
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(Icons.Filled.Shield, R.string.onboarding_1_title, R.string.onboarding_1_desc),
        OnboardingPage(Icons.Filled.UploadFile, R.string.onboarding_2_title, R.string.onboarding_2_desc),
        OnboardingPage(Icons.Filled.TouchApp, R.string.onboarding_3_title, R.string.onboarding_3_desc),
        OnboardingPage(
            icon = Icons.Filled.Download,
            titleRes = R.string.onboarding_4_title,
            descRes = R.string.onboarding_4_desc,
            linkRes = R.string.onboarding_3_link,
        ),
    )
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gemmaUrl = stringResource(R.string.onboarding_gemma_url)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrivacyBadge(
                    modifier = Modifier.padding(start = 12.dp),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(132.dp)
                            .clip(CircleShape)
                            .background(AppTheme.brand.gradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(page.descRes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (page.linkRes != null) {
                        Spacer(Modifier.height(24.dp))
                        FilledTonalButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, gemmaUrl.toUri())) }
                        }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(page.linkRes))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        label = "dotColor",
                    )
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) { Text(stringResource(R.string.onboarding_back)) }
                }
                Spacer(Modifier.weight(1f))
                val isLast = pagerState.currentPage == pages.lastIndex
                Button(onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }) {
                    Text(stringResource(if (isLast) R.string.onboarding_done else R.string.onboarding_next))
                }
            }
        }
    }
}
