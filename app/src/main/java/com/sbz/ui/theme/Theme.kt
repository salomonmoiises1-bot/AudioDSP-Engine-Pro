package com.sbz.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SbzCyan,
    onPrimary = SbzBackground,
    primaryContainer = SbzCyanDim,
    onPrimaryContainer = SbzTextPrimary,
    secondary = SbzAmber,
    onSecondary = SbzBackground,
    background = SbzBackground,
    onBackground = SbzTextPrimary,
    surface = SbzSurface,
    onSurface = SbzTextPrimary,
    surfaceVariant = SbzSurfaceVariant,
    onSurfaceVariant = SbzTextSecondary,
    outline = SbzBorder
)

@Composable
fun SbzTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SbzBackground.toArgb()
            window.navigationBarColor = SbzBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
