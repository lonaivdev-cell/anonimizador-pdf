package dev.lorenzods.anonimizadorpdf.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = Cyan700,
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = OnCyanContainerLight,
    tertiary = Amber700,
    onTertiary = Color.White,
    error = ErrorRed,
    onError = Color.White,
    surface = SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = Teal200,
    onPrimary = Color(0xFF003731),
    primaryContainer = Teal600,
    onPrimaryContainer = TealContainerLight,
    secondary = Cyan200,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Cyan700,
    onSecondaryContainer = CyanContainerLight,
    tertiary = Amber200,
    onTertiary = Color(0xFF402D00),
    surface = SurfaceDark,
)

/**
 * App theme. Dynamic color is intentionally disabled so the clinical palette is deterministic
 * across devices.
 */
@Composable
fun AnonimizadorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
