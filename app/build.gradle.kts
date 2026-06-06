plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.lorenzods.anonimizadorpdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.lorenzods.anonimizadorpdf"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Minification kept off: this app is built/distributed as a debug-style personal tool
            // and CI verifies assembleDebug. Keep rules are provided for when release is enabled.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Adaptive layout (two-pane on tablet)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt (KSP)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room (KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (preferences)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // JSON parsing of LLM output
    implementation(libs.kotlinx.serialization.json)

    // On-device LLM (MediaPipe) — exclude Google Play Services transitives (offline app)
    implementation(libs.mediapipe.tasks.genai) {
        exclude(group = "com.google.android.gms")
    }

    // PDF text extraction
    implementation(libs.pdfbox.android)

    // Unit tests (pure logic)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
