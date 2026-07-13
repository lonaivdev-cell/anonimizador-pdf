package dev.lorenzods.anonimizadorpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class AnonimizadorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Required before any pdfbox-android usage.
        PDFBoxResourceLoader.init(applicationContext)
        sweepCleartextResidue()
    }

    /**
     * Best-effort startup sweep of cleartext clinical residue: files staged for the share sheet
     * (`filesDir/exports`) and pdfbox scratch files left in cache if a previous process died
     * mid-merge/extraction. By the time the app starts again, any past share has long been handed
     * off, so these copies are pure liability.
     */
    private fun sweepCleartextResidue() {
        Thread {
            runCatching { File(filesDir, "exports").deleteRecursively() }
            runCatching {
                cacheDir.listFiles()
                    ?.filter { it.name.startsWith("PDFBox", ignoreCase = true) }
                    ?.forEach { it.delete() }
            }
        }.start()
    }
}
