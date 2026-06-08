package dev.lorenzods.anonimizadorpdf.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.lorenzods.anonimizadorpdf.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = md_primary,
    onPrimary = md_onPrimary,
    primaryContainer = md_primaryContainer,
    onPrimaryContainer = md_onPrimaryContainer,
    secondary = md_secondary,
    onSecondary = md_onSecondary,
    secondaryContainer = md_secondaryContainer,
    onSecondaryContainer = md_onSecondaryContainer,
    tertiary = md_tertiary,
    onTertiary = md_onTertiary,
    tertiaryContainer = md_tertiaryContainer,
    onTertiaryContainer = md_onTertiaryContainer,
    error = md_error,
    onError = md_onError,
    errorContainer = md_errorContainer,
    onErrorContainer = md_onErrorContainer,
    background = md_background,
    onBackground = md_onBackground,
    surface = md_surface,
    onSurface = md_onSurface,
    surfaceVariant = md_surfaceVariant,
    onSurfaceVariant = md_onSurfaceVariant,
    outline = md_outline,
    outlineVariant = md_outlineVariant,
    surfaceContainerLowest = md_surfaceContainerLowest,
    surfaceContainerLow = md_surfaceContainerLow,
    surfaceContainer = md_surfaceContainer,
    surfaceContainerHigh = md_surfaceContainerHigh,
    surfaceContainerHighest = md_surfaceContainerHighest,
)

private val DarkColors = darkColorScheme(
    primary = md_primary_dark,
    onPrimary = md_onPrimary_dark,
    primaryContainer = md_primaryContainer_dark,
    onPrimaryContainer = md_onPrimaryContainer_dark,
    secondary = md_secondary_dark,
    onSecondary = md_onSecondary_dark,
    secondaryContainer = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    tertiary = md_tertiary_dark,
    onTertiary = md_onTertiary_dark,
    tertiaryContainer = md_tertiaryContainer_dark,
    onTertiaryContainer = md_onTertiaryContainer_dark,
    error = md_error_dark,
    onError = md_onError_dark,
    errorContainer = md_errorContainer_dark,
    onErrorContainer = md_onErrorContainer_dark,
    background = md_background_dark,
    onBackground = md_onBackground_dark,
    surface = md_surface_dark,
    onSurface = md_onSurface_dark,
    surfaceVariant = md_surfaceVariant_dark,
    onSurfaceVariant = md_onSurfaceVariant_dark,
    outline = md_outline_dark,
    outlineVariant = md_outlineVariant_dark,
    surfaceContainerLowest = md_surfaceContainerLowest_dark,
    surfaceContainerLow = md_surfaceContainerLow_dark,
    surfaceContainer = md_surfaceContainer_dark,
    surfaceContainerHigh = md_surfaceContainerHigh_dark,
    surfaceContainerHighest = md_surfaceContainerHighest_dark,
)

/** Extra brand colors that don't map to a Material role, exposed via [LocalBrandColors]. */
data class BrandColors(
    val gradient: Brush,
    val redactionHighlight: Color,
    val onRedactionHighlight: Color,
    val isDark: Boolean,
)

val LocalBrandColors = staticCompositionLocalOf {
    BrandColors(
        gradient = Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)),
        redactionHighlight = RedactionHighlightLight,
        onRedactionHighlight = OnRedactionHighlightLight,
        isDark = false,
    )
}

/** Convenience accessor mirroring [MaterialTheme]. */
object AppTheme {
    val brand: BrandColors
        @Composable get() = LocalBrandColors.current
}

@Composable
fun AnonimizadorTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }

    val brand = BrandColors(
        gradient = if (dark) {
            Brush.linearGradient(listOf(BrandGradientStartDark, BrandGradientEndDark))
        } else {
            Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd))
        },
        redactionHighlight = if (dark) RedactionHighlightDark else RedactionHighlightLight,
        onRedactionHighlight = if (dark) OnRedactionHighlightDark else OnRedactionHighlightLight,
        isDark = dark,
    )

    CompositionLocalProvider(LocalBrandColors provides brand) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
