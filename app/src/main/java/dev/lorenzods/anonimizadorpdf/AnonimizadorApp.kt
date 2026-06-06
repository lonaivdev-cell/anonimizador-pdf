package dev.lorenzods.anonimizadorpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AnonimizadorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Required before any pdfbox-android usage.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
