package dev.lorenzods.anonimizadorpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.lorenzods.anonimizadorpdf.presentation.navigation.AppShell
import dev.lorenzods.anonimizadorpdf.presentation.navigation.RootViewModel
import dev.lorenzods.anonimizadorpdf.presentation.theme.AnonimizadorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // A PDF arriving via share (ACTION_SEND) or open (ACTION_VIEW). Held as Compose state so it can
    // be delivered both on first launch and on every later share routed to this single instance.
    private var sharedPdfUri by mutableStateOf<Uri?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Read the launch intent only on a genuine first creation — on a config-change recreation
        // the same shared PDF would otherwise be imported again.
        if (savedInstanceState == null) {
            sharedPdfUri = extractPdfUri(intent)
        }

        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by rootViewModel.dynamicColor.collectAsStateWithLifecycle()
            AnonimizadorTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val windowSizeClass = calculateWindowSizeClass(this)
                AppShell(
                    windowSizeClass = windowSizeClass,
                    sharedPdfUri = sharedPdfUri,
                    onSharedConsumed = { sharedPdfUri = null },
                    rootViewModel = rootViewModel,
                )
            }
        }
    }

    /**
     * With `android:launchMode="singleTask"`, a PDF shared while the app is already running is
     * delivered here instead of spawning a new activity instance.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPdfUri(intent)?.let { sharedPdfUri = it }
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
