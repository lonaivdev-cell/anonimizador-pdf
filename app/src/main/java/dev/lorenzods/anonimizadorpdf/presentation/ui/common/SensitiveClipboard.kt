package dev.lorenzods.anonimizadorpdf.presentation.ui.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Copies clinical text to the clipboard flagged as sensitive, so it is hidden from the Android 13+
 * clipboard preview overlay and skipped by clipboard-history keyboards and cross-device clipboard
 * sync that honor the flag (sync would be a network egress path outside the app's no-INTERNET
 * guarantee). Compose's ClipboardManager cannot attach the flag, hence the platform API.
 */
fun copySensitiveText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(null, text).apply {
        description.extras = PersistableBundle(1).apply {
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                // Same literal the API 33 constant resolves to; a no-op extra on 31/32.
                "android.content.extra.IS_SENSITIVE"
            }
            putBoolean(key, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
