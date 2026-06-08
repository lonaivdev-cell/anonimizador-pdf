package dev.lorenzods.anonimizadorpdf.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseline = Typography()

/**
 * Typography tuned for a reading-heavy clinical tool: slightly tighter, heavier display/headline
 * weights for hierarchy, comfortable body. Uses the platform default family (no bundled fonts —
 * keeps the offline APK lean).
 */
val AppTypography = Typography(
    displaySmall = baseline.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = baseline.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = baseline.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = baseline.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = baseline.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = baseline.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = baseline.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Style for displaying extracted / anonymized document text. */
val DocumentTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 21.sp,
)
