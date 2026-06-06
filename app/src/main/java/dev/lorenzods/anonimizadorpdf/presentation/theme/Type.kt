package dev.lorenzods.anonimizadorpdf.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val baseline = Typography()

val AppTypography = Typography(
    bodyLarge = baseline.bodyLarge.copy(fontFamily = FontFamily.Default),
    titleLarge = baseline.titleLarge.copy(fontFamily = FontFamily.Default),
    labelSmall = baseline.labelSmall.copy(fontFamily = FontFamily.Default),
    // Monospace-friendly body for displaying extracted document text.
)

/** Style for displaying extracted / anonymized document text. */
val DocumentTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
