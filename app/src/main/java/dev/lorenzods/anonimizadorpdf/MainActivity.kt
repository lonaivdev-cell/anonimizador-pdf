package dev.lorenzods.anonimizadorpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.lorenzods.anonimizadorpdf.presentation.navigation.AppShell
import dev.lorenzods.anonimizadorpdf.presentation.theme.AnonimizadorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val sharedPdfUri = extractPdfUri(intent)

        setContent {
            AnonimizadorTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                AppShell(
                    windowSizeClass = windowSizeClass,
                    sharedPdfUri = sharedPdfUri,
                )
            }
        }
    }

    /** Returns a PDF Uri if the activity was launched from a share/view intent. */
    private fun extractPdfUri(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}
